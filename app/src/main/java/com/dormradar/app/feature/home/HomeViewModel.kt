package com.dormradar.app.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.ble.BleClient
import com.dormradar.app.core.ble.BleScanDevice
import com.dormradar.app.core.data.SettingsStore
import com.dormradar.app.core.data.HomeCardLayout
import com.dormradar.app.core.data.HomeCardId
import com.dormradar.app.core.device.ConnState
import com.dormradar.app.core.device.DeviceInfo
import com.dormradar.app.core.device.GateEnergies
import com.dormradar.app.core.device.OpResult
import com.dormradar.app.core.device.RadarConnection
import com.dormradar.app.core.device.RadarState
import com.dormradar.app.core.log.DistanceDiagRecorder
import com.dormradar.app.core.monitor.MonitorMode
import com.dormradar.app.service.RadarMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 扫描列表条目（去重 + 排序用） */
data class ScanItem(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val isRadar: Boolean,
)

/** 能量曲线采样点（首页图表区，docs/06 阶段 2 任务 4） */
data class EnergySample(
    val moveEnergy: Int,
    val staticEnergy: Int,
    val detectDistanceCm: Int,
    val at: Long,
)

/**
 * 首页状态：未连接 → 扫描面板；已连接/连接中 → 设备监控面板（PRD 4.0 简单层）。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bleClient: BleClient,
    private val connection: RadarConnection,
    private val settings: SettingsStore,
    private val diagRecorder: DistanceDiagRecorder,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    companion object {
        private const val SCAN_AUTO_STOP_MS = 30_000L
        /** 能量曲线保留的采样点数（上报约 1 次/秒 ≈ 2 分钟窗口） */
        private const val ENERGY_WINDOW = 120
        /** 显示防抖：目标短暂丢失（走动中信号波动）保持显示的时长 */
        private const val TARGET_HOLD_MS = 2_000L
        /** 疑似静态反射：连续有人判定时长 */
        private const val STATIC_SUSPECT_MS = 60_000L
        /** 疑似静态反射：检测距离波动小于该值（cm）视为"不变" */
        private const val STATIC_DIST_SPREAD_CM = 15

        /** 近场饱和判据：读数上界不超过该值（cm）——落在最近的距离门里 */
        private const val NEAR_FIELD_CM = 50
    }

    val connState: StateFlow<ConnState> = connection.connState
    val radarState: StateFlow<RadarState?> = connection.radarState
    val deviceInfo: StateFlow<DeviceInfo?> = connection.deviceInfo

    // ---- 首页卡片自定义（用户需求 2026-10-07）----
    val gateEnergies: StateFlow<GateEnergies?> = connection.gateEnergies
    val engineeringMode: StateFlow<Boolean> = connection.engineeringMode

    val homeCardLayout: StateFlow<HomeCardLayout> =
        settings.homeCardLayout.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeCardLayout.STANDARD)

    fun setEngineeringMode(enabled: Boolean) {
        viewModelScope.launch { runCatching { connection.setEngineeringMode(enabled) } }
    }

    fun saveCardLayout(layout: HomeCardLayout) {
        viewModelScope.launch { settings.setHomeCardLayout(layout) }
    }

    /** 编辑模式：把某张卡上移一位（只在显示中的卡之间移动；边界自动忽略） */
    fun moveCardUp(id: HomeCardId) = moveCard(id, -1)

    /** 编辑模式：把某张卡下移一位 */
    fun moveCardDown(id: HomeCardId) = moveCard(id, +1)

    private fun moveCard(id: HomeCardId, delta: Int) {
        val current = homeCardLayout.value.cards.toMutableList()
        val index = current.indexOfFirst { it.first == id }
        val target = index + delta
        if (index < 0 || target < 0 || target >= current.size) return
        val item = current.removeAt(index)
        current.add(target, item)
        saveCardLayout(HomeCardLayout(current))
    }

    /** 编辑模式：切某张卡的显示/隐藏（顺序不变） */
    fun toggleCard(id: HomeCardId, visible: Boolean) {
        val current = homeCardLayout.value.cards.map { if (it.first == id) it.first to visible else it }
        saveCardLayout(HomeCardLayout(current))
    }

    /** 配置会话进行中（拖门限写入时禁用编辑） */
    val configBusy: StateFlow<Boolean> = connection.configBusy

    /** 首页拖动门限的结果提示（短暂显示后清除） */
    private val _thresholdMessage = MutableStateFlow<String?>(null)
    val thresholdMessage: StateFlow<String?> = _thresholdMessage.asStateFlow()

    /**
     * 显示用平滑状态（消除走动时的闪烁抖动，用户反馈 2026-09-27）：
     * - 目标丢失后保持 [TARGET_HOLD_MS] 再隐藏（信号在门限附近波动时不闪）；
     * - 距离/能量做指数平滑（相邻帧平均），点不跳动。
     * 原始 [radarState] 保持设备真实值（能量曲线等用）。
     */
    private val _displayState = MutableStateFlow<RadarState?>(null)
    val displayState: StateFlow<RadarState?> = _displayState.asStateFlow()

    /**
     * 疑似静态反射提示（用户反馈 2026-09-28：贴门板后"距离一直不变、没人也显示有人"）：
     * 连续有人 ≥60s 且检测距离极差 <15cm → 大概率是门板/墙体静态反射被当成目标
     * （阈值低于环境底噪的典型症状），引导用户跑部署向导或调高静止阈值。
     */
    private val _suspectedStatic = MutableStateFlow(false)
    val suspectedStatic: StateFlow<Boolean> = _suspectedStatic.asStateFlow()

    /**
     * 疑似静态反射是否属于「近场饱和」——读数被钉在很小的值（≤ [NEAR_FIELD_CM]）。
     * 这两种情况的处置**完全不同**（用户实测反馈 2026-09-28）：
     * - 近场饱和（读数 ≤ 最小距离附近且无人也报有人）：探测面与目标之间夹了介质（门板/玻璃/金属），
     *   反射能量强过人体回波 → **调阈值救不回来**（阈值抬高只会连人一起屏蔽），必须改安装；
     * - 普通误报（读数分布正常）：阈值低于底噪 → 抬阈值/跑部署向导有效。
     */
    private val _suspectedNearField = MutableStateFlow(false)
    val suspectedNearField: StateFlow<Boolean> = _suspectedNearField.asStateFlow()

    private var presenceStartAt = 0L
    private var presenceDistMin = Int.MAX_VALUE
    private var presenceDistMax = 0

    fun dismissStaticHint() {
        _suspectedStatic.value = false
        _suspectedNearField.value = false
    }

    private val _energyHistory = MutableStateFlow<List<EnergySample>>(emptyList())
    val energyHistory: StateFlow<List<EnergySample>> = _energyHistory.asStateFlow()

    init {
        // 常驻滚动收集能量历史（VM 存活期间），曲线窗口随采样滑移
        viewModelScope.launch {
            connection.radarState.collect { s ->
                if (s != null) {
                    _energyHistory.value =
                        (_energyHistory.value + EnergySample(s.moveEnergy, s.staticEnergy, s.detectDistanceCm, s.updatedAt))
                            .takeLast(ENERGY_WINDOW)
                }
                // 疑似静态反射检测（真实原始帧，不用平滑值）
                if (s != null && s.hasTarget) {
                    if (presenceStartAt == 0L) {
                        presenceStartAt = s.updatedAt
                        presenceDistMin = Int.MAX_VALUE
                        presenceDistMax = 0
                    }
                    val d = s.detectDistanceCm
                    if (d > 0) {
                        if (d < presenceDistMin) presenceDistMin = d
                        if (d > presenceDistMax) presenceDistMax = d
                    }
                    if (!_suspectedStatic.value &&
                        s.updatedAt - presenceStartAt >= STATIC_SUSPECT_MS &&
                        presenceDistMax - presenceDistMin < STATIC_DIST_SPREAD_CM
                    ) {
                        _suspectedStatic.value = true
                        // 读数被钉在最小距离附近 → 近场饱和，处置是改安装而不是调参
                        _suspectedNearField.value = presenceDistMax <= NEAR_FIELD_CM
                    }
                } else {
                    presenceStartAt = 0L
                    _suspectedStatic.value = false
                    _suspectedNearField.value = false
                }
                // 显示防抖与平滑
                val prevDisplay = _displayState.value
                // 「目标丢失保持」窗口：设备已报无人，但 2 秒内继续显示上一帧（避免闪"无人"）。
                // 该窗口内的显示距离是**有意保留的旧值**，诊断记录器需要据此打标记，
                // 否则会被误读成"显示层在编造距离"（2026-09-28 审计）。
                val holding = s != null && !s.hasTarget && prevDisplay != null &&
                    prevDisplay.hasTarget && s.updatedAt - prevDisplay.updatedAt < TARGET_HOLD_MS
                val display = when {
                    s == null -> null
                    s.hasTarget ->
                        if (prevDisplay?.hasTarget == true) smoothDisplay(prevDisplay, s) else s

                    holding -> prevDisplay
                    else -> s
                }
                _displayState.value = display
                // 距离诊断记录器：回报显示管线实际输出值（含保持窗口标记）
                diagRecorder.noteDisplay(
                    hasTarget = display?.hasTarget == true,
                    detectDistanceCm = display?.detectDistanceCm ?: -1,
                    inHold = holding,
                )
            }
        }
        // 断开连接即重置疑似反射检测
        viewModelScope.launch {
            connection.connState.collect { state ->
                if (state !is ConnState.Ready) {
                    presenceStartAt = 0L
                    _suspectedStatic.value = false
                    _suspectedNearField.value = false
                }
            }
        }
    }

    /** 相邻帧指数平滑（(a+b+1)/2）；距离 0 表示该通道无目标，不平滑 */
    private fun smoothDisplay(prev: RadarState, new: RadarState): RadarState = new.copy(
        moveDistanceCm = ema(prev.moveDistanceCm, new.moveDistanceCm),
        moveEnergy = ema(prev.moveEnergy, new.moveEnergy),
        staticDistanceCm = ema(prev.staticDistanceCm, new.staticDistanceCm),
        staticEnergy = ema(prev.staticEnergy, new.staticEnergy),
    )

    private fun ema(a: Int, b: Int): Int = if (a <= 0 || b <= 0) b else (a + b + 1) / 2

    /**
     * 首页能量曲线拖门限（用户需求 2026-09-28）：只改目标门的单个通道，
     * 其余门沿用设备当前值，走 applySensitivityTable（含回读校验 + 近门容错）。
     * @param channel 0=运动 1=静止
     */
    fun setGateThreshold(gate: Int, channel: Int, value: Int) {
        if (value !in 0..100) return
        viewModelScope.launch {
            val params = connection.deviceInfo.value?.params ?: return@launch
            if (gate !in params.motionSensitivities.indices) return@launch
            val motion = params.motionSensitivities.copyOf()
            val static = params.staticSensitivities.copyOf()
            if (channel == 0) motion[gate] = value else static[gate] = value
            when (val result = connection.applySensitivityTable(motion, static)) {
                is OpResult.Ok -> _thresholdMessage.value =
                    "已更新：门 $gate ${if (channel == 0) "运动" else "静止"}门限 = $value"

                is OpResult.Fail -> _thresholdMessage.value = "门限更新失败：${result.reason}"
            }
        }
    }

    fun clearThresholdMessage() {
        _thresholdMessage.value = null
    }

    val fakeDataEnabled: StateFlow<Boolean> =
        settings.fakeDataEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _scanItems = MutableStateFlow<List<ScanItem>>(emptyList())
    val scanItems: StateFlow<List<ScanItem>> = _scanItems.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private var scanJob: Job? = null

    /** 开始 BLE 扫描（30 秒自动停止；需要已授予蓝牙权限） */
    fun startScan() {
        if (_scanning.value) return
        _scanItems.value = emptyList()
        _scanError.value = null
        _scanning.value = true
        scanJob = viewModelScope.launch {
            try {
                bleClient.scan().collect { device -> upsert(device) }
            } catch (e: Exception) {
                _scanError.value = e.message ?: "扫描失败"
            } finally {
                _scanning.value = false
            }
        }
        viewModelScope.launch {
            delay(SCAN_AUTO_STOP_MS)
            stopScan()
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanning.value = false
    }

    fun clearScanError() {
        _scanError.value = null
    }

    /** 连接设备（真实 MAC 或模拟模式的占位 MAC） */
    fun connect(mac: String) {
        stopScan()
        connection.connect(mac)
    }

    fun connectFake() = connect(FakeRadarMac)

    fun disconnect() = connection.disconnect()

    // ---- 后台监控（阶段 3）----

    /** 监控开关（设置中的运行意愿；服务启停据此同步） */
    val monitorEnabled: StateFlow<Boolean> =
        settings.monitorEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val monitorMode: StateFlow<MonitorMode> =
        settings.monitorMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitorMode.ALERT)

    /** MIUI 保活引导是否已展示过（首次开启监控时弹一次） */
    val keepAliveGuideShown: StateFlow<Boolean> =
        settings.keepAliveGuideShown.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun enableMonitor() {
        viewModelScope.launch {
            settings.setMonitorEnabled(true)
            RadarMonitorService.start(appContext)
        }
    }

    fun disableMonitor() {
        viewModelScope.launch {
            settings.setMonitorEnabled(false)
            RadarMonitorService.stop(appContext)
        }
    }

    fun markKeepAliveGuideShown() {
        viewModelScope.launch { settings.setKeepAliveGuideShown(true) }
    }

    /** 去重更新扫描列表：雷达设备排前，同组按信号强度降序 */
    private fun upsert(device: BleScanDevice) {
        val nameUpper = device.name?.uppercase()
        val isRadar = device.advertisesRadarService ||
            (nameUpper?.contains("LD2410") == true) ||
            (nameUpper?.startsWith("HLK") == true)
        val item = ScanItem(device.mac, device.name, device.rssi, isRadar)
        val current = _scanItems.value.toMutableList()
        val index = current.indexOfFirst { it.mac == device.mac }
        if (index >= 0) current[index] = item else current.add(item)
        _scanItems.value = current.sortedWith(
            compareByDescending<ScanItem> { it.isRadar }.thenByDescending { it.rssi },
        )
    }
}

/** 模拟模式使用的占位 MAC（与 FakeRadarSource 保持一致） */
const val FakeRadarMac = "FA:KE:RD:AR:00:01"

package com.dormradar.app.core.device

import com.dormradar.app.core.ble.BleClient
import com.dormradar.app.core.ble.BleEvent
import com.dormradar.app.core.data.DEFAULT_BLUETOOTH_PASSWORD
import com.dormradar.app.core.data.SettingsStore
import com.dormradar.app.core.log.DistanceDiagRecorder
import com.dormradar.app.core.log.FrameDir
import com.dormradar.app.core.log.FrameLogStore
import com.dormradar.app.core.log.LogStore
import com.dormradar.app.core.protocol.AckParsers
import com.dormradar.app.core.protocol.CommandBuilder
import com.dormradar.app.core.protocol.DeviceParams
import com.dormradar.app.core.protocol.Frame
import com.dormradar.app.core.protocol.FrameDecoder
import com.dormradar.app.core.protocol.PhotosensitiveConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 密码鉴权失败（不做自动重连，需用户处理） */
class AuthFailedException(message: String) : Exception(message)

/**
 * 设备会话编排（docs/04 §3.3）：
 * 连接 → 鉴权（0x00A8）→ 能力探测（0x00FF → 版本/MAC/参数/分辨率 → 0x00FE）→ Ready；
 * 上报帧解码后更新 [radarState]；意外断连按指数退避自动重连（1s→2s→5s→15s→60s 封顶）。
 *
 * 阶段 2：全部配置能力（docs/06 阶段 2）。配置命令统一走 [config]（0x00FF/0x00FE 自动包裹），
 * 写入后回读校验；模拟设备直接改内存参数，保证无硬件时全流程可体验。
 *
 * 生命周期策略（docs/04 §3.4）：连接编排独立于 UI 存活（单例 + 自有协程域）；
 * 阶段 3 的前台服务将直接持有本类。
 */
@Singleton
class RadarConnection @Inject constructor(
    private val bleClient: BleClient,
    private val settings: SettingsStore,
    private val fakeRadarSource: FakeRadarSource,
    private val log: LogStore,
    private val frameLog: FrameLogStore,
    private val diagRecorder: DistanceDiagRecorder,
) {
    companion object {
        private const val TAG = "RadarConnection"
        private val BACKOFF_MS = longArrayOf(1_000, 2_000, 5_000, 15_000, 60_000)
        private const val AUTH_TIMEOUT_MS = 3_000L
        private const val PROBE_TIMEOUT_MS = 2_000L
        private const val RESOLUTION_PROBE_TIMEOUT_MS = 1_500L
        private const val CONFIG_TIMEOUT_MS = 2_000L

        /** 出厂灵敏度表（docs/03 §4） */
        val FACTORY_MOTION = intArrayOf(50, 50, 40, 30, 20, 15, 15, 15, 15)
        val FACTORY_STATIC = intArrayOf(40, 40, 40, 40, 30, 30, 20, 20, 20)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connState = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val connState: StateFlow<ConnState> = _connState.asStateFlow()

    /** 统一状态入口：赋值 + 运行日志（连接时间线是排障第一线索） */
    private fun setConnState(value: ConnState) {
        _connState.value = value
        log.i(TAG, "连接状态 → $value")
    }

    private val _radarState = MutableStateFlow<RadarState?>(null)
    val radarState: StateFlow<RadarState?> = _radarState.asStateFlow()

    private val _gateEnergies = MutableStateFlow<GateEnergies?>(null)
    val gateEnergies: StateFlow<GateEnergies?> = _gateEnergies.asStateFlow()

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private val _engineeringMode = MutableStateFlow(false)
    val engineeringMode: StateFlow<Boolean> = _engineeringMode.asStateFlow()

    private val _configBusy = MutableStateFlow(false)
    val configBusy: StateFlow<Boolean> = _configBusy.asStateFlow()

    private val _connStats = MutableStateFlow(ConnQualityStats())
    val connStats: StateFlow<ConnQualityStats> = _connStats.asStateFlow()

    private val _rssiSamples = MutableStateFlow<List<RssiSample>>(emptyList())
    val rssiSamples: StateFlow<List<RssiSample>> = _rssiSamples.asStateFlow()

    private val _reconnectHistory = MutableStateFlow<List<ReconnectRecord>>(emptyList())
    val reconnectHistory: StateFlow<List<ReconnectRecord>> = _reconnectHistory.asStateFlow()

    private val decoder = FrameDecoder()
    private var commandSession: CommandSession? = null

    /** 配置会话互斥：同一时刻只允许一个配置流程（0x00FF/0x00FE 不可嵌套） */
    private val configMutex = Mutex()

    @Volatile private var currentMac: String? = null
    @Volatile private var manualDisconnect = false
    private var fakeJob: Job? = null
    private var rssiSamplerJob: Job? = null

    private val isFake: Boolean get() = _deviceInfo.value?.isFake == true

    init {
        // 帧日志开关跟随设置（DataStore → @Volatile 快门，高频路径不读 DataStore）
        scope.launch {
            settings.frameLogEnabled.collect { frameLog.enabled = it }
        }
        // 常驻收集 BLE 事件：数据帧进解码器，断开事件触发重连决策
        scope.launch {
            bleClient.events.collect { event ->
                when (event) {
                    is BleEvent.Data -> onBytes(event.bytes)
                    is BleEvent.Disconnected -> onBleDisconnected(event.mac)
                    is BleEvent.Ready -> Unit // connect() 流程内部处理
                }
            }
        }
    }

    // ---- 对外 API：连接管理 ----

    /** 发起连接（重复调用在连接中会被忽略）。模拟数据开启时走 FakeRadarSource。 */
    fun connect(mac: String) {
        when (_connState.value) {
            is ConnState.Ready, is ConnState.Connecting, is ConnState.Authenticating,
            is ConnState.Initializing, is ConnState.Reconnecting,
            -> return
            else -> Unit
        }
        scope.launch {
            manualDisconnect = false
            currentMac = mac
            val fake = settings.isFakeDataEnabled()
            try {
                if (fake) establishFake() else establish(mac)
            } catch (e: AuthFailedException) {
                log.w(TAG, "鉴权失败：${e.message}")
                setConnState(ConnState.Failed(e.message ?: "密码校验失败"))
            } catch (e: Exception) {
                log.w(TAG, "连接失败：${e.message}")
                setConnState(ConnState.Failed(e.message ?: "连接失败"))
            }
        }
    }

    /** 断开连接（用户主动；不会自动重连） */
    fun disconnect() {
        manualDisconnect = true
        fakeJob?.cancel()
        fakeJob = null
        rssiSamplerJob?.cancel()
        setConnState(ConnState.Disconnected)
        scope.launch { bleClient.disconnect() }
    }

    // ---- 对外 API：配置（阶段 2） ----

    /**
     * 设置最远探测距离门与无人持续时间（0x0060）+ 回读校验。
     * 门范围 2~8；分辨率 0.75m 时门 N ≈ (N+1)×0.75m，0.2m 时 ≈ (N+1)×0.2m。
     */
    suspend fun applyParams(moveMaxGate: Int, staticMaxGate: Int, unmannedSeconds: Int): OpResult {
        if (moveMaxGate !in 2..8 || staticMaxGate !in 2..8) return OpResult.Fail("距离门必须在 2~8 之间")
        if (unmannedSeconds !in 0..65535) return OpResult.Fail("无人持续时间超出范围")
        if (isFake) {
            updateParamsInPlace(
                DeviceParams(
                    maxGate = 8,
                    maxMoveGate = moveMaxGate, maxStaticGate = staticMaxGate,
                    motionSensitivities = FACTORY_MOTION.copyOfRange(0, moveMaxGate + 1),
                    staticSensitivities = FACTORY_STATIC.copyOfRange(0, staticMaxGate + 1),
                    unmannedSeconds = unmannedSeconds,
                ),
            )
            return OpResult.Ok
        }
        return configOp {
            val ack = it.send(
                CommandBuilder.setMaxGatesAndUnmannedTime(moveMaxGate, staticMaxGate, unmannedSeconds), 0x0060,
            )
            if (!ack.success) throw IOException("参数设置被设备拒绝")
            // 回读校验（写入即时生效；为容忍设备落盘延迟，最多重试 3 次读回）
            var params: DeviceParams? = null
            for (attempt in 1..3) {
                delay(100L * attempt)
                params = readParams(it)
                if (params != null && params.maxMoveGate == moveMaxGate &&
                    params.maxStaticGate == staticMaxGate && params.unmannedSeconds == unmannedSeconds
                ) {
                    updateParamsInPlace(params)
                    return@configOp
                }
            }
            throw IOException(
                "回读不一致（运动门${params?.maxMoveGate}/静止门${params?.maxStaticGate}/无人${params?.unmannedSeconds}s）",
            )
        }
    }

    /**
     * 逐门写入灵敏度表（0x0064）+ 回读校验。
     * 容错：部分固件近门（门 0/1）静止灵敏度不可写（docs/03 §4），此时保留原静止值仅写运动通道，
     * 回读校验对被拒门放行（其余门仍严格比对）。
     * @param motion / static 长度必须等于当前设备的门数（params 数组长度）
     */
    suspend fun applySensitivityTable(motion: IntArray, static: IntArray): OpResult {
        val current = _deviceInfo.value?.params
            ?: return OpResult.Fail("设备参数未就绪，请重新连接")
        val gateCount = current.motionSensitivities.size
        if (motion.size != gateCount || static.size != gateCount) {
            return OpResult.Fail("灵敏度表长度不符（应为 $gateCount 门）")
        }
        if (motion.any { it !in 0..100 } || static.any { it !in 0..100 }) return OpResult.Fail("灵敏度必须为 0~100")
        if (isFake) {
            updateParamsInPlace(current.copy(motionSensitivities = motion.copyOf(), staticSensitivities = static.copyOf()))
            return OpResult.Ok
        }
        return configOp {
            val staticRejected = BooleanArray(gateCount)
            for (gate in 0 until gateCount) {
                val ack = it.send(CommandBuilder.setGateSensitivity(gate, motion[gate], static[gate]), 0x0064)
                if (!ack.success) {
                    val fallback = it.send(
                        CommandBuilder.setGateSensitivity(gate, motion[gate], current.staticSensitivities[gate]),
                        0x0064,
                    )
                    if (!fallback.success) throw IOException("距离门 $gate 灵敏度被设备拒绝")
                    staticRejected[gate] = true
                    log.i(TAG, "门 $gate 静止灵敏度写入被拒（近门可能不可写），保留原值 ${current.staticSensitivities[gate]}")
                }
            }
            val params = readParams(it) ?: throw IOException("无法回读参数")
            val motionOk = params.motionSensitivities.contentEquals(motion)
            val staticOk = static.indices.all { g -> staticRejected[g] || params.staticSensitivities[g] == static[g] }
            if (!motionOk || !staticOk) {
                throw IOException("回读灵敏度与设定不一致")
            }
            updateParamsInPlace(params)
        }
    }

    /**
     * 近距屏蔽（「最小距离门」，用户需求 2026-09-27）：把 [0, minGate) 的距离门灵敏度写 100
     * （协议口径 = 屏蔽该门），只有 [minGate, 最远门] 之间的活动会触发；
     * 同时把 ≥minGate 中此前被屏蔽的门恢复为首个活动门的灵敏度（拉近屏蔽线时避免留死区）。
     * minGate = 0 即不屏蔽。
     */
    suspend fun setMinGate(minGate: Int): OpResult {
        val current = _deviceInfo.value?.params ?: return OpResult.Fail("设备参数未就绪")
        if (minGate !in 0..8) return OpResult.Fail("最小距离门必须在 0~8 之间")
        val maxEffective = minOf(current.maxMoveGate, current.maxStaticGate)
        if (minGate >= maxEffective) {
            return OpResult.Fail("最小距离门必须小于最远距离门（当前 $maxEffective）")
        }
        val gateCount = current.motionSensitivities.size
        val firstActive = current.motionSensitivities.indexOfFirst { it < 100 }
        val fillM = if (firstActive >= 0) current.motionSensitivities[firstActive] else 40
        val fillS = if (firstActive >= 0) current.staticSensitivities.getOrElse(firstActive) { 40 } else 40
        val motion = IntArray(gateCount) { g ->
            when {
                g < minGate -> 100
                current.motionSensitivities[g] >= 100 -> fillM // 此前被屏蔽 → 恢复
                else -> current.motionSensitivities[g]
            }
        }
        val static = IntArray(gateCount) { g ->
            when {
                g < minGate -> 100
                current.staticSensitivities[g] >= 100 -> fillS
                else -> current.staticSensitivities[g]
            }
        }
        if (motion.contentEquals(current.motionSensitivities) && static.contentEquals(current.staticSensitivities)) {
            return OpResult.Ok // 无变化
        }
        if (isFake) {
            updateParamsInPlace(current.copy(motionSensitivities = motion, staticSensitivities = static))
            return OpResult.Ok
        }
        return applySensitivityTable(motion, static)
    }

    /** 切换距离分辨率（0x00AA，需固件支持）+ 回读校验 + 刷新参数（门含义变化） */
    suspend fun applyResolution(resolution02m: Boolean): OpResult {
        if (isFake) {
            updateInfoInPlace { it.copy(resolution02m = resolution02m) }
            return OpResult.Ok
        }
        val info = _deviceInfo.value ?: return OpResult.Fail("设备未连接")
        if (info.resolution02m == null) return OpResult.Fail("当前固件不支持分辨率切换")
        val result = configOp {
            val ack = it.send(CommandBuilder.setDistanceResolution(resolution02m), 0x00AA)
            if (!ack.success) throw IOException("分辨率设置被设备拒绝")
        }
        if (result != OpResult.Ok) return result
        // 分辨率切换后距离门含义变化：重新探测参数
        val refresh = refreshDeviceInfo()
        if (refresh != OpResult.Ok) return refresh
        return if (_deviceInfo.value?.resolution02m == resolution02m) OpResult.Ok
        else OpResult.Fail("回读分辨率与设定不一致")
    }

    /** 工程模式开关（0x0062/0x0063）。开启后上报帧带各距离门能量 → [gateEnergies] */
    suspend fun setEngineeringMode(enabled: Boolean): OpResult {
        if (isFake) {
            _engineeringMode.value = enabled
            return OpResult.Ok
        }
        val result = configOp {
            val cmd = if (enabled) CommandBuilder.enableEngineering() else CommandBuilder.disableEngineering()
            val word = if (enabled) 0x0062 else 0x0063
            val ack = it.send(cmd, word)
            if (!ack.success) throw IOException(if (enabled) "开启工程模式被设备拒绝" else "关闭工程模式被设备拒绝")
        }
        if (result != OpResult.Ok) return result
        _engineeringMode.value = enabled
        if (!enabled) _gateEnergies.value = null
        return OpResult.Ok
    }

    /**
     * 开始底噪检测（0x000B，检测时长由命令参数下发；官方 Demo 固定 60s，其他时长以设备实际执行为准）。
     * 进度由上报帧目标状态驱动：0x04=进行中 / 0x05=成功 / 0x06=失败（docs/03 §3.2）；
     * 检测即"灵敏度自动配置"，成功后设备自动写入建议灵敏度，调 [refreshDeviceInfo] 回读展示。
     */
    suspend fun startNoiseDetection(durationSeconds: Int = 60): OpResult {
        if (durationSeconds !in 10..600) return OpResult.Fail("检测时长须为 10~600 秒")
        if (isFake) return OpResult.Ok
        return configOp {
            val ack = it.send(CommandBuilder.startNoiseDetection(durationSeconds), 0x000B)
            if (!ack.success) throw IOException("设备拒绝开始底噪检测")
        }
    }

    /** 重启模块（0x00A3）。ACK 后设备重启、BLE 断开 → 现有自动重连接管 */
    suspend fun reboot(): OpResult {
        if (isFake) return OpResult.Ok
        val result = configOp {
            val ack = it.send(CommandBuilder.reboot(), 0x00A3)
            if (!ack.success) throw IOException("重启命令被设备拒绝")
        }
        // 设备收到 0x00A3 后立即重启，可能来不及回 0x00FE 结束配置（超时）——仍视为成功
        return if (result is OpResult.Fail && result.reason.contains("超时")) OpResult.Ok else result
    }

    /** 恢复出厂设置（0x00A2）+ 回读出厂参数刷新界面 */
    suspend fun factoryReset(): OpResult {
        if (isFake) {
            updateParamsInPlace(
                DeviceParams(
                    maxGate = 8,
                    maxMoveGate = 8, maxStaticGate = 8,
                    motionSensitivities = FACTORY_MOTION.copyOf(),
                    staticSensitivities = FACTORY_STATIC.copyOf(),
                    unmannedSeconds = 5,
                ),
            )
            updateInfoInPlace { it.copy(resolution02m = it.resolution02m) }
            return OpResult.Ok
        }
        val result = configOp {
            val ack = it.send(CommandBuilder.factoryReset(), 0x00A2)
            if (!ack.success) throw IOException("恢复出厂被设备拒绝")
        }
        if (result != OpResult.Ok) return result
        return refreshDeviceInfo()
    }

    /**
     * 蓝牙开关（0x00A4）。**关闭后设备 BLE 断开且无法自动恢复**
     * （恢复方法：2~3 秒内断电重上电 5 次），调用方必须先做二次确认。
     */
    suspend fun setBluetoothEnabled(enabled: Boolean): OpResult {
        if (isFake) return OpResult.Ok
        return configOp {
            val ack = it.send(CommandBuilder.setBluetooth(enabled), 0x00A4)
            if (!ack.success) throw IOException("蓝牙设置被设备拒绝")
        }
    }

    /**
     * 修改设备控制密码（0x00A9）。成功后同步更新 App 内保存的密码
     * （自动重连与新连接直接用新密码，无需用户手动改）。
     */
    suspend fun changeDevicePassword(newPassword: String): OpResult {
        val bytes = newPassword.toByteArray(Charsets.US_ASCII)
        if (bytes.size != 6) return OpResult.Fail("密码必须为 6 个字符")
        if (isFake) {
            settings.setPassword(newPassword)
            return OpResult.Ok
        }
        val result = configOp {
            val ack = it.send(CommandBuilder.setPassword(bytes), 0x00A9)
            if (!ack.success) throw IOException("密码修改被设备拒绝")
        }
        if (result == OpResult.Ok) settings.setPassword(newPassword)
        return result
    }

    /** 设置串口波特率（0x00A1，档位 1~8，重启生效；仅影响串口调试，不影响蓝牙） */
    suspend fun applyBaudRate(selection: Int): OpResult {
        if (selection !in 1..8) return OpResult.Fail("波特率档位必须为 1~8")
        if (isFake) return OpResult.Ok
        return configOp {
            val ack = it.send(CommandBuilder.setBaudRate(selection), 0x00A1)
            if (!ack.success) throw IOException("波特率设置被设备拒绝")
        }
    }

    /** 查询门限值（0x00C5）。返回 (上限, 下限)；超时/无法解析 = 固件不支持（docs/03 §5.6 容错） */
    suspend fun readThreshold(): Pair<Int, Int>? {
        if (isFake) return 80 to 50
        return try {
            config {
                val ack = it.send(CommandBuilder.readThreshold(), 0x00C5)
                if (ack.success) AckParsers.parseThreshold(ack) else null
            }
        } catch (e: Exception) {
            log.i(TAG, "门限值查询失败（可能固件不支持）：${e.message}")
            null
        }
    }

    /** 设置门限值（0x00C4，0 ≤ 下限 ≤ 上限 ≤ 100，新固件"门限距离"功能） */
    suspend fun applyThreshold(upperLimit: Int, lowerLimit: Int): OpResult {
        if (upperLimit !in 0..100 || lowerLimit !in 0..100 || upperLimit < lowerLimit) {
            return OpResult.Fail("门限须满足 0 ≤ 下限 ≤ 上限 ≤ 100")
        }
        if (isFake) return OpResult.Ok
        return configOp {
            val ack = it.send(CommandBuilder.setThreshold(upperLimit, lowerLimit), 0x00C4)
            if (!ack.success) throw IOException("门限值设置被设备拒绝")
        }
    }

    /**
     * 查询光敏/辅助控制配置（0x00AE）。超时 = 固件不支持；
     * state 语义（官方 Demo 确认）：0=关闭 / 1=光感低于阈值触发 / 2=光感高于阈值触发。
     */
    suspend fun readPhotosensitive(): PhotosensitiveConfig? {
        if (isFake) return PhotosensitiveConfig(state = 1, threshold = 0x80)
        return try {
            config {
                val ack = it.send(CommandBuilder.readPhotosensitiveAux(), 0x00AE, RESOLUTION_PROBE_TIMEOUT_MS)
                if (ack.success) AckParsers.parsePhotosensitive(ack.payload) else null
            }
        } catch (e: Exception) {
            log.i(TAG, "光敏配置查询失败（可能固件不支持）：${e.message}")
            null
        }
    }

    /** 设置光敏/辅助控制（0x00AD）：state 0=关闭/1=低于阈值触发/2=高于阈值触发，阈值 0~255 */
    suspend fun applyPhotosensitive(state: Int, threshold: Int): OpResult {
        if (state !in 0..2) return OpResult.Fail("无效的光敏模式")
        if (threshold !in 0..255) return OpResult.Fail("光敏阈值必须为 0~255")
        if (isFake) return OpResult.Ok
        return configOp {
            val ack = it.send(CommandBuilder.setPhotosensitiveAux(state, threshold), 0x00AD)
            if (!ack.success) throw IOException("光敏设置被设备拒绝")
            // 回读校验
            val read = it.send(CommandBuilder.readPhotosensitiveAux(), 0x00AE, RESOLUTION_PROBE_TIMEOUT_MS)
            val back = if (read.success) AckParsers.parsePhotosensitive(read.payload) else null
            if (back != null && (back.state != state || back.threshold != threshold)) {
                throw IOException("回读光敏配置与设定不一致")
            }
        }
    }

    /** 重新探测设备信息（参数/分辨率等），断连重连或底噪检测完成后刷新用 */
    suspend fun refreshDeviceInfo(): OpResult {
        if (isFake) return OpResult.Ok
        if (_connState.value !is ConnState.Ready) return OpResult.Fail("设备未连接")
        return configOp { probeInConfig(it) }
    }

    // ---- 配置会话基础设施 ----

    /**
     * 配置会话模板：0x00FF 使能 → [block] → 0x00FE 结束（finally 保证，设备重启等场景容错）。
     * 互斥串行；期间设备暂停上报（协议行为），UI 的实时数据短暂停更属正常。
     */
    private suspend fun <T> config(block: suspend (CommandSession) -> T): T {
        val session = commandSession ?: throw IOException("设备未连接")
        return configMutex.withLock {
            _configBusy.value = true
            try {
                val enableAck = session.send(CommandBuilder.enableConfig(), 0x00FF, PROBE_TIMEOUT_MS)
                if (!enableAck.success) throw IOException("进入配置模式被设备拒绝")
                try {
                    block(session)
                } finally {
                    runCatching { session.send(CommandBuilder.endConfig(), 0x00FE, PROBE_TIMEOUT_MS) }
                }
            } finally {
                _configBusy.value = false
            }
        }
    }

    /** config + 统一异常 → [OpResult] 转换 */
    private suspend fun configOp(block: suspend (CommandSession) -> Unit): OpResult = try {
        config(block)
        OpResult.Ok
    } catch (e: CommandTimeoutException) {
        log.w(TAG, "配置命令超时：0x%04X".format(e.commandWord))
        OpResult.Fail("命令超时，设备未响应（0x%04X）".format(e.commandWord))
    } catch (e: Exception) {
        log.w(TAG, "配置失败：${e.message}")
        OpResult.Fail(e.message ?: "配置失败")
    }

    /** 在配置会话内读参数（0x0061） */
    private suspend fun readParams(session: CommandSession): DeviceParams? {
        val ack = session.send(CommandBuilder.readParameters(), 0x0061, PROBE_TIMEOUT_MS)
        if (!ack.success) return null
        return AckParsers.parseDeviceParams(ack.payload)
    }

    /** 用最新参数原地更新 deviceInfo（不丢版本/MAC 等其余字段） */
    private fun updateParamsInPlace(params: DeviceParams) {
        updateInfoInPlace { it.copy(params = params) }
    }

    private inline fun updateInfoInPlace(transform: (DeviceInfo) -> DeviceInfo) {
        val info = _deviceInfo.value ?: return
        _deviceInfo.value = transform(info)
    }

    // ---- 真实设备连接编排 ----

    /** 完整建立会话：BLE 就绪 → 鉴权 → 能力探测。失败抛异常由调用方分类处理。 */
    private suspend fun establish(mac: String) {
        resetSession()
        _engineeringMode.value = false
        setConnState(ConnState.Connecting(mac))
        bleClient.connect(mac) // 挂起直至订阅完成（或抛 IOException）

        val password = settings.password()
        commandSession = newCommandSession()
        setConnState(ConnState.Authenticating)
        val authAck = commandSession!!.send(
            CommandBuilder.authenticate(password), 0x00A8, AUTH_TIMEOUT_MS,
        )
        if (!authAck.success) {
            throw AuthFailedException("密码校验失败（默认 $DEFAULT_BLUETOOTH_PASSWORD），请到「设置」修改后重试")
        }

        setConnState(ConnState.Initializing)
        var protocolVersion: Int? = null

        // 配置会话：0x00FF → 探测 → 0x00FE（docs/03 §0；0x00FE 在 finally 中保证发送）
        val enableAck = commandSession!!.send(CommandBuilder.enableConfig(), 0x00FF, PROBE_TIMEOUT_MS)
        if (!enableAck.success) throw IOException("进入配置模式被设备拒绝")
        protocolVersion = AckParsers.parseEnableConfig(enableAck.payload)?.first
        try {
            probeInConfig(commandSession!!)
        } finally {
            runCatching { commandSession!!.send(CommandBuilder.endConfig(), 0x00FE, PROBE_TIMEOUT_MS) }
        }

        setConnState(ConnState.Ready(mac))
        log.i(TAG, "会话就绪：$mac ${_deviceInfo.value?.let { "版本=${it.firmwareVersion}" }}")
        // 连接质量统计清零起算 + RSSI 采样（真实设备）
        _connStats.value = ConnQualityStats(startedAtMs = System.currentTimeMillis())
        _rssiSamples.value = emptyList()
        startRssiSampler()
    }

    /** RSSI 周期采样（每 5s 读一次，最多留 120 点 ≈ 10 分钟；模拟设备跳过） */
    private fun startRssiSampler() {
        rssiSamplerJob?.cancel()
        if (isFake) return
        rssiSamplerJob = scope.launch {
            while (isActive) {
                bleClient.readRssi()?.let { rssi ->
                    if (rssi in -127..0) {
                        _rssiSamples.update {
                            (it + RssiSample(System.currentTimeMillis(), rssi)).takeLast(120)
                        }
                    }
                }
                delay(5_000)
            }
        }
    }

    /** 重连/断连历史记录（App 存活期累计，最多 30 条） */
    private fun recordReconnect(text: String, isError: Boolean) {
        _reconnectHistory.update {
            (it + ReconnectRecord(System.currentTimeMillis(), text, isError)).takeLast(30)
        }
    }

    /** 配置会话内的能力探测：固件/MAC/参数/分辨率（各自独立容错，失败不阻断连接） */
    private suspend fun probeInConfig(session: CommandSession) {
        var firmwareVersion: String? = null
        var macAddress: String? = null
        var params: DeviceParams? = null
        var resolution02m: Boolean? = null
        runCatching {
            val ack = session.send(CommandBuilder.readFirmwareVersion(), 0x00A0, PROBE_TIMEOUT_MS)
            if (ack.success) firmwareVersion = AckParsers.parseFirmwareVersion(ack.payload)
        }
        runCatching {
            val ack = session.send(CommandBuilder.readMac(), 0x00A5, PROBE_TIMEOUT_MS)
            if (ack.success) macAddress = AckParsers.parseMac(ack.payload)
        }
        runCatching {
            val ack = session.send(CommandBuilder.readParameters(), 0x0061, PROBE_TIMEOUT_MS)
            if (ack.success) params = AckParsers.parseDeviceParams(ack.payload)
        }
        runCatching {
            // 能力探测：超时/失败 = 固件不支持距离分辨率（docs/03 §5.6）
            val ack = session.send(CommandBuilder.readDistanceResolution(), 0x00AB, RESOLUTION_PROBE_TIMEOUT_MS)
            if (ack.success) resolution02m = AckParsers.parseDistanceResolution(ack.payload)
        }
        val mac = _deviceInfo.value?.mac ?: currentMac ?: macAddress ?: "未知"
        _deviceInfo.value = DeviceInfo(
            mac = macAddress ?: mac,
            firmwareVersion = firmwareVersion ?: _deviceInfo.value?.firmwareVersion,
            protocolVersion = _deviceInfo.value?.protocolVersion,
            params = params ?: _deviceInfo.value?.params,
            resolution02m = resolution02m ?: _deviceInfo.value?.resolution02m,
        )
    }

    // ---- 模拟设备连接 ----

    private suspend fun establishFake() {
        resetSession()
        _engineeringMode.value = false
        setConnState(ConnState.Connecting(FakeRadarSource.FAKE_MAC))
        delay(600) // 模拟连接耗时
        setConnState(ConnState.Ready(FakeRadarSource.FAKE_MAC))
        _deviceInfo.value = DeviceInfo(
            mac = FakeRadarSource.FAKE_MAC,
            firmwareVersion = "FAKE-1.0.0",
            protocolVersion = 1,
            params = DeviceParams(
                maxGate = 8,
                maxMoveGate = 8, maxStaticGate = 8,
                motionSensitivities = FACTORY_MOTION.copyOf(),
                staticSensitivities = FACTORY_STATIC.copyOf(),
                unmannedSeconds = 5,
            ),
            resolution02m = true,
            isFake = true,
        )
        fakeJob = scope.launch {
            fakeRadarSource.frameFlow().collect { onBytes(it) }
        }
        log.i(TAG, "模拟会话就绪")
    }

    // ---- 数据与断连处理 ----

    /** 原始字节进解码器，帧分发：ACK → 命令会话；上报 → 状态流 */
    private fun onBytes(bytes: ByteArray) {
        if (!isFake) frameLog.record(FrameDir.RX, bytes) // 帧日志（模拟数据不记）
        _connStats.update { it.copy(rxBytes = it.rxBytes + bytes.size) }
        val frames = try {
            decoder.feed(bytes)
        } catch (e: Exception) {
            log.w(TAG, "解码异常：${e.message}")
            _connStats.update { it.copy(decodeErrors = it.decodeErrors + 1) }
            return
        }
        _connStats.update { it.copy(rxFrames = it.rxFrames + frames.size) }
        for (frame in frames) {
            when (frame) {
                is Frame.Ack -> {
                    _connStats.update { it.copy(ackFrames = it.ackFrames + 1) }
                    commandSession?.onAck(frame)
                }
                is Frame.ReportBasic -> {
                    _connStats.update { it.copy(reportFrames = it.reportFrames + 1) }
                    diagRecorder.onRawFrame(
                        frameType = "basic",
                        targetState = frame.targetState,
                        moveDistanceCm = frame.moveDistanceCm,
                        moveEnergy = frame.moveEnergy,
                        staticDistanceCm = frame.staticDistanceCm,
                        staticEnergy = frame.staticEnergy,
                        detectDistanceCm = frame.detectDistanceCm,
                        moveGateEnergies = null,
                        staticGateEnergies = null,
                    )
                    updateRadarState(
                        frame.targetState, frame.moveDistanceCm, frame.moveEnergy,
                        frame.staticDistanceCm, frame.staticEnergy, frame.detectDistanceCm,
                    )
                }
                is Frame.ReportEngineering -> {
                    _connStats.update { it.copy(reportFrames = it.reportFrames + 1) }
                    diagRecorder.onRawFrame(
                        frameType = "engineering",
                        targetState = frame.targetState,
                        moveDistanceCm = frame.moveDistanceCm,
                        moveEnergy = frame.moveEnergy,
                        staticDistanceCm = frame.staticDistanceCm,
                        staticEnergy = frame.staticEnergy,
                        detectDistanceCm = frame.detectDistanceCm,
                        moveGateEnergies = frame.moveGateEnergies,
                        staticGateEnergies = frame.staticGateEnergies,
                    )
                    _gateEnergies.value = GateEnergies(
                        maxMoveGate = frame.maxMoveGate,
                        maxStaticGate = frame.maxStaticGate,
                        moveGateEnergies = frame.moveGateEnergies,
                        staticGateEnergies = frame.staticGateEnergies,
                    )
                    updateRadarState(
                        frame.targetState, frame.moveDistanceCm, frame.moveEnergy,
                        frame.staticDistanceCm, frame.staticEnergy, frame.detectDistanceCm,
                    )
                }
                is Frame.Unknown -> {
                    _connStats.update { it.copy(unknownFrames = it.unknownFrames + 1) }
                    log.i(TAG, "无法识别的帧：${frame.data.size} 字节")
                }
            }
        }
    }

    private fun updateRadarState(
        targetState: Int,
        moveDistanceCm: Int,
        moveEnergy: Int,
        staticDistanceCm: Int,
        staticEnergy: Int,
        detectDistanceCm: Int,
    ) {
        _radarState.value = RadarState(
            targetState = targetState,
            moveDistanceCm = moveDistanceCm,
            moveEnergy = moveEnergy,
            staticDistanceCm = staticDistanceCm,
            staticEnergy = staticEnergy,
            detectDistanceCm = detectDistanceCm,
        )
    }

    private fun onBleDisconnected(mac: String) {
        if (manualDisconnect) {
            setConnState(ConnState.Disconnected)
            return
        }
        if (currentMac != mac) return // 旧连接的迟到事件
        if (_connState.value is ConnState.Failed) return // 初次连接已判定失败：等用户手动重试
        // 意外断连：指数退避自动重连（docs/04 §3.1）。鉴权失败不重连（需用户干预）。
        rssiSamplerJob?.cancel()
        recordReconnect("连接意外断开，开始自动重连", true)
        scope.launch {
            var attempt = 1
            while (isActive && !manualDisconnect && _connState.value !is ConnState.Failed) {
                val backoff = BACKOFF_MS[(attempt - 1).coerceAtMost(BACKOFF_MS.size - 1)]
                setConnState(ConnState.Reconnecting(mac, attempt))
                log.i(TAG, "第 $attempt 次重连（等待 ${backoff}ms）")
                delay(backoff)
                try {
                    establish(mac)
                    recordReconnect("重连成功（第 $attempt 次尝试）", false)
                    return@launch
                } catch (e: AuthFailedException) {
                    setConnState(ConnState.Failed(e.message ?: "密码校验失败"))
                    return@launch
                } catch (e: Exception) {
                    log.w(TAG, "重连失败：${e.message}")
                    recordReconnect("第 $attempt 次重连失败：${e.message}", true)
                    attempt++
                }
            }
        }
    }

    private fun resetSession() {
        decoder.reset()
        commandSession = newCommandSession()
        _radarState.value = null
        _gateEnergies.value = null
        _deviceInfo.value = null
        _engineeringMode.value = false
        rssiSamplerJob?.cancel()
        _rssiSamples.value = emptyList()
    }

    /** 建命令会话：TX 帧记录挂点（发送前记十六进制，见「日志」页）+ TX 计数 */
    private fun newCommandSession() = CommandSession(write = { bytes ->
        frameLog.record(FrameDir.TX, bytes)
        _connStats.update { it.copy(txCommands = it.txCommands + 1) }
        bleClient.write(bytes)
    })
}

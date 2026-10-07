package com.dormradar.app.feature.config

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.deploy.DiagArchiveReuse
import com.dormradar.app.core.deploy.EnergySeriesCollector
import com.dormradar.app.core.deploy.NoiseBaselineAdvisor
import com.dormradar.app.core.device.DeviceInfo
import com.dormradar.app.core.device.GateEnergies
import com.dormradar.app.core.device.OpResult
import com.dormradar.app.core.device.RadarConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 灵敏度矩阵页 VM（详细层，PRD 4.1 第 4 项） */
@HiltViewModel
class SensitivityViewModel @Inject constructor(
    private val connection: RadarConnection,
    private val settings: com.dormradar.app.core.data.SettingsStore,
) : ViewModel() {

    val deviceInfo: StateFlow<DeviceInfo?> = connection.deviceInfo

    /** 工程模式各门实时能量（进入页面自动开启工程模式获取） */
    val gateEnergies: StateFlow<GateEnergies?> = connection.gateEnergies

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _messageIsError = MutableStateFlow(false)
    val messageIsError: StateFlow<Boolean> = _messageIsError.asStateFlow()

    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    init {
        // 进入页面即回读设备当前参数（configOp 内含 0x0061），
        // 修复「显示默认值」问题：草稿永远以设备真实配置为基准（保存成功后同样回读刷新）。
        viewModelScope.launch {
            try {
                connection.refreshDeviceInfo()
            } catch (_: Exception) {
                // 未连接/超时：页面会显示占位提示，不额外处理
            }
            // 开启工程模式：上报帧带各门能量 → 实时能量展示（用户需求 2026-09-28）
            // configOp 内部有互斥锁，与保存操作天然串行，不会冲突。
            when (val r = connection.setEngineeringMode(true)) {
                is OpResult.Fail -> {
                    _messageIsError.value = false
                    _message.value = "实时能量暂不可用：${r.reason}（门限调节与保存不受影响）"
                }
                else -> Unit
            }
        }
    }

    /** 离开页面时关闭工程模式（DisposableEffect onDispose 调用，此时 VM 尚存活） */
    fun disableLiveEnergy() {
        viewModelScope.launch {
            try {
                connection.setEngineeringMode(false)
            } catch (_: Exception) {
                // 未连接/超时：设备断电重连后工程模式本就复位，无需处理
            }
        }
    }

    fun apply(motion: IntArray, static: IntArray) {
        if (_applying.value) return
        _applying.value = true
        viewModelScope.launch {
            when (val result = connection.applySensitivityTable(motion, static)) {
                OpResult.Ok -> {
                    _messageIsError.value = false
                    _message.value = "灵敏度已保存（逐门写入 + 回读校验一致）"
                }
                is OpResult.Fail -> {
                    _messageIsError.value = true
                    _message.value = "保存失败：${result.reason}"
                }
            }
            _applying.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    // ---- 无人基线定参（core/deploy/NoiseBaselineAdvisor）----

    private val _baselineSampling = MutableStateFlow(false)
    val baselineSampling: StateFlow<Boolean> = _baselineSampling.asStateFlow()

    private val _baselineRemain = MutableStateFlow(0)
    val baselineRemain: StateFlow<Int> = _baselineRemain.asStateFlow()

    private val _baselineInfo = MutableStateFlow<String?>(null)
    val baselineInfo: StateFlow<String?> = _baselineInfo.asStateFlow()

    private val _baselineMargin = MutableStateFlow(NoiseBaselineAdvisor.DEFAULT_MARGIN)
    val baselineMargin: StateFlow<Int> = _baselineMargin.asStateFlow()

    private val _baselineBasis = MutableStateFlow(NoiseBaselineAdvisor.Basis.P95)
    val baselineBasis: StateFlow<NoiseBaselineAdvisor.Basis> = _baselineBasis.asStateFlow()

    private val _baselineSuggestion = MutableStateFlow<NoiseBaselineAdvisor.Suggestion?>(null)
    val baselineSuggestion: StateFlow<NoiseBaselineAdvisor.Suggestion?> = _baselineSuggestion.asStateFlow()

    /** 诊断两步引导的存档是否可用（有档案就显示「免重测」按钮，采样档案共享 2026-10-07） */
    val archiveAvailable: StateFlow<Boolean> = settings.deployArchiveCsv
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var baselineJob: Job? = null

    /** 保留原始采样序列，切换边距/口径时可离线重算而不必重新采样 */
    private var baselineMotion: List<IntArray>? = null
    private var baselineStatic: List<IntArray>? = null

    /**
     * 无人基线采样：保持门口无人 [BASELINE_SAMPLE_SECONDS] 秒，收集每门完整能量序列。
     * 采集到"底噪检测进行中"（目标状态 0x04）则作废本次样本——原因见
     * [EnergySeriesCollector.noiseDetectionRunning] 与 [NoiseBaselineAdvisor] 类注释。
     */
    fun startBaselineSampling() {
        if (_baselineSampling.value) return
        val params = deviceInfo.value?.params
        if (params == null) {
            _baselineInfo.value = "设备参数未就绪，请先连接雷达并等待参数读取完成"
            return
        }
        val collector = EnergySeriesCollector(params.motionSensitivities.size)
        _baselineSampling.value = true
        _baselineRemain.value = BASELINE_SAMPLE_SECONDS
        _baselineInfo.value = null
        baselineJob = viewModelScope.launch {
            val energyJob = launch {
                gateEnergies.collect { ge ->
                    if (ge != null) collector.onGateEnergies(ge.moveGateEnergies, ge.staticGateEnergies)
                }
            }
            val stateJob = launch {
                connection.radarState.collect { s ->
                    if (s != null) collector.onTargetState(s.targetState)
                }
            }
            repeat(BASELINE_SAMPLE_SECONDS) { i ->
                delay(1_000)
                _baselineRemain.value = BASELINE_SAMPLE_SECONDS - i - 1
            }
            energyJob.cancel()
            stateJob.cancel()
            _baselineSampling.value = false
            _baselineRemain.value = 0

            if (collector.noiseDetectionRunning) {
                _baselineInfo.value = "采样期间检测到设备正在跑底噪检测，本次样本已作废。" +
                    "底噪检测结束时设备会写回它自己的建议灵敏度，会覆盖这里的设置——请等它结束后重新采样。"
                return@launch
            }
            if (collector.sampleCount == 0) {
                _baselineInfo.value = "本次没采到能量数据：请确认设备已连接（工程模式会在离开本页时自动关闭）"
                return@launch
            }
            baselineMotion = collector.motionSeries()
            baselineStatic = collector.staticSeries()
            recomputeBaseline()
            _baselineInfo.value = "采样完成：${collector.sampleCount} 个样本/门。可直接调整边距看效果，或点「一键应用」写入设备"
        }
    }

    fun cancelBaselineSampling() {
        baselineJob?.cancel()
        baselineJob = null
        _baselineSampling.value = false
        _baselineRemain.value = 0
        _baselineInfo.value = "已取消本次采样"
    }

    /**
     * 复用「距离诊断」两步引导的存档（无人段），免重复采样（用户需求 2026-10-07：测一次处处用）。
     * 档案里无人段没有工程帧、或没有档案时如实提示。
     */
    fun useArchivedBaseline() {
        if (_baselineSampling.value) return
        viewModelScope.launch {
            val csv = settings.deployArchiveCsv.first()
            if (csv.isNullOrBlank()) {
                _baselineInfo.value = "还没有存档采样：先到「设置 → 调试工具 → 距离诊断」完成一次两步引导"
                return@launch
            }
            val meta = settings.deployArchiveMeta.first() ?: ""
            val ageMin = meta.split(',').firstOrNull()?.toLongOrNull()?.let {
                (System.currentTimeMillis() - it) / 60_000
            }
            val archived = com.dormradar.app.core.log.DistanceDiagFormats.parseCsv(csv)
            val noPersonEng = DiagArchiveReuse.noPersonEngineering(archived)
            if (noPersonEng.isEmpty()) {
                _baselineInfo.value = "存档里没有「无人阶段」的工程帧数据，无法复用——请重新走一次两步引导"
                return@launch
            }
            val (motion, static) = DiagArchiveReuse.perGateSeries(noPersonEng)
            baselineMotion = motion
            baselineStatic = static
            recomputeBaseline()
            _baselineInfo.value =
                "已使用 ${ageMin ?: "?"} 分钟前诊断采样的无人段（${noPersonEng.size} 帧/门）" +
                    "算出浮动上界。调整边距看效果，或点「一键应用」写入设备"
        }
    }

    fun setBaselineMargin(value: Int) {
        _baselineMargin.value = value
        recomputeBaseline()
    }

    fun setBaselineBasis(value: NoiseBaselineAdvisor.Basis) {
        _baselineBasis.value = value
        recomputeBaseline()
    }

    /** 一键应用：复用既有的逐门写入 + 回读校验（含近门静止不可写的容错） */
    fun applyBaseline() {
        val s = _baselineSuggestion.value
        if (s == null) {
            _baselineInfo.value = "还没有可应用的建议，请先完成一次无人采样"
            return
        }
        apply(s.motionSensitivities, s.staticSensitivities)
    }

    private fun recomputeBaseline() {
        val motion = baselineMotion ?: return
        val static = baselineStatic ?: return
        val params = deviceInfo.value?.params ?: return
        _baselineSuggestion.value = NoiseBaselineAdvisor.suggest(
            motionSeries = motion,
            staticSeries = static,
            currentMotion = params.motionSensitivities,
            currentStatic = params.staticSensitivities,
            maxMoveGate = params.maxMoveGate,
            maxStaticGate = params.maxStaticGate,
            margin = _baselineMargin.value,
            basis = _baselineBasis.value,
        )
    }

    override fun onCleared() {
        baselineJob?.cancel()
        super.onCleared()
    }

    companion object {
        /** 无人基线采样时长：比部署向导的 30s 长，因为"浮动上界"要用分位数估计 */
        const val BASELINE_SAMPLE_SECONDS = 45
    }
}

/**
 * 灵敏度页（详细层，用户需求 2026-09-28 重构为数值调节）：
 * - 顶部实时能量柱状图（工程模式逐门能量每秒刷新，门限草稿线叠加对照）；
 * - 每门一行，点击「运动 / 静止」数值直接弹出数值编辑（输入 + 步进）；
 * - 「全部距离门」批量滑杆保留；保存按钮统一逐门写入设备。
 *
 * 语义提示：能量 > 灵敏度才判定有人 → 数值越低越灵敏；100 = 屏蔽该距离门。
 */
@Composable
fun SensitivityScreen(
    onBack: () -> Unit,
    viewModel: SensitivityViewModel = hiltViewModel(),
) {
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val gateEnergies by viewModel.gateEnergies.collectAsStateWithLifecycle()
    val applying by viewModel.applying.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageIsError by viewModel.messageIsError.collectAsStateWithLifecycle()
    val baselineSampling by viewModel.baselineSampling.collectAsStateWithLifecycle()
    val baselineRemain by viewModel.baselineRemain.collectAsStateWithLifecycle()
    val baselineInfo by viewModel.baselineInfo.collectAsStateWithLifecycle()
    val baselineMargin by viewModel.baselineMargin.collectAsStateWithLifecycle()
    val baselineBasis by viewModel.baselineBasis.collectAsStateWithLifecycle()
    val baselineSuggestion by viewModel.baselineSuggestion.collectAsStateWithLifecycle()
    val archiveAvailable by viewModel.archiveAvailable.collectAsStateWithLifecycle()
    val params = deviceInfo?.params
    val resolutionCm = if (deviceInfo?.resolution02m == true) 20 else 75

    // 离开页面关闭工程模式（写在 ConfigScaffold 外保证所有分支都会注册）
    DisposableEffect(Unit) {
        onDispose { viewModel.disableLiveEnergy() }
    }

    ConfigScaffold(title = "灵敏度调节", onBack = onBack) { modifier ->
        if (params == null) {
            Column(
                modifier = modifier.fillMaxSize().padding(16.dp),
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (deviceInfo == null) "设备未连接，请先回到首页连接雷达" else "参数读取中…",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@ConfigScaffold
        }

        // 草稿：设备参数变化（进入页面回读 / 保存成功回读 / 重新连接）时重置为最新回读值
        val motion = remember(params) { params.motionSensitivities.map { it.toFloat() }.toMutableStateList() }
        val static = remember(params) { params.staticSensitivities.map { it.toFloat() }.toMutableStateList() }
        val gateCount = params.motionSensitivities.size
        // 未保存提醒：改过数值但尚未写入设备时提示（防止退出后误以为已保存）
        var draftDirty by remember { mutableStateOf(false) }
        LaunchedEffect(params) { draftDirty = false }

        // 数值编辑对话框状态：editGate >= 0 且 editChannel ∈ {0,1} 时弹出
        var editGate by remember { mutableIntStateOf(-1) }
        var editChannel by remember { mutableIntStateOf(-1) }

        LazyColumn(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "雷达把探测距离切成若干「距离门」，每门可独立设灵敏度。\n" +
                        "判定规则：能量 > 灵敏度 → 有人。数值越低越灵敏；设 100 = 屏蔽该门。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (message != null) {
                item { ResultBanner(message!!, isError = messageIsError, onDismiss = viewModel::clearMessage) }
            }

            if (draftDirty) {
                item {
                    Text(
                        "● 有未保存的修改——调整完成后请点底部「保存全部到设备」，直接退出不会生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ---- 实时能量（工程模式逐门能量 + 门限草稿线对照） ----
            item {
                LiveEnergyCard(
                    gateCount = gateCount,
                    resolutionCm = resolutionCm,
                    energies = gateEnergies,
                    motion = motion,
                    static = static,
                )
            }

            // ---- 无人基线定参（把阈值压到"刚好高于环境浮动"）----
            item {
                NoiseBaselineCard(
                    enabled = !applying,
                    sampling = baselineSampling,
                    remainSeconds = baselineRemain,
                    sampleSeconds = SensitivityViewModel.BASELINE_SAMPLE_SECONDS,
                    info = baselineInfo,
                    margin = baselineMargin,
                    basis = baselineBasis,
                    suggestion = baselineSuggestion,
                    applying = applying,
                    resolutionCm = resolutionCm,
                    archiveAvailable = archiveAvailable,
                    onStart = viewModel::startBaselineSampling,
                    onCancel = viewModel::cancelBaselineSampling,
                    onUseArchive = viewModel::useArchivedBaseline,
                    onMargin = viewModel::setBaselineMargin,
                    onBasis = viewModel::setBaselineBasis,
                    onApply = viewModel::applyBaseline,
                )
            }

            // ---- 全部距离门快捷设置 ----
            item {
                BulkCard(
                    gateCount = gateCount,
                    enabled = !applying,
                    onApplyAll = { m, s ->
                        for (i in 0 until gateCount) {
                            motion[i] = m
                            static[i] = s
                        }
                        draftDirty = true
                    },
                )
            }

            // ---- 每门数值行：点击「运动/静止」数值直接修改 ----
            item {
                Text(
                    "逐门调节（点击数值修改）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(gateCount) { gate ->
                GateValueRow(
                    gate = gate,
                    resolutionCm = resolutionCm,
                    motion = motion[gate].toInt(),
                    static = static[gate].toInt(),
                    liveMotion = gateEnergies?.moveGateEnergies?.getOrNull(gate),
                    liveStatic = gateEnergies?.staticGateEnergies?.getOrNull(gate),
                    enabled = !applying,
                    onEdit = { channel ->
                        editGate = gate
                        editChannel = channel
                    },
                )
            }

            item {
                Button(
                    onClick = {
                        viewModel.apply(
                            motion.map { it.toInt() }.toIntArray(),
                            static.map { it.toInt() }.toIntArray(),
                        )
                    },
                    enabled = !applying,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (applying) "正在逐门写入…" else "保存全部到设备（${gateCount} 门 × 2）") }
                Spacer(Modifier.height(12.dp))
            }
        }

        // 数值编辑对话框（改草稿，统一由保存按钮写入设备）
        if (editGate >= 0 && editChannel >= 0) {
            val gate = editGate
            val channel = editChannel
            val range = "%.1f~%.1fm".format(gate * resolutionCm / 100.0, (gate + 1) * resolutionCm / 100.0)
            val liveMotion = gateEnergies?.moveGateEnergies?.getOrNull(gate)
            val liveStatic = gateEnergies?.staticGateEnergies?.getOrNull(gate)
            ThresholdEditDialog(
                title = "门 $gate（$range）· ${if (channel == 0) "运动" else "静止"}灵敏度",
                initial = if (channel == 0) motion[gate].toInt() else static[gate].toInt(),
                subtitle = if (liveMotion != null && liveStatic != null) {
                    "该门实时能量：运动 $liveMotion / 静止 $liveStatic（能量超过灵敏度即判定有人）"
                } else {
                    null
                },
                onDismiss = {
                    editGate = -1
                    editChannel = -1
                },
                onConfirm = { v ->
                    if (channel == 0) motion[gate] = v.toFloat() else static[gate] = v.toFloat()
                    draftDirty = true
                    editGate = -1
                    editChannel = -1
                },
            )
        }
    }
}

// ================= 实时能量柱状图 =================

/** 实时能量图轨道高度 */
private val LiveChartHeight = 160.dp
private val MoveColor = Color(0xFFDC2626)
private val StaticColor = Color(0xFF9333EA)

/**
 * 实时各门能量（工程模式数据，每秒刷新）+ 门限草稿线叠加：
 * 柱子 = 真实能量（会动），横虚线 = 你的门限草稿 —— 柱顶超过横线即会判定有人。
 */
@Composable
private fun LiveEnergyCard(
    gateCount: Int,
    resolutionCm: Int,
    energies: GateEnergies?,
    motion: SnapshotStateList<Float>,
    static: SnapshotStateList<Float>,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("实时能量", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EnergyLegend("运动", MoveColor)
                    EnergyLegend("静止", StaticColor)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "每秒刷新的各门真实能量柱，横虚线为门限草稿——柱顶超过横线即判定有人",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (energies == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "正在读取实时能量…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    // 左侧数值刻度（固定，不随柱区横滚）
                    Canvas(modifier = Modifier.width(24.dp).height(LiveChartHeight)) {
                        listOf(0, 25, 50, 75, 100).forEach { v ->
                            val y = (1f - v / 100f) * size.height
                            drawText(
                                textMeasurer, "$v",
                                topLeft = Offset(0f, y - 6.dp.toPx()),
                                style = TextStyle(fontSize = 9.sp, color = axisColor),
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        repeat(gateCount) { gate ->
                            GateEnergyColumn(
                                gate = gate,
                                resolutionCm = resolutionCm,
                                moveEnergy = energies.moveGateEnergies.getOrNull(gate) ?: 0,
                                staticEnergy = energies.staticGateEnergies.getOrNull(gate) ?: 0,
                                motionThreshold = motion[gate],
                                staticThreshold = static[gate],
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnergyLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** 单个距离门：双能量柱（红=运动 紫=静止）+ 两条门限草稿虚线 + 底部门号/米数 */
@Composable
private fun GateEnergyColumn(
    gate: Int,
    resolutionCm: Int,
    moveEnergy: Int,
    staticEnergy: Int,
    motionThreshold: Float,
    staticThreshold: Float,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(38.dp, LiveChartHeight)) {
            val w = size.width
            val h = size.height
            val barW = 9.dp.toPx()
            val xM = w * 0.28f
            val xS = w * 0.72f

            // 能量柱（从底部起，数据刷新即重绘 → 会动）
            val mh = (moveEnergy.coerceIn(0, 100) / 100f) * h
            val sh = (staticEnergy.coerceIn(0, 100) / 100f) * h
            drawRoundRect(
                MoveColor.copy(alpha = 0.75f),
                topLeft = Offset(xM - barW / 2f, h - mh),
                size = Size(barW, mh),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
            drawRoundRect(
                StaticColor.copy(alpha = 0.75f),
                topLeft = Offset(xS - barW / 2f, h - sh),
                size = Size(barW, sh),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )

            // 门限草稿虚线
            fun thresholdLine(value: Float, color: Color) {
                val y = (1f - value.coerceIn(0f, 100f) / 100f) * h
                drawLine(
                    color,
                    Offset(2f, y), Offset(w - 2f, y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
                )
            }
            thresholdLine(motionThreshold, MoveColor)
            thresholdLine(staticThreshold, StaticColor)
        }
        Spacer(Modifier.height(4.dp))
        Text("门 $gate", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(
            "%.1f~%.1fm".format(gate * resolutionCm / 100.0, (gate + 1) * resolutionCm / 100.0),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ================= 每门数值行 =================

/**
 * 单行：门号 + 距离段 + 实时能量数值 + 两个门限数值按钮（点击弹数值编辑）。
 */
@Composable
private fun GateValueRow(
    gate: Int,
    resolutionCm: Int,
    motion: Int,
    static: Int,
    liveMotion: Int?,
    liveStatic: Int?,
    enabled: Boolean,
    onEdit: (channel: Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("门 $gate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "%.1f~%.1fm".format(gate * resolutionCm / 100.0, (gate + 1) * resolutionCm / 100.0) +
                    if (liveMotion != null && liveStatic != null) " · 实时能量 $liveMotion / $liveStatic" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ValueChip("运动 $motion", MoveColor, enabled) { onEdit(0) }
            ValueChip("静止 $static", StaticColor, enabled) { onEdit(1) }
        }
    }
}

/** 门限数值按钮：药丸形、着通道色、点击进入数值编辑 */
@Composable
private fun ValueChip(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ================= 批量设置 =================

@Composable
private fun BulkCard(gateCount: Int, enabled: Boolean, onApplyAll: (Float, Float) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("全部距离门（批量）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            var m by remember { mutableFloatStateOf(40f) }
            var s by remember { mutableFloatStateOf(40f) }
            Text("运动灵敏度：${m.toInt()}", style = MaterialTheme.typography.bodyMedium)
            Slider(value = m, onValueChange = { m = it }, valueRange = 0f..100f, enabled = enabled)
            Text("静止灵敏度：${s.toInt()}", style = MaterialTheme.typography.bodyMedium)
            Slider(value = s, onValueChange = { s = it }, valueRange = 0f..100f, enabled = enabled)
            Button(
                onClick = { onApplyAll(m, s) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("应用到全部 $gateCount 个距离门") }
        }
    }
}

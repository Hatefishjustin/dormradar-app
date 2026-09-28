package com.dormradar.app.feature.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dormradar.app.core.device.ConnState
import com.dormradar.app.core.device.DeviceInfo
import com.dormradar.app.core.protocol.ProtocolConst
import com.dormradar.app.feature.config.ThresholdEditDialog
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 首页：未连接 → 扫描面板；连接中/已连接 → 设备监控面板（PRD 4.1 第 1/3/12 项）。
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel(), onNavigate: (String) -> Unit = {}) {
    val connState by viewModel.connState.collectAsStateWithLifecycle()
    when (val state = connState) {
        is ConnState.Ready, is ConnState.Connecting, is ConnState.Authenticating,
        is ConnState.Initializing, is ConnState.Reconnecting,
        -> DevicePanel(viewModel, state, onNavigate)
        is ConnState.Disconnected, is ConnState.Failed -> ScanPanel(viewModel, state)
    }
}

// ================= 扫描面板 =================

@Composable
private fun ScanPanel(viewModel: HomeViewModel, connState: ConnState) {
    val scanItems by viewModel.scanItems.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val fakeEnabled by viewModel.fakeDataEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 蓝牙权限：API 31+ 用新权限，旧系统用定位权限（docs/04 §6）
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) viewModel.startScan()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("设备连接", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "扫描附近的 HLK-LD2410 雷达设备并点击连接（默认蓝牙密码 HiLink）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (connState is ConnState.Failed) {
            ErrorBanner(message = connState.reason)
            Spacer(Modifier.height(12.dp))
        }
        scanError?.let {
            ErrorBanner(message = it)
            TextButton(onClick = viewModel::clearScanError) { Text("知道了") }
            Spacer(Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val granted = requiredPermissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (granted) viewModel.startScan() else permissionLauncher.launch(requiredPermissions)
                },
            ) {
                if (scanning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (scanning) "扫描中…（点击停止）" else "开始扫描")
            }
            if (fakeEnabled) {
                OutlinedButton(onClick = viewModel::connectFake) { Text("连接模拟设备") }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (scanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(12.dp))
        if (scanItems.isEmpty() && !scanning) {
            EmptyScanHint(fakeEnabled)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scanItems, key = { it.mac }) { item ->
                    ScanRow(item) { viewModel.connect(item.mac) }
                }
            }
        }
    }
}

@Composable
private fun ScanRow(item: ScanItem, onConnect: () -> Unit) {
    Card(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth().alpha(if (item.isRadar) 1f else 0.55f),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isRadar) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        item.name ?: "未命名设备",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (item.isRadar) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (item.isRadar) AssistChip(onClick = onConnect, label = { Text("雷达") })
                }
                Text(
                    item.mac,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${item.rssi} dBm",
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    item.rssi > -60 -> Color(0xFF16A34A)
                    item.rssi > -80 -> Color(0xFFCA8A04)
                    else -> Color(0xFFDC2626)
                },
            )
        }
    }
}

@Composable
private fun EmptyScanHint(fakeEnabled: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
        Text("尚未发现设备", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            if (fakeEnabled) "确认雷达已通电（模块发热属正常）；或在「设置」中打开模拟数据先体验界面"
            else "确认雷达已通电（模块发热属正常），然后点击「开始扫描」",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ================= 设备监控面板 =================

@Composable
private fun DevicePanel(viewModel: HomeViewModel, connState: ConnState, onNavigate: (String) -> Unit) {
    // 显示用平滑状态：丢失保持 2s + 数值平滑，消除走动时的闪烁抖动
    val displayState by viewModel.displayState.collectAsStateWithLifecycle()
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val energyHistory by viewModel.energyHistory.collectAsStateWithLifecycle()
    val suspectedStatic by viewModel.suspectedStatic.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("首页", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = viewModel::disconnect) { Text("断开") }
            }
        }

        // 重连提示横幅
        if (connState is ConnState.Reconnecting) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "连接断开，正在自动重连（第 ${connState.attempt} 次）…",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        // 状态大卡片（PRD 4.1 第 3 项：三态可视化；用平滑状态防"有人/无人"文字闪烁）
        item {
            StatusHero(radarState = displayState, connState = connState)
        }

        // 疑似静态反射诊断提示（贴门板场景"距离一直不变、没人也显示有人"的根因引导，用户反馈 2026-09-28）
        if (suspectedStatic) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "⚠ 疑似门板静态反射",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "已连续 1 分钟以上显示\"有人\"且距离几乎不变——大概率是门板/墙体的静态反射被当成了目标" +
                                "（灵敏度阈值低于环境底噪的典型症状）。建议：\n" +
                                "① 到「设置 → 调试工具 → 部署向导」重新采样定阈值（最科学）；\n" +
                                "② 或到「灵敏度调节」把静止阈值调高（数值越大越不灵敏）。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onNavigate("tuning") }) { Text("看调参指南") }
                            OutlinedButton(onClick = viewModel::dismissStaticHint) { Text("知道了") }
                        }
                    }
                }
            }
        }

        // 雷达视图（半圆扇形，直观定位目标距离，用户需求 2026-09-27）
        item {
            RadarScopeCard(radarState = displayState, deviceInfo = deviceInfo)
        }

        // 实时数据网格（平滑值，数字不跳动）
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatRow("运动目标", displayState?.moveDistanceCm, displayState?.moveEnergy)
                    HorizontalDivider()
                    StatRow("静止目标", displayState?.staticDistanceCm, displayState?.staticEnergy)
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("检测距离", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            // 无人时设备报 0：显示"—"而不是误导性的"0 m"（用户反馈 2026-09-28）
                            if (displayState?.hasTarget == true) formatMeter(displayState!!.detectDistanceCm) else "--",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        // 能量实时曲线（简单层·首页图表区，docs/06 阶段 2 任务 4；门限可直接拖动调整）
        item {
            val configBusy by viewModel.configBusy.collectAsStateWithLifecycle()
            val thresholdMessage by viewModel.thresholdMessage.collectAsStateWithLifecycle()
            EnergyChartCard(
                history = energyHistory,
                deviceInfo = deviceInfo,
                canEdit = connState is ConnState.Ready && !configBusy,
                thresholdMessage = thresholdMessage,
                onDismissThresholdMessage = viewModel::clearThresholdMessage,
                onThresholdCommit = viewModel::setGateThreshold,
            )
        }

        // 一键设置入口（简单层直达）
        item {
            Card(
                onClick = { onNavigate("quicksetup") },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("一键设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "三套推荐参数（标准 / 灵敏穿门 / 强抗干扰），一键下发并自动校验",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // 后台监控开关（阶段 3：前台服务 + 通知引擎 + 事件时间线）
        item {
            MonitorCard(viewModel)
        }

        // 设备信息卡（PRD 4.1 第 12 项）
        deviceInfo?.let { info ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("设备信息", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        InfoLine("MAC", info.mac)
                        info.firmwareVersion?.let { InfoLine("固件版本", it) }
                        info.protocolVersion?.let { InfoLine("协议版本", "v$it") }
                        info.params?.let { p ->
                            InfoLine("最大距离门", "运动 ${p.maxMoveGate} / 静止 ${p.maxStaticGate}")
                            InfoLine("无人持续时间", "${p.unmannedSeconds} 秒")
                        }
                        info.resolution02m?.let { InfoLine("距离分辨率", if (it) "0.2 m" else "0.75 m") }
                        if (info.isFake) InfoLine("数据来源", "模拟（可在设置中关闭）")
                    }
                }
            }
        }
    }
}

// ================= 能量实时曲线 =================

/**
 * 运动/静止能量随时间曲线 + 当前检测门的灵敏度门限线（docs/06 阶段 2 任务 4）。
 * 纯 Canvas 自绘（不引入图表库，遵守 docs/08 §5.5 依赖纪律）。
 * 用户需求 2026-09-28：左侧带数值刻度；虚线门限可直接上下拖动调整（松手写入设备）。
 */
@Composable
private fun EnergyChartCard(
    history: List<EnergySample>,
    deviceInfo: DeviceInfo?,
    canEdit: Boolean,
    thresholdMessage: String?,
    onDismissThresholdMessage: () -> Unit,
    onThresholdCommit: (gate: Int, channel: Int, value: Int) -> Unit,
) {
    // 结果提示自动消失
    LaunchedEffect(thresholdMessage) {
        if (thresholdMessage != null) {
            delay(4_000)
            onDismissThresholdMessage()
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("能量曲线", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChartLegend("运动", Color(0xFFDC2626))
                    ChartLegend("静止", Color(0xFF9333EA))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "等待数据…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // 最新样本 → 当前检测门 → 门限值（chips 数值修改与虚线拖动共用）
                val latest = history.last()
                val resolutionCm = if (deviceInfo?.resolution02m == true) 20 else 75
                val gate = if (latest.detectDistanceCm > 0) (latest.detectDistanceCm / resolutionCm).coerceAtLeast(0) else 0
                val moveThreshold = deviceInfo?.params?.motionSensitivities?.getOrNull(gate)
                val staticThreshold = deviceInfo?.params?.staticSensitivities?.getOrNull(gate)
                // 数值编辑通道（-1 = 关闭；0=运动 1=静止）
                var editChannel by remember { mutableIntStateOf(-1) }

                EnergyChartCanvas(
                    history = history,
                    gate = gate,
                    moveThreshold = moveThreshold,
                    staticThreshold = staticThreshold,
                    canEdit = canEdit,
                    onCommit = onThresholdCommit,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (canEdit) "虚线 = 当前检测距离处的灵敏度门限，可直接上下拖动调整（红=运动 紫=静止）"
                    else "虚线 = 当前检测距离处的灵敏度门限（能量超过门限即判定有人）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                thresholdMessage?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }

                // 门限数值按钮：点击直接输入精确值（用户需求 2026-09-28，比拖动更精细）
                if (moveThreshold != null && staticThreshold != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "门 $gate：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ThresholdValueChip("运动 $moveThreshold", Color(0xFFDC2626), canEdit) { editChannel = CHANNEL_MOTION }
                        ThresholdValueChip("静止 $staticThreshold", Color(0xFF9333EA), canEdit) { editChannel = CHANNEL_STATIC }
                    }
                }
                if (editChannel >= 0 && moveThreshold != null && staticThreshold != null) {
                    ThresholdEditDialog(
                        title = "门 $gate · ${if (editChannel == CHANNEL_MOTION) "运动" else "静止"}门限",
                        initial = if (editChannel == CHANNEL_MOTION) moveThreshold else staticThreshold,
                        subtitle = "当前检测距离 ${latest.detectDistanceCm} cm（门 $gate），修改后立即写入设备",
                        onDismiss = { editChannel = -1 },
                        onConfirm = { v ->
                            onThresholdCommit(gate, editChannel, v)
                            editChannel = -1
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** 门限数值按钮：药丸形、着通道色，点击弹数值编辑对话框（用户需求 2026-09-28） */
@Composable
private fun ThresholdValueChip(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 门限拖动通道：0=运动 1=静止 */
private const val CHANNEL_MOTION = 0
private const val CHANNEL_STATIC = 1

@Composable
private fun EnergyChartCanvas(
    history: List<EnergySample>,
    gate: Int,
    moveThreshold: Int?,
    staticThreshold: Int?,
    canEdit: Boolean,
    onCommit: (gate: Int, channel: Int, value: Int) -> Unit,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val moveColor = Color(0xFFDC2626)
    val staticColor = Color(0xFF9333EA)
    val textMeasurer = rememberTextMeasurer()

    // 拖动状态：dragChannel >= 0 表示正在拖对应门限线
    var dragChannel by remember { mutableIntStateOf(-1) }
    var dragValue by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .pointerInput(canEdit, moveThreshold, staticThreshold, gate) {
                if (!canEdit || moveThreshold == null || staticThreshold == null) return@pointerInput
                // y 坐标 ↔ 灵敏度换算（与绘制一致：v=0 在底部、100 在顶部，留 4px 边距）
                val h = size.height.toFloat()
                fun yOf(v: Int) = h - (v.coerceIn(0, 100) / 100f) * (h - 4f) - 2f
                fun valueAt(y: Float) = (((h - 2f - y) / (h - 4f)) * 100f).roundToInt().coerceIn(0, 100)
                detectVerticalDragGestures(
                    onDragStart = { pos ->
                        // 抓取距按下点更近的那条线（容差 24dp，避免误抓曲线）
                        val tol = 24.dp.toPx()
                        val dM = abs(pos.y - yOf(moveThreshold))
                        val dS = abs(pos.y - yOf(staticThreshold))
                        dragChannel = when {
                            dM <= tol && dM <= dS -> CHANNEL_MOTION
                            dS <= tol -> CHANNEL_STATIC
                            else -> -1
                        }
                        if (dragChannel >= 0) dragValue = valueAt(pos.y)
                    },
                    onDragEnd = {
                        if (dragChannel >= 0) {
                            onCommit(gate, dragChannel, dragValue)
                        }
                        dragChannel = -1
                    },
                    onDragCancel = { dragChannel = -1 },
                ) { change, _ ->
                    if (dragChannel >= 0) {
                        dragValue = valueAt(change.position.y)
                        change.consume()
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val axisW = 26.dp.toPx() // 左侧刻度区
            val yOf: (Int) -> Float = { v -> h - (v.coerceIn(0, 100) / 100f) * (h - 4f) - 2f }
            val axisStyle = TextStyle(fontSize = 9.sp, color = labelColor)

            // 网格 + 左侧刻度（0/25/50/75/100）
            listOf(0, 25, 50, 75, 100).forEach { g ->
                val y = yOf(g)
                drawLine(gridColor, Offset(axisW, y), Offset(w, y), strokeWidth = 1f)
                drawText(textMeasurer, "$g", topLeft = Offset(0f, y - 6.dp.toPx()), style = axisStyle)
            }

            fun path(values: (EnergySample) -> Int): Path {
                val p = Path()
                val n = history.size
                history.forEachIndexed { i, s ->
                    val x = if (n == 1) w else axisW + i * (w - axisW) / (n - 1)
                    val y = yOf(values(s))
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                return p
            }

            // 门限参考线（当前检测门；拖动中显示拖动值）
            val showMove = if (dragChannel == CHANNEL_MOTION) dragValue else moveThreshold
            val showStatic = if (dragChannel == CHANNEL_STATIC) dragValue else staticThreshold
            showMove?.let {
                drawLine(
                    moveColor,
                    Offset(axisW, yOf(it)), Offset(w, yOf(it)),
                    strokeWidth = if (dragChannel == CHANNEL_MOTION) 5f else 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }
            showStatic?.let {
                drawLine(
                    staticColor.copy(alpha = 0.75f),
                    Offset(axisW, yOf(it)), Offset(w, yOf(it)),
                    strokeWidth = if (dragChannel == CHANNEL_STATIC) 5f else 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f)),
                )
            }

            // 拖动中：右端数值气泡
            if (dragChannel >= 0) {
                val y = yOf(dragValue)
                val color = if (dragChannel == CHANNEL_MOTION) moveColor else staticColor
                val layout = textMeasurer.measure(
                    "$dragValue",
                    TextStyle(fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold),
                )
                val pad = 5.dp.toPx()
                val bubbleW = layout.size.width + pad * 2
                val bx = w - bubbleW - 6.dp.toPx()
                drawRoundRect(color, cornerRadius = CornerRadius(8.dp.toPx()), topLeft = Offset(bx, y - layout.size.height / 2f - pad / 2f), size = Size(bubbleW, layout.size.height + pad))
                drawText(layout, topLeft = Offset(bx + pad, y - layout.size.height / 2f))
            }

            drawPath(path { it.moveEnergy }, color = moveColor, style = Stroke(width = 4f))
            drawPath(path { it.staticEnergy }, color = staticColor, style = Stroke(width = 4f))
        }
    }
}

@Composable
private fun StatusHero(radarState: com.dormradar.app.core.device.RadarState?, connState: ConnState) {
    val state = radarState
    val (label, color) = when (state?.targetState) {
        ProtocolConst.TARGET_MOVING -> "有人 · 运动" to Color(0xFFDC2626)
        ProtocolConst.TARGET_STATIC -> "有人 · 静止" to Color(0xFF9333EA)
        ProtocolConst.TARGET_BOTH -> "有人 · 运动+静止" to Color(0xFFEA580C)
        ProtocolConst.TARGET_NONE -> "无人" to Color(0xFF16A34A)
        null -> if (connState is ConnState.Ready) "等待数据…" to MaterialTheme.colorScheme.outline else "连接中…" to MaterialTheme.colorScheme.outline
        else -> "未知状态" to MaterialTheme.colorScheme.outline
    }
    // 底噪检测期间的扩展状态（0x04~0x06，docs/03 §3.2）
    val noiseLabel = when (state?.targetState) {
        ProtocolConst.TARGET_NOISE_RUNNING -> "底噪检测进行中"
        ProtocolConst.TARGET_NOISE_SUCCESS -> "底噪检测成功"
        ProtocolConst.TARGET_NOISE_FAILED -> "底噪检测失败"
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 呼吸动画圆点：有数据时呈现"活着"的感觉
            val transition = rememberInfiniteTransition(label = "pulse")
            val pulse by transition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "pulseScale",
            )
            Box(
                modifier = Modifier
                    .size(if (state?.hasTarget == true) 84.dp else 72.dp)
                    .alpha(pulse)
                    .background(color.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(40.dp).background(color, CircleShape))
            }
            Spacer(Modifier.height(12.dp))
            Text(noiseLabel ?: label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val updated = state?.updatedAt
            Text(
                if (updated != null) "最后更新：${formatTime(updated)}" else "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatRow(label: String, distanceCm: Int?, energy: Int?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                distanceCm?.takeIf { it > 0 }?.let { formatMeter(it) } ?: "--",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                if (energy != null && energy > 0) "能量 $energy" else "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

// ================= 后台监控（阶段 3） =================

/**
 * 后台监控开关卡（PRD 4.0 简单层：通知开关）。
 * 开启流程：通知权限（API 33+）→ 启动前台服务 → 首次弹 MIUI 保活引导。
 */
@Composable
private fun MonitorCard(viewModel: HomeViewModel) {
    val monitorEnabled by viewModel.monitorEnabled.collectAsStateWithLifecycle()
    val monitorMode by viewModel.monitorMode.collectAsStateWithLifecycle()
    val guideShown by viewModel.keepAliveGuideShown.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showKeepAliveGuide by rememberSaveable { mutableStateOf(false) }

    // 通知权限（API 33+ 运行时申请，docs/04 §6）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.enableMonitor() // 未授权也开启（事件仍入库，授权后即有通知）
        if (granted && !guideShown) showKeepAliveGuide = true
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("后台监控", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "当前模式：${monitorMode.label}。开启后 App 退到后台也能持续检测并发通知，事件自动记入时间线",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = monitorEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        when {
                            needsPermission -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            !guideShown -> {
                                viewModel.enableMonitor()
                                showKeepAliveGuide = true
                            }

                            else -> viewModel.enableMonitor()
                        }
                    } else {
                        viewModel.disableMonitor()
                    }
                },
            )
        }
    }

    if (showKeepAliveGuide) {
        KeepAliveGuideDialog(
            onDone = {
                viewModel.markKeepAliveGuideShown()
                showKeepAliveGuide = false
            },
        )
    }
}

/**
 * 后台保活引导（全品牌适配）：
 * 1. 「一键优化」= 系统电池优化白名单（标准 Android API，各品牌通用）；
 * 2. 正文按手机品牌给出厂商专属省电设置的路径指引。
 */
@Composable
private fun KeepAliveGuideDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
                onDone()
            }) { Text("一键优化") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("下次再说") } },
        title = { Text("让监控更可靠") },
        text = {
            Text(
                "手机系统可能清理后台应用导致监控中断，建议两步：\n\n" +
                    "1. 点「一键优化」，在系统弹窗中允许宿管雷达忽略电池优化（各品牌通用）\n" +
                    "2. 如仍被清理，按你的品牌手动设置：\n${vendorPowerTips()}\n\n" +
                    "设置后通知到达率会显著提升。",
            )
        },
    )
}

/** 按品牌返回厂商省电设置的路径指引（无法覆盖的品牌走通用路径） */
private fun vendorPowerTips(): String {
    val m = Build.MANUFACTURER.lowercase()
    return when {
        m.contains("xiaomi") || m.contains("redmi") ->
            "小米：设置 → 应用设置 → 应用管理 → 宿管雷达 → 省电策略 → 无限制"

        m.contains("huawei") ->
            "华为：设置 → 应用和服务 → 应用管理 → 宿管雷达 → 耗电详情 → 启动管理 → 关闭自动管理、允许后台活动"

        m.contains("honor") ->
            "荣耀：设置 → 应用 → 应用管理 → 宿管雷达 → 启动管理 → 关闭自动管理、允许后台活动"

        m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
            "OPPO / 一加 / realme：设置 → 电池 → 更多设置 → 宿管雷达 → 允许完全后台行为"

        m.contains("vivo") || m.contains("iqoo") ->
            "vivo / iQOO：设置 → 电池 → 后台耗电管理 → 宿管雷达 → 允许后台高耗电"

        m.contains("samsung") ->
            "三星：设置 → 电池 → 后台使用限制 → 把宿管雷达从「受限应用」中移除"

        m.contains("meizu") ->
            "魅族：设置 → 电池 → 优化 → 宿管雷达 → 允许后台运行"

        else ->
            "通用：设置 → 应用 → 宿管雷达 → 电池 → 不受限制"
    }
}

/** 简化工具 */

private fun formatMeter(cm: Int): String = if (cm >= 100) "%.2f m".format(cm / 100.0) else "$cm cm"

private fun formatTime(epochMs: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMs))
}

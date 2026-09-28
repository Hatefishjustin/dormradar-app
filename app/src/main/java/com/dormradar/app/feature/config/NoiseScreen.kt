package com.dormradar.app.feature.config

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.device.OpResult
import com.dormradar.app.core.device.RadarConnection
import com.dormradar.app.core.protocol.DeviceParams
import com.dormradar.app.core.protocol.ProtocolConst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** 底噪检测流程状态机（引导式，docs/06 阶段 2 任务 6） */
sealed interface NoisePhase {
    /** 待开始：展示清场提示 */
    data object Idle : NoisePhase

    /** 命令下发中 */
    data object Starting : NoisePhase

    /** 检测进行中（时长由所选档位下发，期间必须无人） */
    data class Running(val startedAt: Long, val durationSec: Int) : NoisePhase

    /** 成功：设备已自动应用建议灵敏度，展示回读结果 */
    data class Success(val newParams: DeviceParams?) : NoisePhase

    /** 失败（有人经过 / 超时 / 设备拒绝） */
    data class Failed(val reason: String) : NoisePhase
}

/** 可选检测时长档位（秒）：0x000B 命令带时长参数；个别固件可能只支持 60s，以设备实际执行为准 */
val NOISE_DURATION_OPTIONS = listOf(30, 60, 120)

@HiltViewModel
class NoiseViewModel @Inject constructor(
    private val connection: RadarConnection,
) : ViewModel() {

    private val _phase = MutableStateFlow<NoisePhase>(NoisePhase.Idle)
    val phase: StateFlow<NoisePhase> = _phase.asStateFlow()

    private val isFake: Boolean get() = connection.deviceInfo.value?.isFake == true

    /** 所选检测时长（档位见 [NOISE_DURATION_OPTIONS]），失败重试沿用同一时长 */
    private val _durationSec = MutableStateFlow(60)
    val durationSec: StateFlow<Int> = _durationSec.asStateFlow()

    fun setDuration(sec: Int) {
        if (_phase.value is NoisePhase.Idle || _phase.value is NoisePhase.Failed) {
            if (sec in NOISE_DURATION_OPTIONS) _durationSec.value = sec
        }
    }

    fun start() {
        val durationSec = _durationSec.value
        if (_phase.value is NoisePhase.Starting || _phase.value is NoisePhase.Running) return
        viewModelScope.launch {
            _phase.value = NoisePhase.Starting
            when (val r = connection.startNoiseDetection(durationSec)) {
                is OpResult.Fail -> _phase.value = NoisePhase.Failed(r.reason)
                OpResult.Ok -> {
                    _phase.value = NoisePhase.Running(System.currentTimeMillis(), durationSec)
                    if (isFake) {
                        // 模拟设备：数秒后直接成功（缩短演示等待）
                        delay(minOf(durationSec * 1000L / 4, 10_000L))
                        if (_phase.value is NoisePhase.Running) finishSuccess()
                    } else {
                        // 真机：监听上报帧目标状态 0x05=成功 / 0x06=失败（docs/03 §3.2）
                        val result = withTimeoutOrNull(durationSec * 1000L + 30_000L) {
                            connection.radarState.first {
                                it != null && (it.targetState == ProtocolConst.TARGET_NOISE_SUCCESS ||
                                    it.targetState == ProtocolConst.TARGET_NOISE_FAILED)
                            }
                        }
                        when {
                            result == null -> _phase.value = NoisePhase.Failed("超时未收到检测结果，请重试")
                            result.targetState == ProtocolConst.TARGET_NOISE_SUCCESS -> finishSuccess()
                            else -> _phase.value = NoisePhase.Failed("检测失败：期间可能有人经过，请清场后重试")
                        }
                    }
                }
            }
        }
    }

    private suspend fun finishSuccess() {
        connection.refreshDeviceInfo() // 底噪检测 = 灵敏度自动配置，回读设备新参数
        _phase.value = NoisePhase.Success(connection.deviceInfo.value?.params)
    }

    fun reset() {
        if (_phase.value is NoisePhase.Running) return // 进行中不允许重置
        _phase.value = NoisePhase.Idle
    }
}

/**
 * 底噪检测引导页（简单层）：清场提示 → 开始/进度 → 结果展示
 * （docs/06 阶段 2 任务 6；检测完成后设备自动把各门灵敏度写为底噪建议值）。
 */
@Composable
fun NoiseScreen(
    onBack: () -> Unit,
    viewModel: NoiseViewModel = hiltViewModel(),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val durationSec by viewModel.durationSec.collectAsStateWithLifecycle()

    ConfigScaffold(title = "底噪检测", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val p = phase) {
                NoisePhase.Idle -> IdleCard(durationSec, viewModel::setDuration, viewModel::start)
                NoisePhase.Starting -> Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("正在下发检测命令…")
                    }
                }
                is NoisePhase.Running -> RunningCard(p.startedAt, p.durationSec)
                is NoisePhase.Success -> SuccessCard(p.newParams, viewModel::reset)
                is NoisePhase.Failed -> FailedCard(p.reason, viewModel::start)
            }
        }
    }
}

@Composable
private fun IdleCard(
    durationSec: Int,
    onSelectDuration: (Int) -> Unit,
    onStart: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("什么是底噪检测？", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "在无人环境下测量各距离门的背景能量（底噪）。检测完成后设备会" +
                    "自动把每个门的灵敏度设为略高于底噪的建议值并立即生效——" +
                    "相当于自动消除底噪导致的误报，无需再手动保存。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("开始前请确认：", style = MaterialTheme.typography.titleSmall)
                    Text("① 检测区域（含门板另一侧）全程不会有人走动", style = MaterialTheme.typography.bodySmall)
                    Text("② 期间不要遮挡或移动雷达", style = MaterialTheme.typography.bodySmall)
                    Text("③ 检测期间请保持 App 在本页面", style = MaterialTheme.typography.bodySmall)
                }
            }

            // 检测时长档位
            Text("检测时长", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NOISE_DURATION_OPTIONS.forEach { sec ->
                    FilterChip(
                        selected = durationSec == sec,
                        onClick = { onSelectDuration(sec) },
                        label = { Text("${sec} 秒") },
                    )
                }
            }
            Text(
                "时长越长采样越充分、抗误报越稳，快速档适合先试效果。个别旧固件可能只支持 60 秒，以设备实际执行为准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("开始检测（$durationSec 秒）") }
        }
    }
}

@Composable
private fun RunningCard(startedAt: Long, durationSec: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 每秒刷新进度
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            androidx.compose.runtime.LaunchedEffect(startedAt) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(500)
                }
            }
            val elapsedSec = ((now - startedAt) / 1000).coerceIn(0, durationSec.toLong())
            val progress by animateFloatAsState(
                targetValue = elapsedSec / durationSec.toFloat(),
                animationSpec = tween(600),
                label = "noiseProgress",
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(88.dp),
                strokeWidth = 8.dp,
            )
            Text("检测进行中… $elapsedSec / $durationSec 秒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "请保持检测区域无人走动",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SuccessCard(newParams: DeviceParams?, onDone: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(36.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFF16A34A), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                }
                Text("检测完成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                "设备已按底噪测量结果自动配置各门灵敏度（下方为写入后的实际值）。\n\n" +
                    "注意：设备写入的是\"宁漏报不误报\"的保守值（略高于底噪）。" +
                    "如果之后**走动也没反应**，通常是阈值比活动能量还高：\n" +
                    "① 推荐：到「调试工具 → 部署向导」重新采样（底噪+走动各一次，自动算出合理阈值）；\n" +
                    "② 或到「灵敏度调节」把对应门的阈值调低（数值越小越灵敏）。\n" +
                    "如需微调也可到「灵敏度调节」页逐门调整。",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (newParams != null) {
                Spacer(Modifier.height(4.dp))
                Text("当前灵敏度（门 / 运动 / 静止）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                val gateCount = newParams.motionSensitivities.size
                for (g in 0 until gateCount) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("门 $g", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${newParams.motionSensitivities[g]} / ${newParams.staticSensitivities.getOrElse(g) { 0 }}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
}

@Composable
private fun FailedCard(reason: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("检测失败", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(reason, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

package com.dormradar.app.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.device.DeviceInfo
import com.dormradar.app.core.device.OpResult
import com.dormradar.app.core.device.RadarConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 参数配置页 VM：提交草稿到设备（详细层，PRD 4.1 第 5 项） */
@HiltViewModel
class ParamsViewModel @Inject constructor(
    private val connection: RadarConnection,
) : ViewModel() {

    val deviceInfo: StateFlow<DeviceInfo?> = connection.deviceInfo

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _messageIsError = MutableStateFlow(false)
    val messageIsError: StateFlow<Boolean> = _messageIsError.asStateFlow()

    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    fun applyParams(moveMaxGate: Int, staticMaxGate: Int, unmannedSeconds: Int) {
        if (_applying.value) return
        _applying.value = true
        viewModelScope.launch {
            finish(connection.applyParams(moveMaxGate, staticMaxGate, unmannedSeconds), "参数已保存（回读校验一致）")
        }
    }

    fun applyResolution(resolution02m: Boolean) {
        if (_applying.value) return
        _applying.value = true
        viewModelScope.launch {
            finish(connection.applyResolution(resolution02m), "分辨率已切换，距离门含义已刷新")
        }
    }

    /** 近距屏蔽（最小距离门）：[0, minGate) 灵敏度写 100 = 忽略该范围 */
    fun setMinGate(minGate: Int) {
        if (_applying.value) return
        _applying.value = true
        viewModelScope.launch {
            finish(connection.setMinGate(minGate), "近距屏蔽已更新（回读确认）")
        }
    }

    private fun finish(result: OpResult, okMessage: String) {
        when (result) {
            OpResult.Ok -> {
                _messageIsError.value = false
                _message.value = okMessage
            }
            is OpResult.Fail -> {
                _messageIsError.value = true
                _message.value = "保存失败：${result.reason}"
            }
        }
        _applying.value = false
    }

    fun clearMessage() {
        _message.value = null
    }
}

/** 无人时长可选档位（秒） */
private val UNMANNED_STEPS = intArrayOf(0, 1, 2, 5, 10, 15, 30, 60, 120, 300, 600)

/**
 * 参数配置页（详细层）：最远探测距离（运动/静止门）、无人持续时间、距离分辨率
 * （docs/06 阶段 2 任务 1）。
 */
@Composable
fun ParamsScreen(
    onBack: () -> Unit,
    viewModel: ParamsViewModel = hiltViewModel(),
) {
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val applying by viewModel.applying.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageIsError by viewModel.messageIsError.collectAsStateWithLifecycle()
    val params = deviceInfo?.params
    val resolutionCm = if (deviceInfo?.resolution02m == true) 20 else 75

    ConfigScaffold(title = "参数配置", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (message != null) {
                ResultBanner(message!!, isError = messageIsError, onDismiss = viewModel::clearMessage)
            }

            if (params == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (deviceInfo == null) "设备未连接，请先回到首页连接雷达" else "参数读取中…",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // ---- 距离门与无人时长 ----
                GateCard(params, resolutionCm, applying, viewModel::applyParams)

                // ---- 近距屏蔽（最小距离门）----
                MinGateCard(params, resolutionCm, applying, viewModel::setMinGate)

                // ---- 距离分辨率（能力允许时显示）----
                if (deviceInfo?.resolution02m != null) {
                    ResolutionCard(
                        current02m = deviceInfo!!.resolution02m!!,
                        applying = applying,
                        onApply = viewModel::applyResolution,
                    )
                }
            }
        }
    }
}

@Composable
private fun GateCard(
    params: com.dormradar.app.core.protocol.DeviceParams,
    resolutionCm: Int,
    applying: Boolean,
    onApply: (Int, Int, Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("最远探测距离与无人时长", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "距离门 = 雷达把探测范围切成 ${resolutionCm}cm 一段；门数越大看得越远，也越容易受远处干扰",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            var moveGate by remember(params) { mutableFloatStateOf(params.maxMoveGate.toFloat()) }
            var staticGate by remember(params) { mutableFloatStateOf(params.maxStaticGate.toFloat()) }
            var unmannedIdx by remember(params) {
                mutableFloatStateOf(
                    UNMANNED_STEPS.indexOfFirst { it >= params.unmannedSeconds }.coerceAtLeast(0).toFloat(),
                )
            }

            GateSlider(
                label = "运动目标最远门",
                value = moveGate,
                onValueChange = { moveGate = it },
                resolutionCm = resolutionCm,
            )
            GateSlider(
                label = "静止目标最远门",
                value = staticGate,
                onValueChange = { staticGate = it },
                resolutionCm = resolutionCm,
            )

            val unmannedSec = UNMANNED_STEPS[unmannedIdx.toInt().coerceIn(0, UNMANNED_STEPS.size - 1)]
            Text(
                "无人持续时间：${formatSeconds(unmannedSec)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = unmannedIdx,
                onValueChange = { unmannedIdx = it },
                valueRange = 0f..(UNMANNED_STEPS.size - 1).toFloat(),
                steps = UNMANNED_STEPS.size - 2,
            )
            Text(
                "人离开后保持「有人」多久才复位（影响「离开」事件时机）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onApply(moveGate.toInt(), staticGate.toInt(), unmannedSec)
                },
                enabled = !applying,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (applying) "正在保存…" else "保存到设备") }
        }
    }
}

@Composable
private fun GateSlider(label: String, value: Float, onValueChange: (Float) -> Unit, resolutionCm: Int) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "门 ${value.toInt()}（约 ${"%.1f".format(value.toInt() * resolutionCm / 100.0)} m）",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value,
            onValueChange = { onValueChange(it.coerceIn(2f, 8f)) },
            valueRange = 2f..8f,
            steps = 5,
        )
    }
}

/**
 * 近距屏蔽卡（用户需求「最小距离门」）：[0, minGate) 的距离门灵敏度写 100 = 设备忽略该范围；
 * 实际生效范围 = 屏蔽线 → 最远距离门。屏蔽线之后的门沿用首个活动门的灵敏度。
 */
@Composable
private fun MinGateCard(
    params: com.dormradar.app.core.protocol.DeviceParams,
    resolutionCm: Int,
    applying: Boolean,
    onApply: (Int) -> Unit,
) {
    // 当前屏蔽线 = 首个未屏蔽（运动灵敏度 < 100）的门
    val firstActive = params.motionSensitivities.indexOfFirst { it < 100 }
    val currentMinGate = if (firstActive < 0) params.motionSensitivities.size - 1 else firstActive
    val maxEffective = minOf(params.maxMoveGate, params.maxStaticGate)
    val maxSelectable = (maxEffective - 1).coerceAtLeast(0)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("近距屏蔽（最小距离门）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "从雷达到此距离内的动静一律忽略（不报警），只有屏蔽线到最远距离门之间会触发。" +
                    "适合过滤门板自身晃动、贴门近处的干扰。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "当前生效范围：%.1f m ~ %.1f m".format(currentMinGate * resolutionCm / 100.0, maxEffective * resolutionCm / 100.0),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            var selection by remember(params) { mutableStateOf(currentMinGate) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..maxSelectable).take(5).forEach { g ->
                        FilterChip(
                            selected = selection == g,
                            onClick = { selection = g },
                            label = { Text(if (g == 0) "不屏蔽" else "门 $g") },
                        )
                    }
                }
                if (maxSelectable >= 5) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (5..maxSelectable).forEach { g ->
                            FilterChip(
                                selected = selection == g,
                                onClick = { selection = g },
                                label = { Text("门 $g") },
                            )
                        }
                    }
                }
            }
            Text(
                if (selection == 0) "不屏蔽近距（出厂行为）"
                else "将忽略 0 ~ %.1f m 内的所有动静".format(selection * resolutionCm / 100.0),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { onApply(selection) },
                enabled = !applying && selection != currentMinGate,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (selection == currentMinGate) "当前已生效" else "应用近距屏蔽") }
        }
    }
}

@Composable
private fun ResolutionCard(current02m: Boolean, applying: Boolean, onApply: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("距离分辨率", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "0.75m：看得远（门 8 ≈ 6m），适合走廊；0.2m：看得细（近距定位准），适合贴门检测",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !current02m,
                    onClick = { if (!applying && current02m) onApply(false) },
                    label = { Text("0.75 m（远）") },
                )
                FilterChip(
                    selected = current02m,
                    onClick = { if (!applying && !current02m) onApply(true) },
                    label = { Text("0.2 m（细）") },
                )
            }
            Text(
                "切换后距离门数量与含义会变化，现有灵敏度表将按新分辨率重新生效",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ResultBanner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    }
}

internal fun formatSeconds(sec: Int): String = when {
    sec < 60 -> "$sec 秒"
    sec % 60 == 0 -> "${sec / 60} 分钟"
    else -> "${sec / 60} 分 ${sec % 60} 秒"
}

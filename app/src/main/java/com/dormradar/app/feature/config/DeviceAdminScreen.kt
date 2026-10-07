package com.dormradar.app.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.dormradar.app.core.device.DeviceInfo
import com.dormradar.app.core.device.OpResult
import com.dormradar.app.core.device.RadarConnection
import com.dormradar.app.core.protocol.PhotosensitiveConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 设备管理页 VM（PRD 4.1 第 12/13/14 项） */
@HiltViewModel
class DeviceAdminViewModel @Inject constructor(
    private val connection: RadarConnection,
) : ViewModel() {

    val deviceInfo: StateFlow<DeviceInfo?> = connection.deviceInfo

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _messageIsError = MutableStateFlow(false)
    val messageIsError: StateFlow<Boolean> = _messageIsError.asStateFlow()

    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    /** 光敏配置（null = 未探测到/固件不支持） */
    private val _photosensitive = MutableStateFlow<PhotosensitiveConfig?>(null)
    val photosensitive: StateFlow<PhotosensitiveConfig?> = _photosensitive.asStateFlow()

    /** 门限值（null = 未探测到/固件不支持） */
    private val _threshold = MutableStateFlow<Pair<Int, Int>?>(null)
    val threshold: StateFlow<Pair<Int, Int>?> = _threshold.asStateFlow()

    init {
        // 进入页面即探测两项新固件能力（docs/03 §5.6：超时=不支持，容错降级）
        loadPhotosensitive()
        loadThreshold()
    }

    private fun runOp(okMessage: String, op: suspend () -> OpResult) {
        if (_applying.value) return
        _applying.value = true
        viewModelScope.launch {
            when (val r = op()) {
                OpResult.Ok -> {
                    _messageIsError.value = false
                    _message.value = okMessage
                }
                is OpResult.Fail -> {
                    _messageIsError.value = true
                    _message.value = r.reason
                }
            }
            _applying.value = false
        }
    }

    fun reboot() = runOp("重启指令已发送，设备将断开并自动重连") { connection.reboot() }

    fun factoryReset() = runOp("已恢复出厂设置，参数已刷新") { connection.factoryReset() }

    fun setBluetoothEnabled(enabled: Boolean) =
        runOp(if (enabled) "蓝牙已开启" else "蓝牙已关闭（设备将断开连接）") { connection.setBluetoothEnabled(enabled) }

    fun changePassword(newPassword: String) =
        runOp("设备密码已修改，App 内保存的密码已同步更新") { connection.changeDevicePassword(newPassword) }

    fun applyBaudRate(selection: Int) =
        runOp("波特率已设置（档位 $selection，重启后生效）") { connection.applyBaudRate(selection) }

    fun applyPhotosensitive(state: Int, threshold: Int) =
        runOp("光敏配置已保存") { connection.applyPhotosensitive(state, threshold) }

    fun applyThreshold(upper: Int, lower: Int) =
        runOp("门限值已保存") { connection.applyThreshold(upper, lower) }

    fun loadPhotosensitive() {
        viewModelScope.launch { _photosensitive.value = connection.readPhotosensitive() }
    }

    fun loadThreshold() {
        viewModelScope.launch { _threshold.value = connection.readThreshold() }
    }

    fun clearMessage() {
        _message.value = null
    }
}

/** 波特率档位表（0x00A1，docs/03 §2） */
private val BAUD_RATES = listOf(
    1 to "9600", 2 to "19200", 3 to "38400", 4 to "57600",
    5 to "115200", 6 to "230400", 7 to "256000（默认）", 8 to "460800",
)

private val PHOTO_STATES = listOf(0 to "关闭", 1 to "光感低于阈值触发", 2 to "光感高于阈值触发")

/**
 * 设备管理页（详细层）：设备信息、重启、恢复出厂、蓝牙开关、修改密码、
 * 串口波特率、光敏辅助控制、门限值（docs/06 阶段 2 任务 7）。
 */
@Composable
fun DeviceAdminScreen(
    onBack: () -> Unit,
    viewModel: DeviceAdminViewModel = hiltViewModel(),
) {
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val applying by viewModel.applying.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageIsError by viewModel.messageIsError.collectAsStateWithLifecycle()
    val photosensitive by viewModel.photosensitive.collectAsStateWithLifecycle()
    val threshold by viewModel.threshold.collectAsStateWithLifecycle()

    // 二次确认弹窗状态
    var confirmFactoryReset by remember { mutableStateOf(false) }
    var confirmBtOff by remember { mutableStateOf(false) }

    ConfigScaffold(title = "设备管理", onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (message != null) {
                item { ResultBanner(message!!, isError = messageIsError, onDismiss = viewModel::clearMessage) }
            }

            // ---- 设备信息 ----
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("设备信息", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        InfoLine("MAC", deviceInfo?.mac ?: "未连接")
                        InfoLine("固件版本", deviceInfo?.firmwareVersion ?: "—")
                        deviceInfo?.protocolVersion?.let { InfoLine("协议版本", "v$it") }
                        if (deviceInfo?.isFake == true) {
                            InfoLine("数据来源", "模拟设备（操作仅本地生效）")
                        }
                    }
                }
            }

            // ---- 修改设备密码 ----
            item {
                PasswordCard(applying, viewModel::changePassword)
            }

            // ---- 光敏辅助控制 ----
            item {
                PhotosensitiveCard(photosensitive, applying, viewModel::applyPhotosensitive)
            }

            // ---- 门限值 ----
            item {
                ThresholdCard(threshold, applying, viewModel::applyThreshold)
            }

            // ---- 串口波特率 ----
            item {
                BaudRateCard(applying, viewModel::applyBaudRate)
            }

            // ---- 重启 / 恢复出厂 / 蓝牙 ----
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("电源与系统", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(
                            onClick = viewModel::reboot,
                            enabled = !applying,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("重启模块（断开后自动重连）") }
                        OutlinedButton(
                            onClick = { confirmFactoryReset = true },
                            enabled = !applying,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("恢复出厂设置") }
                        OutlinedButton(
                            onClick = { confirmBtOff = true },
                            enabled = !applying,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("关闭设备蓝牙（危险）") }
                    }
                }
            }
        }
    }

    if (confirmFactoryReset) {
        AlertDialog(
            onDismissRequest = { confirmFactoryReset = false },
            title = { Text("恢复出厂设置？") },
            text = { Text("距离门、灵敏度、无人时长等全部参数将恢复为出厂值。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { confirmFactoryReset = false; viewModel.factoryReset() }) { Text("确认恢复") }
            },
            dismissButton = { TextButton(onClick = { confirmFactoryReset = false }) { Text("取消") } },
        )
    }
    if (confirmBtOff) {
        AlertDialog(
            onDismissRequest = { confirmBtOff = false },
            title = { Text("关闭设备蓝牙？") },
            text = {
                Text(
                    "关闭后将立即断开连接，且无法再扫描到设备。\n" +
                        "恢复方法：给雷达断电再上电，在 2~3 秒内重复 5 次。",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmBtOff = false; viewModel.setBluetoothEnabled(false) }) { Text("仍然关闭") }
            },
            dismissButton = { TextButton(onClick = { confirmBtOff = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PasswordCard(applying: Boolean, onApply: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("修改设备密码", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "同时修改设备与 App 内保存的密码（之后连接/自动重连都用新密码）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var draft by rememberSaveable { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(6) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("新密码") },
                    supportingText = { if (draft.length != 6) Text("须为 6 个字符", color = MaterialTheme.colorScheme.error) },
                )
                Button(
                    onClick = { onApply(draft); draft = "" },
                    enabled = !applying && draft.length == 6,
                ) { Text("修改") }
            }
        }
    }
}

@Composable
private fun PhotosensitiveCard(
    config: PhotosensitiveConfig?,
    applying: Boolean,
    onApply: (Int, Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("光敏辅助控制", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (config == null) {
                Text(
                    "当前固件不支持光敏查询（0x00AE 无响应）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Text(
                "让 OUT 引脚联动光照：环境光越过阈值时翻转电平（默认阈值 128）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var state by remember(config) { mutableStateOf(config.state.coerceIn(0, 2)) }
            var threshold by remember(config) { mutableFloatStateOf(config.threshold.toFloat()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PHOTO_STATES.forEach { (value, label) ->
                    FilterChip(
                        selected = state == value,
                        onClick = { state = value },
                        label = { Text(label) },
                    )
                }
            }
            Text("光敏阈值：${threshold.toInt()}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                valueRange = 0f..255f,
                enabled = !applying,
            )
            Button(
                onClick = { onApply(state, threshold.toInt()) },
                enabled = !applying,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存光敏配置") }

            HorizontalDivider()

            // ---- OUT 引脚灯控实验区（用户需求 2026-10-07：外接 LED 的关闭实验）----
            Text("OUT 引脚灯控（实验性）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "若你的 LED 接在 OUT 引脚上：点下面按钮把辅助联动设为「关闭」，再观察 LED 是否熄灭。" +
                    "OUT 关闭后的实际行为依固件而定，请以实测为准；想恢复联动时用上面的滑杆选回模式并保存即可。" +
                    "注：模块板载的小指示灯由固件控制、协议没有关闭命令，只能物理遮蔽。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onApply(0, threshold.toInt()) },
                enabled = !applying,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("关闭辅助联动（OUT 灯控实验）") }
        }
    }
}

@Composable
private fun ThresholdCard(
    threshold: Pair<Int, Int>?,
    applying: Boolean,
    onApply: (Int, Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("门限值", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (threshold == null) {
                Text(
                    "当前固件不支持门限值查询（0x00C5 无响应）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Text(
                "新固件「门限距离」功能：能量升降超过门限才判定状态翻转，抑制临界抖动",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var upper by remember(threshold) { mutableFloatStateOf(threshold.first.toFloat()) }
            var lower by remember(threshold) { mutableFloatStateOf(threshold.second.toFloat()) }
            Text("上门限：${upper.toInt()}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = upper,
                onValueChange = { upper = it.coerceAtLeast(lower) },
                valueRange = 0f..100f,
                enabled = !applying,
            )
            Text("下门限：${lower.toInt()}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = lower,
                onValueChange = { lower = it.coerceAtMost(upper) },
                valueRange = 0f..100f,
                enabled = !applying,
            )
            Button(
                onClick = { onApply(upper.toInt(), lower.toInt()) },
                enabled = !applying,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存门限值") }
        }
    }
}

@Composable
private fun BaudRateCard(applying: Boolean, onApply: (Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("串口波特率", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "仅影响设备串口（调试通道），不影响蓝牙连接。重启后生效，误设后可用蓝牙改回。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var selection by rememberSaveable { mutableStateOf(7) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BAUD_RATES.take(4).forEach { (value, label) ->
                    FilterChip(selected = selection == value, onClick = { selection = value }, label = { Text(label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BAUD_RATES.drop(4).forEach { (value, label) ->
                    FilterChip(selected = selection == value, onClick = { selection = value }, label = { Text(label) })
                }
            }
            Button(
                onClick = { onApply(selection) },
                enabled = !applying && selection != 7,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (selection == 7) "默认档位（256000）" else "设置为 $selection 号档位") }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

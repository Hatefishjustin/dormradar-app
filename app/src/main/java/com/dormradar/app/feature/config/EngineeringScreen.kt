package com.dormradar.app.feature.config

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.device.DeviceInfo
import com.dormradar.app.core.device.GateEnergies
import com.dormradar.app.core.device.OpResult
import com.dormradar.app.core.device.RadarConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 工程模式页 VM（PRD 4.1 第 8 项） */
@HiltViewModel
class EngineeringViewModel @Inject constructor(
    private val connection: RadarConnection,
) : ViewModel() {

    val deviceInfo: StateFlow<DeviceInfo?> = connection.deviceInfo
    val engineeringMode: StateFlow<Boolean> = connection.engineeringMode
    val gateEnergies: StateFlow<GateEnergies?> = connection.gateEnergies

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _messageIsError = MutableStateFlow(false)
    val messageIsError: StateFlow<Boolean> = _messageIsError.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            when (val result = connection.setEngineeringMode(enabled)) {
                OpResult.Ok -> _message.value = null
                is OpResult.Fail -> {
                    _messageIsError.value = true
                    _message.value = "工程模式切换失败：${result.reason}"
                }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

/** EnergyBarsCard 已抽到独立共享文件 EnergyBarsCard.kt（首页「工程能量图」卡共用，2026-10-07） */

/**
 * 工程模式页：各距离门运动/静止能量实时柱状图（docs/06 阶段 2 任务 5）。
 * 工程模式 = 设备上报帧附带每个距离门的实时能量（调试用，能耗略增）。
 */
@Composable
fun EngineeringScreen(
    onBack: () -> Unit,
    viewModel: EngineeringViewModel = hiltViewModel(),
) {
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val enabled by viewModel.engineeringMode.collectAsStateWithLifecycle()
    val energies by viewModel.gateEnergies.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageIsError by viewModel.messageIsError.collectAsStateWithLifecycle()
    val resolutionCm = if (deviceInfo?.resolution02m == true) 20 else 75

    ConfigScaffold(title = "工程模式", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (message != null) {
                ResultBanner(message!!, isError = messageIsError, onDismiss = viewModel::clearMessage)
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用工程模式", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "上报帧附带每个距离门的实时能量（数据量增大、耗电略增），\n断开连接后自动关闭",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = viewModel::setEnabled)
                }
            }

            if (!enabled) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "打开上方开关后，这里将实时显示每个距离门的运动（红）/静止（紫）能量柱状图，" +
                            "用于观察哪个距离段有活动、判断灵敏度该调高还是调低",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                EnergyBarsCard(energies, resolutionCm)
            }
        }
    }
}


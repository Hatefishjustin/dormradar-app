package com.dormradar.app.feature.quicksetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.device.DeviceInfo
import com.dormradar.app.core.device.OpResult
import com.dormradar.app.core.device.PresetPack
import com.dormradar.app.core.device.RadarConnection
import com.dormradar.app.core.device.applyPresetPack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 一键设置（简单层，docs/06 阶段 2 任务 3）：
 * 三套推荐参数包一键下发 + 回读校验 + 结果提示。
 */
@HiltViewModel
class QuickSetupViewModel @Inject constructor(
    private val connection: RadarConnection,
) : ViewModel() {

    val deviceInfo: StateFlow<DeviceInfo?> = connection.deviceInfo

    /** 正在应用的参数包 id（null = 空闲） */
    private val _applying = MutableStateFlow<String?>(null)
    val applying: StateFlow<String?> = _applying.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _messageIsError = MutableStateFlow(false)
    val messageIsError: StateFlow<Boolean> = _messageIsError.asStateFlow()

    fun apply(pack: PresetPack) {
        if (_applying.value != null) return
        _applying.value = pack.id
        _message.value = null
        viewModelScope.launch {
            when (val result = applyPresetPack(connection, pack)) {
                OpResult.Ok -> {
                    _messageIsError.value = false
                    _message.value = "已应用「${pack.name}」（回读校验一致）。请观察 1 分钟：" +
                        "若没人也显示有人，说明阈值低于环境底噪——到「调试工具 → 部署向导」重新采样最科学，" +
                        "或到「灵敏度调节」调高阈值（数值越大越不灵敏）。"
                }
                is OpResult.Fail -> {
                    _messageIsError.value = true
                    _message.value = "应用失败：${result.reason}"
                }
            }
            _applying.value = null
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

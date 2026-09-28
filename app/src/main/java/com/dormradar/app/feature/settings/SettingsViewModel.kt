package com.dormradar.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.data.SettingsStore
import com.dormradar.app.core.device.ConnState
import com.dormradar.app.core.device.RadarConnection
import com.dormradar.app.core.monitor.MonitorMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    connection: RadarConnection,
) : ViewModel() {

    /** 连接状态：驱动「详细设置」入口可用性 */
    val connState: StateFlow<ConnState> = connection.connState

    val bluetoothPassword: StateFlow<String> =
        settings.bluetoothPassword.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "HiLink")

    val fakeDataEnabled: StateFlow<Boolean> =
        settings.fakeDataEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 三档监控模式（阶段 3） */
    val monitorMode: StateFlow<MonitorMode> =
        settings.monitorMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitorMode.ALERT)

    fun setMonitorMode(mode: MonitorMode) {
        viewModelScope.launch { settings.setMonitorMode(mode) }
    }

    fun savePassword(value: String) {
        viewModelScope.launch {
            // 密码必须 6 字节 ASCII（协议 0x00A9 帧格式约束）；不满足则回退默认
            val sanitized = value.takeIf { it.length == 6 && it.all { c -> c.code in 0x20..0x7E } }
                ?: com.dormradar.app.core.data.DEFAULT_BLUETOOTH_PASSWORD
            settings.setPassword(sanitized)
        }
    }

    fun setFakeDataEnabled(value: Boolean) {
        viewModelScope.launch { settings.setFakeDataEnabled(value) }
    }
}

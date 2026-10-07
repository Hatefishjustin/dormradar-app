package com.dormradar.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.data.SettingsStore
import com.dormradar.app.core.device.ConnState
import com.dormradar.app.core.device.DeviceInfo
import com.dormradar.app.core.device.RadarConnection
import com.dormradar.app.core.log.LogExporter
import com.dormradar.app.core.monitor.MonitorMode
import com.dormradar.app.core.update.GiteeRelease
import com.dormradar.app.core.update.UpdateChecker
import com.dormradar.app.core.update.UpdateConfig
import com.dormradar.app.core.update.UpdateDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    connection: RadarConnection,
    private val exporter: LogExporter,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** 连接状态：驱动「详细设置」入口可用性 */
    val connState: StateFlow<ConnState> = connection.connState

    /** 雷达设备信息：报 bug 摘要用 */
    val deviceInfo: StateFlow<DeviceInfo?> = connection.deviceInfo

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

    // ---- App 自动更新（Gitee 源已写死在 UpdateConfig，用户决策 2026-10-07）----

    /** 检查更新状态机：Idle → Checking →（Ready[有新版+已下载] / UpToDate / Failed） */
    data class UpdateUiState(
        val checking: Boolean = false,
        val downloading: Boolean = false,
        val progress: Int = 0,
        val release: GiteeRelease? = null,
        val downloadedApk: File? = null,
        val message: String? = null,
        /** 启动弹窗的「下次再说」：本次会话不再弹（设置页手动检查不受影响） */
        val silenced: Boolean = false,
    )

    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    /** 启动弹窗「下次再说」 */
    fun silenceUpdatePrompt() {
        _updateState.value = _updateState.value.copy(silenced = true)
    }

    fun checkUpdate(currentVersionCode: Int) {
        if (_updateState.value.checking || _updateState.value.downloading) return
        viewModelScope.launch {
            _updateState.value = UpdateUiState(checking = true)
            when (val result = UpdateChecker.fetch(UpdateConfig.OWNER, UpdateConfig.REPO, currentVersionCode)) {
                is UpdateChecker.CheckResult.NewRelease -> {
                    _updateState.value = UpdateUiState(release = result.release, message = null)
                }
                is UpdateChecker.CheckResult.UpToDate ->
                    _updateState.value = UpdateUiState(message = result.message)
                is UpdateChecker.CheckResult.Failure ->
                    _updateState.value = UpdateUiState(message = result.message)
            }
        }
    }

    fun downloadUpdate() {
        val release = _updateState.value.release ?: return
        if (_updateState.value.downloading) return
        viewModelScope.launch {
            _updateState.value = _updateState.value.copy(downloading = true, progress = 0, message = null)
            val result = UpdateDownloader.download(appContext, release) { p ->
                _updateState.value = _updateState.value.copy(progress = p)
            }
            result.fold(
                onSuccess = { apk ->
                    _updateState.value = _updateState.value.copy(downloading = false, downloadedApk = apk)
                },
                onFailure = { e ->
                    _updateState.value = _updateState.value.copy(
                        downloading = false,
                        message = "下载失败：${e.message ?: "未知错误"}",
                    )
                },
            )
        }
    }

    fun clearUpdateMessage() {
        _updateState.value = _updateState.value.copy(message = null)
    }

    /** 一键收集诊断包（复用 LogExporter 的 zip 打包），结果回调给 UI 分享 */
    fun collectFeedbackZip(onReady: (File) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(exporter.export())
            } catch (e: Exception) {
                onError(e.message ?: "未知错误")
            }
        }
    }
}

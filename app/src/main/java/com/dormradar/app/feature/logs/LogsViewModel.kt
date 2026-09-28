package com.dormradar.app.feature.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.data.SettingsStore
import com.dormradar.app.core.log.FrameLogEntry
import com.dormradar.app.core.log.FrameLogStore
import com.dormradar.app.core.log.LogEntry
import com.dormradar.app.core.log.LogExporter
import com.dormradar.app.core.log.LogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** 日志页（阶段 4 任务 1：分级日志查看 + 原始帧记录 + zip 导出分享） */
@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logStore: LogStore,
    private val frameLogStore: FrameLogStore,
    private val settings: SettingsStore,
    private val exporter: LogExporter,
) : ViewModel() {

    val logs: StateFlow<List<LogEntry>> = logStore.logs
    val frames: StateFlow<List<FrameLogEntry>> = frameLogStore.frames

    val frameLogEnabled: StateFlow<Boolean> = settings.frameLogEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), frameLogStore.enabled)

    fun setFrameLogEnabled(value: Boolean) {
        frameLogStore.enabled = value // 快门立即生效（RadarConnection 的同步收集稍后追平 DataStore）
        viewModelScope.launch { settings.setFrameLogEnabled(value) }
    }

    fun clearLogs() {
        logStore.clear()
        frameLogStore.clear()
    }

    /** 导出 zip（IO 线程），结果回调给 UI 做分享 */
    fun export(onReady: (File) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(exporter.export())
            } catch (e: Exception) {
                onError(e.message ?: "未知错误")
            }
        }
    }
}

package com.dormradar.app.core.log

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 日志级别（docs/04 §3.7：运行日志分级 D/I/W/E） */
enum class LogLevel(val label: String) {
    D("调试"), I("信息"), W("警告"), E("错误"),
}

/** 一条运行日志（内存环形缓冲，App 存活期间有效，重启清空） */
data class LogEntry(
    val timeMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)

/**
 * 运行日志环形缓冲（docs/04 §3.7）：约 2000 条，满了挤掉最旧的。
 *
 * - [log] 是纯数据路径（不碰 Logcat），JVM 单测可用；
 * - 便捷方法 d/i/w/e 同时镜像到 Logcat（MIUI 吞 Log.d，关键事件请用 i 及以上）。
 */
@Singleton
class LogStore @Inject constructor() {
    companion object {
        const val MAX_ENTRIES = 2000
    }

    private val buffer = ArrayDeque<LogEntry>()
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(level: LogLevel, tag: String, message: String) {
        synchronized(buffer) {
            buffer.addLast(LogEntry(System.currentTimeMillis(), level, tag, message))
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            _logs.value = buffer.toList()
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        log(LogLevel.D, tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        log(LogLevel.I, tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        log(LogLevel.W, tag, message)
    }

    fun e(tag: String, message: String, tr: Throwable? = null) {
        Log.e(tag, message, tr)
        log(
            LogLevel.E, tag,
            if (tr != null) "$message（${tr.javaClass.simpleName}: ${tr.message}）" else message,
        )
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            _logs.value = emptyList()
        }
    }
}

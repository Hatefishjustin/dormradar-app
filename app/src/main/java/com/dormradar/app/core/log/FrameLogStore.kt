package com.dormradar.app.core.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 帧方向：TX = App→设备（写 fff2），RX = 设备→App（fff1 Notify） */
enum class FrameDir(val label: String) {
    TX("发送"), RX("接收"),
}

/** 一条原始帧记录（十六进制文本） */
data class FrameLogEntry(
    val timeMs: Long,
    val dir: FrameDir,
    val size: Int,
    val hex: String,
)

/**
 * 原始帧日志（docs/04 §3.7：调试模式开启后记录收发十六进制流，用于协议排障）。
 *
 * - 默认关闭，由「日志」页开关控制（DataStore 持久 + RadarConnection 负责同步到本类）；
 * - 仅内存环形缓冲（不落盘，App 重启清空）；
 * - 注意：帧流包含鉴权命令（蓝牙密码明文），导出分享时请自行留意。
 */
@Singleton
class FrameLogStore @Inject constructor() {
    companion object {
        const val MAX_ENTRIES = 800
    }

    /** 记录开关（高频路径读这个 @Volatile，避免每帧读 DataStore） */
    @Volatile
    var enabled: Boolean = false

    private val buffer = ArrayDeque<FrameLogEntry>()
    private val _frames = MutableStateFlow<List<FrameLogEntry>>(emptyList())
    val frames: StateFlow<List<FrameLogEntry>> = _frames.asStateFlow()

    fun record(dir: FrameDir, bytes: ByteArray) {
        if (!enabled || bytes.isEmpty()) return
        synchronized(buffer) {
            buffer.addLast(
                FrameLogEntry(
                    timeMs = System.currentTimeMillis(),
                    dir = dir,
                    size = bytes.size,
                    hex = LogFormats.toHex(bytes),
                ),
            )
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            _frames.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            _frames.value = emptyList()
        }
    }
}

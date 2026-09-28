package com.dormradar.app.core.log

import com.dormradar.app.core.data.room.RadarEventEntity
import com.dormradar.app.core.device.DeviceInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志导出的文本格式化（纯 Kotlin，JVM 可单测）。
 * 时间统一用 Locale.US，保证导出文件里的格式稳定。
 */
object LogFormats {

    fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    /** 运行日志单行：`09-27 15:30:00.123 I/RadarConnection: 消息` */
    fun runtimeLine(entry: LogEntry): String {
        val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(entry.timeMs))
        return "$time ${entry.level.name}/${entry.tag}: ${entry.message}"
    }

    /** 原始帧单行：`09-27 15:30:00.123 TX [8B] FD FC FB FA ...` */
    fun frameLine(entry: FrameLogEntry): String {
        val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(entry.timeMs))
        return "$time ${entry.dir.name} [${entry.size}B] ${entry.hex}"
    }

    /** CSV 字段转义：含逗号/引号/换行时加引号，内部引号翻倍 */
    fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** 事件流水导出为 CSV（含表头，时间列人类可读） */
    fun eventsCsv(events: List<RadarEventEntity>): String = buildString {
        appendLine("id,时间,类型,MAC,详情")
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        events.forEach { e ->
            append(e.id)
            append(',')
            append(csvEscape(timeFormat.format(Date(e.timestamp))))
            append(',')
            append(csvEscape(e.type))
            append(',')
            append(csvEscape(e.mac))
            append(',')
            appendLine(csvEscape(e.detail))
        }
    }

    /** 导出信息头 + 当前设备配置快照（未连接时只有 App 信息） */
    fun configDump(info: DeviceInfo?, appVersion: String, deviceModel: String): String = buildString {
        appendLine("宿管雷达 DormRadar 日志导出")
        appendLine("导出时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("App 版本：v$appVersion")
        appendLine("手机型号：$deviceModel")
        appendLine()
        if (info == null) {
            appendLine("设备：当前未连接（无配置快照）")
            return@buildString
        }
        if (info.isFake) appendLine("设备：模拟设备（数据为合成，非真实雷达）")
        appendLine("设备 MAC：${info.mac}")
        appendLine("固件版本：${info.firmwareVersion ?: "未知"}")
        appendLine("协议版本：${info.protocolVersion ?: "未知"}")
        appendLine(
            "距离分辨率：" + when (info.resolution02m) {
                true -> "0.2m"
                false -> "0.75m"
                null -> "未知（按 0.75m 计）"
            },
        )
        val p = info.params
        if (p == null) {
            appendLine("设备参数：未获取")
            return@buildString
        }
        val resM = if (info.resolution02m == true) 0.2 else 0.75
        appendLine("最远运动门：${p.maxMoveGate}（约 ${"%.2f".format((p.maxMoveGate + 1) * resM)} 米）")
        appendLine("最远静止门：${p.maxStaticGate}（约 ${"%.2f".format((p.maxStaticGate + 1) * resM)} 米）")
        appendLine("无人持续时间：${p.unmannedSeconds} 秒")
        appendLine("灵敏度表（门号: 运动/静止，100=屏蔽）：")
        p.motionSensitivities.indices.forEach { g ->
            appendLine("  门 $g：${p.motionSensitivities[g]} / ${p.staticSensitivities.getOrElse(g) { -1 }}")
        }
    }
}

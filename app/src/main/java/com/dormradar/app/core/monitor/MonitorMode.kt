package com.dormradar.app.core.monitor

/**
 * 三档监控模式（PRD §5 通知策略的总开关，docs/01）。
 * 决定 NotificationPolicy 的事件 → 通知矩阵。
 */
enum class MonitorMode(val label: String, val description: String) {
    /** 事件只记录到时间线；仅断连/重连失败等"死要见尸"告警会通知 */
    SILENT("静默记录", "只记录事件，不发检测通知（断连等重要告警仍会提醒）"),

    /** 有人出现时通知，离开静默（推荐日常使用） */
    ALERT("有人提醒", "有人出现时发通知；离开静默"),

    /** 出现/离开/连接事件全部通知，用于排查问题 */
    FULL("全量调试", "所有事件（含离开/连接变化）都通知，用于排查");

    companion object {
        fun fromName(name: String?): MonitorMode = entries.firstOrNull { it.name == name } ?: ALERT
    }
}

/** 事件类型（时间线 + 通知评估的输入；Room 中存 name） */
enum class RadarEventType(val label: String) {
    APPEAR("有人出现"),
    LEAVE("有人离开"),
    CONNECT("连接建立"),
    DISCONNECT("意外断开"),
    RECONNECTED("重连成功"),
    GIVE_UP("连接失败"),
    MONITOR_START("监控开启"),
    MONITOR_STOP("监控停止"),
}

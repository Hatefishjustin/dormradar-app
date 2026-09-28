package com.dormradar.app.core.monitor

/**
 * 通知决策引擎（docs/04 §3.5，纯 Kotlin 可单测）。
 *
 * 职责：事件类型 + 监控模式 → 是否通知 / 走哪个渠道 / 节流合并计数。
 * 通知文案拼接在服务层（需要距离等上下文），本类只管"要不要发"。
 *
 * 决策矩阵：
 * | 事件            | 静默记录 | 有人提醒 | 全量调试 |
 * |-----------------|---------|---------|---------|
 * | 有人出现         | 不通知   | 通知(检测) | 通知(检测) |
 * | 有人离开         | 不通知   | 不通知    | 通知(检测) |
 * | 意外断开/连接失败 | 通知(设备) | 通知(设备) | 通知(设备) |
 * | 连接建立/重连成功 | 不通知   | 通知(设备) | 通知(设备) |
 *
 * 节流：检测渠道同类通知最小间隔（默认 60s），窗口内的重复出现只入库不通知；
 * 窗口过后第一条通知携带合并计数（"期间共 N 次活动"）。设备状态渠道不节流
 * （断连必须立即知道，docs/06 风险表"死要见尸"）。
 */
class NotificationPolicy(
    private val throttleWindowMs: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class Channel { DETECTION, DEVICE_STATUS }

    sealed interface Decision {
        /** 发通知：目标渠道 + 本次通知覆盖的活动次数（>1 表示节流窗口内合并了多次） */
        data class Send(val channel: Channel, val mergedCount: Int) : Decision

        data object Skip : Decision
    }

    private var lastDetectionSentAt = 0L
    private var suppressed = 0

    fun evaluate(type: RadarEventType, mode: MonitorMode): Decision = when (type) {
        RadarEventType.APPEAR -> if (mode == MonitorMode.SILENT) {
            Decision.Skip
        } else {
            val now = clock()
            if (now - lastDetectionSentAt < throttleWindowMs) {
                suppressed++
                Decision.Skip
            } else {
                val merged = suppressed + 1
                suppressed = 0
                lastDetectionSentAt = now
                Decision.Send(Channel.DETECTION, merged)
            }
        }

        RadarEventType.LEAVE ->
            if (mode == MonitorMode.FULL) Decision.Send(Channel.DETECTION, 1) else Decision.Skip

        RadarEventType.DISCONNECT, RadarEventType.GIVE_UP ->
            Decision.Send(Channel.DEVICE_STATUS, 1)

        RadarEventType.CONNECT, RadarEventType.RECONNECTED ->
            if (mode != MonitorMode.SILENT) Decision.Send(Channel.DEVICE_STATUS, 1) else Decision.Skip

        RadarEventType.MONITOR_START, RadarEventType.MONITOR_STOP -> Decision.Skip
    }
}

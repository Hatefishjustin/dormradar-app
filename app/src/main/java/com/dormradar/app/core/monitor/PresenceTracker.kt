package com.dormradar.app.core.monitor

import com.dormradar.app.core.device.RadarState

/** 有无人的状态沿（由 PresenceTracker 输出） */
data class PresenceEdge(
    val type: RadarEventType,
    /** LEAVE 时 = 本次有人持续的毫秒数；APPEAR 时 = 0 */
    val durationMs: Long,
)

/**
 * 有人出现/离开的沿检测（docs/04 §4.2）。
 *
 * 设计要点：
 * - 只在**新上报帧**到来时比较，BLE 断流不会误发"离开"（断连另有 DISCONNECT 事件）；
 * - "离开"的延时判定交给设备侧的无人持续时间（docs/01），这里不做二次防抖；
 * - [reset] 用于连接建立/重连成功后清空旧状态，避免跨连接误判。
 */
class PresenceTracker {
    private var hadTarget = false
    private var appearAtMs = 0L

    fun reset() {
        hadTarget = false
        appearAtMs = 0L
    }

    /** @return 状态沿事件；无变化 / 无数据返回 null */
    fun update(state: RadarState?): PresenceEdge? {
        if (state == null) return null
        val has = state.hasTarget
        if (has == hadTarget) return null
        hadTarget = has
        return if (has) {
            appearAtMs = state.updatedAt
            PresenceEdge(RadarEventType.APPEAR, 0L)
        } else {
            val duration = (state.updatedAt - appearAtMs).coerceAtLeast(0L)
            PresenceEdge(RadarEventType.LEAVE, duration)
        }
    }
}

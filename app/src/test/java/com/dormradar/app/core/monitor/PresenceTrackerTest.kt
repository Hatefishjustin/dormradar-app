package com.dormradar.app.core.monitor

import com.dormradar.app.core.device.RadarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 有无人沿检测（docs/04 §4.2）：只在新帧比较、跨连接重置、离开带持续时长 */
class PresenceTrackerTest {

    private fun state(hasTarget: Boolean, at: Long) = RadarState(
        targetState = if (hasTarget) 1 else 0,
        moveDistanceCm = if (hasTarget) 150 else 0,
        moveEnergy = if (hasTarget) 60 else 0,
        staticDistanceCm = 0,
        staticEnergy = 0,
        detectDistanceCm = if (hasTarget) 150 else 0,
        updatedAt = at,
    )

    @Test
    fun `无人到有人产生出现事件`() {
        val t = PresenceTracker()
        assertNull(t.update(state(false, 0)))
        val edge = t.update(state(true, 1_000))
        assertEquals(RadarEventType.APPEAR, edge?.type)
        assertEquals(0L, edge?.durationMs)
    }

    @Test
    fun `有人到无人产生离开事件并带持续时长`() {
        val t = PresenceTracker()
        t.update(state(false, 0))
        t.update(state(true, 10_000))
        val edge = t.update(state(false, 75_000))
        assertEquals(RadarEventType.LEAVE, edge?.type)
        assertEquals(65_000L, edge?.durationMs)
    }

    @Test
    fun `状态不变不产生事件`() {
        val t = PresenceTracker()
        t.update(state(false, 0))
        assertNull(t.update(state(false, 1_000)))
        t.update(state(true, 2_000))
        assertNull(t.update(state(true, 3_000))) // 有人保持（运动→静止也是有人）
        assertNull(t.update(state(true, 4_000)))
    }

    @Test
    fun `null帧不产生事件`() {
        val t = PresenceTracker()
        t.update(state(true, 0))
        assertNull(t.update(null))
    }

    @Test
    fun `重置后重新出现触发出现事件`() {
        val t = PresenceTracker()
        t.update(state(false, 0))
        t.update(state(true, 1_000))
        t.reset() // 模拟重连成功
        assertNull(t.update(null))
        val edge = t.update(state(true, 5_000))
        assertEquals(RadarEventType.APPEAR, edge?.type)
    }

    @Test
    fun `底噪检测扩展状态不算有人`() {
        val t = PresenceTracker()
        t.update(state(false, 0))
        // targetState 4/5/6 = 底噪检测进度，不在 1..3
        assertNull(t.update(state(false, 1_000).copy(targetState = 4)))
        assertNull(t.update(state(false, 2_000).copy(targetState = 5)))
    }
}

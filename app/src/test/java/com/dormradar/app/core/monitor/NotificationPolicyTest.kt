package com.dormradar.app.core.monitor

import com.dormradar.app.core.monitor.NotificationPolicy.Channel
import com.dormradar.app.core.monitor.NotificationPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 三档监控模式 × 事件类型决策矩阵 + 检测渠道节流（PRD §5.2） */
class NotificationPolicyTest {

    private var fakeNow = 1_000_000L

    private fun policy(windowMs: Long = 60_000L) = NotificationPolicy(windowMs) { fakeNow }

    // ---- 决策矩阵 ----

    @Test
    fun `有人出现 - 静默模式不通知`() {
        val d = policy().evaluate(RadarEventType.APPEAR, MonitorMode.SILENT)
        assertEquals(Decision.Skip, d)
    }

    @Test
    fun `有人出现 - 提醒与全量模式走检测渠道`() {
        assertEquals(
            Channel.DETECTION,
            (policy().evaluate(RadarEventType.APPEAR, MonitorMode.ALERT) as Decision.Send).channel,
        )
        assertEquals(
            Channel.DETECTION,
            (policy().evaluate(RadarEventType.APPEAR, MonitorMode.FULL) as Decision.Send).channel,
        )
    }

    @Test
    fun `有人离开 - 仅全量模式通知`() {
        assertEquals(Decision.Skip, policy().evaluate(RadarEventType.LEAVE, MonitorMode.SILENT))
        assertEquals(Decision.Skip, policy().evaluate(RadarEventType.LEAVE, MonitorMode.ALERT))
        assertEquals(
            Channel.DETECTION,
            (policy().evaluate(RadarEventType.LEAVE, MonitorMode.FULL) as Decision.Send).channel,
        )
    }

    @Test
    fun `断开与失败 - 任何模式都通知且走设备状态渠道`() {
        for (mode in MonitorMode.entries) {
            assertEquals(
                Channel.DEVICE_STATUS,
                (policy().evaluate(RadarEventType.DISCONNECT, mode) as Decision.Send).channel,
            )
            assertEquals(
                Channel.DEVICE_STATUS,
                (policy().evaluate(RadarEventType.GIVE_UP, mode) as Decision.Send).channel,
            )
        }
    }

    @Test
    fun `连接建立与重连成功 - 静默不通知其余通知`() {
        assertEquals(Decision.Skip, policy().evaluate(RadarEventType.CONNECT, MonitorMode.SILENT))
        assertEquals(Decision.Skip, policy().evaluate(RadarEventType.RECONNECTED, MonitorMode.SILENT))
        assertEquals(
            Channel.DEVICE_STATUS,
            (policy().evaluate(RadarEventType.CONNECT, MonitorMode.ALERT) as Decision.Send).channel,
        )
        assertEquals(
            Channel.DEVICE_STATUS,
            (policy().evaluate(RadarEventType.RECONNECTED, MonitorMode.FULL) as Decision.Send).channel,
        )
    }

    @Test
    fun `监控开关事件从不通知`() {
        for (mode in MonitorMode.entries) {
            assertEquals(Decision.Skip, policy().evaluate(RadarEventType.MONITOR_START, mode))
            assertEquals(Decision.Skip, policy().evaluate(RadarEventType.MONITOR_STOP, mode))
        }
    }

    // ---- 节流（仅检测渠道；设备状态不受节流影响）----

    @Test
    fun `节流窗口内的重复出现被合并`() {
        val p = policy(windowMs = 60_000)
        // 第一次：发出
        val first = p.evaluate(RadarEventType.APPEAR, MonitorMode.ALERT)
        assertTrue(first is Decision.Send && first.mergedCount == 1)

        // 窗口内两次：跳过
        fakeNow += 10_000
        assertEquals(Decision.Skip, p.evaluate(RadarEventType.APPEAR, MonitorMode.ALERT))
        fakeNow += 10_000
        assertEquals(Decision.Skip, p.evaluate(RadarEventType.APPEAR, MonitorMode.ALERT))

        // 窗口过后：发出并携带合并计数 3（1 次已发 + 2 次被抑制）
        fakeNow += 45_000
        val after = p.evaluate(RadarEventType.APPEAR, MonitorMode.ALERT) as Decision.Send
        assertEquals(Channel.DETECTION, after.channel)
        assertEquals(3, after.mergedCount)
    }

    @Test
    fun `设备状态事件不受节流影响`() {
        val p = policy(windowMs = 60_000)
        p.evaluate(RadarEventType.DISCONNECT, MonitorMode.ALERT)
        fakeNow += 1_000
        val again = p.evaluate(RadarEventType.DISCONNECT, MonitorMode.ALERT)
        assertTrue(again is Decision.Send)
    }

    @Test
    fun `静默模式下节流不累积合并计数`() {
        val p = policy(windowMs = 60_000)
        fakeNow += 10_000
        assertEquals(Decision.Skip, p.evaluate(RadarEventType.APPEAR, MonitorMode.SILENT))
        fakeNow += 100_000 // 窗口早已过去
        val d = p.evaluate(RadarEventType.APPEAR, MonitorMode.ALERT) as Decision.Send
        assertEquals(1, d.mergedCount) // 静默期的跳过不计数
    }
}

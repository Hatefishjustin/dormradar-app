package com.dormradar.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 配置差异对比（模板 vs 当前设备） */
class ConfigDiffTest {

    private fun data(
        maxMove: Int = 8,
        maxStatic: Int = 6,
        unmanned: Int = 5,
        motion: IntArray = intArrayOf(50, 40, 30),
        static: IntArray = intArrayOf(40, 30, 20),
    ) = ConfigSnapshotData(maxMove, maxStatic, unmanned, false, motion, static)

    @Test
    fun `完全一致时无差异行`() {
        assertTrue(ConfigDiff.diff(data(), data()).isEmpty())
    }

    @Test
    fun `标量差异各占一行`() {
        val lines = ConfigDiff.diff(data(), data(maxMove = 6, unmanned = 10))
        assertEquals(2, lines.size)
        assertEquals("最远运动门", lines[0].field)
        assertEquals("8", lines[0].templateValue)
        assertEquals("6", lines[0].currentValue)
        assertEquals("无人持续时间", lines[1].field)
    }

    @Test
    fun `灵敏度差异按门列出`() {
        val lines = ConfigDiff.diff(data(), data(motion = intArrayOf(50, 99, 30)))
        assertEquals(1, lines.size)
        assertEquals("门 1", lines[0].field)
        assertTrue(lines[0].templateValue.contains("40") && lines[0].currentValue.contains("99"))
    }

    @Test
    fun `静止通道差异也按门列出`() {
        val lines = ConfigDiff.diff(data(), data(static = intArrayOf(40, 30, 88)))
        assertEquals(1, lines.size)
        assertEquals("门 2", lines[0].field)
    }

    @Test
    fun `分辨率差异占一行`() {
        val t = data().copy(resolution02m = true)
        val lines = ConfigDiff.diff(t, data())
        assertEquals(1, lines.size)
        assertEquals("距离分辨率", lines[0].field)
        assertEquals("0.2m", lines[0].templateValue)
        assertEquals("0.75m", lines[0].currentValue)
    }
}

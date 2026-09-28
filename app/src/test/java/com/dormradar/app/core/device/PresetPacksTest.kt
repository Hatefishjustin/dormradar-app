package com.dormradar.app.core.device

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 一键设置参数包测试（docs/06 阶段 2 任务 3） */
class PresetPacksTest {

    @Test
    fun `参数包数量与字段合法性`() {
        assertTrue(PresetPacks.ALL.size >= 3)
        PresetPacks.ALL.forEach { pack ->
            assertEquals(9, pack.motion.size)
            assertEquals(9, pack.static.size)
            assertTrue("运动门 2~8", pack.moveMaxGate in 2..8)
            assertTrue("静止门 2~8", pack.staticMaxGate in 2..8)
            assertTrue(pack.motion.all { it in 0..100 })
            assertTrue(pack.static.all { it in 0..100 })
        }
    }

    @Test
    fun `映射表 - 等长原样返回拷贝`() {
        val src = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
        val out = PresetPacks.mapTable(src, 9)
        assertArrayEquals(src, out)
        assertTrue(out !== src) // 拷贝而非引用
    }

    @Test
    fun `映射表 - 缩短到7门按比例取值`() {
        val src = intArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90)
        // 7 门：idx = i*8/6 → 0,1,2,4,5,6,8
        assertArrayEquals(intArrayOf(10, 20, 30, 50, 60, 70, 90), PresetPacks.mapTable(src, 7))
    }

    @Test
    fun `映射表 - 扩展到13门重复末段策略`() {
        val src = intArrayOf(10, 50, 90)
        val out = PresetPacks.mapTable(src, 13)
        assertEquals(13, out.size)
        assertEquals(10, out.first())
        assertEquals(90, out.last())
        assertTrue(out.all { it in 10..90 })
    }

    @Test
    fun `映射表 - 单门源表安全处理`() {
        assertArrayEquals(intArrayOf(7, 7, 7), PresetPacks.mapTable(intArrayOf(7), 3))
    }
}

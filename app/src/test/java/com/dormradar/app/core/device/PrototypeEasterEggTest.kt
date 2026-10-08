package com.dormradar.app.core.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 原型机一号机彩蛋纯逻辑测试（2026-10-08） */
class PrototypeEasterEggTest {

    @Test
    fun `一号机 MAC 且未见过时弹彩蛋`() {
        assertTrue(PrototypeEasterEgg.shouldShow("21:A9:6C:A4:63:5B", emptySet()))
    }

    @Test
    fun `MAC 格式差异不影响匹配（小写、无冒号、横线）`() {
        assertTrue(PrototypeEasterEgg.shouldShow("21a96ca4635b", emptySet()))
        assertTrue(PrototypeEasterEgg.shouldShow("21-a9-6c-a4-63-5b", emptySet()))
        assertEquals("21A96CA4635B", PrototypeEasterEgg.normalize("21:A9:6C:A4:63:5B"))
    }

    @Test
    fun `已见过的 MAC 不再弹`() {
        val seen = setOf(PrototypeEasterEgg.macToRemember())
        assertFalse(PrototypeEasterEgg.shouldShow("21:A9:6C:A4:63:5B", seen))
    }

    @Test
    fun `别的设备不弹`() {
        assertFalse(PrototypeEasterEgg.shouldShow("AA:BB:CC:DD:EE:FF", emptySet()))
    }

    @Test
    fun `吐槽文案轮换且不越界`() {
        val first = PrototypeEasterEgg.humbleReply(1)
        assertEquals(first, PrototypeEasterEgg.humbleReply(1 + 5)) // 轮换周期 = 5
        assertTrue(PrototypeEasterEgg.humbleReply(3).isNotBlank())
    }

    @Test
    fun `一般般按钮文案逐级变化`() {
        assertEquals("一般般", PrototypeEasterEgg.humbleButtonLabel(0))
        assertEquals("一般般", PrototypeEasterEgg.humbleButtonLabel(-1))
        assertEquals("手滑了，再选一次", PrototypeEasterEgg.humbleButtonLabel(1))
        assertEquals("点这个已经没用了", PrototypeEasterEgg.humbleButtonLabel(2))
        assertEquals("（按钮罢工中）", PrototypeEasterEgg.humbleButtonLabel(3))
        assertEquals("（按钮罢工中）", PrototypeEasterEgg.humbleButtonLabel(99))
    }
}

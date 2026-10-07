package com.dormradar.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 首页卡片布局编解码（DataStore 单键 CSV） */
class HomeCardLayoutTest {

    @Test
    fun `标准布局编码解码往返一致`() {
        val back = HomeCardLayout.decode(HomeCardLayout.STANDARD.encode())
        assertEquals(HomeCardLayout.STANDARD, back)
    }

    @Test
    fun `decode 空 null 回退标准布局`() {
        assertEquals(HomeCardLayout.STANDARD, HomeCardLayout.decode(null))
        assertEquals(HomeCardLayout.STANDARD, HomeCardLayout.decode(""))
        assertEquals(HomeCardLayout.STANDARD, HomeCardLayout.decode("   "))
    }

    @Test
    fun `decode 脏数据回退标准布局`() {
        assertEquals(HomeCardLayout.STANDARD, HomeCardLayout.decode("完全是垃圾"))
        assertEquals(HomeCardLayout.STANDARD, HomeCardLayout.decode("status,scope"))
        assertEquals(HomeCardLayout.STANDARD, HomeCardLayout.decode("status:x,scope:2"))
    }

    @Test
    fun `decode 未知 id 被忽略_已知项保留_缺失补齐`() {
        val back = HomeCardLayout.decode("BOGUS:1,status:1,scope:0")
        // BOGUS 忽略；status/scope 保留用户顺序；缺失项按默认顺序与默认可见性补齐（共 8 项）
        assertEquals(HomeCardId.entries.size, back.cards.size)
        assertEquals(HomeCardId.STATUS to true, back.cards[0])
        assertEquals(HomeCardId.SCOPE to false, back.cards[1])
    }

    @Test
    fun `decode 缺失 id 按默认顺序补齐`() {
        val back = HomeCardLayout.decode("scope:1,status:1")
        // 只有 2 项 → 其余 6 项按 DEFAULT_ORDER 追加
        assertEquals(HomeCardId.entries.size, back.cards.size)
        assertEquals(HomeCardId.SCOPE, back.cards[0].first) // 用户顺序优先
        assertEquals(HomeCardId.GRID, back.cards[2].first) // 默认补齐从 GRID 开始
    }

    @Test
    fun `预设 SIMPLE 编码解码往返稳定`() {
        val back = HomeCardLayout.decode(HomeCardLayout.SIMPLE.encode())
        assertEquals(HomeCardLayout.SIMPLE, back)
    }

    @Test
    fun `重复 id 只保留首次出现`() {
        val back = HomeCardLayout.decode("status:1,status:0,scope:1")
        assertEquals(1, back.cards.count { it.first == HomeCardId.STATUS })
        assertTrue(back.cards.first { it.first == HomeCardId.STATUS }.second)
    }

    @Test
    fun `预设 SIMPLE 只含状态雷达监控`() {
        val ids = HomeCardLayout.SIMPLE.cards.filter { it.second }.map { it.first }
        assertEquals(listOf(HomeCardId.STATUS, HomeCardId.SCOPE, HomeCardId.MONITOR), ids)
    }

    @Test
    fun `预设 FULL 全开_标准布局工程能量图默认关`() {
        assertTrue(HomeCardLayout.FULL.cards.all { it.second })
        val eng = HomeCardLayout.STANDARD.cards.first { it.first == HomeCardId.ENGINEERING }
        assertEquals(false, eng.second)
    }
}

package com.dormradar.app.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 运行日志环形缓冲（纯数据路径 log()，不碰 Logcat） */
class LogStoreTest {

    @Test
    fun `追加按顺序可见`() {
        val store = LogStore()
        store.log(LogLevel.I, "T1", "第一条")
        store.log(LogLevel.W, "T2", "第二条")
        val logs = store.logs.value
        assertEquals(2, logs.size)
        assertEquals("第一条", logs[0].message)
        assertEquals("第二条", logs[1].message)
        assertEquals(LogLevel.I, logs[0].level)
        assertEquals(LogLevel.W, logs[1].level)
        assertEquals("T1", logs[0].tag)
    }

    @Test
    fun `超过容量挤掉最旧的`() {
        val store = LogStore()
        repeat(LogStore.MAX_ENTRIES + 100) { n -> store.log(LogLevel.D, "T", "m$n") }
        val logs = store.logs.value
        assertEquals(LogStore.MAX_ENTRIES, logs.size)
        assertEquals("m100", logs.first().message) // 前 100 条被挤掉
        assertEquals("m${LogStore.MAX_ENTRIES + 99}", logs.last().message)
    }

    @Test
    fun `clear 清空`() {
        val store = LogStore()
        store.log(LogLevel.E, "T", "boom")
        store.clear()
        assertTrue(store.logs.value.isEmpty())
    }

    @Test
    fun `时间戳单调不减`() {
        val store = LogStore()
        repeat(50) { store.log(LogLevel.I, "T", "m$it") }
        val times = store.logs.value.map { it.timeMs }
        assertEquals(times.sorted(), times)
    }
}

package com.dormradar.app.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 原始帧记录（开关门控 + 环形缓冲 + 十六进制） */
class FrameLogStoreTest {

    @Test
    fun `默认关闭时不记录`() {
        val store = FrameLogStore()
        store.record(FrameDir.TX, byteArrayOf(0xFD.toByte(), 0xFC.toByte()))
        store.record(FrameDir.RX, byteArrayOf(0xF4.toByte()))
        assertTrue(store.frames.value.isEmpty())
    }

    @Test
    fun `开启后记录并转十六进制`() {
        val store = FrameLogStore()
        store.enabled = true
        store.record(FrameDir.TX, byteArrayOf(0xFD.toByte(), 0xFC.toByte(), 0xFB.toByte()))
        store.record(FrameDir.RX, byteArrayOf(0x04))
        val frames = store.frames.value
        assertEquals(2, frames.size)
        assertEquals(FrameDir.TX, frames[0].dir)
        assertEquals(3, frames[0].size)
        assertEquals("FD FC FB", frames[0].hex)
        assertEquals(FrameDir.RX, frames[1].dir)
        assertEquals("04", frames[1].hex)
    }

    @Test
    fun `空字节不记录`() {
        val store = FrameLogStore()
        store.enabled = true
        store.record(FrameDir.RX, ByteArray(0))
        assertTrue(store.frames.value.isEmpty())
    }

    @Test
    fun `超过容量挤掉最旧的`() {
        val store = FrameLogStore()
        store.enabled = true
        repeat(FrameLogStore.MAX_ENTRIES + 10) { n ->
            store.record(FrameDir.TX, byteArrayOf(n.toByte()))
        }
        val frames = store.frames.value
        assertEquals(FrameLogStore.MAX_ENTRIES, frames.size)
        assertEquals("%02X".format(10), frames.first().hex)
    }

    @Test
    fun `关闭后立即停止记录`() {
        val store = FrameLogStore()
        store.enabled = true
        store.record(FrameDir.TX, byteArrayOf(0x01))
        store.enabled = false
        store.record(FrameDir.TX, byteArrayOf(0x02))
        assertEquals(1, store.frames.value.size)
    }

    @Test
    fun `clear 清空`() {
        val store = FrameLogStore()
        store.enabled = true
        store.record(FrameDir.RX, byteArrayOf(0x01))
        store.clear()
        assertTrue(store.frames.value.isEmpty())
    }
}

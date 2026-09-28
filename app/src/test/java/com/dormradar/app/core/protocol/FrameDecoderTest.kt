package com.dormradar.app.core.protocol

import com.dormradar.app.core.protocol.Frame.Ack
import com.dormradar.app.core.protocol.Frame.ReportBasic
import com.dormradar.app.core.protocol.Frame.ReportEngineering
import com.dormradar.app.core.protocol.Frame.Unknown
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式解码黄金测试。
 * 核心用例：官方帧格式 + 半包（一帧分多次到达）+ 粘包（一次到达多帧）+ 脏字节容错。
 * 覆盖 docs/03 §5 列出的全部解析器注意事项。
 */
class FrameDecoderTest {

    // ---- 构造样本 ----

    /** 基础上报帧：运动目标，运动 300cm/88 能量，静止 250cm/60 能量，检测距离 300cm */
    private val basicReport: ByteArray = run {
        val body = byteArrayOf(
            0x01, // 目标状态：运动
            0x2C, 0x01, // 运动距离 300cm（小端）
            0x58, // 运动能量 88
            0xFA.toByte(), 0x00, // 静止距离 250cm
            0x3C, // 静止能量 60
            0x2C, 0x01, // 检测距离 300cm
        )
        frame(ProtocolConst.TYPE_BASIC, body)
    }

    /** 工程模式上报帧：9 门能量 + 无额外字段 */
    private val engineeringReport: ByteArray = run {
        val body = ByteArray(9 + 2 + 9 + 9)
        body[0] = 0x03 // 运动+静止
        // 距离字段随意，测试只关心门数组
        val maxGates = byteArrayOf(0x08, 0x08)
        maxGates.copyInto(body, 9)
        for (i in 0..8) body[11 + i] = (10 + i).toByte() // 运动能量 10..18
        for (i in 0..8) body[20 + i] = (20 + i).toByte() // 静止能量 20..28
        frame(ProtocolConst.TYPE_ENGINEERING, body)
    }

    /** 使能配置 ACK（官方 PDF）：状态 0000 + 协议版本 0x0001 + 缓冲区 0x0040 */
    private val enableConfigAck = hex("FDFCFBFA0800FF0100000100400004030201")

    /** 按帧格式组一个上报帧：头 + 类型 + 0xAA + body + 0x55 0x00 + 尾 */
    private fun frame(type: Int, body: ByteArray): ByteArray {
        val inner = byteArrayOf(type.toByte(), ProtocolConst.TARGET_HEAD.toByte()) + body +
            byteArrayOf(ProtocolConst.TARGET_TAIL_1.toByte(), ProtocolConst.TARGET_TAIL_2.toByte())
        val len = inner.size
        return ProtocolConst.REPORT_HEAD +
            byteArrayOf((len and 0xFF).toByte(), ((len shr 8) and 0xFF).toByte()) +
            inner + ProtocolConst.REPORT_TAIL
    }

    // ---- 用例 ----

    @Test
    fun `基础上报帧完整解析`() {
        val frames = FrameDecoder().feed(basicReport)
        assertEquals(1, frames.size)
        val f = frames[0] as ReportBasic
        assertEquals(1, f.targetState)
        assertEquals(300, f.moveDistanceCm)
        assertEquals(88, f.moveEnergy)
        assertEquals(250, f.staticDistanceCm)
        assertEquals(60, f.staticEnergy)
        assertEquals(300, f.detectDistanceCm)
    }

    // ---- 黄金测试补全（距离抖动取证，2026-09-28）：仅静止 / 两者同在 / 距离 >255cm 高字节 ----

    /** 仅静止目标：状态 2，静止 400cm/70 能量，运动距离 0 */
    @Test
    fun `仅静止目标 - 运动字段为0_静止距离能量正确`() {
        val body = byteArrayOf(
            0x02, // 目标状态：静止
            0x00, 0x00, // 运动距离 0
            0x00, // 运动能量 0
            0x90.toByte(), 0x01, // 静止距离 400cm（小端：400 = 0x0190）
            0x46, // 静止能量 70
            0x90.toByte(), 0x01, // 检测距离 400cm
        )
        val frames = FrameDecoder().feed(frame(ProtocolConst.TYPE_BASIC, body))
        assertEquals(1, frames.size)
        val f = frames[0] as ReportBasic
        assertEquals(2, f.targetState)
        assertEquals(0, f.moveDistanceCm)
        assertEquals(0, f.moveEnergy)
        assertEquals(400, f.staticDistanceCm)
        assertEquals(70, f.staticEnergy)
        assertEquals(400, f.detectDistanceCm)
    }

    /** 运动+静止同在：两个通道各自独立解析，检测距离取较大者 */
    @Test
    fun `两者同在 - 运动与静止通道互不串扰`() {
        val body = byteArrayOf(
            0x03, // 目标状态：运动+静止
            0x64, 0x00, // 运动距离 100cm
            0x50, // 运动能量 80
            0xE2.toByte(), 0x01, // 静止距离 482cm（小端）
            0x28, // 静止能量 40
            0xE2.toByte(), 0x01, // 检测距离 482cm
        )
        val frames = FrameDecoder().feed(frame(ProtocolConst.TYPE_BASIC, body))
        assertEquals(1, frames.size)
        val f = frames[0] as ReportBasic
        assertEquals(3, f.targetState)
        assertEquals(100, f.moveDistanceCm)
        assertEquals(80, f.moveEnergy)
        assertEquals(482, f.staticDistanceCm)
        assertEquals(40, f.staticEnergy)
        assertEquals(482, f.detectDistanceCm)
    }

    /** 距离 600cm：高字节非零（0x0258），验证小端拼接无误（抖动排查：远门目标是否被截断成低字节） */
    @Test
    fun `距离超过255cm - 高字节正确拼接`() {
        val body = byteArrayOf(
            0x01,
            0x58, 0x02, // 运动距离 600cm = 0x0258
            0x32, // 运动能量 50
            0x00, 0x00, // 静止距离 0
            0x00, // 静止能量 0
            0x58, 0x02, // 检测距离 600cm
        )
        val frames = FrameDecoder().feed(frame(ProtocolConst.TYPE_BASIC, body))
        val f = frames[0] as ReportBasic
        assertEquals(600, f.moveDistanceCm)
        assertEquals(600, f.detectDistanceCm)
        assertEquals(0, f.staticDistanceCm)
    }

    @Test
    fun `半包 - 一帧跨多个BLE通知`() {
        val decoder = FrameDecoder()
        assertTrue(decoder.feed(basicReport.copyOfRange(0, 10)).isEmpty())
        assertTrue(decoder.feed(basicReport.copyOfRange(10, 16)).isEmpty())
        val frames = decoder.feed(basicReport.copyOfRange(16, basicReport.size))
        assertEquals(1, frames.size)
        assertEquals(88, (frames[0] as ReportBasic).moveEnergy)
    }

    @Test
    fun `粘包 - ACK与上报帧背靠背到达`() {
        val decoder = FrameDecoder()
        val combined = enableConfigAck + basicReport
        val frames = decoder.feed(combined)
        assertEquals(2, frames.size)
        val ack = frames[0] as Ack
        assertEquals(0x01FF, ack.ackCommand)
        assertEquals(0, ack.status)
        assertTrue(ack.success)
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x40, 0x00), ack.payload)
        assertTrue(frames[1] is ReportBasic)
    }

    @Test
    fun `脏字节前缀 - 帧头前有垃圾数据`() {
        val decoder = FrameDecoder()
        val garbage = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        val frames = decoder.feed(garbage + basicReport)
        assertEquals(1, frames.size)
        assertEquals(300, (frames[0] as ReportBasic).detectDistanceCm)
    }

    @Test
    fun `帧尾校验失败 - 丢弃坏帧并自动恢复`() {
        val decoder = FrameDecoder()
        // 破坏 basicReport 的帧尾最后一个字节
        val corrupted = basicReport.copyOf()
        corrupted[corrupted.size - 1] = 0x00
        val frames = decoder.feed(corrupted + basicReport)
        assertEquals(1, frames.size)
        assertEquals(1, (frames[0] as ReportBasic).targetState)
    }

    @Test
    fun `工程模式帧 - 9门能量数组`() {
        val frames = FrameDecoder().feed(engineeringReport)
        assertEquals(1, frames.size)
        val f = frames[0] as ReportEngineering
        assertEquals(3, f.targetState)
        assertEquals(8, f.maxMoveGate)
        assertEquals(8, f.maxStaticGate)
        assertEquals(9, f.moveGateEnergies.size)
        assertEquals(9, f.staticGateEnergies.size)
        assertEquals(10, f.moveGateEnergies[0])
        assertEquals(18, f.moveGateEnergies[8])
        assertEquals(20, f.staticGateEnergies[0])
        assertEquals(28, f.staticGateEnergies[8])
        assertEquals(0, f.extra.size)
    }

    @Test
    fun `工程模式帧 - 尾部带容错字段（如光照或OUT）`() {
        val body = ByteArray(9 + 2 + 9 + 9 + 2) // 末尾多 2 字节（光照+OUT，待实机确认）
        body[0] = 0x02
        body[9] = 0x08
        body[10] = 0x08
        for (i in 0..8) body[11 + i] = (10 + i).toByte()
        for (i in 0..8) body[20 + i] = (20 + i).toByte()
        body[29] = 0x7B // 光照
        body[30] = 0x01 // OUT
        val frames = FrameDecoder().feed(frame(ProtocolConst.TYPE_ENGINEERING, body))
        val f = frames[0] as ReportEngineering
        assertEquals(9, f.moveGateEnergies.size)
        assertEquals(2, f.extra.size)
        assertEquals(0x7B, f.extra[0].toInt() and 0xFF)
    }

    @Test
    fun `未知数据类型 - 保留原始数据为Unknown`() {
        val frames = FrameDecoder().feed(frame(0x03, ByteArray(9)))
        assertEquals(1, frames.size)
        assertTrue(frames[0] is Unknown)
    }

    @Test
    fun `reset - 重连后清缓冲不串帧`() {
        val decoder = FrameDecoder()
        decoder.feed(basicReport.copyOfRange(0, 8)) // 留半帧在缓冲
        decoder.reset()
        assertEquals(0, decoder.pendingBytes())
        val frames = decoder.feed(basicReport)
        assertEquals(1, frames.size)
    }

    // ---- 工具 ----

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { i -> ((Character.digit(s[i * 2], 16) shl 4) + Character.digit(s[i * 2 + 1], 16)).toByte() }
}

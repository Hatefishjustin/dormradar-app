package com.dormradar.app.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ACK 载荷解析测试。
 * 布局依据官方安卓 Demo `DeviceState.kt`（docs/reference/AndroidDemo），
 * 样本按其解析逻辑反向构造。
 */
class AckParsersTest {

    @Test
    fun `读参数 - 最大门8共9门灵敏度`() {
        // 布局（ESPHome + 官方 Demo 交叉验证）：AA + 最大门8 + 运动门6 + 静止门8 + 9运动 + 9静止 + 无人5s
        // 用三个不同门值锁定字段顺序（此前实现错把最大门/运动门当运动/静止门）
        val payload = byteArrayOf(
            0xAA.toByte(), 0x08, 0x06, 0x08,
            50, 50, 40, 30, 20, 15, 15, 15, 15, // 运动灵敏度（出厂表）
            40, 40, 40, 40, 30, 30, 20, 20, 20, // 静止灵敏度
            0x05, 0x00, // 无人 5 秒
        )
        val p = AckParsers.parseDeviceParams(payload)!!
        assertEquals(8, p.maxGate)
        assertEquals(6, p.maxMoveGate)
        assertEquals(8, p.maxStaticGate)
        assertArrayEquals(intArrayOf(50, 50, 40, 30, 20, 15, 15, 15, 15), p.motionSensitivities)
        assertArrayEquals(intArrayOf(40, 40, 40, 40, 30, 30, 20, 20, 20), p.staticSensitivities)
        assertEquals(5, p.unmannedSeconds)
    }

    @Test
    fun `读参数 - 灵敏度越界视为结构误判返回null`() {
        val payload = byteArrayOf(0xAA.toByte(), 0x01, 0x01, 0x01, 50, 50, 200.toByte(), 0x05, 0x00)
        assertNull(AckParsers.parseDeviceParams(payload))
    }

    @Test
    fun `读参数 - 长度不足返回null`() {
        assertNull(AckParsers.parseDeviceParams(byteArrayOf(0xAA.toByte(), 0x08, 0x08)))
        assertNull(AckParsers.parseDeviceParams(ByteArray(0)))
    }

    @Test
    fun `使能配置ACK - 官方样本 版本1缓冲64`() {
        // 官方 PDF：ACK payload = 0100 4000 → 协议版本 1、缓冲区 0x40
        val p = AckParsers.parseEnableConfig(byteArrayOf(0x01, 0x00, 0x40, 0x00))!!
        assertEquals(1, p.first)
        assertEquals(0x40, p.second)
    }

    @Test
    fun `固件版本 - 按真机样本解析`() {
        // 真机实测 payload：[00 01 44 02 17 09 07 25] → 1.00.68，构建号 0217090725
        val v = AckParsers.parseFirmwareVersion(byteArrayOf(0x00, 0x01, 0x44, 0x02, 0x17, 0x09, 0x07, 0x25.toByte()))!!
        assertEquals("1.00.68 (0217090725)", v)
    }

    @Test
    fun `MAC - 类型字节后取6字节大端展示`() {
        val mac = AckParsers.parseMac(byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66))!!
        assertEquals("11:22:33:44:55:66", mac)
        assertNull(AckParsers.parseMac(byteArrayOf(0x00, 0x11, 0x22)))
    }

    @Test
    fun `距离分辨率 - 0x01为02m 0x00为075m`() {
        assertEquals(true, AckParsers.parseDistanceResolution(byteArrayOf(0x01)))
        assertEquals(false, AckParsers.parseDistanceResolution(byteArrayOf(0x00)))
        assertNull(AckParsers.parseDistanceResolution(byteArrayOf(0x07)))
        assertNull(AckParsers.parseDistanceResolution(ByteArray(0)))
    }

    @Test
    fun `光敏配置 - state加阈值两字节`() {
        val cfg = AckParsers.parsePhotosensitive(byteArrayOf(0x01, 0x80.toByte()))!!
        assertEquals(1, cfg.state)
        assertEquals(0x80, cfg.threshold)
        assertNull(cfg.frequentHigh) // 旧固件无电频字节
    }

    @Test
    fun `光敏配置 - 新固件第三字节为电频状态`() {
        val high = AckParsers.parsePhotosensitive(byteArrayOf(0x02, 0x40, 0x01))!!
        assertEquals(2, high.state)
        assertEquals(0x40, high.threshold)
        assertEquals(true, high.frequentHigh)
        val low = AckParsers.parsePhotosensitive(byteArrayOf(0x00, 0x40, 0x00))!!
        assertEquals(false, low.frequentHigh)
        assertNull(AckParsers.parsePhotosensitive(byteArrayOf(0x01)))
    }

    @Test
    fun `门限值 - 布局A官方Demo口径无状态字`() {
        // 官方 Demo analysisThresholdData：inner = cmd(C501) + 上限 + 00 + 下限 + 00
        // 本解析器视角：status = 上限|0x00，payload = [00, 下限, 00]
        val ack = Frame.Ack(ackCommand = 0x01C5, status = 80, payload = byteArrayOf(0x00, 50, 0x00))
        val (upper, lower) = AckParsers.parseThreshold(ack)!!
        assertEquals(80, upper)
        assertEquals(50, lower)
    }

    @Test
    fun `门限值 - 布局B标准ACK带状态字`() {
        val ack = Frame.Ack(ackCommand = 0x01C5, status = 0, payload = byteArrayOf(80, 0x00, 50, 0x00))
        val (upper, lower) = AckParsers.parseThreshold(ack)!!
        assertEquals(80, upper)
        assertEquals(50, lower)
    }

    @Test
    fun `门限值 - 无法识别返回null`() {
        assertNull(AckParsers.parseThreshold(Frame.Ack(0x01C5, 0, byteArrayOf(0x07))))
        assertNull(AckParsers.parseThreshold(Frame.Ack(0x01C5, 0, ByteArray(0))))
    }
}

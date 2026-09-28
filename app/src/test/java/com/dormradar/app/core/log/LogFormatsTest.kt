package com.dormradar.app.core.log

import com.dormradar.app.core.data.room.RadarEventEntity
import com.dormradar.app.core.device.DeviceInfo
import com.dormradar.app.core.protocol.DeviceParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/** 导出文本格式化（纯函数） */
class LogFormatsTest {

    @Test
    fun `十六进制转换`() {
        assertEquals("FD FC FB FA", LogFormats.toHex(byteArrayOf(0xFD.toByte(), 0xFC.toByte(), 0xFB.toByte(), 0xFA.toByte())))
        assertEquals("", LogFormats.toHex(ByteArray(0)))
        assertEquals("00 0A FF", LogFormats.toHex(byteArrayOf(0, 10, 255.toByte())))
    }

    @Test
    fun `运行日志行格式`() {
        val timeMs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).parse("2026-09-27 15:30:00.123")!!.time
        val line = LogFormats.runtimeLine(LogEntry(timeMs, LogLevel.W, "RadarConnection", "第 1 次重连"))
        assertEquals("09-27 15:30:00.123 W/RadarConnection: 第 1 次重连", line)
    }

    @Test
    fun `原始帧行格式`() {
        val timeMs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).parse("2026-09-27 08:00:00.500")!!.time
        val line = LogFormats.frameLine(FrameLogEntry(timeMs, FrameDir.TX, 4, "FD FC FB FA"))
        assertEquals("09-27 08:00:00.500 TX [4B] FD FC FB FA", line)
    }

    @Test
    fun `CSV 转义`() {
        assertEquals("普通", LogFormats.csvEscape("普通"))
        assertEquals("\"a,b\"", LogFormats.csvEscape("a,b"))
        assertEquals("\"说\"\"你好\"\"\"", LogFormats.csvEscape("说\"你好\""))
        assertEquals("\"换\n行\"", LogFormats.csvEscape("换\n行"))
    }

    @Test
    fun `事件 CSV 含表头且转义逗号`() {
        val csv = LogFormats.eventsCsv(
            listOf(
                // 半角逗号必须转义；全角逗号不触发转义（不破坏 CSV 结构）
                RadarEventEntity(id = 1, mac = "AA:BB", timestamp = 0, type = "APPEAR", detail = "有人出现,距离 1.2 米"),
            ),
        )
        assertTrue(csv.startsWith("id,时间,类型,MAC,详情"))
        assertTrue(csv.contains("APPEAR"))
        assertTrue(csv.contains("\"有人出现,距离 1.2 米\"")) // 含半角逗号被引号包裹
        assertEquals(2, csv.trim().lines().size)
    }

    @Test
    fun `配置快照包含关键字段`() {
        val info = DeviceInfo(
            mac = "AA:BB:CC:DD:EE:FF",
            firmwareVersion = "1.00.68",
            protocolVersion = 1,
            params = DeviceParams(
                maxGate = 8,
                maxMoveGate = 8, maxStaticGate = 6,
                motionSensitivities = IntArray(9) { 50 },
                staticSensitivities = IntArray(9) { 40 },
                unmannedSeconds = 5,
            ),
            resolution02m = false,
        )
        val dump = LogFormats.configDump(info, "0.4.0", "Xiaomi Redmi Note 11")
        assertTrue(dump.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(dump.contains("1.00.68"))
        assertTrue(dump.contains("0.75m"))
        assertTrue(dump.contains("无人持续时间：5 秒"))
        assertTrue(dump.contains("门 0：50 / 40"))
        assertTrue(dump.contains("v0.4.0"))
    }

    @Test
    fun `未连接时导出配置快照降级`() {
        val dump = LogFormats.configDump(null, "0.4.0", "Test")
        assertTrue(dump.contains("当前未连接"))
        assertFalse(dump.contains("设备 MAC"))
    }
}

package com.dormradar.app.core.log

import com.dormradar.app.core.device.DeviceInfo
import com.dormradar.app.core.protocol.DeviceParams
import org.junit.Assert.assertTrue
import org.junit.Test

/** 报 bug 设备摘要（纯函数） */
class FeedbackSummaryTest {

    @Test
    fun `已连接时含关键信息`() {
        val info = DeviceInfo(
            mac = "AA:BB:CC",
            firmwareVersion = "1.00.68",
            protocolVersion = 1,
            params = DeviceParams(
                maxGate = 8,
                maxMoveGate = 8, maxStaticGate = 6,
                motionSensitivities = intArrayOf(50, 50, 100),
                staticSensitivities = intArrayOf(40, 40, 100),
                unmannedSeconds = 5,
            ),
            resolution02m = false,
        )
        val s = FeedbackSummary.build("0.6.0", "Xiaomi M2011K2C", info)
        assertTrue(s.contains("App 版本：v0.6.0"))
        assertTrue(s.contains("手机：Xiaomi M2011K2C"))
        assertTrue(s.contains("AA:BB:CC"))
        assertTrue(s.contains("1.00.68"))
        assertTrue(s.contains("无人 5s"))
        assertTrue(s.contains("[50, 50, 100]"))
    }

    @Test
    fun `未连接时降级显示`() {
        val s = FeedbackSummary.build("0.6.0", "Test", null)
        assertTrue(s.contains("未连接"))
        assertTrue(s.contains("v0.6.0"))
    }

    @Test
    fun `模拟设备有标注`() {
        val info = DeviceInfo(
            mac = "FA:KE",
            firmwareVersion = "FAKE-1.0.0",
            protocolVersion = 1,
            params = null,
            resolution02m = null,
            isFake = true,
        )
        val s = FeedbackSummary.build("0.6.0", "Test", info)
        assertTrue(s.contains("模拟设备"))
    }
}

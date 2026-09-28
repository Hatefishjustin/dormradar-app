package com.dormradar.app.core.data

import com.dormradar.app.core.device.DeviceInfo
import com.dormradar.app.core.protocol.DeviceParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 配置快照编解码（行式 k=v，稳定格式） */
class ConfigCodecTest {

    private val data = ConfigSnapshotData(
        maxMoveGate = 8,
        maxStaticGate = 6,
        unmannedSeconds = 5,
        resolution02m = false,
        motionSensitivities = intArrayOf(50, 40, 30, 100),
        staticSensitivities = intArrayOf(40, 30, 20, 100),
    )

    @Test
    fun `编码解码往返一致`() {
        val text = ConfigCodec.encode(data)
        val back = ConfigCodec.decode(text)
        assertEquals(data, back)
    }

    @Test
    fun `分辨率未知编解码为 null`() {
        val back = ConfigCodec.decode(ConfigCodec.encode(data.copy(resolution02m = null)))
        assertEquals(null, back?.resolution02m)
    }

    @Test
    fun `0-2m 分辨率往返`() {
        val back = ConfigCodec.decode(ConfigCodec.encode(data.copy(resolution02m = true)))
        assertEquals(true, back?.resolution02m)
    }

    @Test
    fun `损坏文本返回 null`() {
        assertNull(ConfigCodec.decode("完全不是配置"))
        assertNull(ConfigCodec.decode("v=2\nmaxMove=8")) // 版本不识别
        assertNull(ConfigCodec.decode("v=1\nmaxMove=abc")) // 数字损坏
        assertNull(ConfigCodec.decode("v=1")) // 缺字段
        assertNull(ConfigCodec.decode("v=1\nmaxMove=8\nmaxStatic=6\nunmanned=5\nm=1,2\ns=1")) // 表长不一致
    }

    @Test
    fun `带空白与空行的文本可解码`() {
        val text = "\n v=1 \n\nmaxMove=8\nmaxStatic=6\nunmanned=5\nres=1\nm=50, 40\ns=40, 30\n"
        val back = ConfigCodec.decode(text)
        assertEquals(8, back?.maxMoveGate)
        assertTrue(back?.motionSensitivities?.contentEquals(intArrayOf(50, 40)) == true)
    }

    @Test
    fun `从设备信息取快照`() {
        val info = DeviceInfo(
            mac = "AA:BB",
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
        val snapshot = ConfigCodec.fromDeviceInfo(info)
        assertEquals(8, snapshot?.maxMoveGate)
        assertEquals(6, snapshot?.maxStaticGate)
        assertEquals(9, snapshot?.motionSensitivities?.size)
        assertNull(ConfigCodec.fromDeviceInfo(null))
    }
}

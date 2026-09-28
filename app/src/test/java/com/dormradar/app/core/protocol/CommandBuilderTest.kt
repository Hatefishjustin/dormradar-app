package com.dormradar.app.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 命令构造黄金测试。
 * 样本来源（勿改动，均为权威字节流）：
 * - 官方《LD2410C 串口通信协议 V1.09》PDF 示例（docs/03 §2）；
 * - 官方安卓 Demo [com.hlk.hlkradartool.util.BaseVolume] 常量（docs/reference/AndroidDemo）。
 */
class CommandBuilderTest {

    @Test
    fun `使能配置 - 官方PDF样本`() {
        assertArrayEquals(hex("FDFCFBFA0400FF00010004030201"), CommandBuilder.enableConfig())
    }

    @Test
    fun `结束配置 - 官方PDF样本`() {
        assertArrayEquals(hex("FDFCFBFA0200FE0004030201"), CommandBuilder.endConfig())
    }

    @Test
    fun `使能工程模式 - 官方Demo样本`() {
        assertArrayEquals(hex("FDFCFBFA0200620004030201"), CommandBuilder.enableEngineering())
    }

    @Test
    fun `最大门8加无人5秒 - 官方PDF样本`() {
        // PDF 原文：最大门8+5秒 → FD FC FB FA 14 00 60 00 00 00 08 00 00 00 01 00 08 00 00 00 02 00 05 00 00 00 04 03 02 01
        assertArrayEquals(
            hex("FDFCFBFA1400600000000800000001000800000002000500000004030201"),
            CommandBuilder.setMaxGatesAndUnmannedTime(moveMaxGate = 8, staticMaxGate = 8, unmannedSeconds = 5),
        )
    }

    @Test
    fun `设置单个门灵敏度`() {
        // 门 3：运动 40(0x28)、静止 50(0x32)；长度 0x14，结构与 0x0060 一致
        assertArrayEquals(
            hex("FDFCFBFA1400640000000300000001002800000002003200000004030201"),
            CommandBuilder.setGateSensitivity(gate = 3, motionSensitivity = 40, staticSensitivity = 50),
        )
    }

    @Test
    fun `全门灵敏度 - 门号0xFFFF`() {
        assertArrayEquals(
            hex("FDFCFBFA140064000000FFFF000001002800000002003200000004030201"),
            CommandBuilder.setGateSensitivity(gate = 0xFFFF, motionSensitivity = 40, staticSensitivity = 50),
        )
    }

    @Test
    fun `读取参数 - 官方PDF样本`() {
        assertArrayEquals(hex("FDFCFBFA0200610004030201"), CommandBuilder.readParameters())
    }

    @Test
    fun `获取MAC - 官方PDF样本`() {
        assertArrayEquals(hex("FDFCFBFA0400A500010004030201"), CommandBuilder.readMac())
    }

    @Test
    fun `校验密码HiLink - 官方Demo checkKeyByPwd 逻辑`() {
        // Demo：长度固定 0800，密码按 ASCII 原文发送；"HiLink" = 48 69 4C 69 6E 6B
        assertArrayEquals(hex("FDFCFBFA0800A80048694C696E6B04030201"), CommandBuilder.authenticate("HiLink"))
    }

    @Test
    fun `密码长度不是6字节时报错`() {
        assertThrows(IllegalArgumentException::class.java) { CommandBuilder.authenticate("ABC") }
        assertThrows(IllegalArgumentException::class.java) { CommandBuilder.setPassword("HiLink!") }
    }

    @Test
    fun `距离分辨率 - 官方Demo 两种取值`() {
        // CMD_FULL_SET_DOOR_DPI_02（0.2m）/ CMD_FULL_SET_DOOR_DPI_075（0.75m）
        assertArrayEquals(hex("FDFCFBFA0400AA00010004030201"), CommandBuilder.setDistanceResolution(resolution02m = true))
        assertArrayEquals(hex("FDFCFBFA0400AA00000004030201"), CommandBuilder.setDistanceResolution(resolution02m = false))
        assertArrayEquals(hex("FDFCFBFA0200AB0004030201"), CommandBuilder.readDistanceResolution())
    }

    @Test
    fun `底噪检测60秒 - 官方Demo CMD_GROUND_NOISE_AUTO_2410`() {
        assertArrayEquals(hex("FDFCFBFA04000B003C0004030201"), CommandBuilder.startNoiseDetection(durationSeconds = 60))
    }

    @Test
    fun `查询底噪检测状态 - 复刻清单来源（协议V1_07，官方Demo未用，待真机验证）`() {
        assertArrayEquals(hex("FDFCFBFA02001B0004030201"), CommandBuilder.queryNoiseDetectionStatus())
    }

    @Test
    fun `波特率档位 - 官方Demo CMD_FULL_SET_BAUD_256000`() {
        // 档位 7 = 256000；Demo 样本 FDFCFBFA0400A100070004030201
        assertArrayEquals(hex("FDFCFBFA0400A100070004030201"), CommandBuilder.setBaudRate(selection = 7))
        assertThrows(IllegalArgumentException::class.java) { CommandBuilder.setBaudRate(selection = 9) }
    }

    @Test
    fun `蓝牙开关 - 官方Demo CMD_TYPE_BLUETOOTH_OPEN_2450`() {
        assertArrayEquals(hex("FDFCFBFA0400A400010004030201"), CommandBuilder.setBluetooth(enabled = true))
        assertArrayEquals(hex("FDFCFBFA0400A400000004030201"), CommandBuilder.setBluetooth(enabled = false))
    }

    @Test
    fun `OTA使能 - 官方Demo CMD_FULL_OTA_ENABLE`() {
        assertArrayEquals(hex("FDFCFBFA0400C000010004030201"), CommandBuilder.setOtaEnabled(enabled = true))
        assertArrayEquals(hex("FDFCFBFA0400C000000004030201"), CommandBuilder.setOtaEnabled(enabled = false))
    }

    @Test
    fun `光敏辅助控制 - 官方Demo setPhotosensitive 长度0400`() {
        assertArrayEquals(hex("FDFCFBFA0400AD0001C804030201"), CommandBuilder.setPhotosensitiveAux(state = 1, value = 200))
        assertArrayEquals(hex("FDFCFBFA0200AE0004030201"), CommandBuilder.readPhotosensitiveAux())
    }

    @Test
    fun `门限值 - 官方Demo setThreshold 结构`() {
        // 上限 50(0x32)、下限 10(0x0A)，各占 1B + 0x00 填充；长度 0600
        assertArrayEquals(hex("FDFCFBFA0600C40032000A0004030201"), CommandBuilder.setThreshold(upperLimit = 50, lowerLimit = 10))
        assertArrayEquals(hex("FDFCFBFA0200C50004030201"), CommandBuilder.readThreshold())
    }

    // ---- 工具 ----

    /** 十六进制字符串转字节数组 */
    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { i -> ((Character.digit(s[i * 2], 16) shl 4) + Character.digit(s[i * 2 + 1], 16)).toByte() }
}

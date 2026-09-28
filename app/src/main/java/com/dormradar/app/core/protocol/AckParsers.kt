package com.dormradar.app.core.protocol

/**
 * ACK 载荷解析器（Ack.payload = 去掉命令字与状态字之后的部分）。
 * 布局依据官方安卓 Demo `DeviceState.kt` 的解析逻辑（docs/reference/AndroidDemo），
 * 并以容错方式解析（docs/03 §5.6：固件差异做能力探测，不硬编码）。
 */
object AckParsers {

    /**
     * 0x0061 读取参数 ACK。
     * 布局（官方 Demo analysisReadParameter + ESPHome ld2410.cpp 双重交叉验证，2026-09-27 真机确认）：
     * `0xAA + 最大距离门(1B) + 配置运动门(1B) + 配置静止门(1B)
     *  + (最大门+1) 个运动灵敏度 + (最大门+1) 个静止灵敏度 + 无人持续时间(2B 小端)`
     * 注意：首个字段是「最大距离门」（决定门数与数组长度，0.75m 时=8），
     * 配置运动门/静止门在其后 —— 此前的实现错把前两字段当运动/静止门导致回读校验永远失败。
     */
    fun parseDeviceParams(payload: ByteArray): DeviceParams? {
        if (payload.size < 7) return null
        if (payload[0].toInt() and 0xFF != ProtocolConst.TARGET_HEAD) return null
        val maxGate = payload[1].toInt() and 0xFF
        if (maxGate !in 1..16) return null
        val gateCount = maxGate + 1
        val expectedSize = 4 + gateCount * 2 + 2
        if (payload.size < expectedSize) return null
        val motion = IntArray(gateCount) { payload[4 + it].toInt() and 0xFF }
        val static = IntArray(gateCount) { payload[4 + gateCount + it].toInt() and 0xFF }
        if (motion.any { it > 100 } || static.any { it > 100 }) return null // 灵敏度越界 → 结构误判
        val unmanned = FrameDecoder.le16(payload, 4 + gateCount * 2)
        return DeviceParams(
            maxGate = maxGate,
            maxMoveGate = payload[2].toInt() and 0xFF,
            maxStaticGate = payload[3].toInt() and 0xFF,
            motionSensitivities = motion,
            staticSensitivities = static,
            unmannedSeconds = unmanned,
        )
    }

    /**
     * 0x00FF 使能配置 ACK：协议版本(2B 小端) + 缓冲区大小(2B 小端，典型 0x0040)。
     * @return Pair(协议版本, 缓冲区大小)
     */
    fun parseEnableConfig(payload: ByteArray): Pair<Int, Int>? {
        if (payload.size < 4) return null
        return FrameDecoder.le16(payload, 0) to FrameDecoder.le16(payload, 2)
    }

    /**
     * 0x00A0 固件版本 ACK。真机实测样本 [00 01 44 02 17 09 07 25] → "1.00.68 (0217090725)"，
     * 与官方工具版本风格（V1.00.68 + 构建号）一致。
     */
    fun parseFirmwareVersion(payload: ByteArray): String? {
        if (payload.size < 4) return null
        val major = payload[1].toInt() and 0xFF
        val minor = "%02X".format(payload[0])
        val patch = payload[2].toInt() and 0xFF
        val tail = payload.drop(3).joinToString("") { "%02X".format(it) }
        return "$major.$minor.$patch${if (tail.isEmpty()) "" else " ($tail)"}"
    }

    /**
     * 0x00A5 MAC 地址 ACK：类型(1B) + MAC(6B)。（ESPHome 与官方 Demo 均为 6 字节；
     * docs/03 旧版"3B MAC"为笔误，已修正）
     */
    fun parseMac(payload: ByteArray): String? {
        val mac = payload.drop(1).take(6)
        if (mac.size < 6) return null
        return mac.joinToString(":") { "%02X".format(it) }
    }

    /**
     * 0x00AB 距离分辨率 ACK：1 字节，0x01 = 0.2m / 0x00 = 0.75m。
     */
    fun parseDistanceResolution(payload: ByteArray): Boolean? {
        val v = payload.getOrNull(0)?.toInt() ?: return null
        return when (v and 0xFF) {
            0x01 -> true
            0x00 -> false
            else -> null
        }
    }

    /**
     * 0x00AE 光敏/辅助控制查询 ACK：state(1B) + 阈值(1B) [+ 电频(1B，新固件)]。
     * state 语义（官方 Demo DeviceState.getPhotosensitiveStateHttp 确认）：
     * 0x00=关闭 / 0x01=光感低于阈值触发 / 0x02=光感高于阈值触发；
     * 阈值 0~255（默认 0x80）；固件 ≥2.01.23021610 时第 3 字节为 OUT 电频状态（0x01=高电平）。
     */
    fun parsePhotosensitive(payload: ByteArray): PhotosensitiveConfig? {
        if (payload.size < 2) return null
        return PhotosensitiveConfig(
            state = payload[0].toInt() and 0xFF,
            threshold = payload[1].toInt() and 0xFF,
            frequentHigh = if (payload.size >= 3) (payload[2].toInt() and 0xFF) == 0x01 else null,
        )
    }

    /**
     * 0x00C5 门限值查询 ACK（容错双布局，真机复核后收敛，见 docs/08）：
     * - 布局 A（官方 Demo analysisThresholdData 口径，无标准 2B 状态字）：
     *   inner = 命令字(2B) + 上限(1B) + 00 + 下限(1B) + 00 → 本解析器的 status=上限、payload=[00, 下限, 00]
     * - 布局 B（标准 ACK）：status=0，payload = [上限, 00, 下限, 00]
     */
    fun parseThreshold(ack: Frame.Ack): Pair<Int, Int>? {
        val p = ack.payload
        return when {
            // 布局 A：status 低字节 = 上限，payload[1] = 下限
            p.size >= 2 && p[0].toInt() == 0x00 && ack.status != 0 ->
                (ack.status and 0xFF) to (p[1].toInt() and 0xFF)
            // 布局 B：status = 0（成功），payload = [上限, 00, 下限, 00]
            p.size >= 4 && p[1].toInt() == 0x00 && p[3].toInt() == 0x00 ->
                (p[0].toInt() and 0xFF) to (p[2].toInt() and 0xFF)
            // 布局 A 变体（下限后无填充字节）
            p.size >= 2 && p[0].toInt() == 0x00 -> (ack.status and 0xFF) to (p[1].toInt() and 0xFF)
            else -> null
        }
    }
}

/** 0x0061 读回的设备参数 */
data class DeviceParams(
    /** 最大距离门（硬件上限，决定门数 = maxGate+1；0.75m 时为 8） */
    val maxGate: Int,
    val maxMoveGate: Int,
    val maxStaticGate: Int,
    val motionSensitivities: IntArray,
    val staticSensitivities: IntArray,
    val unmannedSeconds: Int,
)

/** 0x00AE 读回的光敏配置 */
data class PhotosensitiveConfig(
    val state: Int,
    val threshold: Int,
    /** OUT 电频状态（0x01=高电平）；固件不支持时为 null */
    val frequentHigh: Boolean? = null,
)

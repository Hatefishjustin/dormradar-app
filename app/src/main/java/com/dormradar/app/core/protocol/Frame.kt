package com.dormradar.app.core.protocol

/**
 * LD2410C 协议常量与帧模型。
 * 权威依据：docs/03-协议速查-LD2410C.md（源自官方协议 PDF V1.09 + 官方安卓 Demo 源码）。
 * 本包零 Android 依赖，可纯 JVM 单测。
 */
object ProtocolConst {
    /** 命令帧头 FD FC FB FA（手机 → 设备，含 ACK 回传） */
    val CMD_HEAD: ByteArray = byteArrayOf(0xFD.toByte(), 0xFC.toByte(), 0xFB.toByte(), 0xFA.toByte())

    /** 命令帧尾 04 03 02 01 */
    val CMD_TAIL: ByteArray = byteArrayOf(0x04, 0x03, 0x02, 0x01)

    /** 上报帧头 F4 F3 F2 F1（设备 → 手机） */
    val REPORT_HEAD: ByteArray = byteArrayOf(0xF4.toByte(), 0xF3.toByte(), 0xF2.toByte(), 0xF1.toByte())

    /** 上报帧尾 F8 F7 F6 F5 */
    val REPORT_TAIL: ByteArray = byteArrayOf(0xF8.toByte(), 0xF7.toByte(), 0xF6.toByte(), 0xF5.toByte())

    /** 上报帧内数据头 0xAA / 尾 0x55 0x00 */
    const val TARGET_HEAD: Int = 0xAA
    const val TARGET_TAIL_1: Int = 0x55
    const val TARGET_TAIL_2: Int = 0x00

    /** 上报帧数据类型 */
    const val TYPE_ENGINEERING: Int = 0x01
    const val TYPE_BASIC: Int = 0x02

    /** 目标状态 */
    const val TARGET_NONE: Int = 0
    const val TARGET_MOVING: Int = 1
    const val TARGET_STATIC: Int = 2
    const val TARGET_BOTH: Int = 3

    /** 底噪检测期间的目标状态扩展值（docs/03 §3.2） */
    const val TARGET_NOISE_RUNNING: Int = 0x04
    const val TARGET_NOISE_SUCCESS: Int = 0x05
    const val TARGET_NOISE_FAILED: Int = 0x06

    /** 基本信息数据体长度：状态(1)+运动距离(2)+运动能量(1)+静止距离(2)+静止能量(1)+检测距离(2) */
    const val BASIC_BODY_SIZE: Int = 9
}

/** 解析出的帧。ACK 与上报帧共用此密封类。 */
sealed class Frame {

    /**
     * ACK 帧（设备对命令的应答）。
     * @param ackCommand 应答命令字 = 原命令字 | 0x0100（如 0x01FF）
     * @param status ACK 状态：0 = 成功，非 0 = 失败
     * @param payload status 之后的返回数据（如读参数、读版本的内容）
     */
    data class Ack(
        val ackCommand: Int,
        val status: Int,
        val payload: ByteArray,
    ) : Frame() {
        val success: Boolean get() = status == 0
    }

    /** 基础上报帧（默认模式，数据类型 0x02） */
    data class ReportBasic(
        val targetState: Int,
        val moveDistanceCm: Int,
        val moveEnergy: Int,
        val staticDistanceCm: Int,
        val staticEnergy: Int,
        val detectDistanceCm: Int,
    ) : Frame()

    /**
     * 工程模式上报帧（数据类型 0x01）：在基本信息后追加各距离门能量。
     * 各门数量不写死（现固件 9 门，索引 0~8）；固件差异字段（光照/OUT 等）
     * 全部落入 [extra]，待实机确认后细化（docs/03 §3.2）。
     */
    data class ReportEngineering(
        val targetState: Int,
        val moveDistanceCm: Int,
        val moveEnergy: Int,
        val staticDistanceCm: Int,
        val staticEnergy: Int,
        val detectDistanceCm: Int,
        val maxMoveGate: Int,
        val maxStaticGate: Int,
        val moveGateEnergies: IntArray,
        val staticGateEnergies: IntArray,
        val extra: ByteArray,
    ) : Frame()

    /** 帧结构完整但内容无法识别（未知命令/未知数据类型），保留原文供调试 */
    data class Unknown(
        val kind: Kind,
        val data: ByteArray,
    ) : Frame() {
        enum class Kind { COMMAND, REPORT }
    }
}

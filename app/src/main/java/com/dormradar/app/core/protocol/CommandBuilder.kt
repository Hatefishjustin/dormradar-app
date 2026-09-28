package com.dormradar.app.core.protocol

/**
 * LD2410C 命令构造器（手机 → 设备）。
 *
 * 帧格式（docs/03 §1.1）：帧头(4B) + 帧内数据长度(2B 小端) + 命令字(2B) + 命令值 + 帧尾(4B)。
 * 所有黄金样本来自官方协议 PDF 示例与官方安卓 Demo 常量（docs/03 §2 / reference/AndroidDemo）。
 *
 * 注意：配置类命令须包裹在 [enableConfig]（0x00FF）与 [endConfig]（0x00FE）之间；
 * ACK 命令字 = 原命令字 | 0x0100，串行发送等 ACK（会话编排由上层 RadarConnection 负责）。
 */
object CommandBuilder {

    // ---- 配置会话 ----

    /** 使能配置（0x00FF，值 0x0001）。官方样本：FDFCFBFA 0400 FF00 0100 04030201 */
    fun enableConfig(): ByteArray = build(0x00FF, u16(0x0001))

    /** 结束配置（0x00FE，无值）。官方样本：FDFCFBFA 0200 FE00 04030201 */
    fun endConfig(): ByteArray = build(0x00FE)

    // ---- 参数与灵敏度 ----

    /**
     * 最大距离门与无人持续时间（0x0060）。
     * 三组「参数字(2B)+参数值(4B)」：运动门(0x0000)、静止门(0x0001)，范围 2~8；无人时长(0x0002)，0~65535 秒。
     */
    fun setMaxGatesAndUnmannedTime(moveMaxGate: Int, staticMaxGate: Int, unmannedSeconds: Int): ByteArray =
        build(
            0x0060,
            u16(0x0000) + u32(moveMaxGate) +
                u16(0x0001) + u32(staticMaxGate) +
                u16(0x0002) + u32(unmannedSeconds),
        )

    /** 读取参数（0x0061，无值） */
    fun readParameters(): ByteArray = build(0x0061)

    /** 使能工程模式（0x0062）。官方样本：FDFCFBFA 0200 6200 04030201 */
    fun enableEngineering(): ByteArray = build(0x0062)

    /** 关闭工程模式（0x0063） */
    fun disableEngineering(): ByteArray = build(0x0063)

    /**
     * 距离门灵敏度配置（0x0064）。
     * @param gate 距离门号；0xFFFF = 全部门（官方 Demo [CreateControlData.setFromDoorSPL]）
     * @param motionSensitivity 运动灵敏度 0~100（100 = 屏蔽该门）
     * @param staticSensitivity 静止灵敏度 0~100
     */
    fun setGateSensitivity(gate: Int, motionSensitivity: Int, staticSensitivity: Int): ByteArray =
        build(
            0x0064,
            u16(0x0000) + u32(gate) +
                u16(0x0001) + u32(motionSensitivity) +
                u16(0x0002) + u32(staticSensitivity),
        )

    // ---- 设备信息与控制 ----

    /** 读取固件版本（0x00A0） */
    fun readFirmwareVersion(): ByteArray = build(0x00A0)

    /**
     * 设置串口波特率（0x00A1，值 = 档位序号 1~8，重启生效）。
     * 官方 Demo 确认映射：1=9600、2=19200、3=38400、4=57600、5=115200、6=230400、7=256000、8=460800。
     * 仅影响 UART，不影响 BLE。
     */
    fun setBaudRate(selection: Int): ByteArray {
        require(selection in 1..8) { "波特率档位必须为 1~8，实际 $selection" }
        return build(0x00A1, u16(selection))
    }

    /** 恢复出厂设置（0x00A2） */
    fun factoryReset(): ByteArray = build(0x00A2)

    /** 重启模块（0x00A3；BLE 会断开，上层需自动重连） */
    fun reboot(): ByteArray = build(0x00A3)

    /** 蓝牙开关（0x00A4）。官方 Demo 确认：开 = 0x0001，关 = 0x0000 */
    fun setBluetooth(enabled: Boolean): ByteArray = build(0x00A4, u16(if (enabled) 0x0001 else 0x0000))

    /** 获取 MAC 地址（0x00A5，值 0x0001；返回 3B MAC，大端） */
    fun readMac(): ByteArray = build(0x00A5, u16(0x0001))

    // ---- 蓝牙密码 ----

    /**
     * 校验密码（0x00A8）。BLE 连接后必须最先发送（默认密码 `HiLink`，6 字节 ASCII），
     * 通过后设备才开始透传数据。官方 Demo [CreateControlData.checkKeyByPwd]：长度固定 0x0008。
     * @throws IllegalArgumentException 密码必须恰好 6 字节
     */
    fun authenticate(password: ByteArray): ByteArray {
        require(password.size == 6) { "密码必须为 6 字节，实际 ${password.size}" }
        return build(0x00A8, password)
    }

    /** 校验密码（ASCII 快捷重载），如 authenticate("HiLink") */
    fun authenticate(password: String): ByteArray = authenticate(password.toByteArray(Charsets.US_ASCII))

    /** 设置控制密码（0x00A9，6 字节新密码） */
    fun setPassword(password: ByteArray): ByteArray {
        require(password.size == 6) { "密码必须为 6 字节，实际 ${password.size}" }
        return build(0x00A9, password)
    }

    fun setPassword(password: String): ByteArray = setPassword(password.toByteArray(Charsets.US_ASCII))

    // ---- V1.09 新增能力（能力探测后使用） ----

    /**
     * 设置距离分辨率（0x00AA）。官方 Demo 确认：0x0001 = 0.2m，0x0000 = 0.75m。
     * 设置后距离门含义随之变化。
     */
    fun setDistanceResolution(resolution02m: Boolean): ByteArray =
        build(0x00AA, u16(if (resolution02m) 0x0001 else 0x0000))

    /** 查询距离分辨率（0x00AB）。官方样本：FDFCFBFA 0200 AB00 04030201 */
    fun readDistanceResolution(): ByteArray = build(0x00AB)

    /**
     * 设置光敏/辅助控制配置（0x00AD）。
     * 官方 Demo [CreateControlData.setPhotosensitive]：state(1B) + value(1B)，长度固定 0x0004。
     */
    fun setPhotosensitiveAux(state: Int, value: Int): ByteArray =
        build(0x00AD, byteArrayOf(state.toByte(), value.toByte()))

    /** 查询光敏/辅助控制配置（0x00AE） */
    fun readPhotosensitiveAux(): ByteArray = build(0x00AE)

    /** 开始底噪检测/灵敏度自动配置（0x000B，参数 = 检测时长秒；官方 Demo 2410C 固定 60s） */
    fun startNoiseDetection(durationSeconds: Int = 60): ByteArray {
        require(durationSeconds in 1..0xFFFF) { "检测时长须为 1~65535 秒" }
        return build(0x000B, u16(durationSeconds))
    }

    /**
     * 查询底噪检测状态（0x001B，返回 0=未进行/1=进行中/2=已完成）。
     * ⚠️ 来源为复刻清单（协议 V1.07）；官方 2410C Demo 未使用该命令，
     * 实现时优先按上报帧目标状态 0x04/0x05/0x06 判断进度，本命令作兜底轮询（待真机验证）。
     */
    fun queryNoiseDetectionStatus(): ByteArray = build(0x001B)

    /** OTA 升级使能/关闭（0x00C0）。官方 Demo 确认：使能 0x0001 / 关闭 0x0000（流程见 ADR-009） */
    fun setOtaEnabled(enabled: Boolean): ByteArray = build(0x00C0, u16(if (enabled) 0x0001 else 0x0000))

    /** 设置门限值（0x00C4）。官方 Demo [CreateControlData.setThreshold]：上限(1B)+0x00+下限(1B)+0x00 */
    fun setThreshold(upperLimit: Int, lowerLimit: Int): ByteArray =
        build(0x00C4, byteArrayOf(upperLimit.toByte(), 0x00, lowerLimit.toByte(), 0x00))

    /** 查询门限值（0x00C5） */
    fun readThreshold(): ByteArray = build(0x00C5)

    // ---- 帧拼装 ----

    /** 组帧：帧头 + 长度(2B 小端) + 命令字(2B 小端) + 命令值 + 帧尾 */
    private fun build(commandWord: Int, values: ByteArray = ByteArray(0)): ByteArray {
        require(commandWord in 0x0000..0xFFFF)
        val innerLen = 2 + values.size
        val frame = ByteArray(4 + 2 + innerLen + 4)
        ProtocolConst.CMD_HEAD.copyInto(frame, 0)
        frame[4] = (innerLen and 0xFF).toByte()
        frame[5] = ((innerLen shr 8) and 0xFF).toByte()
        frame[6] = (commandWord and 0xFF).toByte()
        frame[7] = ((commandWord shr 8) and 0xFF).toByte()
        values.copyInto(frame, 8)
        ProtocolConst.CMD_TAIL.copyInto(frame, 8 + values.size)
        return frame
    }

    private fun u16(value: Int): ByteArray =
        byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

    private fun u32(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )
}

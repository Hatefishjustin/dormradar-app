package com.dormradar.app.core.protocol

import com.dormradar.app.core.protocol.Frame.Ack
import com.dormradar.app.core.protocol.Frame.ReportBasic
import com.dormradar.app.core.protocol.Frame.ReportEngineering
import com.dormradar.app.core.protocol.Frame.Unknown

/**
 * 增量流式帧解码器（docs/03 §5 实现注意）。
 *
 * BLE 通知按 MTU 切片到达：一帧可能跨多个通知（半包），一个通知也可能含多帧（粘包），
 * 中间还可能混入脏字节。本类维护字节缓冲，循环扫描帧头，按
 * 「帧头匹配 + 长度合法 + 帧尾匹配」三重校验出帧；失败则逐字节后移继续扫。
 *
 * 用法：每收到一段 BLE 通知字节就调用 [feed]，返回本段新解出的帧列表（可能为空）。
 */
class FrameDecoder {

    private val buffer = ArrayList<Byte>(INITIAL_CAPACITY)

    /** 喂入新收到的字节，返回本次解出的完整帧（按到达顺序） */
    fun feed(bytes: ByteArray): List<Frame> {
        buffer.addAll(bytes.toList())
        val frames = ArrayList<Frame>()
        while (true) {
            val frame = tryExtractOne() ?: break
            frames.add(frame)
        }
        return frames
    }

    /** 丢弃全部缓冲（如重连后清状态，官方 Demo 连接成功时也清缓冲） */
    fun reset() = buffer.clear()

    /** 当前缓冲内未成帧的字节数（调试/测试用） */
    fun pendingBytes(): Int = buffer.size

    /**
     * 尝试从缓冲中提取一帧。
     * @return 解出的帧；缓冲不足（半包）或缓冲内已无可解析内容时返回 null（保留现场等下一包）。
     * 内部循环处理"帧头误判/坏帧"：丢 1 字节后继续向后扫描，保证坏帧不会卡住后续合法帧。
     */
    private fun tryExtractOne(): Frame? {
        while (true) {
            // 1. 在缓冲内找最早的帧头（命令帧头与上报帧头取更靠前者）
            val cmdIdx = indexOf(buffer, ProtocolConst.CMD_HEAD, 0)
            val rptIdx = indexOf(buffer, ProtocolConst.REPORT_HEAD, 0)
            if (cmdIdx < 0 && rptIdx < 0) {
                // 无任何帧头：只保留末尾 3 字节（可能是被切断的帧头前缀），其余丢弃
                keepTail(3)
                return null
            }
            val headStart = if (cmdIdx < 0) rptIdx else if (rptIdx < 0) cmdIdx else minOf(cmdIdx, rptIdx)
            if (headStart > 0) {
                // 帧头前的脏字节直接丢弃
                removeFirst(headStart)
            }
            val isCommand = headStart == cmdIdx

            // 2. 长度字段（帧头后 2 字节，小端） = 帧内数据长度
            if (buffer.size < HEAD_SIZE + LEN_SIZE) return null
            val innerLen = readLe16(HEAD_SIZE)
            if (innerLen <= 0 || innerLen > MAX_INNER_LEN) {
                // 长度非法：当前帧头是误判，丢 1 字节继续扫
                removeFirst(1)
                continue
            }

            // 3. 等齐整帧：帧头(4) + 长度(2) + 帧内数据 + 帧尾(4)
            val totalLen = HEAD_SIZE + LEN_SIZE + innerLen + TAIL_SIZE
            if (buffer.size < totalLen) return null

            // 4. 校验帧尾；不匹配则帧头为误判，丢 1 字节继续扫
            val expectedTail = if (isCommand) ProtocolConst.CMD_TAIL else ProtocolConst.REPORT_TAIL
            if (!matches(buffer, totalLen - TAIL_SIZE, expectedTail)) {
                removeFirst(1)
                continue
            }

            // 5. 解析帧内数据
            val innerStart = HEAD_SIZE + LEN_SIZE
            val inner = ByteArray(innerLen) { buffer[innerStart + it] }
            removeFirst(totalLen)

            return if (isCommand) parseCommandInner(inner) else parseReportInner(inner)
        }
    }

    // ---- 命令/ACK 域解析：帧内数据 = 命令字(2B 小端) + 返回值 ----

    private fun parseCommandInner(inner: ByteArray): Frame {
        if (inner.size < 2) return Unknown(Frame.Unknown.Kind.COMMAND, inner)
        val command = le16(inner, 0)
        val isAck = (command shr 8) == 0x01
        return if (isAck && inner.size >= 4) {
            val status = le16(inner, 2)
            Ack(ackCommand = command, status = status, payload = inner.copyOfRange(4, inner.size))
        } else {
            Unknown(Frame.Unknown.Kind.COMMAND, inner)
        }
    }

    // ---- 上报域解析：帧内数据 = 类型(1B) + 0xAA + 数据体 + 0x55 0x00 ----

    private fun parseReportInner(inner: ByteArray): Frame {
        val okStructure = inner.size >= 4 &&
            inner[1].toInt() and 0xFF == ProtocolConst.TARGET_HEAD &&
            inner[inner.size - 2].toInt() and 0xFF == ProtocolConst.TARGET_TAIL_1 &&
            inner[inner.size - 1].toInt() and 0xFF == ProtocolConst.TARGET_TAIL_2
        if (!okStructure) return Unknown(Frame.Unknown.Kind.REPORT, inner)

        val type = inner[0].toInt() and 0xFF
        // 数据体 = 去掉 类型(1) + 0xAA(1) + 0x55 0x00(2)
        val body = inner.copyOfRange(2, inner.size - 2)
        return when (type) {
            ProtocolConst.TYPE_BASIC -> parseBasicBody(body)
            ProtocolConst.TYPE_ENGINEERING -> parseEngineeringBody(body)
            else -> Unknown(Frame.Unknown.Kind.REPORT, inner)
        }
    }

    private fun parseBasicBody(body: ByteArray): Frame {
        if (body.size < ProtocolConst.BASIC_BODY_SIZE) return Unknown(Frame.Unknown.Kind.REPORT, body)
        return ReportBasic(
            targetState = body[0].toInt() and 0xFF,
            moveDistanceCm = le16(body, 1),
            moveEnergy = body[3].toInt() and 0xFF,
            staticDistanceCm = le16(body, 4),
            staticEnergy = body[6].toInt() and 0xFF,
            detectDistanceCm = le16(body, 7),
        )
    }

    private fun parseEngineeringBody(body: ByteArray): Frame {
        // 9 字节基本信息 + 最大运动门(1) + 最大静止门(1) + 运动能量[n] + 静止能量[n] + 容错字段
        val fixed = ProtocolConst.BASIC_BODY_SIZE + 2
        if (body.size < fixed) return Unknown(Frame.Unknown.Kind.REPORT, body)
        val basic = parseBasicBody(body.copyOfRange(0, ProtocolConst.BASIC_BODY_SIZE)) as? ReportBasic
            ?: return Unknown(Frame.Unknown.Kind.REPORT, body)

        val maxMoveGate = body[9].toInt() and 0xFF
        val maxStaticGate = body[10].toInt() and 0xFF
        val remaining = body.size - fixed
        // 门数组长度 = 最大距离门 + 1（协议语义）。但光照/OUT 等尾随字段与"多出的门"
        // 在字节数上存在歧义，故按「候选门数合法 + 尾随字段 ≤ 2 字节」校验，不符则退回对半分。
        val candidate = maxOf(maxMoveGate, maxStaticGate) + 1
        val gateCount = if (candidate in 1..remaining / 2 && remaining - candidate * 2 <= 2) {
            candidate
        } else {
            remaining / 2
        }
        val extraLen = remaining - gateCount * 2
        val moveGates = IntArray(gateCount) { body[fixed + it].toInt() and 0xFF }
        val staticGates = IntArray(gateCount) { body[fixed + gateCount + it].toInt() and 0xFF }
        val extra = if (extraLen > 0) body.copyOfRange(fixed + gateCount * 2, body.size) else ByteArray(0)

        return ReportEngineering(
            targetState = basic.targetState,
            moveDistanceCm = basic.moveDistanceCm,
            moveEnergy = basic.moveEnergy,
            staticDistanceCm = basic.staticDistanceCm,
            staticEnergy = basic.staticEnergy,
            detectDistanceCm = basic.detectDistanceCm,
            maxMoveGate = maxMoveGate,
            maxStaticGate = maxStaticGate,
            moveGateEnergies = moveGates,
            staticGateEnergies = staticGates,
            extra = extra,
        )
    }

    // ---- 字节缓冲工具 ----

    private fun readLe16(offset: Int): Int =
        (buffer[offset].toInt() and 0xFF) or ((buffer[offset + 1].toInt() and 0xFF) shl 8)

    private fun keepTail(n: Int) {
        if (buffer.size > n) removeFirst(buffer.size - n)
    }

    private fun removeFirst(count: Int) {
        // ArrayList 中部删除 O(n)；单帧 ≤ 200 字节，量级可接受
        for (i in 0 until count) buffer.removeAt(0)
    }

    companion object {
        private const val HEAD_SIZE = 4
        private const val LEN_SIZE = 2
        private const val TAIL_SIZE = 4

        /** 帧内数据长度上限（当前协议最大帧 ≈ 35B，留 10 倍余量防脏长度字段） */
        private const val MAX_INNER_LEN = 350
        private const val INITIAL_CAPACITY = 512

        /** 在 list 中从 from 开始查找 pattern 首次出现位置，找不到返回 -1 */
        private fun indexOf(list: List<Byte>, pattern: ByteArray, from: Int): Int {
            if (pattern.isEmpty() || list.size < pattern.size) return -1
            outer@ for (i in from..list.size - pattern.size) {
                for (j in pattern.indices) {
                    if (list[i + j] != pattern[j]) continue@outer
                }
                return i
            }
            return -1
        }

        private fun matches(list: List<Byte>, offset: Int, pattern: ByteArray): Boolean {
            if (offset < 0 || offset + pattern.size > list.size) return false
            for (j in pattern.indices) {
                if (list[offset + j] != pattern[j]) return false
            }
            return true
        }

        internal fun le16(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }
}

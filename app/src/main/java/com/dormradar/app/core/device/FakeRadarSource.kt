package com.dormradar.app.core.device

import com.dormradar.app.core.protocol.ProtocolConst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模拟雷达数据源（阶段 1 开发者选项，docs/06 阶段 1 任务 5）。
 *
 * 生成**真实协议格式的字节流**，走同一条 FrameDecoder 解析链路，
 * 既能在无硬件时开发/演示 UI，也能持续回归解析器。
 */
@Singleton
class FakeRadarSource @Inject constructor() {

    companion object {
        const val FAKE_MAC = "FA:KE:RD:AR:00:01"
        private const val INTERVAL_MS = 500L
    }

    /** 周期性输出模拟上报帧：有人/无人交替 + 距离/能量摆动，每 10 帧混入一帧工程模式 */
    fun frameFlow(): Flow<ByteArray> = flow {
        var tick = 0
        while (true) {
            val moving = (tick / 8) % 2 == 0   // 约 4 秒有人 / 4 秒无人
            val present = (tick / 48) % 2 == 1 // 每 24 秒出现一段"完全无人"
            val moveDist = if (moving) 120 + (tick % 32) * 6 else 0
            val moveEnergy = if (moving) 55 + (tick % 30) else 0
            val staticDist = if (!moving && present) 300 else 0
            val staticEnergy = if (!moving && present) 45 else 0
            val state = when {
                moving && present -> ProtocolConst.TARGET_BOTH
                moving -> ProtocolConst.TARGET_MOVING
                present -> ProtocolConst.TARGET_STATIC
                else -> ProtocolConst.TARGET_NONE
            }
            val detect = maxOf(moveDist, staticDist)
            val basic = buildBasicFrame(state, moveDist, moveEnergy, staticDist, staticEnergy, detect)
            emit(basic)
            if (tick % 10 == 0) {
                // 工程帧：基本信息 + 9 门能量（模拟能量沿距离衰减）
                val moveGates = IntArray(9) { i ->
                    if (moveDist > i * 75) ((moveEnergy - i * 6).coerceAtLeast(0)) else 0
                }
                val staticGates = IntArray(9) { i ->
                    if (staticDist > i * 75) ((staticEnergy - i * 6).coerceAtLeast(0)) else 0
                }
                emit(buildEngineeringFrame(state, moveDist, moveEnergy, staticDist, staticEnergy, detect, moveGates, staticGates))
            }
            tick++
            delay(INTERVAL_MS)
        }
    }.flowOn(Dispatchers.Default)

    /** 构造基础上报帧（帧格式与 FrameDecoderTest 的构造一致：docs/03 §3） */
    fun buildBasicFrame(
        targetState: Int,
        moveDistanceCm: Int,
        moveEnergy: Int,
        staticDistanceCm: Int,
        staticEnergy: Int,
        detectDistanceCm: Int,
    ): ByteArray {
        val body = byteArrayOf(
            targetState.toByte(),
            (moveDistanceCm and 0xFF).toByte(), ((moveDistanceCm shr 8) and 0xFF).toByte(),
            moveEnergy.toByte(),
            (staticDistanceCm and 0xFF).toByte(), ((staticDistanceCm shr 8) and 0xFF).toByte(),
            staticEnergy.toByte(),
            (detectDistanceCm and 0xFF).toByte(), ((detectDistanceCm shr 8) and 0xFF).toByte(),
        )
        return frame(ProtocolConst.TYPE_BASIC, body)
    }

    /** 构造工程模式上报帧（9 门能量 + 无额外字段） */
    fun buildEngineeringFrame(
        targetState: Int,
        moveDistanceCm: Int,
        moveEnergy: Int,
        staticDistanceCm: Int,
        staticEnergy: Int,
        detectDistanceCm: Int,
        moveGates: IntArray,
        staticGates: IntArray,
    ): ByteArray {
        val body = byteArrayOf(
            targetState.toByte(),
            (moveDistanceCm and 0xFF).toByte(), ((moveDistanceCm shr 8) and 0xFF).toByte(),
            moveEnergy.toByte(),
            (staticDistanceCm and 0xFF).toByte(), ((staticDistanceCm shr 8) and 0xFF).toByte(),
            staticEnergy.toByte(),
            (detectDistanceCm and 0xFF).toByte(), ((detectDistanceCm shr 8) and 0xFF).toByte(),
            0x08, 0x08, // 最大运动门 / 最大静止门
        ) + moveGates.map { it.toByte() }.toByteArray() + staticGates.map { it.toByte() }.toByteArray()
        return frame(ProtocolConst.TYPE_ENGINEERING, body)
    }

    private fun frame(type: Int, body: ByteArray): ByteArray {
        val inner = byteArrayOf(type.toByte(), ProtocolConst.TARGET_HEAD.toByte()) + body +
            byteArrayOf(ProtocolConst.TARGET_TAIL_1.toByte(), ProtocolConst.TARGET_TAIL_2.toByte())
        val len = inner.size
        return ProtocolConst.REPORT_HEAD +
            byteArrayOf((len and 0xFF).toByte(), ((len shr 8) and 0xFF).toByte()) +
            inner + ProtocolConst.REPORT_TAIL
    }
}

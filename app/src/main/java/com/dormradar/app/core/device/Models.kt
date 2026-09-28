package com.dormradar.app.core.device

/** 连接状态机（UI 直读）。docs/04 §3.1 的状态在 RadarConnection 内编排 */
sealed interface ConnState {
    data object Disconnected : ConnState
    data class Connecting(val mac: String) : ConnState
    data object Authenticating : ConnState
    data object Initializing : ConnState
    data class Ready(val mac: String) : ConnState
    data class Reconnecting(val mac: String, val attempt: Int) : ConnState
    data class Failed(val reason: String) : ConnState
}

/** 实时雷达状态（最近一次上报帧的语义化结果） */
data class RadarState(
    val targetState: Int,
    val moveDistanceCm: Int,
    val moveEnergy: Int,
    val staticDistanceCm: Int,
    val staticEnergy: Int,
    val detectDistanceCm: Int,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val hasTarget: Boolean get() = targetState in 1..3
}

/** 工程模式各门能量（最近一次工程帧） */
data class GateEnergies(
    val maxMoveGate: Int,
    val maxStaticGate: Int,
    val moveGateEnergies: IntArray,
    val staticGateEnergies: IntArray,
)

/** 连接成功后探测到的设备信息 */
data class DeviceInfo(
    val mac: String,
    val firmwareVersion: String?,
    val protocolVersion: Int?,
    val params: com.dormradar.app.core.protocol.DeviceParams?,
    val resolution02m: Boolean?,
    val isFake: Boolean = false,
)

/** 配置操作结果（UI 直读展示） */
sealed interface OpResult {
    data object Ok : OpResult
    data class Fail(val reason: String) : OpResult
}

/** 一次 RSSI 采样（连接质量页曲线） */
data class RssiSample(val timeMs: Long, val rssi: Int)

/** 收发统计（连接质量页；连接建立起算，断开清零） */
data class ConnQualityStats(
    val startedAtMs: Long = 0,
    val rxBytes: Long = 0,
    val rxFrames: Long = 0,
    val reportFrames: Long = 0,
    val ackFrames: Long = 0,
    val unknownFrames: Long = 0,
    val decodeErrors: Long = 0,
    val txCommands: Long = 0,
)

/** 重连/断连历史（App 存活期间累计，最多留 30 条） */
data class ReconnectRecord(val timeMs: Long, val text: String, val isError: Boolean)

package com.dormradar.app.core.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 距离诊断记录器（调试用途，用户反馈 2026-09-28"首页距离跳动/不准"取证工具）：
 * 每个上报帧记一行样本（含该帧到达时刻显示管线的输出快照），环形缓冲 12000 行，可导出 CSV。
 *
 * 数据流：RadarConnection.onBytes（每帧原始值）→ [onRawFrame]；
 * HomeViewModel（displayState 更新后）→ [noteDisplay] 提供最近的显示管线输出。
 *
 * 时序说明：onRawFrame 发生在该帧赋给 radarState 之前，noteDisplay 滞后一帧——
 * 行内 [DistanceDiagSample.displayDistanceCm] 语义 = 「该帧到达时刻显示管线的输出」，差一帧不影响诊断。
 *
 * relMs 语义（2026-09-28 修正，原实现有缺陷）：相对**缓冲中第一条样本**的毫秒数，天然单调递增。
 * 此前是"相对本次 start() 时刻"而 start() 不清缓冲 → 停-启一次 relMs 跳回 0，
 * 按它做差分算帧率会得到负数/巨值。现在 start/stop 任意多次，已记录的行都不会失真。
 */
@Singleton
class DistanceDiagRecorder @Inject constructor() {

    companion object {
        const val MAX_ENTRIES = 12_000
    }

    private val buffer = ArrayDeque<DistanceDiagSample>()
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _samples = MutableStateFlow<List<DistanceDiagSample>>(emptyList())
    val samples: StateFlow<List<DistanceDiagSample>> = _samples.asStateFlow()

    /** 缓冲中第一条样本的墙钟时间（relMs 起点）。只在缓冲从空变为非空时设定一次，
     *  **不随环形缓冲裁剪而移动** —— 否则裁剪发生时新行的 relMs 会相对旧行倒退数小时。 */
    private var firstWallClockMs = 0L

    /** 墙钟（单测可替换；生产使用系统时间） */
    internal var clock: () -> Long = System::currentTimeMillis

    /** 最近一次显示管线输出快照（由 HomeViewModel 回报） */
    @Volatile private var uiDisplayDistanceCm: Int = -1

    /** 最近一次输出是否来自"目标丢失保持"窗口（保留的是旧值） */
    @Volatile private var uiInHold: Boolean = false

    /** 当前引导阶段（见 [DiagPhase]）；只影响之后写入的样本 */
    @Volatile private var currentPhase: Int = DiagPhase.NONE

    /** 设置引导阶段（引导式两阶段流程由 ViewModel 指挥） */
    fun setPhase(phase: Int) {
        currentPhase = phase
    }

    /** 开始记录（不清空已有缓冲；relMs 对已有行保持单调） */
    fun start() {
        _recording.value = true
    }

    fun stop() {
        _recording.value = false
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            _samples.value = emptyList()
            firstWallClockMs = 0L
            currentPhase = DiagPhase.NONE
        }
    }

    /**
     * 显示管线输出回报（HomeViewModel 在 displayState 更新后调用）。
     * @param hasTarget 显示管线当前是否呈现"有人"（false → 行内距离记 -1）
     * @param inHold 本次输出是否来自「目标丢失保持 2 秒」窗口
     */
    fun noteDisplay(hasTarget: Boolean, detectDistanceCm: Int, inHold: Boolean) {
        uiDisplayDistanceCm = if (hasTarget) detectDistanceCm else -1
        uiInHold = inHold
    }

    /** 上报帧原始值入缓冲（仅 recording 时记录；basic 帧门数组传 null） */
    fun onRawFrame(
        frameType: String,
        targetState: Int,
        moveDistanceCm: Int,
        moveEnergy: Int,
        staticDistanceCm: Int,
        staticEnergy: Int,
        detectDistanceCm: Int,
        moveGateEnergies: IntArray?,
        staticGateEnergies: IntArray?,
    ) {
        if (!_recording.value) return
        val now = clock()
        synchronized(buffer) {
            if (buffer.isEmpty()) firstWallClockMs = now
            buffer.addLast(
                DistanceDiagSample(
                    wallClockMs = now,
                    relMs = now - firstWallClockMs,
                    frameType = frameType,
                    phase = currentPhase,
                    targetState = targetState,
                    moveDistanceCm = moveDistanceCm,
                    moveEnergy = moveEnergy,
                    staticDistanceCm = staticDistanceCm,
                    staticEnergy = staticEnergy,
                    detectDistanceCm = detectDistanceCm,
                    displayDistanceCm = uiDisplayDistanceCm,
                    inHold = uiInHold,
                    moveGateEnergies = moveGateEnergies?.copyOf(),
                    staticGateEnergies = staticGateEnergies?.copyOf(),
                ),
            )
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            _samples.value = buffer.toList()
        }
    }

    /** 导出/分析用快照 */
    fun snapshot(): List<DistanceDiagSample> = samples.value
}

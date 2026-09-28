package com.dormradar.app.core.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 距离诊断记录器（调试用途，用户反馈 2026-09-28"首页距离跳动/不准"取证工具）：
 * 每个上报帧记一行样本（含该时刻 UI 实际显示值快照），环形缓冲 12000 行，可导出 CSV。
 *
 * 数据流：RadarConnection.onBytes（每帧原始值）→ [onRawFrame]；
 * HomeViewModel（displayState 更新后）→ [noteDisplay] 提供最近 UI 显示值。
 * 时序说明：onRawFrame 发生在该帧赋给 radarState 之前，noteDisplay 略滞后一帧——
 * 行内 ui_display_cm 语义 = "该帧到达时刻 UI 实际显示的距离"，差一帧不影响抖动诊断。
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

    private var startedAtMs = 0L

    /** 最近一次 UI 显示值（HomeViewModel 每次显示状态更新后回报） */
    @Volatile
    private var uiDisplayDistanceCm: Int = -1

    /** 开始记录（重置相对时间起点；已有缓冲保留，clear 可清） */
    fun start() {
        startedAtMs = System.currentTimeMillis()
        _recording.value = true
    }

    fun stop() {
        _recording.value = false
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            _samples.value = emptyList()
        }
    }

    /** UI 显示值回报（无人时传 hasTarget=false，行内记 -1） */
    fun noteDisplay(hasTarget: Boolean, detectDistanceCm: Int) {
        uiDisplayDistanceCm = if (hasTarget) detectDistanceCm else -1
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
        val now = System.currentTimeMillis()
        val sample = DistanceDiagSample(
            wallClockMs = now,
            relMs = if (startedAtMs == 0L) 0 else now - startedAtMs,
            frameType = frameType,
            targetState = targetState,
            moveDistanceCm = moveDistanceCm,
            moveEnergy = moveEnergy,
            staticDistanceCm = staticDistanceCm,
            staticEnergy = staticEnergy,
            detectDistanceCm = detectDistanceCm,
            displayDistanceCm = uiDisplayDistanceCm,
            moveGateEnergies = moveGateEnergies?.copyOf(),
            staticGateEnergies = staticGateEnergies?.copyOf(),
        )
        synchronized(buffer) {
            buffer.addLast(sample)
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            _samples.value = buffer.toList()
        }
    }
}

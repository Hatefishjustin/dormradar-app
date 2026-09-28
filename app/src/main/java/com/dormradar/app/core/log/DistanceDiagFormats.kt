package com.dormradar.app.core.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一行距离诊断样本（对应一个上报帧 + 该帧到达时刻显示管线输出的快照）。
 *
 * 语义澄清（2026-09-28 审计修正，重要）：
 * [displayDistanceCm] 不是"平滑后的距离"——**首页对 detect 距离不做任何平滑**
 * （HomeViewModel.smoothDisplay 只覆盖 move/static 两个通道的距离与能量），
 * 所以该列通常与 [detectDistanceCm] 完全相等。两者出现差异的唯一情形是
 * [inHold] = true 的"目标丢失保持"行（设备已报无人，UI 按 TARGET_HOLD_MS 继续显示旧值）。
 * 分析时若看到大量 UI≠原始 的行而不区分保持窗口，会误判成"显示层在编造数据"。
 */
data class DistanceDiagSample(
    val wallClockMs: Long,
    val relMs: Long,
    val frameType: String,          // basic / engineering
    val targetState: Int,
    val moveDistanceCm: Int,
    val moveEnergy: Int,
    val staticDistanceCm: Int,
    val staticEnergy: Int,
    val detectDistanceCm: Int,
    /** 该帧到达时显示管线的输出（≈ 原始值；差异仅出现在保持窗口） */
    val displayDistanceCm: Int,
    /** 该行是否处于"目标丢失保持"窗口（true 时 displayDistanceCm 是有意保留的旧值） */
    val inHold: Boolean,
    val moveGateEnergies: IntArray?,// 仅工程帧；basic 为 null
    val staticGateEnergies: IntArray?,
) {
    /** 是否判定为有人（目标状态 1~3；底噪扩展状态 4~6 不算） */
    val hasTarget: Boolean get() = targetState in 1..3

    override fun equals(other: Any?): Boolean = other is DistanceDiagSample &&
        wallClockMs == other.wallClockMs && relMs == other.relMs && frameType == other.frameType &&
        targetState == other.targetState && moveDistanceCm == other.moveDistanceCm &&
        moveEnergy == other.moveEnergy && staticDistanceCm == other.staticDistanceCm &&
        staticEnergy == other.staticEnergy && detectDistanceCm == other.detectDistanceCm &&
        displayDistanceCm == other.displayDistanceCm && inHold == other.inHold &&
        moveGateEnergies?.contentEquals(other.moveGateEnergies) == true &&
        staticGateEnergies?.contentEquals(other.staticGateEnergies) == true

    override fun hashCode(): Int = listOf(
        wallClockMs, relMs, frameType, targetState, moveDistanceCm, moveEnergy,
        staticDistanceCm, staticEnergy, detectDistanceCm, displayDistanceCm, inHold,
        moveGateEnergies?.contentHashCode() ?: 0, staticGateEnergies?.contentHashCode() ?: 0,
    ).hashCode()
}

/**
 * 距离诊断 CSV 格式化（纯 Kotlin 可单测）。
 *
 * 列设计要点（2026-09-28 修正）：
 * - `epoch_ms` 是机器可解析的绝对时间（`time` 列只有时分秒，跨零点无法区分）；
 * - `has_target` 直接把状态换算成布尔，取样分析时不必再解码 0/1/2/3/4/5/6；
 * - `ui_hold` 标记保持窗口，避免把"有意保留的旧值"当成显示层变形；
 * - 门能量数组用分号分隔（避免与 CSV 逗号冲突）；basic 帧两列为空。
 */
object DistanceDiagFormats {

    const val HEADER =
        "epoch_ms,time,rel_ms,frame,target_state,has_target,move_dist_cm,move_energy," +
            "static_dist_cm,static_energy,detect_dist_cm,ui_detect_cm,ui_hold,move_gates,static_gates"

    /** CSV 列数（测试与预览共用，改列时只需改这里） */
    const val COLUMN_COUNT = 15

    fun timeText(wallClockMs: Long): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(wallClockMs))

    fun line(s: DistanceDiagSample): String = buildString {
        append(s.wallClockMs)
        append(',').append(timeText(s.wallClockMs))
        append(',').append(s.relMs)
        append(',').append(s.frameType)
        append(',').append(s.targetState)
        append(',').append(if (s.hasTarget) 1 else 0)
        append(',').append(s.moveDistanceCm)
        append(',').append(s.moveEnergy)
        append(',').append(s.staticDistanceCm)
        append(',').append(s.staticEnergy)
        append(',').append(s.detectDistanceCm)
        append(',').append(s.displayDistanceCm)
        append(',').append(if (s.inHold) 1 else 0)
        append(',').append(s.moveGateEnergies?.joinToString(";") ?: "")
        append(',').append(s.staticGateEnergies?.joinToString(";") ?: "")
    }

    fun csv(samples: List<DistanceDiagSample>): String = buildString {
        appendLine(HEADER)
        samples.forEach { appendLine(line(it)) }
    }
}

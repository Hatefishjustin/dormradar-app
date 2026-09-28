package com.dormradar.app.core.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一行距离诊断样本（对应一个上报帧 + 该时刻的 UI 显示值快照） */
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
    val displayDistanceCm: Int,     // UI 实际显示的检测距离（平滑后）；-1 = 无人/未显示
    val moveGateEnergies: IntArray?,// 仅工程帧；basic 为 null
    val staticGateEnergies: IntArray?,
) {
    override fun equals(other: Any?): Boolean = other is DistanceDiagSample &&
        wallClockMs == other.wallClockMs && relMs == other.relMs && frameType == other.frameType &&
        targetState == other.targetState && moveDistanceCm == other.moveDistanceCm &&
        moveEnergy == other.moveEnergy && staticDistanceCm == other.staticDistanceCm &&
        staticEnergy == other.staticEnergy && detectDistanceCm == other.detectDistanceCm &&
        displayDistanceCm == other.displayDistanceCm &&
        moveGateEnergies?.contentEquals(other.moveGateEnergies) == true &&
        staticGateEnergies?.contentEquals(other.staticGateEnergies) == true

    override fun hashCode(): Int = listOf(
        wallClockMs, relMs, frameType, targetState, moveDistanceCm, moveEnergy,
        staticDistanceCm, staticEnergy, detectDistanceCm, displayDistanceCm,
        moveGateEnergies?.contentHashCode() ?: 0, staticGateEnergies?.contentHashCode() ?: 0,
    ).hashCode()
}

/**
 * 距离诊断 CSV 格式化（纯 Kotlin 可单测）。
 * 门能量数组用分号分隔（避免与 CSV 逗号冲突）；basic 帧两列为空。
 */
object DistanceDiagFormats {

    const val HEADER =
        "time,rel_ms,frame,target_state,move_dist_cm,move_energy,static_dist_cm,static_energy," +
            "detect_dist_cm,ui_display_cm,move_gates,static_gates"

    fun timeText(wallClockMs: Long): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(wallClockMs))

    fun line(s: DistanceDiagSample): String = buildString {
        append(timeText(s.wallClockMs))
        append(',').append(s.relMs)
        append(',').append(s.frameType)
        append(',').append(s.targetState)
        append(',').append(s.moveDistanceCm)
        append(',').append(s.moveEnergy)
        append(',').append(s.staticDistanceCm)
        append(',').append(s.staticEnergy)
        append(',').append(s.detectDistanceCm)
        append(',').append(s.displayDistanceCm)
        append(',').append(s.moveGateEnergies?.joinToString(";") ?: "")
        append(',').append(s.staticGateEnergies?.joinToString(";") ?: "")
    }

    fun csv(samples: List<DistanceDiagSample>): String = buildString {
        appendLine(HEADER)
        samples.forEach { appendLine(line(it)) }
    }
}

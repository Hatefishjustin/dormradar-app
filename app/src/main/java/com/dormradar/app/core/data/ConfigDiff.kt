package com.dormradar.app.core.data

/** 一条差异行（字段名 / 模板值 / 当前设备值） */
data class ConfigDiffLine(val field: String, val templateValue: String, val currentValue: String)

/**
 * 配置差异对比（纯 Kotlin 可单测）：模板 vs 当前设备，逐项比较。
 * 输出人类可读的中文差异行；灵敏度表只列有差异的门。
 */
object ConfigDiff {

    fun diff(template: ConfigSnapshotData, current: ConfigSnapshotData): List<ConfigDiffLine> {
        val lines = mutableListOf<ConfigDiffLine>()
        if (template.maxMoveGate != current.maxMoveGate) {
            lines += ConfigDiffLine("最远运动门", "${template.maxMoveGate}", "${current.maxMoveGate}")
        }
        if (template.maxStaticGate != current.maxStaticGate) {
            lines += ConfigDiffLine("最远静止门", "${template.maxStaticGate}", "${current.maxStaticGate}")
        }
        if (template.unmannedSeconds != current.unmannedSeconds) {
            lines += ConfigDiffLine("无人持续时间", "${template.unmannedSeconds}s", "${current.unmannedSeconds}s")
        }
        if (template.resolution02m != null && current.resolution02m != null &&
            template.resolution02m != current.resolution02m
        ) {
            lines += ConfigDiffLine(
                "距离分辨率",
                if (template.resolution02m == true) "0.2m" else "0.75m",
                if (current.resolution02m == true) "0.2m" else "0.75m",
            )
        }
        val gateCount = minOf(template.motionSensitivities.size, current.motionSensitivities.size)
        (0 until gateCount).forEach { g ->
            if (template.motionSensitivities[g] != current.motionSensitivities[g] ||
                template.staticSensitivities[g] != current.staticSensitivities[g]
            ) {
                lines += ConfigDiffLine(
                    "门 $g",
                    "运动 ${template.motionSensitivities[g]} / 静止 ${template.staticSensitivities[g]}",
                    "运动 ${current.motionSensitivities[g]} / 静止 ${current.staticSensitivities[g]}",
                )
            }
        }
        return lines
    }
}

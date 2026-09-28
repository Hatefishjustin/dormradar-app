package com.dormradar.app.core.deploy

/**
 * 「无人基线定参」——把各门灵敏度压到"刚好高于环境浮动"的位置（纯 Kotlin 可单测）。
 *
 * 与既有两条定参路径的分工（重要，别混用）：
 * | 路径 | 采样 | 阈值算法 | 定位 |
 * |---|---|---|---|
 * | 设备侧底噪检测（0x000B） | 设备自己测 | 设备内建建议值（实测偏高、不够灵敏） | 给一个基准 |
 * | 部署向导 [DeployAdvisor] | 底噪 + 有人走动两段 | (底噪上界 + 活动峰值) / 2 | 首次安装、要兼顾灵敏度与误报 |
 * | **本类** | **只需无人一段** | **该门浮动上界 + 小边距** | 压阈值提灵敏度，或矫治底噪检测给高了 |
 *
 * 语义前提：设备判定规则是「能量 > 灵敏度 → 有人」。所以阈值必须**严格高于**无人时的
 * 能量浮动上界，同时越贴近上界越灵敏。边距就是"高一点点"的那个量。
 *
 * 上界口径（[Basis]）：
 * - [Basis.P95]（默认）：取 95 分位，代表"**正常**浮动"的上界，不会被偶发单点尖峰抬高
 *   ——对应"不要高太多，不然又不灵敏了"；
 * - [Basis.MAX]：取绝对最高值，更保守（宁可钝一点也不误报）。
 *
 * 两条不变量（都有单测钉住）：
 * 1. **不解除用户已屏蔽的门**：当前值 ≥ 100 的门一律保持 100。否则会把用户特意做的
 *    近距屏蔽/方向屏蔽（`RadarConnection.setMinGate` 的产物）一起打开，属回归；
 * 2. **超出最远门的门保持屏蔽**：门号 > maxMoveGate / maxStaticGate 的一律 100。
 */
object NoiseBaselineAdvisor {

    /** "稍微高一点点"的默认边距 */
    const val DEFAULT_MARGIN = 5

    /** 可选边距档位 */
    val MARGIN_OPTIONS = listOf(0, 3, 5, 10)

    /** 阈值下限：再低就等于把噪声当目标 */
    private const val MIN_THRESHOLD = 5

    /** 上界口径 */
    enum class Basis(val label: String) {
        P95("典型上界（95 分位，推荐）"),
        MAX("绝对最高值（更保守）"),
    }

    /** 单门在无人阶段的浮动画像 */
    data class GateProfile(
        val gate: Int,
        val samples: Int,
        val motionMin: Int,
        val motionMax: Int,
        /** 该门的浮动上界（按 [Basis] 取 p95 或 max） */
        val motionCeiling: Int,
        val staticMin: Int,
        val staticMax: Int,
        val staticCeiling: Int,
    )

    /** 建议结果（可直接喂给 RadarConnection.applySensitivityTable） */
    data class Suggestion(
        val motionSensitivities: IntArray,
        val staticSensitivities: IntArray,
        val profiles: List<GateProfile>,
        /** 因用户此前已屏蔽而保持 100 的门 */
        val shieldedGates: List<Int>,
        /** 因浮动上界过高、无法定出可用阈值而改为屏蔽的门 */
        val unusableGates: List<Int>,
        /** 每门依据文案（给 UI 展示） */
        val rationale: List<String>,
        val margin: Int,
        val basis: Basis,
    ) {
        override fun equals(other: Any?): Boolean = other is Suggestion &&
            margin == other.margin && basis == other.basis &&
            motionSensitivities.contentEquals(other.motionSensitivities) &&
            staticSensitivities.contentEquals(other.staticSensitivities) &&
            shieldedGates == other.shieldedGates && unusableGates == other.unusableGates

        override fun hashCode(): Int = listOf(
            margin, basis,
            motionSensitivities.contentHashCode(), staticSensitivities.contentHashCode(),
            shieldedGates, unusableGates,
        ).hashCode()
    }

    /**
     * @param motionSeries 每门的运动能量采样序列（下标 = 门号）；空数组表示该门没采到数据
     * @param staticSeries 每门的静止能量采样序列
     * @param currentMotion 设备当前运动灵敏度（用于识别"用户已屏蔽的门"）
     * @param currentStatic 设备当前静止灵敏度
     * @param maxMoveGate / maxStaticGate 当前最远门；超出的门保持屏蔽
     */
    fun suggest(
        motionSeries: List<IntArray>,
        staticSeries: List<IntArray>,
        currentMotion: IntArray,
        currentStatic: IntArray,
        maxMoveGate: Int,
        maxStaticGate: Int,
        margin: Int = DEFAULT_MARGIN,
        basis: Basis = Basis.P95,
    ): Suggestion {
        val gateCount = minOf(
            motionSeries.size, staticSeries.size, currentMotion.size, currentStatic.size,
        )
        val motion = IntArray(gateCount)
        val static = IntArray(gateCount)
        val profiles = ArrayList<GateProfile>(gateCount)
        val shielded = mutableListOf<Int>()
        val unusable = mutableListOf<Int>()
        val rationale = mutableListOf<String>()

        for (g in 0 until gateCount) {
            val mSeries = motionSeries[g]
            val sSeries = staticSeries[g]
            val mCeiling = ceiling(mSeries, basis)
            val sCeiling = ceiling(sSeries, basis)
            profiles += GateProfile(
                gate = g, samples = maxOf(mSeries.size, sSeries.size),
                motionMin = mSeries.minOrNull() ?: 0, motionMax = mSeries.maxOrNull() ?: 0,
                motionCeiling = mCeiling,
                staticMin = sSeries.minOrNull() ?: 0, staticMax = sSeries.maxOrNull() ?: 0,
                staticCeiling = sCeiling,
            )

            // 不变量 1：用户已屏蔽的门保持屏蔽
            val motionShielded = currentMotion[g] >= 100
            val staticShielded = currentStatic[g] >= 100
            // 不变量 2：超出最远门的门保持屏蔽
            val motionOutOfRange = g > maxMoveGate
            val staticOutOfRange = g > maxStaticGate

            motion[g] = decide(
                ceiling = mCeiling, noData = mSeries.isEmpty(), keepShielded = motionShielded || motionOutOfRange,
                current = currentMotion[g], margin = margin,
            )
            static[g] = decide(
                ceiling = sCeiling, noData = sSeries.isEmpty(), keepShielded = staticShielded || staticOutOfRange,
                current = currentStatic[g], margin = margin,
            )

            if (motionShielded || staticShielded) shielded += g
            if (motion[g] >= 100 && !(motionShielded || motionOutOfRange)) unusable += g

            rationale += buildRationale(g, mSeries, sSeries, mCeiling, sCeiling, motion[g], static[g], motionShielded || motionOutOfRange, staticShielded || staticOutOfRange, margin)
        }

        return Suggestion(
            motionSensitivities = motion,
            staticSensitivities = static,
            profiles = profiles,
            shieldedGates = shielded,
            unusableGates = unusable,
            rationale = rationale,
            margin = margin,
            basis = basis,
        )
    }

    /** 单门单通道的决策；返回最终灵敏度（0~100） */
    private fun decide(
        ceiling: Int,
        noData: Boolean,
        keepShielded: Boolean,
        current: Int,
        margin: Int,
    ): Int = when {
        keepShielded -> 100
        // 没采到数据 → 什么都不改，保持设备当前值（0 采样不等于底噪为 0）
        noData -> current
        ceiling + margin > 100 -> 100
        else -> (ceiling + margin).coerceAtLeast(MIN_THRESHOLD)
    }

    private fun buildRationale(
        gate: Int,
        mSeries: IntArray,
        sSeries: IntArray,
        mCeiling: Int,
        sCeiling: Int,
        motion: Int,
        static: Int,
        motionLocked: Boolean,
        staticLocked: Boolean,
        margin: Int,
    ): String = buildString {
        append("门 ").append(gate).append("：")
        if (mSeries.isEmpty() && sSeries.isEmpty()) {
            append("本次未采到数据，保持当前值")
            return@buildString
        }
        fun part(name: String, series: IntArray, ceiling: Int, result: Int, locked: Boolean) {
            if (locked) {
                append(name).append(" 保持 100（已屏蔽）；")
                return
            }
            if (series.isEmpty()) {
                append(name).append(" 无数据保持原值；")
                return
            }
            append(name).append(" 浮动 ").append(series.min()).append('~').append(series.max())
            append("（上界 ").append(ceiling).append("）")
            append(if (result >= 100) " → 太高，改为屏蔽" else " → 建议 " + result)
            append("（+").append(margin).append("）；")
        }
        part("运动", mSeries, mCeiling, motion, motionLocked)
        part("静止", sSeries, sCeiling, static, staticLocked)
    }

    /** 浮动上界：p95（最近秩）或绝对最高 */
    private fun ceiling(series: IntArray, basis: Basis): Int = when {
        series.isEmpty() -> 0
        basis == Basis.MAX -> series.max()
        else -> {
            val sorted = series.sortedArray()
            val idx = ((sorted.size - 1) * 0.95).toInt().coerceIn(0, sorted.size - 1)
            sorted[idx]
        }
    }
}

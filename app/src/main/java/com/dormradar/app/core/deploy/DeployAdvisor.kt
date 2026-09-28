package com.dormradar.app.core.deploy

/**
 * 采样期间单个距离门的能量统计。
 * @param avgEnergy 平均能量（底噪基线用）
 * @param maxEnergy 最大能量（走动峰值用）
 */
data class GateSample(val avgEnergy: Int, val maxEnergy: Int)

/** 一次采样：各门能量统计（下标 = 门号，长度 = 设备门数） */
data class EnergySamples(val gates: List<GateSample>) {
    val gateCount: Int get() = gates.size
}

/** 自动建议参数（可直接喂给 RadarConnection.applyParams / applySensitivityTable） */
data class DeploySuggestion(
    val maxMoveGate: Int,
    val maxStaticGate: Int,
    val motionSensitivities: IntArray,
    val staticSensitivities: IntArray,
    /** 无区分度而被屏蔽（写 100）的门号 */
    val maskedGates: List<Int>,
    /** 每门建议依据（给 UI 展示："底噪 12 → 峰值 58 → 阈值 35"） */
    val rationale: List<String>,
) {
    override fun equals(other: Any?): Boolean = other is DeploySuggestion &&
        maxMoveGate == other.maxMoveGate && maxStaticGate == other.maxStaticGate &&
        motionSensitivities.contentEquals(other.motionSensitivities) &&
        staticSensitivities.contentEquals(other.staticSensitivities)

    override fun hashCode(): Int = listOf(
        maxMoveGate, maxStaticGate,
        motionSensitivities.contentHashCode(), staticSensitivities.contentHashCode(),
    ).hashCode()
}

/**
 * 部署向导自动建议（docs/06 阶段 4 任务 2，纯 Kotlin 可单测）。
 *
 * 协议语义：能量 > 灵敏度 → 判有人；所以阈值必须落在 [底噪上限, 活动峰值) 之间——
 * 低于底噪会误报，不低于活动峰值就永远不触发。
 *
 * 算法：对每个门 g，底噪上界 N = 底噪采样的 max（清场采样是底噪的唯一权威来源——
 * 活动期均值混有人体能量，不能当底噪）；活动峰值 P = 活动采样的 max。
 * 若 P < N + [CONTRAST_MARGIN] → 该门无区分度 → 屏蔽（100）；
 * 否则阈值 = (N + P) / 2，clamp 到 5..99。最远门 = 最后一个有区分度的门。
 */
object DeployAdvisor {

    /** 判定"有区分度"所需的最小峰-底差 */
    const val CONTRAST_MARGIN = 10
    private const val THRESHOLD_MIN = 5
    private const val THRESHOLD_MAX = 99

    /**
     * @param noise 底噪采样（清场状态下采）
     * @param activity 有人走动采样（门口/走廊正常活动）
     * @return 无任何门有区分度时返回 null（提示重新采样）
     */
    fun suggest(noise: EnergySamples, activity: EnergySamples): DeploySuggestion? {
        val gateCount = minOf(noise.gateCount, activity.gateCount)
        if (gateCount == 0) return null

        val motion = IntArray(gateCount) { THRESHOLD_MAX }
        val static = IntArray(gateCount) { THRESHOLD_MAX }
        val masked = mutableListOf<Int>()
        val rationale = mutableListOf<String>()
        var lastUsefulMotion = -1
        var lastUsefulStatic = -1

        for (g in 0 until gateCount) {
            val n = noise.gates[g]
            val a = activity.gates[g]
            // 底噪上界：只信清场采样的最大值（活动期均值混有人体能量，不能当底噪）
            val noiseCeiling = n.maxEnergy
            val peakMotion = a.maxEnergy
            val peakStatic = a.maxEnergy // 同一次活动采样的峰值（走动同时抬高运动/静止通道）
            val usefulMotion = peakMotion >= noiseCeiling + CONTRAST_MARGIN
            val usefulStatic = peakStatic >= noiseCeiling + CONTRAST_MARGIN
            if (usefulMotion) {
                motion[g] = ((noiseCeiling + peakMotion) / 2).coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)
                lastUsefulMotion = g
            }
            if (usefulStatic) {
                static[g] = ((noiseCeiling + peakStatic) / 2).coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)
                lastUsefulStatic = g
            }
            if (!usefulMotion && !usefulStatic) {
                masked += g
                rationale.add("门 $g：底噪 $noiseCeiling / 峰值 $peakMotion，无区分度 → 屏蔽")
            } else {
                rationale.add(
                    "门 $g：底噪 $noiseCeiling → 峰值运动 $peakMotion / 静止 $peakStatic → " +
                        "阈值 ${motion[g]} / ${static[g]}",
                )
            }
        }

        if (lastUsefulMotion < 0 && lastUsefulStatic < 0) return null

        // 建议门号：最后一个有区分度的门；无区分度一侧保持 2（协议最小有效门）并全部屏蔽
        val maxMoveGate = if (lastUsefulMotion >= 0) lastUsefulMotion else 2
        val maxStaticGate = if (lastUsefulStatic >= 0) lastUsefulStatic else 2

        // 超出建议门号的门一律屏蔽（表完整性：写满全表，靠 maxGate 与 100 双保险）
        for (g in (maxMoveGate + 1) until gateCount) motion[g] = 100
        for (g in (maxStaticGate + 1) until gateCount) static[g] = 100

        return DeploySuggestion(maxMoveGate, maxStaticGate, motion, static, masked, rationale)
    }
}

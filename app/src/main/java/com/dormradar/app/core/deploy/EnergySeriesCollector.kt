package com.dormradar.app.core.deploy

/**
 * 无人基线采样的数据收集器（纯 Kotlin 可单测）。
 *
 * 与部署向导只留 avg/max 不同，这里**保留每门的完整能量序列**——因为"正常浮动上界"
 * 要用分位数（p95）而不是单点最大值来刻画，只看 max 会被一个偶发尖峰抬高阈值，
 * 结果就是用户说的"高太多、又不够灵敏了"。
 *
 * 同时承担一个安全职责：**采样期间若设备正在跑底噪检测（0x000B 进行中，目标状态 0x04）**，
 * 置位 [noiseDetectionRunning]。原因是设备侧检测结束时会自动写入它自己的建议灵敏度表，
 * 会把我们刚写入的阈值覆盖掉；而且检测期间的上报帧不是正常目标数据，采到的样本没有意义。
 */
class EnergySeriesCollector(gateCount: Int) {

    private val motion = List(gateCount) { ArrayList<Int>() }
    private val static = List(gateCount) { ArrayList<Int>() }

    @Volatile
    var noiseDetectionRunning: Boolean = false
        private set

    /** 每次收到工程模式各门能量时调用 */
    fun onGateEnergies(moveGateEnergies: IntArray, staticGateEnergies: IntArray) {
        for (g in 0 until motion.size) {
            motion[g].add(moveGateEnergies.getOrElse(g) { 0 })
            static[g].add(staticGateEnergies.getOrElse(g) { 0 })
        }
    }

    /** 每次收到目标状态时调用（用于识别底噪检测进行中） */
    fun onTargetState(targetState: Int) {
        if (targetState == TARGET_NOISE_RUNNING) noiseDetectionRunning = true
    }

    /** 已采到的样本数（每门的样本数相同） */
    val sampleCount: Int get() = motion.firstOrNull()?.size ?: 0

    fun motionSeries(): List<IntArray> = motion.map { it.toIntArray() }

    fun staticSeries(): List<IntArray> = static.map { it.toIntArray() }

    companion object {
        /** 底噪检测进行中的目标状态（docs/03 §3.2） */
        const val TARGET_NOISE_RUNNING = 0x04
    }
}

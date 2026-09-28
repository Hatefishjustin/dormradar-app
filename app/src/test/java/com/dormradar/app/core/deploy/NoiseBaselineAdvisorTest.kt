package com.dormradar.app.core.deploy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 无人基线定参（NoiseBaselineAdvisor）+ 采样收集器（EnergySeriesCollector）。
 *
 * 重点钉住两条不变量与"边距"语义——它们直接决定用户手机上会不会出现
 * "灵敏度反而变钝"或"把屏蔽好的门又打开"这类回归。
 */
class NoiseBaselineAdvisorTest {

    private fun ramp(from: Int, to: Int) = IntArray(to - from + 1) { from + it }

    /** 默认场景：9 门，运动浮动 4~12 / 静止浮动 2~8，设备当前值均为 40（未屏蔽） */
    private fun suggest(
        motion: IntArray = ramp(4, 12),
        static: IntArray = ramp(2, 8),
        currentMotion: IntArray = IntArray(9) { 40 },
        currentStatic: IntArray = IntArray(9) { 40 },
        maxMoveGate: Int = 8,
        maxStaticGate: Int = 8,
        margin: Int = NoiseBaselineAdvisor.DEFAULT_MARGIN,
        basis: NoiseBaselineAdvisor.Basis = NoiseBaselineAdvisor.Basis.P95,
    ): NoiseBaselineAdvisor.Suggestion {
        val gateCount = currentMotion.size
        return NoiseBaselineAdvisor.suggest(
            motionSeries = List(gateCount) { motion.copyOf() },
            staticSeries = List(gateCount) { static.copyOf() },
            currentMotion = currentMotion,
            currentStatic = currentStatic,
            maxMoveGate = maxMoveGate,
            maxStaticGate = maxStaticGate,
            margin = margin,
            basis = basis,
        )
    }

    // ---- 核心：把阈值压到"刚高于浮动上界" ----

    @Test
    fun `P95 口径下阈值等于典型上界加边距`() {
        // ramp(4,12) 共 9 个值，p95（最近秩 idx=(9-1)*0.95=7）取 sorted[7]=11
        // ramp(2,8)  共 7 个值，idx=(7-1)*0.95=5 → sorted[5]=7
        val s = suggest(margin = 5, basis = NoiseBaselineAdvisor.Basis.P95)
        assertEquals(16, s.motionSensitivities[0])
        assertEquals(12, s.staticSensitivities[0])
        assertEquals(11, s.profiles[0].motionCeiling)
        assertEquals(7, s.profiles[0].staticCeiling)
    }

    @Test
    fun `MAX 口径更保守_取绝对最高值`() {
        val s = suggest(margin = 5, basis = NoiseBaselineAdvisor.Basis.MAX)
        assertEquals(17, s.motionSensitivities[0]) // 12 + 5
        assertEquals(13, s.staticSensitivities[0]) // 8 + 5
    }

    @Test
    fun `边距为 0 时阈值就是上界`() {
        val s = suggest(margin = 0, basis = NoiseBaselineAdvisor.Basis.MAX)
        assertEquals(12, s.motionSensitivities[0])
        assertEquals(8, s.staticSensitivities[0])
    }

    @Test
    fun `阈值有下限_底噪全零也不会置成 0`() {
        val s = suggest(
            motion = IntArray(5) { 0 }, static = IntArray(5) { 0 }, margin = 0,
        )
        assertEquals(5, s.motionSensitivities[0])
        assertEquals(5, s.staticSensitivities[0])
    }

    @Test
    fun `单点尖峰不会抬高 P95 口径的结果`() {
        // 45 个样本里只有一个 60 的尖峰，其余 3~10：P95 仍贴近正常范围，MAX 才会被抬到 60
        val withSpike = IntArray(45) { if (it == 20) 60 else 3 + it % 8 }
        val p95 = suggest(motion = withSpike, basis = NoiseBaselineAdvisor.Basis.P95)
        val max = suggest(motion = withSpike, basis = NoiseBaselineAdvisor.Basis.MAX)
        assertTrue("P95 口径应远离尖峰", p95.motionSensitivities[0] < 30)
        assertEquals(65, max.motionSensitivities[0]) // 60 + 5
    }

    // ---- 不变量 1：不解除用户已屏蔽的门 ----

    @Test
    fun `用户已屏蔽的门保持 100`() {
        val currentMotion = IntArray(9) { 40 }.also { it[3] = 100 }
        val s = suggest(currentMotion = currentMotion)
        assertEquals(100, s.motionSensitivities[3])
        assertTrue(s.shieldedGates.contains(3))
        // 其余门照常定参
        assertEquals(16, s.motionSensitivities[0])
    }

    @Test
    fun `静止被屏蔽同样保留`() {
        val currentStatic = IntArray(9) { 40 }.also { it[1] = 100 }
        val s = suggest(currentStatic = currentStatic)
        assertEquals(100, s.staticSensitivities[1])
        assertTrue(s.shieldedGates.contains(1))
    }

    // ---- 不变量 2：超出最远门的门保持屏蔽 ----

    @Test
    fun `超出最远门的门保持 100`() {
        val s = suggest(maxMoveGate = 5, maxStaticGate = 6)
        for (g in 6..8) assertEquals("运动门 $g", 100, s.motionSensitivities[g])
        for (g in 7..8) assertEquals("静止门 $g", 100, s.staticSensitivities[g])
        assertEquals(16, s.motionSensitivities[5])
        assertEquals(12, s.staticSensitivities[6])
    }

    // ---- 无数据 / 不可用 ----

    @Test
    fun `该门无采样时保持设备当前值`() {
        val motion = List(9) { ramp(4, 12) }.toMutableList().also { it[5] = IntArray(0) }
        val static = List(9) { ramp(2, 8) }
        val s = NoiseBaselineAdvisor.suggest(
            motionSeries = motion, staticSeries = static,
            currentMotion = IntArray(9) { 40 }, currentStatic = IntArray(9) { 40 },
            maxMoveGate = 8, maxStaticGate = 8,
        )
        assertEquals(40, s.motionSensitivities[5])   // 保持原值，不是 5
        assertEquals(12, s.staticSensitivities[5])   // 静止有数据，照常定参
    }

    @Test
    fun `浮动上界过高无法定参时改为屏蔽并登记`() {
        val s = suggest(motion = IntArray(9) { 98 }, margin = 5)
        assertEquals(100, s.motionSensitivities[0])
        assertTrue(s.unusableGates.contains(0))
        assertTrue(s.rationale[0].contains("太高"))
    }

    // ---- 依据文案 ----

    @Test
    fun `每门都给出可读依据`() {
        val s = suggest()
        assertEquals(9, s.rationale.size)
        assertTrue(s.rationale[0].contains("门 0"))
        assertTrue(s.rationale[0].contains("浮动 4~12"))
        assertTrue(s.rationale[0].contains("建议 16"))
    }

    // ---- 采样收集器 ----

    @Test
    fun `收集器保留每门完整序列而非只留最大`() {
        val c = EnergySeriesCollector(3)
        c.onGateEnergies(intArrayOf(1, 2, 3), intArrayOf(4, 5, 6))
        c.onGateEnergies(intArrayOf(7, 8, 9), intArrayOf(10, 11, 12))
        assertEquals(2, c.sampleCount)
        assertEquals(listOf(1, 7), c.motionSeries()[0].toList())
        assertEquals(listOf(6, 12), c.staticSeries()[2].toList())
    }

    @Test
    fun `缺门的数据按 0 补齐不越界`() {
        val c = EnergySeriesCollector(3)
        c.onGateEnergies(intArrayOf(5), intArrayOf(6)) // 只报了一门
        assertEquals(0, c.motionSeries()[2][0])
        assertEquals(0, c.staticSeries()[1][0])
    }

    @Test
    fun `采样期间发现底噪检测进行中会被标记`() {
        val c = EnergySeriesCollector(2)
        assertFalse(c.noiseDetectionRunning)
        c.onTargetState(0)                       // 无人，正常
        assertFalse(c.noiseDetectionRunning)
        c.onTargetState(EnergySeriesCollector.TARGET_NOISE_RUNNING)
        assertTrue(c.noiseDetectionRunning)
    }
}

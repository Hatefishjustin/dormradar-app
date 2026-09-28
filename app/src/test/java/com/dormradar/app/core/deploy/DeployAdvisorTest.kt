package com.dormradar.app.core.deploy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 部署向导自动建议算法（纯逻辑） */
class DeployAdvisorTest {

    private fun sample(vararg pairs: Pair<Int, Int>) = EnergySamples(pairs.map { GateSample(it.first, it.second) })

    @Test
    fun `有区分度时阈值取底噪与峰值中间`() {
        // 门 0：底噪 max 20 / 活动 max 60 → 阈值 (20+60)/2 = 40
        val s = DeployAdvisor.suggest(
            noise = sample(20 to 20),
            activity = sample(25 to 60),
        )
        assertNotNull(s)
        assertEquals(0, s!!.maxMoveGate)
        assertEquals(40, s.motionSensitivities[0])
        assertEquals(40, s.staticSensitivities[0])
        assertTrue(s.maskedGates.isEmpty())
    }

    @Test
    fun `无区分度的门被屏蔽`() {
        // 门 1：活动峰值 25 < 底噪 20 + 10（余量）→ 屏蔽
        val s = DeployAdvisor.suggest(
            noise = sample(20 to 20, 20 to 20),
            activity = sample(25 to 60, 22 to 25),
        )
        assertNotNull(s)
        assertEquals(listOf(1), s!!.maskedGates)
        assertEquals(100, s.motionSensitivities[1])
        assertEquals(0, s.maxMoveGate) // 最后一个有用门 = 门 0（门 1 已屏蔽）
    }

    @Test
    fun `全无区分度时返回 null`() {
        val s = DeployAdvisor.suggest(
            noise = sample(50 to 50),
            activity = sample(52 to 55),
        )
        assertNull(s)
    }

    @Test
    fun `超出建议门号的门一律屏蔽`() {
        val s = DeployAdvisor.suggest(
            noise = sample(10 to 10, 10 to 10, 10 to 10, 10 to 10),
            activity = sample(15 to 50, 12 to 12, 12 to 12, 12 to 12),
        )
        assertNotNull(s)
        assertEquals(0, s!!.maxMoveGate)
        assertEquals(100, s.motionSensitivities[1])
        assertEquals(100, s.staticSensitivities[3])
    }

    @Test
    fun `峰值恰达到底噪加余量算有区分度_差一点就屏蔽`() {
        // 门 0：30 ≥ 20+10（恰好达到）→ 阈值 (20+30)/2 = 25
        // 门 1：29 < 30 → 屏蔽
        val s = DeployAdvisor.suggest(
            noise = sample(20 to 20, 20 to 20),
            activity = sample(25 to 30, 25 to 29),
        )
        assertNotNull(s)
        assertEquals(25, s!!.motionSensitivities[0])
        assertEquals(listOf(1), s.maskedGates)
    }

    @Test
    fun `活动期均值不参与底噪上界`() {
        // 底噪 max 20 是唯一权威：阈值 (20+70)/2 = 45，不受活动 avg 30 影响
        val s = DeployAdvisor.suggest(
            noise = sample(18 to 20),
            activity = sample(30 to 70),
        )
        assertEquals(45, s!!.motionSensitivities[0])
    }

    @Test
    fun `每门都有依据说明`() {
        val s = DeployAdvisor.suggest(
            noise = sample(20 to 20, 20 to 20),
            activity = sample(25 to 60, 22 to 25),
        )
        assertEquals(2, s!!.rationale.size)
        assertTrue(s.rationale[0].contains("门 0"))
        assertTrue(s.rationale[1].contains("屏蔽"))
    }
}

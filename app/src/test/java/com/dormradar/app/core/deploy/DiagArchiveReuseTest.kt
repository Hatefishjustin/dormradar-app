package com.dormradar.app.core.deploy

import com.dormradar.app.core.log.DiagPhase
import com.dormradar.app.core.log.DistanceDiagSample
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 采样档案复用（2026-10-07「测一次处处用」）：
 * 两步引导存档 → 无人基线定参（perGateSeries）/ 部署向导（toEnergySamples）的输入形态转换。
 * 关键语义：toEnergySamples 与部署向导实时采样同口径（每门 max(运动,静止) 的 avg/max）。
 */
class DiagArchiveReuseTest {

    private fun frame(
        phase: Int,
        moveGates: IntArray?,
        staticGates: IntArray? = moveGates,
    ) = DistanceDiagSample(
        wallClockMs = 1_000L,
        relMs = 0L,
        frameType = if (moveGates == null) "basic" else "engineering",
        phase = phase,
        targetState = 0,
        moveDistanceCm = 0,
        moveEnergy = 0,
        staticDistanceCm = 0,
        staticEnergy = 0,
        detectDistanceCm = 0,
        displayDistanceCm = 0,
        inHold = false,
        moveGateEnergies = moveGates,
        staticGateEnergies = staticGates,
    )

    @Test
    fun `按阶段过滤_只留工程帧`() {
        val samples = listOf(
            frame(DiagPhase.NO_PERSON, null),          // basic 帧 → 排除
            frame(DiagPhase.NONE, intArrayOf(1)),      // 未分段 → 排除
            frame(DiagPhase.PERSON, intArrayOf(1)),    // 有人段不进无人集合
            frame(DiagPhase.NO_PERSON, intArrayOf(2, 3)),
        )
        assertEquals(
            listOf(2, 3),
            DiagArchiveReuse.noPersonEngineering(samples).single().moveGateEnergies!!.toList(),
        )
        assertEquals(1, DiagArchiveReuse.personEngineering(samples).size)
    }

    @Test
    fun `perGateSeries 按门聚合各帧`() {
        val frames = listOf(
            frame(DiagPhase.NO_PERSON, intArrayOf(1, 10, 100)),
            frame(DiagPhase.NO_PERSON, intArrayOf(2, 20, 200)),
        )
        val (motion, static) = DiagArchiveReuse.perGateSeries(frames)
        assertEquals(3, motion.size)
        assertEquals(listOf(1, 2), motion[0].toList())
        assertEquals(listOf(10, 20), motion[1].toList())
        assertEquals(listOf(100, 200), motion[2].toList())
        // 本构造里 static 与 move 同值（IntArray 元素级比较，不能直接 assertEquals List<IntArray>）
        assertEquals(motion.map { it.toList() }, static.map { it.toList() })
    }

    @Test
    fun `perGateSeries 容忍长短不一的帧_缺门补 0`() {
        val frames = listOf(
            frame(DiagPhase.NO_PERSON, intArrayOf(1, 2, 3)),
            frame(DiagPhase.NO_PERSON, intArrayOf(7)),
        )
        val (motion, _) = DiagArchiveReuse.perGateSeries(frames)
        assertEquals(3, motion.size)
        assertEquals(listOf(1, 7), motion[0].toList())
        assertEquals(listOf(2, 0), motion[1].toList())
        assertEquals(listOf(3, 0), motion[2].toList())
    }

    @Test
    fun `toEnergySamples 与实时采样同口径_max运动静止取avg和max`() {
        val frames = listOf(
            frame(DiagPhase.NO_PERSON, intArrayOf(10, 0), intArrayOf(20, 4)),
            frame(DiagPhase.NO_PERSON, intArrayOf(30, 1), intArrayOf(12, 8)),
        )
        val es = DiagArchiveReuse.toEnergySamples(frames, 2)
        assertEquals(2, es.gateCount)
        // 门 0：每帧 max(10,20)=20、max(30,12)=30 → avg 25、max 30
        assertEquals(25, es.gates[0].avgEnergy)
        assertEquals(30, es.gates[0].maxEnergy)
        // 门 1：每帧 max(0,4)=4、max(1,8)=8 → avg 6、max 8
        assertEquals(6, es.gates[1].avgEnergy)
        assertEquals(8, es.gates[1].maxEnergy)
    }

    @Test
    fun `gateCountOf 取各帧最大门数`() {
        val frames = listOf(
            frame(DiagPhase.NO_PERSON, intArrayOf(1)),
            frame(DiagPhase.NO_PERSON, intArrayOf(1, 2, 3)),
        )
        assertEquals(3, DiagArchiveReuse.gateCountOf(frames))
    }
}

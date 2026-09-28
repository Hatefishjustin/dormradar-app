package com.dormradar.app.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 距离诊断自动分析（DistanceDiagAnalysis）：
 * 判定规则是本轮"让工具自己出结论"的核心，必须钉住——否则阈值一改就没人知道结论还准不准。
 */
class DistanceDiagAnalysisTest {

    /** 造一行样本；display 默认 = detect（代表"显示层零处理"的真实现状） */
    private fun s(
        relMs: Long,
        detect: Int,
        hasTarget: Boolean = true,
        frameType: String = "basic",
        display: Int? = null,
        inHold: Boolean = false,
    ) = DistanceDiagSample(
        wallClockMs = 1_700_000_000_000L + relMs,
        relMs = relMs,
        frameType = frameType,
        targetState = if (hasTarget) 2 else 0,
        moveDistanceCm = 0,
        moveEnergy = 0,
        staticDistanceCm = 0,
        staticEnergy = 0,
        detectDistanceCm = detect,
        displayDistanceCm = display ?: if (hasTarget) detect else -1,
        inHold = inHold,
        moveGateEnergies = null,
        staticGateEnergies = null,
    )

    @Test
    fun `空样本返回 null`() {
        assertNull(DistanceDiagAnalysis.summarize(emptyList()))
    }

    @Test
    fun `显示层零变形_差异全部落在保持窗口`() {
        val samples = listOf(
            s(0, 300),
            s(1000, 420),
            // 设备报无人，显示管线仍保留旧值（保持窗口）
            s(2000, 0, hasTarget = false, display = 420, inHold = true),
            s(3000, 0, hasTarget = false),
        )
        val sum = DistanceDiagAnalysis.summarize(samples)!!
        assertEquals(1, sum.uiDiffRows)
        assertEquals(1, sum.uiDiffHoldRows)
        assertEquals(DistanceDiagAnalysis.VerdictKind.TARGET_MOVING, sum.kind)
        assertTrue(sum.findings.first().contains("零变形"))
        assertTrue(sum.findings.first().contains("保持"))
    }

    @Test
    fun `非保持窗口出现 UI 差异判为显示层变形`() {
        val samples = listOf(
            s(0, 300, display = 250),      // 非保持窗口却与原始不一致
            s(1000, 320, display = 250),
        )
        val sum = DistanceDiagAnalysis.summarize(samples)!!
        assertEquals(2, sum.uiDiffRows)
        assertEquals(0, sum.uiDiffHoldRows)
        assertEquals(DistanceDiagAnalysis.VerdictKind.DISPLAY_DISTORTION, sum.kind)
    }

    @Test
    fun `无人段读数恒定判为固定物回波`() {
        val samples = listOf(
            s(0, 32, hasTarget = false),
            s(1000, 32, hasTarget = false),
            s(2000, 33, hasTarget = false),
        )
        val sum = DistanceDiagAnalysis.summarize(samples)!!
        assertEquals(DistanceDiagAnalysis.VerdictKind.STATIC_ECHO, sum.kind)
        assertEquals(32, sum.withoutTarget!!.detectMedian)
        assertEquals(1, sum.withoutTarget!!.spread)
        assertTrue(sum.findings.any { it.contains("没有捕捉到") })   // 缺少有人段对照
    }

    @Test
    fun `有人段极差达到一个距离门判为目标真实移动`() {
        val samples = listOf(
            s(0, 100), s(1000, 220), s(2000, 400), s(3000, 180),
        )
        val sum = DistanceDiagAnalysis.summarize(samples, resolutionCm = 75)!!
        assertEquals(DistanceDiagAnalysis.VerdictKind.TARGET_MOVING, sum.kind)
        assertEquals(300, sum.withTarget!!.spread)
        assertTrue(sum.verdict.contains("不应做平滑"))
    }

    @Test
    fun `有人段小波动但无人段在动_判为设备噪声`() {
        val samples = listOf(
            s(0, 200), s(1000, 205), s(2000, 198),      // 有人段极差 7 < 75
            s(3000, 30, hasTarget = false), s(4000, 35, hasTarget = false),
        )
        val sum = DistanceDiagAnalysis.summarize(samples, resolutionCm = 75)!!
        assertEquals(DistanceDiagAnalysis.VerdictKind.DEVICE_NOISE, sum.kind)
        assertEquals(0, sum.uiDiffRows)
    }

    @Test
    fun `逐帧抖动只比较同帧类型_不把 basic 与 engineering 互相比`() {
        // 交替出现且读数差异巨大；若按到达顺序求差会得到 200 的假跳变
        val samples = listOf(
            s(0, 300, frameType = "basic"),
            s(100, 100, frameType = "engineering"),
            s(200, 300, frameType = "basic"),
            s(300, 100, frameType = "engineering"),
        )
        val sum = DistanceDiagAnalysis.summarize(samples)!!
        assertEquals(0, sum.withTarget!!.maxJump)
        assertEquals(0, sum.withTarget!!.jitterP90)
        assertEquals(2, sum.basicRows)
        assertEquals(2, sum.engRows)
    }

    @Test
    fun `帧率按帧类型分别计算_不叠加翻倍`() {
        // basic 10 行覆盖 9000ms；engineering 5 行混插其间
        val basics = (0 until 10).map { s(it * 1000L, 200, frameType = "basic") }
        val engs = (0 until 5).map { s(it * 2000L, 50, frameType = "engineering") }
        val sum = DistanceDiagAnalysis.summarize(basics + engs)!!
        // 合并后总时长仍是 9000ms；若把两种帧相加算帧率会得到 15/9≈1.67 的错误值
        assertEquals(9_000L, sum.durationMs)
        assertEquals(10 * 1000.0 / 9_000.0, sum.basicFps, 0.0001)
        assertEquals(5 * 1000.0 / 9_000.0, sum.engFps, 0.0001)
        assertEquals(15, sum.totalRows)
    }

    @Test
    fun `无人行的 UI 输出 -1 不计入显示层差异`() {
        val samples = listOf(
            s(0, 200),
            s(1000, 0, hasTarget = false),
            s(2000, 0, hasTarget = false),
        )
        val sum = DistanceDiagAnalysis.summarize(samples)!!
        assertEquals(0, sum.uiDiffRows)
        // 有人段恒定、无人段读数为 0（设备正常的"无目标"值，不是回波）→ 正常稳定
        assertEquals(DistanceDiagAnalysis.VerdictKind.STABLE, sum.kind)
    }

    @Test
    fun `只有无人样本且读数为零_判为样本不足`() {
        val samples = listOf(
            s(0, 0, hasTarget = false),
            s(1000, 0, hasTarget = false),
        )
        val sum = DistanceDiagAnalysis.summarize(samples)!!
        assertEquals(DistanceDiagAnalysis.VerdictKind.INSUFFICIENT, sum.kind)
        assertTrue(sum.verdict.contains("无人"))
    }

    @Test
    fun `帧率数值符合公式`() {
        val basics = (0 until 10).map { s(it * 1000L, 200, frameType = "basic") }
        val sum = DistanceDiagAnalysis.summarize(basics)!!
        assertEquals(9_000L, sum.durationMs)
        assertEquals(10 * 1000.0 / 9_000.0, sum.basicFps, 0.0001)
        assertEquals(0.0, sum.engFps, 0.0001)
        assertTrue(sum.findings.any { it.contains("帧率") && it.contains("basic") })
    }
}

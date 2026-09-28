package com.dormradar.app.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 距离诊断自动分析（DistanceDiagAnalysis）：
 * 判定规则是本轮"让工具自己出结论"的核心，必须钉住——否则阈值一改就没人知道结论还准不准。
 * 引导模式的分段依据（阶段标记 vs 设备自报状态）尤其要钉住，那是误报率/检出率能不能算出来的前提。
 */
class DistanceDiagAnalysisTest {

    /** 造一行样本；display 默认 = detect（代表"显示层零处理"的真实现状） */
    private fun s(
        relMs: Long,
        detect: Int,
        hasTarget: Boolean = true,
        frameType: String = "basic",
        phase: Int = DiagPhase.NONE,
        display: Int? = null,
        inHold: Boolean = false,
        moveGates: IntArray? = null,
        staticGates: IntArray? = null,
    ) = DistanceDiagSample(
        wallClockMs = 1_700_000_000_000L + relMs,
        relMs = relMs,
        frameType = frameType,
        phase = phase,
        targetState = if (hasTarget) 2 else 0,
        moveDistanceCm = 0,
        moveEnergy = 0,
        staticDistanceCm = 0,
        staticEnergy = 0,
        detectDistanceCm = detect,
        displayDistanceCm = display ?: if (hasTarget) detect else -1,
        inHold = inHold,
        moveGateEnergies = moveGates,
        staticGateEnergies = staticGates,
    )

    // ================= 显示层 =================

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

    // ================= 旧数据（无阶段标记，按设备自报状态切分）=================

    @Test
    fun `无人段读数恒定判为固定物回波`() {
        val samples = listOf(
            s(0, 32, hasTarget = false),
            s(1000, 32, hasTarget = false),
            s(2000, 33, hasTarget = false),
        )
        val sum = DistanceDiagAnalysis.summarize(samples)!!
        assertFalse(sum.guided)
        assertEquals(DistanceDiagAnalysis.VerdictKind.STATIC_ECHO, sum.kind)
        assertEquals(32, sum.noPerson!!.detectMedian)
        assertEquals(1, sum.noPerson!!.spread)
        assertNull(sum.falsePositiveRate)
        assertTrue(sum.findings.any { it.contains("没有") && it.contains("有人") })
    }

    @Test
    fun `有人段极差达到一个距离门判为目标真实移动`() {
        val samples = listOf(
            s(0, 100), s(1000, 220), s(2000, 400), s(3000, 180),
        )
        val sum = DistanceDiagAnalysis.summarize(samples, resolutionCm = 75)!!
        assertEquals(DistanceDiagAnalysis.VerdictKind.TARGET_MOVING, sum.kind)
        assertEquals(300, sum.person!!.spread)
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
    fun `逐帧抖动只比较同帧类型_不把 basic 与 engineering 互相比`() {
        // 交替出现且读数差异巨大；若按到达顺序求差会得到 200 的假跳变
        val samples = listOf(
            s(0, 300, frameType = "basic"),
            s(100, 100, frameType = "engineering"),
            s(200, 300, frameType = "basic"),
            s(300, 100, frameType = "engineering"),
        )
        val sum = DistanceDiagAnalysis.summarize(samples)!!
        assertEquals(0, sum.person!!.maxJump)
        assertEquals(0, sum.person!!.jitterP90)
        assertEquals(2, sum.basicRows)
        assertEquals(2, sum.engRows)
    }

    // ================= 引导模式（按阶段标记切分，能算误报率/检出率）=================

    @Test
    fun `引导模式按阶段切分_不按设备自报状态`() {
        // 无人阶段里设备全部误判"有人"；若按 hasTarget 切分，无人段会变成空。
        // 读数取 300cm（非近场），以隔离"按阶段切分"这一点，不与近场饱和判据混淆。
        val samples = List(10) {
            s(it * 1000L, 300, hasTarget = true, phase = DiagPhase.NO_PERSON)
        } + List(10) {
            s(10_000L + it * 1000L, 320, hasTarget = true, phase = DiagPhase.PERSON)
        }
        val sum = DistanceDiagAnalysis.summarize(samples)!!
        assertTrue(sum.guided)
        assertEquals(10, sum.noPerson!!.rows)
        assertEquals(10, sum.person!!.rows)
        assertEquals(1.0, sum.falsePositiveRate!!, 0.0001)
        assertEquals(1.0, sum.detectionRate!!, 0.0001)
        assertEquals(DistanceDiagAnalysis.VerdictKind.FALSE_POSITIVE, sum.kind)
        assertTrue(sum.verdict.contains("无人基线定参"))
    }

    @Test
    fun `误报率与检出率按帧数计算`() {
        // 无人阶段 20 帧中 3 帧误判 → 15%；有人阶段 20 帧中 18 帧检出 → 90%
        val noPerson = List(20) {
            s(it * 1000L, 40, hasTarget = it < 3, phase = DiagPhase.NO_PERSON)
        }
        val person = List(20) {
            s(20_000L + it * 1000L, 300, hasTarget = it < 18, phase = DiagPhase.PERSON)
        }
        val sum = DistanceDiagAnalysis.summarize(noPerson + person)!!
        assertEquals(0.15, sum.falsePositiveRate!!, 0.0001)
        assertEquals(0.90, sum.detectionRate!!, 0.0001)
        // 15% 未达误报警戒线、90% 检出正常 → 不被判为误报/漏报
        assertTrue(sum.kind != DistanceDiagAnalysis.VerdictKind.FALSE_POSITIVE)
        assertTrue(sum.kind != DistanceDiagAnalysis.VerdictKind.MISSED_DETECTION)
        assertTrue(sum.findings.any { it.contains("误报率 15%") })
        assertTrue(sum.findings.any { it.contains("检出率 90%") })
    }

    @Test
    fun `有人阶段几乎检不出_判为漏报主导`() {
        val noPerson = List(10) {
            s(it * 1000L, 0, hasTarget = false, phase = DiagPhase.NO_PERSON)
        }
        val person = List(10) {
            s(10_000L + it * 1000L, 300, hasTarget = false, phase = DiagPhase.PERSON)
        }
        val sum = DistanceDiagAnalysis.summarize(noPerson + person)!!
        assertEquals(0.0, sum.detectionRate!!, 0.0001)
        assertEquals(DistanceDiagAnalysis.VerdictKind.MISSED_DETECTION, sum.kind)
        assertTrue(sum.verdict.contains("阈值可能被抬得过高"))
    }

    @Test
    fun `引导模式正常场景_检出好误报低则看物理结论`() {
        val noPerson = List(10) {
            s(it * 1000L, 30, hasTarget = false, phase = DiagPhase.NO_PERSON)
        }
        val person = List(10) {
            s(10_000L + it * 1000L, 250 + it * 10, hasTarget = true, phase = DiagPhase.PERSON)
        }
        val sum = DistanceDiagAnalysis.summarize(noPerson + person, resolutionCm = 75)!!
        assertEquals(0.0, sum.falsePositiveRate!!, 0.0001)
        assertEquals(1.0, sum.detectionRate!!, 0.0001)
        assertEquals(DistanceDiagAnalysis.VerdictKind.TARGET_MOVING, sum.kind)
        assertEquals(90, sum.person!!.spread)
    }

    // ================= 近场饱和（用户 2026-09-28 实测：隔门板恒报 30cm）=================

    @Test
    fun `无人阶段读数被钉在最小值_判为近场饱和并指向改安装`() {
        // 用户实测场景：隔着门板，无人时也一直报 30cm 有人
        val noPerson = List(10) {
            s(it * 1000L, 30, hasTarget = true, phase = DiagPhase.NO_PERSON)
        }
        val person = List(10) {
            s(10_000L + it * 1000L, 30 + it % 3, hasTarget = true, phase = DiagPhase.PERSON)
        }
        val sum = DistanceDiagAnalysis.summarize(noPerson + person)!!
        assertEquals(DistanceDiagAnalysis.VerdictKind.NEAR_FIELD_SATURATION, sum.kind)
        assertTrue(sum.verdict.contains("近场饱和"))
        assertTrue(sum.verdict.contains("调灵敏度没用"))
        assertTrue(sum.verdict.contains("改安装") || sum.verdict.contains("不要夹介质"))
    }

    @Test
    fun `近场饱和优先于误报主导`() {
        // 两者都表现为"无人也报有人"，但近场饱和的处置是改安装、不是压阈值——必须优先判近场
        val noPerson = List(10) {
            s(it * 1000L, 30, hasTarget = true, phase = DiagPhase.NO_PERSON)
        }
        val person = List(10) {
            s(10_000L + it * 1000L, 32, hasTarget = true, phase = DiagPhase.PERSON)
        }
        val sum = DistanceDiagAnalysis.summarize(noPerson + person)!!
        assertEquals(1.0, sum.falsePositiveRate!!, 0.0001)
        assertEquals(DistanceDiagAnalysis.VerdictKind.NEAR_FIELD_SATURATION, sum.kind)
    }

    @Test
    fun `读数在正常距离上的误报仍判误报主导`() {
        // 无人阶段读数在 3 米附近、八成误报 → 属于阈值偏低，可压阈值矫治，不该判成近场
        val noPerson = List(10) {
            s(it * 1000L, 300, hasTarget = it < 8, phase = DiagPhase.NO_PERSON)
        }
        val person = List(10) {
            s(10_000L + it * 1000L, 300, hasTarget = true, phase = DiagPhase.PERSON)
        }
        val sum = DistanceDiagAnalysis.summarize(noPerson + person)!!
        assertEquals(DistanceDiagAnalysis.VerdictKind.FALSE_POSITIVE, sum.kind)
        assertTrue(sum.verdict.contains("无人基线定参"))
    }

    @Test
    fun `无引导阶段标记时不会误判成近场饱和`() {
        // 旧格式数据：按 hasTarget 切分，noPerson 是"设备自报无人"的行，targetRate 恒为 0
        val samples = listOf(
            s(0, 30, hasTarget = false), s(1000, 30, hasTarget = false),
        )
        val sum = DistanceDiagAnalysis.summarize(samples)!!
        assertFalse(sum.guided)
        assertEquals(DistanceDiagAnalysis.VerdictKind.STATIC_ECHO, sum.kind)
    }

    // ================= 门能量分布 =================

    @Test
    fun `门能量分布平均值并指出峰值门`() {
        val samples = listOf(
            s(0, 30, moveGates = intArrayOf(60, 20, 5), staticGates = intArrayOf(40, 10, 0)),
            s(1000, 30, moveGates = intArrayOf(80, 30, 10), staticGates = intArrayOf(60, 20, 0)),
        )
        val sum = DistanceDiagAnalysis.summarize(samples)!!
        // 每门取 max(运动, 静止) 后求平均：门0 (60+80)/2=70、门1 (20+30)/2=25、门2 (5+10)/2=7
        assertEquals(listOf(70, 25, 7), sum.gateEnergyMeans)
        assertEquals(0, sum.dominantGate)
        assertTrue(sum.findings.any { it.contains("门能量分布") && it.contains("峰值在门 0") })
    }

    @Test
    fun `无工程帧时门能量分布为空`() {
        val sum = DistanceDiagAnalysis.summarize(listOf(s(0, 300)))!!
        assertNull(sum.gateEnergyMeans)
        assertNull(sum.dominantGate)
    }

    @Test
    fun `近场饱和的结论里带上峰值门作为证据`() {
        val noPerson = List(4) {
            s(it * 1000L, 30, phase = DiagPhase.NO_PERSON, moveGates = intArrayOf(70, 8, 2), staticGates = intArrayOf(50, 5, 0))
        }
        val person = List(4) {
            s(4_000L + it * 1000L, 32, phase = DiagPhase.PERSON, moveGates = intArrayOf(72, 9, 2), staticGates = intArrayOf(52, 6, 0))
        }
        val sum = DistanceDiagAnalysis.summarize(noPerson + person)!!
        assertEquals(DistanceDiagAnalysis.VerdictKind.NEAR_FIELD_SATURATION, sum.kind)
        assertEquals(0, sum.dominantGate)
        assertTrue(sum.verdict.contains("能量也确实堆在门 0"))
    }

    // ================= 帧率 =================

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
    fun `帧率数值符合公式`() {
        val basics = (0 until 10).map { s(it * 1000L, 200, frameType = "basic") }
        val sum = DistanceDiagAnalysis.summarize(basics)!!
        assertEquals(9_000L, sum.durationMs)
        assertEquals(10 * 1000.0 / 9_000.0, sum.basicFps, 0.0001)
        assertEquals(0.0, sum.engFps, 0.0001)
        assertTrue(sum.findings.any { it.contains("帧率") && it.contains("basic") })
    }

    @Test
    fun `分段统计非空则字段自洽`() {
        val sum = DistanceDiagAnalysis.summarize(listOf(s(0, 120), s(1000, 340)))!!
        assertNotNull(sum.person)
        assertEquals(2, sum.person!!.rows)
        assertEquals(2, sum.person!!.targetFrames)
        assertEquals(120, sum.person!!.detectMin)
        assertEquals(340, sum.person!!.detectMax)
        assertEquals(1.0, sum.person!!.targetRate, 0.0001)
    }
}

package com.dormradar.app.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/** 距离诊断 CSV 格式化 + 环形缓冲 + relMs 单调性 + 阶段标记 */
class DistanceDiagTest {

    private fun sample(
        detect: Int = 300,
        display: Int = 295,
        frameType: String = "basic",
        phase: Int = DiagPhase.NONE,
        targetState: Int = 2,
        inHold: Boolean = false,
        moveGates: IntArray? = null,
        staticGates: IntArray? = null,
    ) = DistanceDiagSample(
        wallClockMs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            .parse("2026-09-28 15:30:00.123")!!.time,
        relMs = 1500,
        frameType = frameType,
        phase = phase,
        targetState = targetState,
        moveDistanceCm = 0,
        moveEnergy = 0,
        staticDistanceCm = 300,
        staticEnergy = 66,
        detectDistanceCm = detect,
        displayDistanceCm = display,
        inHold = inHold,
        moveGateEnergies = moveGates,
        staticGateEnergies = staticGates,
    )

    /** 记录器 + 可控时钟（relMs 相关用例需要确定的时间推进） */
    private fun recorder(): Pair<DistanceDiagRecorder, () -> Unit> {
        val r = DistanceDiagRecorder()
        var now = 1_000_000L
        r.clock = { now }
        return r to { now += 10L }
    }

    // ---- CSV 格式化 ----

    @Test
    fun `CSV 行包含全部列_门数组用分号`() {
        val line = DistanceDiagFormats.line(
            sample(moveGates = intArrayOf(1, 2, 3), staticGates = intArrayOf(4, 5)),
        )
        val cols = line.split(',')
        assertEquals(DistanceDiagFormats.COLUMN_COUNT, cols.size)
        assertEquals(16, cols.size)
        assertEquals("15:30:00.123", cols[1])
        assertEquals("1500", cols[2])
        assertEquals("basic", cols[3])
        assertEquals("0", cols[4])          // phase
        assertEquals("2", cols[5])          // target_state
        assertEquals("1", cols[6])          // has_target
        assertEquals("300", cols[11])       // 原始检测距离
        assertEquals("295", cols[12])       // 显示管线输出
        assertEquals("0", cols[13])         // 保持标记
        assertEquals("1;2;3", cols[14])
        assertEquals("4;5", cols[15])
    }

    @Test
    fun `epoch_ms 列可机器解析`() {
        val expected = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            .parse("2026-09-28 15:30:00.123")!!.time
        val cols = DistanceDiagFormats.line(sample()).split(',')
        assertEquals(expected.toString(), cols[0])
    }

    @Test
    fun `phase 列写入引导阶段`() {
        assertEquals("1", DistanceDiagFormats.line(sample(phase = DiagPhase.NO_PERSON)).split(',')[4])
        assertEquals("2", DistanceDiagFormats.line(sample(phase = DiagPhase.PERSON)).split(',')[4])
        assertEquals("0", DistanceDiagFormats.line(sample(phase = DiagPhase.NONE)).split(',')[4])
    }

    @Test
    fun `has_target 列按状态换算_底噪扩展状态不算有人`() {
        // 0=无人 / 1运动 / 2静止 / 3两者 / 4~6 底噪检测阶段
        assertEquals("0", DistanceDiagFormats.line(sample(targetState = 0)).split(',')[6])
        assertEquals("1", DistanceDiagFormats.line(sample(targetState = 1)).split(',')[6])
        assertEquals("1", DistanceDiagFormats.line(sample(targetState = 3)).split(',')[6])
        assertEquals("0", DistanceDiagFormats.line(sample(targetState = 4)).split(',')[6])
        assertEquals("0", DistanceDiagFormats.line(sample(targetState = 5)).split(',')[6])
    }

    @Test
    fun `保持窗口标记写入 ui_hold 列`() {
        val cols = DistanceDiagFormats.line(sample(inHold = true)).split(',')
        assertEquals("1", cols[13])
    }

    @Test
    fun `basic 帧门能量列为空`() {
        val cols = DistanceDiagFormats.line(sample()).split(',')
        assertEquals("", cols[14])
        assertEquals("", cols[15])
    }

    @Test
    fun `UI 输出 -1 表示无显示`() {
        val line = DistanceDiagFormats.line(sample(display = -1))
        assertTrue(line.endsWith(",-1,0,,"))
    }

    @Test
    fun `csv 含表头且行数正确`() {
        val csv = DistanceDiagFormats.csv(listOf(sample(), sample(detect = 310)))
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertEquals(DistanceDiagFormats.HEADER, lines[0])
    }

    // ---- 环形缓冲与门控 ----

    @Test
    fun `环形缓冲挤掉最旧`() {
        val r = DistanceDiagRecorder()
        r.start()
        repeat(DistanceDiagRecorder.MAX_ENTRIES + 50) { n ->
            r.onRawFrame("basic", 1, n, 0, 0, 0, n, null, null)
        }
        val samples = r.samples.value
        assertEquals(DistanceDiagRecorder.MAX_ENTRIES, samples.size)
        assertEquals(50, samples.first().detectDistanceCm) // 前 50 条被挤掉
    }

    @Test
    fun `未开始记录时不采样`() {
        val r = DistanceDiagRecorder()
        r.onRawFrame("basic", 1, 100, 0, 0, 0, 100, null, null)
        assertTrue(r.samples.value.isEmpty())
    }

    @Test
    fun `停止后停止记录`() {
        val r = DistanceDiagRecorder()
        r.start()
        r.onRawFrame("basic", 1, 100, 0, 0, 0, 100, null, null)
        r.stop()
        r.onRawFrame("basic", 1, 110, 0, 0, 0, 110, null, null)
        assertEquals(1, r.samples.value.size)
        assertEquals(100, r.samples.value.first().detectDistanceCm)
    }

    @Test
    fun `noteDisplay 快照进后续行`() {
        val r = DistanceDiagRecorder()
        r.start()
        r.noteDisplay(hasTarget = true, detectDistanceCm = 280, inHold = false)
        r.onRawFrame("basic", 2, 0, 0, 300, 60, 300, null, null)
        assertEquals(280, r.samples.value.first().displayDistanceCm)
        assertTrue(!r.samples.value.first().inHold)

        r.noteDisplay(hasTarget = false, detectDistanceCm = 0, inHold = true)
        r.onRawFrame("basic", 0, 0, 0, 0, 0, 0, null, null)
        assertEquals(-1, r.samples.value.last().displayDistanceCm)
        assertTrue(r.samples.value.last().inHold)
    }

    // ---- 阶段标记 ----

    @Test
    fun `setPhase 只影响之后写入的样本`() {
        val r = DistanceDiagRecorder()
        r.start()
        r.onRawFrame("basic", 0, 0, 0, 0, 0, 0, null, null)
        r.setPhase(DiagPhase.NO_PERSON)
        r.onRawFrame("basic", 0, 0, 0, 0, 0, 10, null, null)
        r.setPhase(DiagPhase.PERSON)
        r.onRawFrame("basic", 1, 0, 0, 0, 0, 200, null, null)

        val s = r.samples.value
        assertEquals(DiagPhase.NONE, s[0].phase)
        assertEquals(DiagPhase.NO_PERSON, s[1].phase)
        assertEquals(DiagPhase.PERSON, s[2].phase)
    }

    @Test
    fun `clear 重置阶段标记`() {
        val r = DistanceDiagRecorder()
        r.start()
        r.setPhase(DiagPhase.PERSON)
        r.clear()
        r.onRawFrame("basic", 0, 0, 0, 0, 0, 0, null, null)
        assertEquals(DiagPhase.NONE, r.samples.value.first().phase)
    }

    // ---- relMs 单调性（2026-09-28 修正的缺陷）----

    @Test
    fun `relMs 跨 停止再开始 仍单调递增`() {
        val (r, tick) = recorder()
        r.start()
        r.onRawFrame("basic", 1, 0, 0, 0, 0, 100, null, null)
        tick()          // +10ms
        r.stop()
        repeat(6000) { tick() }   // 停 1 分钟
        r.start()
        r.onRawFrame("basic", 1, 0, 0, 0, 0, 110, null, null)

        val s = r.samples.value
        assertEquals(2, s.size)
        assertEquals(0L, s[0].relMs)
        // 关键：相对缓冲首行（而非新一次 start() 时刻），因此不会跳回 0
        assertEquals(60_010L, s[1].relMs)
        assertTrue(s[1].relMs > s[0].relMs)
    }

    @Test
    fun `环形缓冲裁剪后 relMs 不回退`() {
        val (r, tick) = recorder()
        r.start()
        repeat(DistanceDiagRecorder.MAX_ENTRIES + 3) { i ->
            r.onRawFrame("basic", 1, 0, 0, 0, 0, i, null, null)
            tick()
        }
        val s = r.samples.value
        assertEquals(DistanceDiagRecorder.MAX_ENTRIES, s.size)
        // 头部被挤掉 3 条：首行 relMs 不从 0 重来，末行仍严格更大
        assertEquals(30L, s.first().relMs)
        assertTrue(s.last().relMs > s.first().relMs)
    }

    @Test
    fun `clear 重置 relMs 起点`() {
        val (r, tick) = recorder()
        r.start()
        r.onRawFrame("basic", 1, 0, 0, 0, 0, 100, null, null)
        repeat(5) { tick() }
        r.clear()
        r.onRawFrame("basic", 1, 0, 0, 0, 0, 110, null, null)
        assertEquals(1, r.samples.value.size)
        assertEquals(0L, r.samples.value.first().relMs)
    }

    // ---- parseCsv（采样档案共享 2026-10-07：存档 → 其他功能复用）----

    @Test
    fun `parseCsv roundtrip_工程帧含门数组与阶段`() {
        val original = listOf(
            sample(
                phase = DiagPhase.NO_PERSON, frameType = "engineering", display = -1, inHold = true,
                moveGates = intArrayOf(3, 25, 0, 0), staticGates = intArrayOf(9, 40, 7, 0),
            ),
            sample(
                phase = DiagPhase.PERSON, frameType = "engineering", detect = 450,
                moveGates = intArrayOf(60, 12), staticGates = intArrayOf(5, 88),
            ),
        )
        val parsed = DistanceDiagFormats.parseCsv(DistanceDiagFormats.csv(original))
        assertEquals(original, parsed)
    }

    @Test
    fun `parseCsv roundtrip_basic帧门列为空`() {
        val original = listOf(sample())
        val parsed = DistanceDiagFormats.parseCsv(DistanceDiagFormats.csv(original))
        assertEquals(original, parsed)
        assertNull(parsed[0].moveGateEnergies)
        assertNull(parsed[0].staticGateEnergies)
    }

    @Test
    fun `parseCsv 跳过表头空行与坏行`() {
        val csv = DistanceDiagFormats.HEADER + "\n" + "\n" + "not,a,row\n" +
            "1,2,3\n" + DistanceDiagFormats.line(sample()) + "\n"
        val parsed = DistanceDiagFormats.parseCsv(csv)
        assertEquals(1, parsed.size)
        assertEquals(sample(), parsed[0])
    }
}

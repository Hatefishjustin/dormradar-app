package com.dormradar.app.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/** 距离诊断 CSV 格式化 + 环形缓冲 */
class DistanceDiagTest {

    private fun sample(
        detect: Int = 300,
        display: Int = 295,
        frameType: String = "basic",
        moveGates: IntArray? = null,
        staticGates: IntArray? = null,
    ) = DistanceDiagSample(
        wallClockMs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            .parse("2026-09-28 15:30:00.123")!!.time,
        relMs = 1500,
        frameType = frameType,
        targetState = 2,
        moveDistanceCm = 0,
        moveEnergy = 0,
        staticDistanceCm = 300,
        staticEnergy = 66,
        detectDistanceCm = detect,
        displayDistanceCm = display,
        moveGateEnergies = moveGates,
        staticGateEnergies = staticGates,
    )

    @Test
    fun `CSV 行包含全部列_门数组用分号`() {
        val line = DistanceDiagFormats.line(sample(moveGates = intArrayOf(1, 2, 3), staticGates = intArrayOf(4, 5)))
        val cols = line.split(',')
        assertEquals(12, cols.size)
        assertEquals("15:30:00.123", cols[0])
        assertEquals("1500", cols[1])
        assertEquals("basic", cols[2])
        assertEquals("300", cols[8])
        assertEquals("295", cols[9])
        assertEquals("1;2;3", cols[10])
        assertEquals("4;5", cols[11])
    }

    @Test
    fun `basic 帧门能量列为空`() {
        val line = DistanceDiagFormats.line(sample())
        val cols = line.split(',')
        assertEquals("", cols[10])
        assertEquals("", cols[11])
    }

    @Test
    fun `UI 显示 -1 表示无显示`() {
        val line = DistanceDiagFormats.line(sample(display = -1))
        assertTrue(line.endsWith(",-1,,"))
    }

    @Test
    fun `csv 含表头且行数正确`() {
        val csv = DistanceDiagFormats.csv(listOf(sample(), sample(detect = 310)))
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertEquals(DistanceDiagFormats.HEADER, lines[0])
    }

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
        r.noteDisplay(hasTarget = true, detectDistanceCm = 280)
        r.onRawFrame("basic", 2, 0, 0, 300, 60, 300, null, null)
        assertEquals(280, r.samples.value.first().displayDistanceCm)
        r.noteDisplay(hasTarget = false, detectDistanceCm = 0)
        r.onRawFrame("basic", 0, 0, 0, 0, 0, 0, null, null)
        assertEquals(-1, r.samples.value.last().displayDistanceCm)
    }
}

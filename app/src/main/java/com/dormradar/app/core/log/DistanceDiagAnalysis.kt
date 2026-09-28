package com.dormradar.app.core.log

import kotlin.math.roundToInt

/**
 * 距离诊断自动分析（纯 Kotlin 可单测）。
 *
 * 设计动机（2026-09-28）：原始 CSV 的"原始值 vs UI 显示值"两列无法独立回答 Q10——
 * 因为首页对 detect 距离**不做任何平滑**，两列在构造上必然相等，唯一的差异来自
 * 「目标丢失保持」窗口。与其让人对着 12000 行猜，不如把判定规则固化成代码。
 *
 * 第二轮改造（引导式两阶段）：分段依据从"设备自报的有人/无人"改为**App 指挥的阶段标记**
 * （[DiagPhase]）。差别很关键——用设备自报状态分段是循环论证（拿设备的判断去评估设备的
 * 判断），用阶段标记分段才能真正算出：
 * - **误报率** = 无人阶段里设备判"有人"的帧占比（门口没人却报警）
 * - **检出率** = 有人阶段里设备判"有人"的帧占比（有人走过却漏掉）
 * 这两个数正是 docs/06 阶段 4 的验收口径。
 */
object DistanceDiagAnalysis {

    /** 读数视为"恒定"的极差上限（cm） */
    private const val CONSTANT_SPREAD_CM = 2

    /** 误报率超过该值即判为"误报主导" */
    private const val FALSE_POSITIVE_ALERT = 0.2

    /** 检出率低于该值即判为"漏报主导" */
    private const val DETECTION_MIN = 0.5

    /** 一段样本（无人阶段或有人阶段）的距离统计 */
    data class Segment(
        val rows: Int,
        val detectMin: Int,
        val detectMax: Int,
        val detectMedian: Int,
        /** 相邻同类型帧 |Δ距离| 的 p50 / p90 / 最大值（逐帧抖动强度） */
        val jitterP50: Int,
        val jitterP90: Int,
        val maxJump: Int,
        /** 该段里设备判"有人"的帧数与占比 */
        val targetFrames: Int,
    ) {
        val spread: Int get() = detectMax - detectMin
        val targetRate: Double get() = if (rows == 0) 0.0 else targetFrames.toDouble() / rows
    }

    data class Summary(
        val totalRows: Int,
        val basicRows: Int,
        val engRows: Int,
        val holdRows: Int,
        val durationMs: Long,
        /** 分帧类型的真实频率（basic 与 engineering 必须分开算，否则翻倍） */
        val basicFps: Double,
        val engFps: Double,
        /** 是否走了引导式阶段标记（false = 旧数据，按设备自报状态切分） */
        val guided: Boolean,
        /** 无人阶段统计 */
        val noPerson: Segment?,
        /** 有人阶段统计 */
        val person: Segment?,
        /** 误报率（无人阶段判"有人"的占比）；无引导阶段时为 null */
        val falsePositiveRate: Double?,
        /** 检出率（有人阶段判"有人"的占比）；无引导阶段时为 null */
        val detectionRate: Double?,
        /** 显示管线输出 ≠ 原始距离的行数 */
        val uiDiffRows: Int,
        /** 其中处于"目标丢失保持"窗口的行数 */
        val uiDiffHoldRows: Int,
        /** 结论分类（UI 据此配色，不必解析中文） */
        val kind: VerdictKind,
        /** 一句话结论 */
        val verdict: String,
        /** 分项发现，供 UI 逐条展示 */
        val findings: List<String>,
    )

    /** 结论的机器可读分类 */
    enum class VerdictKind {
        /** 样本不足（空、或只有"无目标且读数为 0"的段落，缺少有人对照） */
        INSUFFICIENT,

        /** 读数稳定，未见异常抖动（这是最常见也最正常的结果） */
        STABLE,

        /** 固定物回波：无人时仍报非零且恒定的距离（门板/墙体反射） */
        STATIC_ECHO,

        /** 设备/环境噪声：读数在跳，但不构成一个距离门的幅度且无固定物特征 */
        DEVICE_NOISE,

        /** 目标真实在移动（有人阶段极差达到一个距离门）——正常现象，不该平滑掉 */
        TARGET_MOVING,

        /** 误报主导：门口无人却大量判为有人 */
        FALSE_POSITIVE,

        /** 漏报主导：有人走过却大量判为无人 */
        MISSED_DETECTION,

        /** 显示层在非保持窗口改变了距离值（预期不应出现） */
        DISPLAY_DISTORTION,
    }

    /**
     * @param samples 记录器缓冲快照（到达顺序）
     * @param resolutionCm 当前距离分辨率（20 或 75），用于判断"极差是否达到一个距离门"
     */
    fun summarize(samples: List<DistanceDiagSample>, resolutionCm: Int = 75): Summary? {
        if (samples.isEmpty()) return null
        val gate = resolutionCm.coerceAtLeast(1)
        val basicRows = samples.count { it.frameType == "basic" }
        val engRows = samples.count { it.frameType == "engineering" }
        val holdRows = samples.count { it.inHold }
        // 时长取 min/max 而非首尾元素：不把"列表顺序 = 时间顺序"当成契约（帧率是除法分母，错了会全盘失真）
        val durationMs = (samples.maxOf { it.relMs } - samples.minOf { it.relMs }).coerceAtLeast(0L)

        // 分段：优先用引导阶段标记（能算出误报率/检出率）；旧数据退回按设备自报状态切分
        val guided = samples.any { it.phase != DiagPhase.NONE }
        val noPerson = if (guided) {
            segment(samples.filter { it.phase == DiagPhase.NO_PERSON })
        } else {
            segment(samples.filter { !it.hasTarget })
        }
        val person = if (guided) {
            segment(samples.filter { it.phase == DiagPhase.PERSON })
        } else {
            segment(samples.filter { it.hasTarget })
        }
        val falsePositiveRate = if (guided) noPerson?.targetRate else null
        val detectionRate = if (guided) person?.targetRate else null

        // 只比较"显示管线确实呈现了距离"的行：无人时显示管线输出 -1（界面显示 "--"），
        // 那不是"数值不一致"，若计入会让每段无人样本都误判成显示层变形（2026-09-28 修正）。
        val uiDiff = samples.filter { it.displayDistanceCm >= 0 && it.displayDistanceCm != it.detectDistanceCm }
        val uiDiffHold = uiDiff.count { it.inHold }

        val findings = mutableListOf<String>()

        // 1) 显示层结论（审计结论的实证列：detect 距离未经平滑）
        findings += if (uiDiff.isEmpty()) {
            "显示层零变形：UI 输出与原始距离逐行完全一致（detect 距离未经任何平滑处理）"
        } else if (uiDiff.size == uiDiffHold) {
            "显示层零变形：${uiDiff.size} 行 UI≠原始全部落在「丢失保持 2 秒」窗口内（有意保留旧值），" +
                "非保持窗口零差异 → 首页距离跳动 = 原始帧跳动，与显示处理无关"
        } else {
            "⚠ 存在 ${uiDiff.size - uiDiffHold} 行非保持窗口的 UI≠原始——预期不应出现，需检查显示逻辑"
        }

        // 2) 帧率（分类型，避免把 basic+engineering 相加得出翻倍帧率）
        findings += buildString {
            append("帧率：basic ${"%.2f".format(fpsOf(basicRows, durationMs))} 帧/秒")
            if (engRows > 0) append("，engineering ${"%.2f".format(fpsOf(engRows, durationMs))} 帧/秒")
            append("（共 ${samples.size} 行 / ${durationMs} ms）")
        }

        // 3) 分段结论
        if (guided) {
            noPerson?.let { s ->
                findings += "无人阶段：${s.rows} 帧，检测距离 ${s.detectMin}~${s.detectMax} cm（中位 ${s.detectMedian}）" +
                    "，其中 ${s.targetFrames} 帧被判「有人」→ 误报率 ${"%.0f".format(s.targetRate * 100)}%"
            }
            person?.let { s ->
                findings += "有人阶段：${s.rows} 帧，检测距离 ${s.detectMin}~${s.detectMax} cm（中位 ${s.detectMedian}，" +
                    "极差 ${s.spread}，逐帧抖动 p50 ${s.jitterP50} / p90 ${s.jitterP90}）" +
                    "，其中 ${s.targetFrames} 帧被判「有人」→ 检出率 ${"%.0f".format(s.targetRate * 100)}%"
            }
        } else {
            noPerson?.let { s ->
                findings += "无人段：${s.rows} 帧，检测距离 ${s.detectMin}~${s.detectMax} cm" +
                    "（中位 ${s.detectMedian}，极差 ${s.spread}，逐帧抖动 p50 ${s.jitterP50} / p90 ${s.jitterP90}）"
            }
            person?.let { s ->
                findings += "有人段：${s.rows} 帧，检测距离 ${s.detectMin}~${s.detectMax} cm" +
                    "（中位 ${s.detectMedian}，极差 ${s.spread}，逐帧抖动 p50 ${s.jitterP50} / p90 ${s.jitterP90}）"
            }
            findings += "（本段数据没有引导阶段标记，按设备自报的有人/无人切分——" +
                "这种切分无法算出误报率/检出率，建议用引导式两阶段重新采一次）"
        }
        if (person == null) {
            findings += "⚠ 本次记录没有「有人」阶段的数据——缺少对照无法评估灵敏度"
        }

        val kind = classify(guided, falsePositiveRate, detectionRate, noPerson, person, uiDiff.size, uiDiffHold, gate)
        return Summary(
            totalRows = samples.size,
            basicRows = basicRows,
            engRows = engRows,
            holdRows = holdRows,
            durationMs = durationMs,
            basicFps = fpsOf(basicRows, durationMs),
            engFps = fpsOf(engRows, durationMs),
            guided = guided,
            noPerson = noPerson,
            person = person,
            falsePositiveRate = falsePositiveRate,
            detectionRate = detectionRate,
            uiDiffRows = uiDiff.size,
            uiDiffHoldRows = uiDiffHold,
            kind = kind,
            verdict = verdictText(kind, noPerson, person, falsePositiveRate, detectionRate, gate),
            findings = findings,
        )
    }

    // ---- 判定 ----

    private fun classify(
        guided: Boolean,
        falsePositiveRate: Double?,
        detectionRate: Double?,
        noPerson: Segment?,
        person: Segment?,
        uiDiffRows: Int,
        uiDiffHoldRows: Int,
        gate: Int,
    ): VerdictKind = when {
        uiDiffRows > uiDiffHoldRows -> VerdictKind.DISPLAY_DISTORTION

        // 引导模式优先回答"误报/漏报"这两个用户真正关心的问题
        guided && falsePositiveRate != null && falsePositiveRate >= FALSE_POSITIVE_ALERT ->
            VerdictKind.FALSE_POSITIVE

        guided && detectionRate != null && detectionRate < DETECTION_MIN ->
            VerdictKind.MISSED_DETECTION

        person == null && noPerson == null -> VerdictKind.INSUFFICIENT

        // 只有无人阶段：读数为 0 表示"确实没东西"（正常），非零恒定才是固定物回波
        person == null -> when {
            noPerson!!.detectMedian <= 0 -> VerdictKind.INSUFFICIENT
            noPerson.spread <= CONSTANT_SPREAD_CM -> VerdictKind.STATIC_ECHO
            else -> VerdictKind.DEVICE_NOISE
        }

        person.spread >= gate -> VerdictKind.TARGET_MOVING
        person.spread <= CONSTANT_SPREAD_CM -> {
            val echo = (noPerson?.detectMedian ?: 0) > 0 &&
                (noPerson?.spread ?: 0) <= CONSTANT_SPREAD_CM
            if (echo) VerdictKind.STATIC_ECHO else VerdictKind.STABLE
        }

        else -> VerdictKind.DEVICE_NOISE
    }

    private fun verdictText(
        kind: VerdictKind,
        noPerson: Segment?,
        person: Segment?,
        falsePositiveRate: Double?,
        detectionRate: Double?,
        gate: Int,
    ): String = when (kind) {
        VerdictKind.INSUFFICIENT ->
            "样本不足，无法判定。" +
                (if (person == null && noPerson != null) {
                    "本次只有「无人」阶段的数据——请走引导流程（无人 → 有人）重新采一次"
                } else {
                    "请走引导流程重新采一次"
                })

        VerdictKind.STABLE ->
            "判定：读数稳定，未发现异常抖动" +
                (person?.let { "（有人阶段 ${it.rows} 帧集中在 ${it.detectMedian} cm，极差仅 ${it.spread} cm）" } ?: "") +
                "。若你仍然觉得「距离在跳」，那是目标本身在不同距离被先后探到，属正常现象。"

        VerdictKind.STATIC_ECHO ->
            "判定：固定物回波（静态反射）。无人时设备仍报非零且几乎不变的距离" +
                (noPerson?.let { "（恒定在 ${it.detectMedian} cm、极差 ${it.spread} cm）" } ?: "") +
                "——这不是「抖动」。调参方向：抬高该距离门的静止阈值，或用「部署向导」重新定参"

        VerdictKind.DEVICE_NOISE ->
            "判定：设备/环境噪声导致读数波动，显示层无责任。原始帧本身在跳" +
                (person?.let {
                    "（有人阶段抖动 p50 ${it.jitterP50} cm / p90 ${it.jitterP90} cm，" +
                        "极差 ${it.spread} cm，未达一个距离门 $gate cm）"
                } ?: "") +
                "——先排查安装贴合与底噪，必要时抬高静止阈值"

        VerdictKind.TARGET_MOVING ->
            "判定：目标真实在移动，读数变化属正常（有人阶段极差 ${person?.spread} cm ≥ 一个距离门 $gate cm）。" +
                "不应做平滑——平滑会让「有人走近」的读数变迟钝"

        VerdictKind.FALSE_POSITIVE ->
            "判定：误报主导。门口无人时仍有 ${pct(falsePositiveRate)} 的帧被判「有人」" +
                (noPerson?.let { "（无人阶段读数 ${it.detectMin}~${it.detectMax} cm）" } ?: "") +
                "——这是各门阈值低于环境浮动上界的典型症状。处置：到「灵敏度调节 → 无人基线定参」" +
                "用它把阈值压到「刚好高于浮动上界」（比设备自带底噪检测更灵敏），也可先跑部署向导"

        VerdictKind.MISSED_DETECTION ->
            "判定：漏报主导。走廊有人走动时只有 ${pct(detectionRate)} 的帧被判「有人」" +
                "——阈值可能被抬得过高，或门板对能量的衰减超出预期。" +
                "处置：到「灵敏度调节」适当下调相关距离门的阈值，并用工程模式对照各门峰值确认衰减程度"

        VerdictKind.DISPLAY_DISTORTION ->
            "判定：显示层在非保持窗口改变了距离值（预期不应出现），需检查 HomeViewModel 的显示逻辑"
    }

    private fun pct(rate: Double?): String = if (rate == null) "?" else "%.0f%%".format(rate * 100)

    // ---- 统计工具 ----

    private fun fpsOf(rows: Int, durationMs: Long): Double =
        if (durationMs <= 0L) 0.0 else rows * 1000.0 / durationMs

    private fun segment(rows: List<DistanceDiagSample>): Segment? {
        if (rows.isEmpty()) return null
        val distances = rows.map { it.detectDistanceCm }.sorted()
        val jitters = jitterSeries(rows)
        return Segment(
            rows = rows.size,
            detectMin = distances.first(),
            detectMax = distances.last(),
            detectMedian = percentile(distances.toIntArray(), 0.5),
            jitterP50 = percentile(jitters, 0.5),
            jitterP90 = percentile(jitters, 0.9),
            maxJump = jitters.maxOrNull() ?: 0,
            targetFrames = rows.count { it.hasTarget },
        )
    }

    /**
     * 逐帧距离变化量序列：只对**同一帧类型**的相邻帧求差（basic 与 engineering 混排会
     * 制造虚假跳变），按 relMs 排序保证时间顺序正确。
     */
    private fun jitterSeries(rows: List<DistanceDiagSample>): IntArray {
        val out = ArrayList<Int>()
        rows.groupBy { it.frameType }.forEach { (_, group) ->
            val ordered = group.sortedBy { it.relMs }
            for (i in 1 until ordered.size) {
                out += kotlin.math.abs(ordered[i].detectDistanceCm - ordered[i - 1].detectDistanceCm)
            }
        }
        return out.toIntArray()
    }

    /** 最近秩分位数（样本量小时比插值更稳，便于单测固化） */
    internal fun percentile(sorted: IntArray, p: Double): Int {
        if (sorted.isEmpty()) return 0
        val idx = ((sorted.size - 1) * p).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }
}

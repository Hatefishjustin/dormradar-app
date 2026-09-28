package com.dormradar.app.core.log

import android.content.Context
import android.os.Build
import com.dormradar.app.core.data.EventRepository
import com.dormradar.app.core.device.RadarConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日志导出（docs/04 §3.7）：打包 zip = 运行日志 + 原始帧 + 事件 CSV + 当前配置 + 距离诊断，
 * 输出到 cacheDir/logs/，由 UI 用 FileProvider 分享出去。
 *
 * 清理策略（2026-09-28 修正）：原实现 `dir.listFiles().forEach { it.delete() }` 会把
 * **刚导出、正躺在系统分享面板里等待接收方的那个文件**一并删掉（接收方稍后读取即失败）。
 * 现在只清理本模块产出、且已超过 [EXPORT_TTL_MS] 的文件——新导出的文件永不落在清理范围内，
 * 同时旧文件仍会被回收，缓存不会无限膨胀。
 */
@Singleton
class LogExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logStore: LogStore,
    private val frameLogStore: FrameLogStore,
    private val diagRecorder: DistanceDiagRecorder,
    private val eventRepository: EventRepository,
    private val connection: RadarConnection,
) {
    private companion object {
        /** 导出文件保留时长：超过则可被下次导出回收（远大于"导出后立即分享"的时间窗） */
        const val EXPORT_TTL_MS = 10 * 60 * 1000L
        val OWN_PREFIXES = listOf("dormradar_log_", "distance_diag_")
    }

    suspend fun export(): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        cleanOldExports(dir)

        val dump = LogFormats.configDump(
            info = connection.deviceInfo.value,
            appVersion = appVersion(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
        val runtime = buildString {
            appendLine(dump)
            appendLine()
            appendLine("==== 运行日志（内存环形缓冲，最多 ${LogStore.MAX_ENTRIES} 条） ====")
            logStore.logs.value.forEach { appendLine(LogFormats.runtimeLine(it)) }
        }
        val frames = buildString {
            appendLine("==== 原始帧（内存环形缓冲，最多 ${FrameLogStore.MAX_ENTRIES} 帧） ====")
            if (frameLogStore.frames.value.isEmpty()) {
                appendLine("（未开启记录或无数据——到「设置 → 日志与导出」开启后重试）")
            } else {
                frameLogStore.frames.value.forEach { appendLine(LogFormats.frameLine(it)) }
            }
        }
        val csv = LogFormats.eventsCsv(eventRepository.dumpAll())
        val diag = diagRecorder.snapshot()

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val zip = File(dir, "dormradar_log_$stamp.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { zos ->
            fun entry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            entry("runtime_log.txt", runtime)
            entry("frame_log.txt", frames)
            entry("events.csv", csv)
            entry("device_config.txt", dump)
            entry("distance_diag.csv", DistanceDiagFormats.csv(diag))
            // 结论文本单独成文件：CSV 保持纯数据，分析结论不污染表格解析
            diagSummaryText(diag)?.let { entry("distance_diag_summary.txt", it) }
        }
        zip
    }

    /**
     * 单独导出距离诊断 CSV（cacheDir/logs/distance_diag_<时间戳>.csv，FileProvider 分享用）。
     * 保持纯 CSV（分析结论在诊断页与 zip 内的 summary 文件里）。
     */
    suspend fun exportDistanceCsv(): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        cleanOldExports(dir)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        File(dir, "distance_diag_$stamp.csv").apply {
            writeText(DistanceDiagFormats.csv(diagRecorder.snapshot()))
        }
    }

    /** 自动分析结论（无样本时返回 null）；解析度从当前设备信息取，取不到按 0.75m */
    private fun diagSummaryText(samples: List<DistanceDiagSample>): String? {
        val resolutionCm = if (connection.deviceInfo.value?.resolution02m == true) 20 else 75
        val summary = DistanceDiagAnalysis.summarize(samples, resolutionCm) ?: return null
        return buildString {
            appendLine("==== 距离诊断自动分析（共 ${summary.totalRows} 行 / ${summary.durationMs} ms） ====")
            appendLine("距离分辨率：$resolutionCm cm")
            appendLine()
            appendLine("【结论】${summary.verdict}")
            appendLine()
            appendLine("【分项】")
            summary.findings.forEach { appendLine("- $it") }
            appendLine()
            appendLine("【说明】ui_detect_cm 与 detect_dist_cm 的差异只可能来自 ui_hold=1 的")
            appendLine("「目标丢失保持 2 秒」窗口——首页对 detect 距离不做任何平滑。")
        }
    }

    /** 只回收本模块产出的、已过保留期的旧文件 */
    private fun cleanOldExports(dir: File) {
        val cutoff = System.currentTimeMillis() - EXPORT_TTL_MS
        dir.listFiles()?.forEach { f ->
            val own = f.isFile && OWN_PREFIXES.any { f.name.startsWith(it) }
            if (own && f.lastModified() < cutoff) f.delete()
        }
    }

    private fun appVersion(): String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }
}

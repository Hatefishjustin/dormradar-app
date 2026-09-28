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
 * 日志导出（docs/04 §3.7）：打包 zip = 运行日志 + 原始帧 + 事件 CSV + 当前配置，
 * 输出到 cacheDir/logs/，由 UI 用 FileProvider 分享出去。旧导出会被清掉，缓存不膨胀。
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
    suspend fun export(): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }

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
            entry("distance_diag.csv", DistanceDiagFormats.csv(diagRecorder.samples.value))
        }
        zip
    }

    /**
     * 单独导出距离诊断 CSV（cacheDir/logs/distance_diag_<时间戳>.csv，FileProvider 分享用）。
     * 同时会把诊断 CSV 一并并入常规 zip 导出（见 [export]）。
     */
    suspend fun exportDistanceCsv(): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        File(dir, "distance_diag_$stamp.csv").apply {
            writeText(DistanceDiagFormats.csv(diagRecorder.samples.value))
        }
    }

    private fun appVersion(): String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }
}

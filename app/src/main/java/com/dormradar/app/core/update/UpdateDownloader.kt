package com.dormradar.app.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新 APK 下载与安装引导（用户需求 2026-10-07）。
 * - 下载到 cacheDir/update/（FileProvider 需在 file_paths.xml 暴露该路径）；
 * - 私有仓库的资产下载需要令牌（Authorization 头，不落日志）；
 * - 安装前检查「允许安装未知应用」权限，未授予则引导跳系统设置。
 */
object UpdateDownloader {

    /** 下载 release 的 APK；onProgress 回调 0~100 */
    suspend fun download(
        context: Context,
        release: GiteeRelease,
        token: String?,
        onProgress: (Int) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "update").apply { mkdirs() }
            // 清掉旧 APK（同名或不同版本都只保留最新下载）
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, release.apkName)

            val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                if (!token.isNullOrBlank()) {
                    conn.setRequestProperty("Authorization", "token ${token.trim()}")
                }
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("下载失败：Gitee 返回 HTTP $code")
                val total = conn.contentLengthLong.let { if (it > 0) it else -1L }
                var downloaded = 0L
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) onProgress((downloaded * 100 / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
                onProgress(100)
                out
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * 生成安装 Intent：未授予「安装未知应用」权限时返回 null 并通过 [onNeedPermission]
     * 引导跳系统设置（装机后再次点击即可安装）。
     */
    fun installIntent(context: Context, apk: File, onNeedPermission: () -> Unit): Intent? {
        val pm = context.applicationContext.packageManager
        if (!pm.canRequestPackageInstalls()) {
            onNeedPermission()
            return null
        }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 跳「安装未知应用」授权页的 Intent（由 UI 层 startActivity） */
    fun unknownSourceSettingsIntent(context: Context): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

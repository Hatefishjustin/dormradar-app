package com.dormradar.app.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gitee 更新检查（docs/08 §2.26：App 内自动更新）。
 *
 * GET https://gitee.com/api/v5/repos/{owner}/{repo}/releases/latest
 * - 更新仓库为**公开**仓库，匿名访问即可（App 内零令牌，用户决策 2026-10-07）；
 * - 网络 IO 全部在 Dispatchers.IO。
 */
object UpdateChecker {

    /** 检查结果：release = 有新版本可分析；null = 已是最新/无 APK 资产/解析失败（用 message 区分） */
    sealed interface CheckResult {
        data class NewRelease(val release: GiteeRelease) : CheckResult
        data class UpToDate(val message: String) : CheckResult
        data class Failure(val message: String) : CheckResult
    }

    suspend fun fetch(owner: String, repo: String, currentVersionCode: Int): CheckResult =
        withContext(Dispatchers.IO) {
            if (owner.isBlank() || repo.isBlank()) {
                return@withContext CheckResult.Failure("更新源未配置")
            }
            val urlStr = "https://gitee.com/api/v5/repos/$owner/$repo/releases/latest"
            try {
                val (code, text) = httpGet(urlStr)
                when (code) {
                    200 -> {
                        val release = GiteeReleaseParser.parse(text)
                        when {
                            release == null ->
                                CheckResult.Failure("Release 格式无法识别（需含 dormradar-<版本>-<versionCode>.apk 资产）")
                            release.isNewerThan(currentVersionCode) -> CheckResult.NewRelease(release)
                            else -> CheckResult.UpToDate("已是最新版本（v${currentVersionCode}）")
                        }
                    }
                    404 -> CheckResult.Failure("更新源不可用：仓库不存在或还没有发布任何 Release（404）")
                    else -> CheckResult.Failure("Gitee 返回 HTTP $code")
                }
            } catch (e: IOException) {
                CheckResult.Failure("网络请求失败：${e.message ?: "未知 IO 错误"}")
            } catch (e: Exception) {
                CheckResult.Failure("检查更新失败：${e.message ?: e.javaClass.simpleName}")
            }
        }

    /** GET 并返回 (HTTP 状态码, 响应体文本) */
    private fun httpGet(urlStr: String): Pair<Int, String> {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.buffered()?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            code to text
        } finally {
            conn.disconnect()
        }
    }
}

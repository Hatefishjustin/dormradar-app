package com.dormradar.app.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gitee 更新检查（docs/08 §3：App 内自动更新，用户需求 2026-10-07）。
 *
 * GET https://gitee.com/api/v5/repos/{owner}/{repo}/releases/latest
 * - 公开仓库匿名可访问；私有仓库需要 `?access_token=`；
 * - **令牌安全**：token 只来自设置页（DataStore），本类不落日志、不写文件；
 * - 网络 IO 全部在 Dispatchers.IO。
 */
object UpdateChecker {

    /** 检查结果：release = 有新版本可分析；null = 已是最新/无 APK 资产/解析失败（用 message 区分） */
    sealed interface CheckResult {
        data class NewRelease(val release: GiteeRelease) : CheckResult
        data class UpToDate(val message: String) : CheckResult
        data class Failure(val message: String) : CheckResult
    }

    suspend fun fetch(owner: String, repo: String, token: String?, currentVersionCode: Int): CheckResult =
        withContext(Dispatchers.IO) {
            if (owner.isBlank() || repo.isBlank()) {
                return@withContext CheckResult.Failure("更新源未配置：请先在设置里填写 Gitee 用户名与仓库名")
            }
            val urlStr = buildString {
                append("https://gitee.com/api/v5/repos/")
                append(owner.trim())
                append('/')
                append(repo.trim())
                append("/releases/latest")
                if (!token.isNullOrBlank()) {
                    append("?access_token=")
                    append(token.trim())
                }
            }
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
                    401 -> CheckResult.Failure("鉴权失败：令牌无效或已过期（401）。私有仓库才需要填令牌")
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

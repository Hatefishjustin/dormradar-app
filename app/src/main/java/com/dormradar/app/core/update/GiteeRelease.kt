package com.dormradar.app.core.update

/**
 * Gitee Release 数据模型 + 极简 JSON 解析（纯 Kotlin 可单测）。
 *
 * 为什么手写解析器：项目零第三方依赖（docs/08 §5.5），kotlinx-serialization 未引入；
 * org.json 在 JVM 单测里是空壳。Gitee releases/latest 响应只需要 5 个字段，
 * 一个递归下降解析器（object/array/string/number/bool/null）足够且完全可测。
 *
 * 期望的响应结构（Gitee API v5 GET /repos/{owner}/{repo}/releases/latest）：
 * ```json
 * {
 *   "tag_name": "v0.6.0",
 *   "name": "v0.6.0",
 *   "body": "更新说明…",
 *   "assets": [ { "name": "dormradar-0.6.0-11.apk",
 *                 "browser_download_url": "https://gitee.com/…/dormradar-0.6.0-11.apk" } ]
 * }
 * ```
 * **发布约定**：APK 资产命名 `dormradar-<版本>-<versionCode>.apk`，versionCode 从文件名尾段提取。
 */
data class GiteeRelease(
    val tag: String,
    val name: String,
    val body: String,
    val versionCode: Int,
    val apkName: String,
    val apkUrl: String,
) {
    /** 是否比 [currentCode] 新（相同版本返回 false，回退返回 false） */
    fun isNewerThan(currentCode: Int): Boolean = versionCode > currentCode

    companion object {
        /** 从 APK 资产名尾段提取 versionCode：`dormradar-0.6.0-11.apk` → 11；不符返回 null */
        fun versionCodeFromApkName(name: String): Int? {
            val match = Regex("-(\\d+)\\.apk$").find(name) ?: return null
            return match.groupValues[1].toIntOrNull()
        }
    }
}

object GiteeReleaseParser {

    /** 从 releases/latest 的 JSON 文本解析；结构不符/没有 APK 资产返回 null */
    fun parse(json: String): GiteeRelease? {
        val root = JsonLite.parse(json) as? Map<*, *> ?: return null
        val tag = (root["tag_name"] as? String) ?: return null
        val name = (root["name"] as? String) ?: tag
        val body = (root["body"] as? String) ?: ""
        val assets = root["assets"] as? List<*> ?: return null
        for (asset in assets) {
            val map = asset as? Map<*, *> ?: continue
            val apkName = (map["name"] as? String) ?: continue
            if (!apkName.endsWith(".apk", ignoreCase = true)) continue
            val url = (map["browser_download_url"] as? String) ?: continue
            val code = GiteeRelease.versionCodeFromApkName(apkName) ?: continue
            return GiteeRelease(
                tag = tag,
                name = name,
                body = body,
                versionCode = code,
                apkName = apkName,
                apkUrl = url,
            )
        }
        return null
    }
}

/**
 * 极简递归下降 JSON 解析（只产 Map/List/String/Double/Boolean/null）。
 * 字符串支持标准转义（反斜杠系列与 \uXXXX）。
 */
internal object JsonLite {

    fun parse(text: String): Any? {
        var index = 0

        fun skipWs() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun expect(word: String) {
            if (!text.startsWith(word, index)) throw IllegalArgumentException("bad literal at $index")
            index += word.length
        }

        fun parseString(): String {
            index++ // 开引号
            val sb = StringBuilder()
            while (index < text.length) {
                val c = text[index]
                when {
                    c == '"' -> { index++; return sb.toString() }
                    c == '\\' -> {
                        index++
                        if (index >= text.length) throw IllegalArgumentException("bad escape")
                        when (val e = text[index]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (index + 4 >= text.length) throw IllegalArgumentException("bad unicode")
                                sb.append(text.substring(index + 1, index + 5).toInt(16).toChar())
                                index += 4
                            }
                            else -> throw IllegalArgumentException("bad escape \\$e")
                        }
                        index++
                    }
                    else -> { sb.append(c); index++ }
                }
            }
            throw IllegalArgumentException("unterminated string")
        }

        fun parseNumber(): Double {
            val start = index
            while (index < text.length && (text[index].isDigit() || text[index] in "+-.eE")) index++
            if (start == index) throw IllegalArgumentException("bad number at $index")
            return text.substring(start, index).toDouble()
        }

        fun parseValue(): Any? {
            skipWs()
            if (index >= text.length) throw IllegalArgumentException("unexpected end")
            return when (text[index]) {
                '{' -> {
                    index++
                    val map = LinkedHashMap<String, Any?>()
                    skipWs()
                    if (index < text.length && text[index] == '}') { index++; return map }
                    while (true) {
                        skipWs()
                        val key = parseString()
                        skipWs()
                        if (index >= text.length || text[index] != ':') {
                            throw IllegalArgumentException("expect colon at $index")
                        }
                        index++
                        map[key] = parseValue()
                        skipWs()
                        if (index >= text.length) throw IllegalArgumentException("unterminated object")
                        when (text[index]) {
                            ',' -> index++
                            '}' -> { index++; return map }
                            else -> throw IllegalArgumentException("bad object at $index")
                        }
                    }
                }
                '[' -> {
                    index++
                    val list = ArrayList<Any?>()
                    skipWs()
                    if (index < text.length && text[index] == ']') { index++; return list }
                    while (true) {
                        list += parseValue()
                        skipWs()
                        if (index >= text.length) throw IllegalArgumentException("unterminated array")
                        when (text[index]) {
                            ',' -> index++
                            ']' -> { index++; return list }
                            else -> throw IllegalArgumentException("bad array at $index")
                        }
                    }
                }
                '"' -> parseString()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> parseNumber()
            }
        }

        return try {
            parseValue()
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

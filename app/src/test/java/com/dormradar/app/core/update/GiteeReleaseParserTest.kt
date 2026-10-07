package com.dormradar.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gitee Release 解析与版本比较（手写极简 JSON 解析器的黄金测试） */
class GiteeReleaseParserTest {

    /** 模拟 Gitee API v5 releases/latest 的真实响应结构 */
    private val golden = """
        {
          "id": 123456,
          "tag_name": "v0.6.0",
          "target_commitish": "master",
          "prerelease": false,
          "name": "v0.6.0 首页卡片自定义",
          "body": "新增：首页卡片自定义\n更新：修复若干问题\n含 \"引号转义\" 与 https://gitee.com/link",
          "draft": false,
          "assets": [
            { "name": "source.zip", "browser_download_url": "https://gitee.com/a/b/releases/download/v0.6.0/source.zip" },
            { "name": "dormradar-0.6.0-11.apk", "browser_download_url": "https://gitee.com/a/b/releases/download/v0.6.0/dormradar-0.6.0-11.apk" }
          ],
          "created_at": "2026-10-07T10:00:00+08:00"
        }
    """.trimIndent()

    @Test
    fun `解析黄金样例 - 取 APK 资产与版本码`() {
        val r = GiteeReleaseParser.parse(golden)
        assertNotNull(r)
        r!!
        assertEquals("v0.6.0", r.tag)
        assertEquals("v0.6.0 首页卡片自定义", r.name)
        assertTrue(r.body.contains("首页卡片自定义"))
        assertTrue(r.body.contains("\"引号转义\"")) // JSON 转义还原
        assertEquals("dormradar-0.6.0-11.apk", r.apkName)
        assertTrue(r.apkUrl.startsWith("https://gitee.com/"))
        assertEquals(11, r.versionCode)
    }

    @Test
    fun `没有 APK 资产返回 null`() {
        val json = """
            {"tag_name":"v1","name":"v1","body":"","assets":[
              {"name":"readme.txt","browser_download_url":"https://x/readme.txt"}]}
        """.trimIndent()
        assertNull(GiteeReleaseParser.parse(json))
        assertNull(GiteeReleaseParser.parse("""{"tag_name":"v1","assets":[]}"""))
        assertNull(GiteeReleaseParser.parse("not json at all"))
        assertNull(GiteeReleaseParser.parse(""))
    }

    @Test
    fun `资产名不含版本码尾段返回 null`() {
        val json = """
            {"tag_name":"v1","assets":[{"name":"app.apk","browser_download_url":"https://x/app.apk"}]}
        """.trimIndent()
        assertNull(GiteeReleaseParser.parse(json))
    }

    @Test
    fun `版本码提取边界`() {
        assertEquals(11, GiteeRelease.versionCodeFromApkName("dormradar-0.6.0-11.apk"))
        assertEquals(2, GiteeRelease.versionCodeFromApkName("app-1.0-2.apk"))
        assertEquals(123, GiteeRelease.versionCodeFromApkName("dormradar_123.apk".replace("_", "-")))
        assertNull(GiteeRelease.versionCodeFromApkName("readme.txt"))
        assertNull(GiteeRelease.versionCodeFromApkName("app-beta.apk"))
    }

    @Test
    fun `isNewerThan 边界`() {
        val r11 = GiteeRelease("v", "v", "", 11, "a.apk", "u")
        assertTrue(r11.isNewerThan(10))
        assertFalse(r11.isNewerThan(11)) // 相同版本
        assertFalse(r11.isNewerThan(12)) // 回退
        assertFalse(r11.isNewerThan(100)) // 跨大版本
    }

    @Test
    fun `JSON 布尔与数字类型字段不影响解析`() {
        // prerelease/draft 是 boolean、id 是 number：解析器需正确跳过
        val r = GiteeReleaseParser.parse(golden)
        assertNotNull(r)
    }
}

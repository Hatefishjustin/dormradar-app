package com.dormradar.app.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** APK 头校验纯函数测试（v0.6.2，防「错误页存成 .apk」装不上） */
class ApkValidationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(bytes: ByteArray): File = tmp.newFile("x.bin").apply { writeBytes(bytes) }

    @Test
    fun `真 APK 头（PK）判定为有效`() {
        val f = write(byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(64))
        assertTrue(ApkValidation.looksLikeApk(f))
    }

    @Test
    fun `HTML 错误页判定为无效`() {
        val f = write("<!DOCTYPE html><html>".toByteArray() + ByteArray(64))
        assertFalse(ApkValidation.looksLikeApk(f))
    }

    @Test
    fun `空文件判定为无效`() {
        assertFalse(ApkValidation.looksLikeApk(write(ByteArray(0))))
    }

    @Test
    fun `文件不存在判定为无效`() {
        assertFalse(ApkValidation.looksLikeApk(File(tmp.root, "nope.bin")))
    }
}

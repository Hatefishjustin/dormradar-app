package com.dormradar.app.core.update

import java.io.File
import java.io.RandomAccessFile

/**
 * APK 文件有效性校验（v0.6.2）：APK 本质是 ZIP，文件头固定为「PK」。
 * 下载链路偶发拿到的是 Gitee 错误页/登录页 HTML，不校验的话点安装只会报
 * 「解析包出错」甚至无声失败。纯 Kotlin 无 Android 依赖，可 JVM 单测。
 */
object ApkValidation {

    /** ZIP 本地文件头魔数（APK 与 ZIP 同构） */
    private const val MIN_BYTES = 4

    fun looksLikeApk(file: File): Boolean {
        if (!file.isFile) return false
        if (file.length() < MIN_BYTES) return false
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(2)
            raf.readFully(head)
            return head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        }
    }
}

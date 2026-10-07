package com.dormradar.app.core.log

import com.dormradar.app.core.device.DeviceInfo

/**
 * 报 bug 的设备摘要（一键复制到剪贴板，用户需求 2026-10-07）。
 * 纯 Kotlin 可单测；一行一条，方便贴在微信/邮件里。
 */
object FeedbackSummary {

    fun build(
        appVersion: String,
        deviceModel: String,
        info: DeviceInfo?,
    ): String = buildString {
        appendLine("【设备信息摘要】")
        appendLine("App 版本：v$appVersion")
        appendLine("手机：$deviceModel")
        if (info == null) {
            appendLine("雷达：未连接")
            return@buildString
        }
        if (info.isFake) appendLine("雷达：模拟设备")
        appendLine("雷达 MAC：${info.mac}")
        appendLine("固件：${info.firmwareVersion ?: "未知"}")
        appendLine("分辨率：${if (info.resolution02m == true) "0.2m" else "0.75m（默认）"}")
        info.params?.let { p ->
            appendLine(
                "最远门：运动 ${p.maxMoveGate} / 静止 ${p.maxStaticGate}；无人 ${p.unmannedSeconds}s",
            )
            appendLine(
                "灵敏度：运动 ${p.motionSensitivities.toList()} 静止 ${p.staticSensitivities.toList()}",
            )
        }
    }
}

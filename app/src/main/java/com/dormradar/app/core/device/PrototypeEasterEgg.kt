package com.dormradar.app.core.device

/**
 * 原型机一号机彩蛋（用户需求 2026-10-08）：
 * MAC 21:A9:6C:A4:63:5B 是 Justin 的第一台成品雷达，所有人在首次连接它时
 * 弹出隐藏成就弹窗（powered by Justin）。弹窗有两个按钮：「一般般」「太有实力了」，
 * 点「一般般」关不掉（吐槽文案轮换 + 按钮文案逐级变化），只有点「太有实力了」才能关闭。
 * 纯 Kotlin 无 Android 依赖，可 JVM 单测。
 */
object PrototypeEasterEgg {

    /** 一号机（第一台成品原型机）的 MAC */
    const val TARGET_MAC = "21:A9:6C:A4:63:5B"

    /** MAC 规范化：去掉分隔符、统一大写（Android 地址为大写冒号格式，但比较不做格式假设） */
    fun normalize(mac: String): String = mac.replace(":", "").replace("-", "").uppercase()

    /** 该连接是否要弹彩蛋：MAC 是一号机 且 从未见过（seenMacs 已规范化） */
    fun shouldShow(connMac: String, seenMacs: Set<String>): Boolean =
        normalize(connMac) == normalize(TARGET_MAC) && normalize(connMac) !in seenMacs

    /** 点「太有实力了」后要永久记住的规范化 MAC（存 DataStore） */
    fun macToRemember(): String = normalize(TARGET_MAC)

    /** 点「一般般」的吐槽文案（按点击次数轮换，index 从 0 起） */
    private val HUMBLE_REPLIES = listOf(
        "嗯？本机检测到了不诚实的信号。",
        "再摸着良心说一次？",
        "诚意扫描中……扫描结果：口是心非。",
        "一般认为，你其实觉得很有实力。",
        "这个按钮已经决定不再相信你了。",
    )

    fun humbleReply(humbleCount: Int): String =
        HUMBLE_REPLIES[(humbleCount - 1).coerceAtLeast(0).mod(HUMBLE_REPLIES.size)]

    /** 「一般般」按钮文案随点击次数逐级变化（1 起步；0 = 还没点过） */
    fun humbleButtonLabel(humbleCount: Int): String = when {
        humbleCount <= 0 -> "一般般"
        humbleCount == 1 -> "手滑了，再选一次"
        humbleCount == 2 -> "点这个已经没用了"
        else -> "（按钮罢工中）"
    }
}

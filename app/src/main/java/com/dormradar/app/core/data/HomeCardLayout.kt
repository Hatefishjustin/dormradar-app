package com.dormradar.app.core.data

/**
 * 首页卡片布局（用户需求 2026-10-07：首页卡片可自定义——显示/隐藏 + 上下排序）。
 *
 * 编码为单行 CSV 存 DataStore 单键（`id:1,id:0,...`），符合 SettingsStore「轻量键值」惯例：
 * - 未知 id 忽略（向前兼容：老版本收到新版本多出的卡片 id 不崩）；
 * - 缺失的 id 按默认顺序补齐（可见性取默认）；
 * - 空/脏串整体回退 [DEFAULT]（= 标准布局）。
 */
enum class HomeCardId {
    STATUS,        // 状态大卡
    SCOPE,         // 雷达视图
    GRID,          // 实时数据网格
    ENERGY,        // 能量实时曲线
    ENGINEERING,   // 工程模式每门能量图（新增卡片，默认关）
    QUICK_SETUP,   // 一键设置入口
    MONITOR,       // 后台监控
    DEVICE_INFO,   // 设备信息
}

/** 有序的卡片清单：id + 是否显示 */
data class HomeCardLayout(val cards: List<Pair<HomeCardId, Boolean>>) {

    /** 编码为 `id:1,id:0` 形式的 CSV（仅保留显示中的顺序语义：全部条目都编码，含隐藏） */
    fun encode(): String = cards.joinToString(",") { "${it.first.name}:${if (it.second) "1" else "0"}" }

    companion object {
        /** 默认顺序（v0.5.5 首页现状 + 新增工程能量图，工程能量图默认关） */
        val DEFAULT_ORDER: List<Pair<HomeCardId, Boolean>> = listOf(
            HomeCardId.STATUS to true,
            HomeCardId.SCOPE to true,
            HomeCardId.GRID to true,
            HomeCardId.ENERGY to true,
            HomeCardId.ENGINEERING to false,
            HomeCardId.QUICK_SETUP to true,
            HomeCardId.MONITOR to true,
            HomeCardId.DEVICE_INFO to true,
        )

        val STANDARD = HomeCardLayout(DEFAULT_ORDER)

        /** 简洁：只显示状态大卡 / 雷达视图 / 后台监控（完整 8 项清单，其余显式隐藏——保证 encode→decode 往返稳定） */
        val SIMPLE = HomeCardLayout(
            listOf(
                HomeCardId.STATUS to true,
                HomeCardId.SCOPE to true,
                HomeCardId.GRID to false,
                HomeCardId.ENERGY to false,
                HomeCardId.ENGINEERING to false,
                HomeCardId.QUICK_SETUP to false,
                HomeCardId.MONITOR to true,
                HomeCardId.DEVICE_INFO to false,
            ),
        )

        /** 全能：全部卡片打开 */
        val FULL = HomeCardLayout(HomeCardId.entries.map { it to true })

        /** 解码；raw 为空/格式坏时回退 [STANDARD]，缺项按默认顺序与默认可见性补齐 */
        fun decode(raw: String?): HomeCardLayout {
            if (raw.isNullOrBlank()) return STANDARD
            val parsed = mutableListOf<Pair<HomeCardId, Boolean>>()
            raw.split(',').forEach { token ->
                val parts = token.trim().split(':')
                if (parts.size != 2) return@forEach
                val id = HomeCardId.entries.firstOrNull { it.name.equals(parts[0], ignoreCase = true) }
                    ?: return@forEach
                val visible = when (parts[1].trim()) {
                    "1" -> true
                    "0" -> false
                    else -> return@forEach
                }
                if (parsed.none { it.first == id }) parsed += id to visible
            }
            if (parsed.isEmpty()) return STANDARD
            // 缺失的 id 按默认顺序与默认可见性补齐（追加到末尾）
            DEFAULT_ORDER.forEach { def ->
                if (parsed.none { it.first == def.first }) parsed += def
            }
            return HomeCardLayout(parsed)
        }
    }
}

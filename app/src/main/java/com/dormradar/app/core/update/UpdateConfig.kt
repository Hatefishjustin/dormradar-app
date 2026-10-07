package com.dormradar.app.core.update

/**
 * 更新源配置（写死版，用户决策 2026-10-07：「你直接搞定 gitee 然后写死在程序里面不要让用户自己填」）。
 *
 * ⚠️ 安全提示：本文件含 Gitee 私人令牌，**仓库必须保持私有**（hatefishjustin/dormradar-app 为私有仓库）。
 * 令牌权限 = 该账号的仓库读写；泄露请立即到 Gitee「设置 → 私人令牌」重置。
 */
object UpdateConfig {
    const val OWNER = "hatefishjustin"
    const val REPO = "dormradar-app"
    const val TOKEN = "219e1a35842b42531dbd4553f51b68f5"
}

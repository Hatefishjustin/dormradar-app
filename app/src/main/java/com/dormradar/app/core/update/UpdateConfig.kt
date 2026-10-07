package com.dormradar.app.core.update

/**
 * 更新源配置（公开仓库，匿名访问；用户决策 2026-10-07：「不要把令牌写死进去，让软件扫描识别指定的库」）。
 * 令牌只存在于用户本地的 publish_release.ps1（发版上传用），App 内零令牌。
 */
object UpdateConfig {
    const val OWNER = "hatefishjustin"
    const val REPO = "dormradar-update"
}

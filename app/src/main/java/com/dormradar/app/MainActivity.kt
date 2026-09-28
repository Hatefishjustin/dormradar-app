package com.dormradar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dormradar.app.ui.AppRoot
import com.dormradar.app.ui.theme.DormRadarTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity 入口（docs/04 §5）：Compose 承载全部 UI，
 * 底部导航 = 首页（扫描/监控）/ 事件 / 设置。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DormRadarTheme {
                AppRoot()
            }
        }
    }
}

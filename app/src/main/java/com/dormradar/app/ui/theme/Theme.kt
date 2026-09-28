package com.dormradar.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 品牌色初稿：深蓝底 + 雷达绿，后续在 ui/ 内细化
private val RadarGreen = Color(0xFF4ADE80)
private val DeepBlue = Color(0xFF0F1E2E)

private val LightColors = lightColorScheme(
    primary = Color(0xFF166534),
    secondary = RadarGreen,
    background = Color(0xFFF7FAF7),
)

private val DarkColors = darkColorScheme(
    primary = RadarGreen,
    secondary = RadarGreen,
    background = DeepBlue,
)

@Composable
fun DormRadarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12+ 支持动态取色；深色下仍用品牌深蓝以保证夜间对比度
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme -> {
            val context = LocalContext.current
            dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

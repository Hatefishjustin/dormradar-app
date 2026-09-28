package com.dormradar.app.feature.config

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * 详细设置子页通用骨架：顶栏（返回 + 标题 + 可选 actions）+ 内容区。
 * 子页不显示底部 Tab（AppRoot 控制），营造"深入设置"层次（PRD 4.0）。
 * content 收到的 Modifier 已应用 Scaffold 内边距，直接链式使用即可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (modifier: Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = actions,
            )
        },
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}

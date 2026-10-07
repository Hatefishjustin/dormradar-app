package com.dormradar.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dormradar.app.core.update.UpdateDownloader

/**
 * 启动更新提示（用户需求 2026-10-07：「每次打开如果发现有新版本自动弹出询问是否更新」）。
 * 挂在 AppRoot 顶层；每次冷启动检查一次，发现新版弹窗询问；点「下次再说」本次不再打扰，
 * 「立即更新」下载完成后按钮变「立即安装」。
 */
@Composable
fun UpdatePromptHost(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val versionCode = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        } catch (_: Exception) {
            0
        }
    }
    var checked by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (checked == 0) {
            checked = 1
            viewModel.checkUpdate(versionCode)
        }
    }

    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val release = updateState.release ?: return
    // 用户点了「下次再说」→ 本次会话不再弹（除非已经下载完成等安装）
    if (updateState.downloadedApk == null && updateState.silenced) return

    AlertDialog(
        onDismissRequest = { if (updateState.downloadedApk == null) viewModel.silenceUpdatePrompt() },
        properties = DialogProperties(dismissOnBackPress = updateState.downloadedApk == null),
        title = { Text("发现新版本：${release.name}") },
        text = {
            Column {
                if (updateState.downloading) {
                    LinearProgressIndicator(
                        progress = { updateState.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "下载中 ${updateState.progress}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (updateState.downloadedApk != null) {
                    Text("下载完成，点「立即安装」即可升级（安装界面请允许安装）")
                } else if (release.body.isNotBlank()) {
                    Text(release.body, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !updateState.downloading,
                onClick = {
                    val apk = updateState.downloadedApk
                    if (apk != null) {
                        val intent = UpdateDownloader.installIntent(context, apk) {
                            context.startActivity(UpdateDownloader.unknownSourceSettingsIntent(context))
                        }
                        intent?.let { context.startActivity(it) }
                    } else {
                        viewModel.downloadUpdate()
                    }
                },
            ) {
                Text(
                    when {
                        updateState.downloading -> "下载中…"
                        updateState.downloadedApk != null -> "立即安装"
                        else -> "立即更新"
                    },
                )
            }
        },
        dismissButton = {
            if (updateState.downloadedApk == null) {
                TextButton(onClick = { viewModel.silenceUpdatePrompt() }) { Text("下次再说") }
            }
        },
    )
}

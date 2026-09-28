package com.dormradar.app.feature.logs

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dormradar.app.core.log.DistanceDiagFormats
import com.dormradar.app.core.log.DistanceDiagSample
import com.dormradar.app.feature.config.ConfigScaffold
import kotlinx.coroutines.launch
import java.io.File

/**
 * 距离诊断页（取证工具，用户反馈 2026-09-28"首页距离跳动/不准"）：
 * 开始记录后每上报帧记一行（原始帧值 + 该时刻 UI 显示值快照），导出 CSV 分析。
 * 进入自动开工程模式（顺带采集各门能量），离开自动关（模式照抄灵敏度页）。
 */
@Composable
fun DistanceDiagScreen(
    onBack: () -> Unit,
    viewModel: DistanceDiagViewModel = hiltViewModel(),
) {
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 进页自动开工程模式（顺带采集各门能量），离开自动关（模式照抄灵敏度页：onDispose 时 VM 尚存活）
    LaunchedEffect(Unit) { viewModel.setEngineering(true) }
    DisposableEffect(Unit) {
        onDispose { viewModel.setEngineering(false) }
    }

    fun shareCsv() {
        viewModel.exportCsv(
            onReady = { file: File ->
                runCatching {
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file,
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享距离诊断 CSV"))
                }.onFailure {
                    scope.launch { snackbarHostState.showSnackbar("分享失败：${it.message}") }
                }
            },
            onError = { msg: String ->
                scope.launch { snackbarHostState.showSnackbar("导出失败：$msg") }
            },
        )
    }

    ConfigScaffold(
        title = "距离诊断",
        onBack = onBack,
        actions = {
            IconButton(
                onClick = { if (recording) viewModel.stop() else viewModel.start() },
            ) {
                Icon(
                    if (recording) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                    contentDescription = if (recording) "停止记录" else "开始记录",
                )
            }
            IconButton(onClick = { viewModel.clear() }) {
                Icon(Icons.Outlined.Delete, contentDescription = "清空")
            }
            IconButton(onClick = { shareCsv() }) {
                Icon(Icons.Filled.Share, contentDescription = "导出 CSV")
            }
        },
    ) { modifier ->
        Box(modifier = modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("距离诊断记录器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "用于定位\"首页距离跳动/不准\"的根因：记录每个上报帧的原始距离/能量与该时刻 UI 实际显示值。" +
                        "对比\"原始 detect 距离\"与\"UI 显示\"两列即可区分「雷达本身抖动」与「显示处理放大」；" +
                        "若原始帧本身就错误，则怀疑解析或固件（结合门能量列判断）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (recording) "● 记录中" else "○ 未记录",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "已记录 ${samples.size} 行（环形缓冲 12000 行，超出挤掉最旧）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (samples.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (recording) "记录中，等待上报帧…（连接设备后每秒应有若干行）"
                            else "点右上角 ▶ 开始记录；等 1~2 分钟（覆盖有人走动和无人两个阶段）后点 ↗ 导出 CSV",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // 最近 40 行预览（完整数据走 CSV 导出）
                    Text(
                        "最近 ${minOf(samples.size, 40)} 行预览（CSV 列：时间,相对ms,帧类型,状态,运动距离,运动能量,静止距离,静止能量,检测距离,UI显示距离,运动门能量,静止门能量）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    LazyColumn {
                        items(samples.takeLast(40).asReversed()) { s ->
                            DiagRow(s)
                        }
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun DiagRow(s: DistanceDiagSample) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            DistanceDiagFormats.timeText(s.wallClockMs),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${s.frameType.take(3)}/状态${s.targetState}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "原始 ${s.detectDistanceCm}cm",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            if (s.displayDistanceCm >= 0) "UI ${s.displayDistanceCm}cm" else "UI —",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

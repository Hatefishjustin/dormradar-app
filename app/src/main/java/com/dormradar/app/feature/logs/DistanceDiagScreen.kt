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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.dormradar.app.core.log.DistanceDiagAnalysis
import com.dormradar.app.core.log.DistanceDiagFormats
import com.dormradar.app.core.log.DistanceDiagSample
import com.dormradar.app.feature.config.ConfigScaffold
import kotlinx.coroutines.launch
import java.io.File

/**
 * 距离诊断页（取证工具，用户反馈 2026-09-28"首页距离跳动/不准"）：
 * 开始记录后每上报帧记一行（原始帧值 + 显示管线输出），**并实时给出自动判定结论**。
 *
 * 进入自动开工程模式（顺带采集各门能量），离开自动关（模式照抄灵敏度页）。
 * 判定规则在 [DistanceDiagAnalysis]（纯 Kotlin 可单测）——避免"导出 CSV 再找人分析"的往返。
 */
@Composable
fun DistanceDiagScreen(
    onBack: () -> Unit,
    viewModel: DistanceDiagViewModel = hiltViewModel(),
) {
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 进页自动开工程模式，离开自动关（onDispose 时 VM 尚存活）
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("距离诊断", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "记录每个上报帧的距离/能量，并自动判定\"距离跳动\"的根因。" +
                            "建议：点右上角 ▶ 后，先让走廊无人静置约 1 分钟，再有人正常走动约 1 分钟。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            if (recording) "● 记录中" else "○ 未记录",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (recording) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "已记录 ${samples.size} 行（环形缓冲 12000 行，超出挤掉最旧）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ---- 自动结论卡（本页的主要产出）----
                if (summary == null) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (recording) "记录中，等待上报帧…（连接设备后每秒应有若干行）"
                                else "点右上角 ▶ 开始记录；覆盖「无人静置」和「有人走动」两段后再看结论",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    item { SummaryCard(summary!!) }
                }

                // ---- 原始数据预览 ----
                if (samples.isNotEmpty()) {
                    item {
                        Text(
                            "最近 ${minOf(samples.size, 30)} 行预览（CSV 列：epoch_ms, 时间, 相对ms, 帧类型, " +
                                "状态, 是否有人, 运动距离, 运动能量, 静止距离, 静止能量, 检测距离, UI输出距离, " +
                                "保持标记, 运动门能量, 静止门能量）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(samples.takeLast(30).asReversed()) { s -> DiagRow(s) }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** 自动结论卡：一句话判定 + 分项发现 */
@Composable
private fun SummaryCard(summary: DistanceDiagAnalysis.Summary) {
    val container = when (summary.kind) {
        DistanceDiagAnalysis.VerdictKind.DISPLAY_DISTORTION -> MaterialTheme.colorScheme.errorContainer
        DistanceDiagAnalysis.VerdictKind.STATIC_ECHO -> MaterialTheme.colorScheme.tertiaryContainer
        DistanceDiagAnalysis.VerdictKind.DEVICE_NOISE -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("自动结论", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(summary.verdict, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
            summary.findings.forEach { f ->
                Text(
                    f,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            when {
                s.inHold -> "UI ${s.displayDistanceCm}cm 保持"
                s.displayDistanceCm >= 0 -> "UI ${s.displayDistanceCm}cm"
                else -> "UI —"
            },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (s.inHold) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        )
    }
}

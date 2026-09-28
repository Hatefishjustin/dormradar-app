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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.dormradar.app.core.log.DiagPhase
import com.dormradar.app.core.log.DistanceDiagAnalysis
import com.dormradar.app.core.log.DistanceDiagFormats
import com.dormradar.app.core.log.DistanceDiagSample
import com.dormradar.app.feature.config.ConfigScaffold
import kotlinx.coroutines.launch
import java.io.File

/**
 * 距离诊断页（取证工具，用户反馈 2026-09-28"首页距离跳动/不准"）。
 *
 * 两步引导：App 明确指挥「先保持无人 45 秒 → 再到走廊走动 45 秒」，自动打阶段标记、
 * 自动出结论。不再让用户自己安排两段——那正是上一版被反馈"不知道什么时候该无人"的原因。
 * 判定规则在 [DistanceDiagAnalysis]（纯 Kotlin 可单测）。
 */
@Composable
fun DistanceDiagScreen(
    onBack: () -> Unit,
    viewModel: DistanceDiagViewModel = hiltViewModel(),
) {
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val guide by viewModel.guide.collectAsStateWithLifecycle()
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
            IconButton(onClick = viewModel::clear) {
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
                    Text(
                        "距离诊断",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点下面的按钮，App 会分两步指导你完成一次采样，然后自动判定\"距离跳动\"的根因、" +
                            "并算出误报率与检出率。全过程约 ${DistanceDiagViewModel.PHASE_SECONDS * 2} 秒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item { GuideCard(guide, viewModel) }

                // ---- 自动结论（本页的主要产出）----
                val s = summary
                if (s != null) {
                    item { SummaryCard(s) }
                }

                // ---- 原始数据预览 ----
                if (samples.isNotEmpty()) {
                    item {
                        Text(
                            "最近 ${minOf(samples.size, 30)} 行预览（CSV 列：epoch_ms, 时间, 相对ms, 帧类型, " +
                                "阶段, 状态, 是否有人, 运动距离, 运动能量, 静止距离, 静止能量, 检测距离, " +
                                "UI输出距离, 保持标记, 运动门能量, 静止门能量）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(samples.takeLast(30).asReversed()) { sample -> DiagRow(sample) }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** 两步引导卡：明确告诉用户"现在该干什么" */
@Composable
private fun GuideCard(guide: GuideUi, viewModel: DistanceDiagViewModel) {
    val container = when (guide.stage) {
        GuideStage.NO_PERSON -> MaterialTheme.colorScheme.secondaryContainer
        GuideStage.PERSON -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (guide.stage) {
                GuideStage.IDLE -> {
                    Text("两步引导采样", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "第 1 步（${DistanceDiagViewModel.PHASE_SECONDS} 秒）：保持门口无人——你也离开门口，别在雷达前方走动；\n" +
                            "第 2 步（${DistanceDiagViewModel.PHASE_SECONDS} 秒）：到走廊里正常来回走动（请别人配合，" +
                            "或你走到门外再走回来）。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "到第 2 步时 App 会提示你切换，不需要掐表。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = viewModel::startGuide, modifier = Modifier.fillMaxWidth()) {
                        Text("开始两步引导")
                    }
                }

                GuideStage.NO_PERSON -> PhaseBody(
                    step = "第 1 步 / 共 2 步",
                    title = "现在请保持无人",
                    detail = "请离开门口，不要在雷达前方走动或停留。这一步用来测出「没人时」的环境浮动上界。",
                    remain = guide.remainSeconds,
                    onCancel = viewModel::cancelGuide,
                )

                GuideStage.PERSON -> PhaseBody(
                    step = "第 2 步 / 共 2 步",
                    title = "现在请在走廊来回走动",
                    detail = "请让另一人在门外走廊正常走过（或你走到门外再回来）。这一步用来测出有人时的读数与检出率。",
                    remain = guide.remainSeconds,
                    onCancel = viewModel::cancelGuide,
                )

                GuideStage.DONE -> {
                    Text("两步采样已完成", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "结论见下方。若结果不合预期（例如采样时被人打扰），直接重新采一次。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::startGuide) { Text("重新采样") }
                        OutlinedButton(onClick = viewModel::clear) { Text("清空数据") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseBody(
    step: String,
    title: String,
    detail: String,
    remain: Int,
    onCancel: () -> Unit,
) {
    Text(step, style = MaterialTheme.typography.labelMedium)
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "剩余 $remain 秒",
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
    )
    LinearProgressIndicator(
        progress = { (DistanceDiagViewModel.PHASE_SECONDS - remain).toFloat() / DistanceDiagViewModel.PHASE_SECONDS },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = onCancel) { Text("取消") }
}

/** 自动结论卡：一句话判定 + 误报率/检出率 + 分项发现 */
@Composable
private fun SummaryCard(summary: DistanceDiagAnalysis.Summary) {
    val container = when (summary.kind) {
        DistanceDiagAnalysis.VerdictKind.DISPLAY_DISTORTION,
        DistanceDiagAnalysis.VerdictKind.FALSE_POSITIVE,
        DistanceDiagAnalysis.VerdictKind.MISSED_DETECTION,
        -> MaterialTheme.colorScheme.errorContainer

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

            val fp = summary.falsePositiveRate
            val dr = summary.detectionRate
            if (fp != null || dr != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "误报率 " + if (fp == null) "—" else "%.0f%%".format(fp * 100),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "检出率 " + if (dr == null) "—" else "%.0f%%".format(dr * 100),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            summary.findings.forEach { finding ->
                Text(
                    finding,
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
            DiagPhase.label(s.phase),
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

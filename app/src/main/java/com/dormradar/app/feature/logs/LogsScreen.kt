package com.dormradar.app.feature.logs

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dormradar.app.core.log.FrameDir
import com.dormradar.app.core.log.FrameLogEntry
import com.dormradar.app.core.log.LogEntry
import com.dormradar.app.core.log.LogLevel
import com.dormradar.app.feature.config.ConfigScaffold
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志页（阶段 4 任务 1，docs/06 §阶段 4）：
 * 运行日志（分级过滤 + 实时滚动）/ 原始帧（开关 + 十六进制）/ 顶栏导出 zip 分享。
 */
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val frames by viewModel.frames.collectAsStateWithLifecycle()
    val frameEnabled by viewModel.frameLogEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0=运行日志 1=原始帧
    var visibleLevels by remember { mutableStateOf(LogLevel.entries.toSet()) }

    fun shareZip() {
        viewModel.export(
            onReady = { file: File ->
                runCatching {
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file,
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享日志"))
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
        title = "日志",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.clearLogs() }) {
                Icon(Icons.Outlined.Delete, contentDescription = "清空")
            }
            IconButton(onClick = { shareZip() }) {
                Icon(Icons.Filled.Share, contentDescription = "导出分享")
            }
        },
    ) { modifier ->
        Box(modifier = modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("运行日志") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("原始帧") })
                }
                if (tab == 0) {
                    RuntimeLogList(logs, visibleLevels) { level ->
                        visibleLevels =
                            if (level in visibleLevels) visibleLevels - level else visibleLevels + level
                    }
                } else {
                    FrameLogList(frames, frameEnabled, viewModel::setFrameLogEnabled)
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

// ---- 运行日志 ----

@Composable
private fun RuntimeLogList(
    logs: List<LogEntry>,
    visibleLevels: Set<LogLevel>,
    onToggleLevel: (LogLevel) -> Unit,
) {
    val filtered = remember(logs, visibleLevels) { logs.filter { it.level in visibleLevels } }
    val listState = rememberLazyListState()
    // 用户停在底部附近才自动跟随（往上翻看历史时不打扰）
    val atEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(filtered.size) {
        if (filtered.isEmpty()) return@LaunchedEffect
        if (atEnd || listState.layoutInfo.totalItemsCount == 0) {
            listState.animateScrollToItem(filtered.size - 1)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            "共 ${logs.size} 条 · 显示 ${filtered.size} 条（最多保留 ${com.dormradar.app.core.log.LogStore.MAX_ENTRIES} 条，重启后清空）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            LogLevel.entries.forEach { level ->
                FilterChip(
                    selected = level in visibleLevels,
                    onClick = { onToggleLevel(level) },
                    label = { Text(level.label) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (filtered.isEmpty()) {
            EmptyHint(
                if (logs.isEmpty()) "暂无日志\n连接雷达或操作 App 后，这里会记录连接时间线和配置操作"
                else "当前筛选下没有日志（试试勾选其他级别）",
            )
        } else {
            LazyColumn(state = listState) {
                items(filtered) { entry -> RuntimeLogRow(entry) }
            }
        }
    }
}

@Composable
private fun RuntimeLogRow(entry: LogEntry) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            formatTime(entry.timeMs),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            entry.level.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = levelColor(entry.level),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "${entry.tag}: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(entry.message, style = MaterialTheme.typography.labelSmall)
    }
}

// ---- 原始帧 ----

@Composable
private fun FrameLogList(
    frames: List<FrameLogEntry>,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val atEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(frames.size) {
        if (frames.isEmpty()) return@LaunchedEffect
        if (atEnd || listState.layoutInfo.totalItemsCount == 0) {
            listState.animateScrollToItem(frames.size - 1)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "记录原始帧（调试）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "开启后记录与雷达收发的每一帧原始数据（十六进制），用于协议排障。包含鉴权命令（蓝牙密码明文），导出分享请留意。仅存内存，重启清空；模拟设备不记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        }
        Text(
            "已记录 ${frames.size} 帧（最多保留 ${com.dormradar.app.core.log.FrameLogStore.MAX_ENTRIES} 帧）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        if (frames.isEmpty()) {
            EmptyHint(
                if (enabled) "暂无帧记录\n连接真实雷达后，收发的数据帧会显示在这里"
                else "记录未开启\n打开上面的开关，再连接雷达试试",
            )
        } else {
            LazyColumn(state = listState) {
                items(frames) { entry -> FrameLogRow(entry) }
            }
        }
    }
}

@Composable
private fun FrameLogRow(entry: FrameLogEntry) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatTime(entry.timeMs),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (entry.dir == FrameDir.TX) "↑ 发送" else "↓ 接收",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (entry.dir == FrameDir.TX) MaterialTheme.colorScheme.primary else Color(0xFF2E7D32),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${entry.size} 字节",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            entry.hex,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ---- 通用 ----

@Composable
private fun EmptyHint(text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.D -> MaterialTheme.colorScheme.outline
    LogLevel.I -> MaterialTheme.colorScheme.primary
    LogLevel.W -> Color(0xFFEF6C00)
    LogLevel.E -> MaterialTheme.colorScheme.error
}

private fun formatTime(timeMs: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timeMs))

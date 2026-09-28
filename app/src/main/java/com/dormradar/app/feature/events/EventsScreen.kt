package com.dormradar.app.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dormradar.app.core.data.EventFilter
import com.dormradar.app.core.data.room.RadarEventEntity
import com.dormradar.app.core.monitor.RadarEventType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 事件时间线（PRD 4.2：有人出现/离开、连接断开/恢复、监控开关，全部带时间戳可查）。
 * 通知历史即设备运行日志的摘要（docs/01 §5.3）。
 */
@Composable
fun EventsScreen(viewModel: EventsViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<RadarEventEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("事件时间线", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showClearDialog = true }, enabled = events.isNotEmpty()) { Text("清空") }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "保留最近 30 天 · 共 ${events.size} 条（当前筛选）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        // 筛选
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            EventFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { viewModel.setFilter(f) },
                    label = { Text(f.label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (events.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
            ) {
                Text("暂无事件", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "开启首页的「后台监控」后，有人出现/离开、连接变化都会记录在这里",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events, key = { it.id }) { event ->
                    EventRow(event, onClick = { selected = event })
                }
            }
        }
    }

    // ---- 事件详情（阶段 4 任务 5：回看调参，展示当时能量快照） ----
    selected?.let { event ->
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("关闭") } },
            title = { Text("事件详情") },
            text = {
                Column {
                    Text(
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(event.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(event.detail, style = MaterialTheme.typography.bodyMedium)
                    if (event.payload.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "当时的能量快照（调参对照用）：",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            event.payload,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } },
            title = { Text("清空全部事件？") },
            text = { Text("时间线里的 ${events.size} 条记录将全部删除，无法恢复。") },
        )
    }
}

@Composable
private fun EventRow(event: RadarEventEntity, onClick: () -> Unit = {}) {
    val type = remember(event.type) { RadarEventType.entries.firstOrNull { it.name == event.type } }
    val color = when (type) {
        RadarEventType.APPEAR -> 0xFFDC2626 // 红：有人
        RadarEventType.LEAVE -> 0xFF9333EA // 紫：离开
        RadarEventType.CONNECT, RadarEventType.RECONNECTED -> 0xFF16A34A // 绿：连接正常
        RadarEventType.DISCONNECT, RadarEventType.GIVE_UP -> 0xFFEA580C // 橙：连接异常
        else -> 0xFF64748B // 灰：监控开关等
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(10.dp).background(androidx.compose.ui.graphics.Color(color), CircleShape),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        type?.label ?: event.type,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        formatEventTime(event.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    event.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 今天只显示时分秒，跨天补日期 */
private fun formatEventTime(epochMs: Long): String {
    val now = Date()
    val date = Date(epochMs)
    val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now) ==
        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(date)
    return SimpleDateFormat(
        if (today) "HH:mm:ss" else "MM-dd HH:mm",
        Locale.getDefault(),
    ).format(date)
}

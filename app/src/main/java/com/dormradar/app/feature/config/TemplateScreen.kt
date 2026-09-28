package com.dormradar.app.feature.config

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dormradar.app.core.data.room.ConfigSnapshotEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 配置模板页（阶段 4 任务 3）：把当前设备配置存为命名模板，一键应用（= 回滚手段），
 * 应用前先看与当前设备的差异。模拟设备同样可全流程体验。
 */
@Composable
fun TemplateScreen(
    onBack: () -> Unit,
    viewModel: TemplateViewModel = hiltViewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val configBusy by viewModel.configBusy.collectAsStateWithLifecycle()
    var showSaveDialog by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf<ConfigSnapshotEntity?>(null) }
    var viewing by remember { mutableStateOf<ConfigSnapshotEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    ConfigScaffold(
        title = "配置模板",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showSaveDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "保存当前配置")
            }
        },
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "把当前设备配置存为模板；换场景/调坏参数时一键应用回滚。应用前自动对比差异。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (message != null) {
                Text(
                    message!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (templates.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                ) {
                    Text("暂无模板", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "连接设备后点右上角 +，把当前配置存为模板",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(templates, key = { it.id }) { t ->
                        TemplateCard(
                            template = t,
                            onClick = { viewing = t },
                            onApply = { applying = t },
                            onDelete = { viewModel.delete(t.id) },
                        )
                    }
                }
            }
        }
    }

    if (configBusy) {
        // 简单的"设备忙"遮罩提示
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator(Modifier.height(24.dp)) }
    }

    // ---- 保存当前配置 ----
    if (showSaveDialog) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        showSaveDialog = false
                        viewModel.saveCurrent(name.trim()) { err -> message = err ?: "模板已保存" }
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("取消") } },
            title = { Text("保存当前配置为模板") },
            text = {
                Column {
                    Text(
                        viewModel.currentConfig?.let { c ->
                            "运动门 ${c.maxMoveGate} · 静止门 ${c.maxStaticGate} · 无人 ${c.unmannedSeconds}s · ${c.motionSensitivities.size} 门灵敏度表"
                        } ?: "设备未连接或参数未就绪",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("模板名称") },
                        placeholder = { Text("如：穿门标准档") },
                    )
                }
            },
        )
    }

    // ---- 查看差异 ----
    viewing?.let { t ->
        val diff = remember(t) { viewModel.diffWithCurrent(t) }
        AlertDialog(
            onDismissRequest = { viewing = null },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("关闭") } },
            title = { Text("模板：${t.name}") },
            text = {
                Text(
                    when {
                        diff == null -> "无法对比（模板损坏或设备未连接）"
                        diff.isEmpty() -> "与当前设备配置完全一致"
                        else -> "与当前设备的差异（模板值 → 当前值）：\n\n" +
                            diff.joinToString("\n") { "· ${it.field}：${it.templateValue} → ${it.currentValue}" }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
    }

    // ---- 应用（二次确认） ----
    applying?.let { t ->
        AlertDialog(
            onDismissRequest = { if (!configBusy) applying = null },
            confirmButton = {
                TextButton(
                    enabled = !configBusy,
                    onClick = {
                        viewModel.apply(t) { err -> message = err ?: "已应用「${t.name}」" }
                        applying = null
                    },
                ) { Text("应用") }
            },
            dismissButton = { TextButton(onClick = { applying = null }) { Text("取消") } },
            title = { Text("应用模板「${t.name}」？") },
            text = {
                Text(
                    "将覆盖设备当前的运动/静止门、无人时长和全部灵敏度（分辨率不自动切换）。" +
                        "如需回滚，应用另一个模板即可。",
                )
            },
        )
    }
}

@Composable
private fun TemplateCard(
    template: ConfigSnapshotEntity,
    onClick: () -> Unit,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp).padding(end = 8.dp)) {
                    Text(template.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(template.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onApply) { Text("应用") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                "点名称看与当前设备的差异",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

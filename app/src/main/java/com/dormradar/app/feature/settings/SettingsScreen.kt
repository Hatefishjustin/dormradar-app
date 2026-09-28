package com.dormradar.app.feature.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dormradar.app.core.device.ConnState
import com.dormradar.app.core.monitor.MonitorMode

/**
 * 设置页（PRD 4.0 两层信息架构）：
 * 简单层 = 密码 + 模拟数据；专业项收纳进「详细设置」（连接后可用）。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit = {},
) {
    val password by viewModel.bluetoothPassword.collectAsStateWithLifecycle()
    val fakeEnabled by viewModel.fakeDataEnabled.collectAsStateWithLifecycle()
    val connState by viewModel.connState.collectAsStateWithLifecycle()
    val connected = connState is ConnState.Ready
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        // ---- 详细设置入口（深层收纳）----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    "详细设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                DetailEntry("参数配置", "距离门 / 无人时长 / 分辨率", connected) { onNavigate("params") }
                DetailEntry("灵敏度调节", "无人基线定参 + 逐距离门微调（专业）", connected) { onNavigate("sensitivity") }
                DetailEntry("工程模式", "各距离门实时能量图", connected) { onNavigate("engineering") }
                DetailEntry("底噪检测", "一键科学设定灵敏度", connected) { onNavigate("noise") }
                DetailEntry("设备管理", "重启 / 恢复出厂 / 光敏 / 密码", connected) { onNavigate("device") }
                if (!connected) {
                    Text(
                        "连接设备后可配置（先到首页连接雷达）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- 监控与通知（阶段 3）----
        val monitorMode by viewModel.monitorMode.collectAsStateWithLifecycle()
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("监控与通知", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "监控模式决定哪些事件会发通知（事件的记录不受影响）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                MonitorMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setMonitorMode(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = monitorMode == mode, onClick = { viewModel.setMonitorMode(mode) })
                        Column {
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 通知渠道：跳系统设置（每渠道独立铃声/震动，PRD 5.2）
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            },
                        )
                    }
                }) { Text("通知渠道设置（铃声 / 震动）›") }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- 调试工具（阶段 4）----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    "调试工具",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                DetailEntry("调参指南", "概念 / 症状对照 / 标准流程 / 安装要点", true) { onNavigate("tuning") }
                DetailEntry("部署向导", "位置建议 → 采样 → 自动建议参数（换位置后重跑）", connected) { onNavigate("deploy") }
                DetailEntry("配置模板", "保存 / 应用 / 差异对比（回滚手段）", true) { onNavigate("templates") }
                DetailEntry("连接质量", "RSSI 曲线 / 帧统计 / 重连历史", connected) { onNavigate("quality") }
                DetailEntry("距离诊断", "两步引导采样（无人→有人）+ 自动判定误报率/检出率", connected) { onNavigate("diag") }
                DetailEntry("日志与导出", "运行日志 / 原始帧记录 / zip 分享", true) { onNavigate("logs") }
                if (!connected) {
                    Text(
                        "部署向导/连接质量/距离诊断需先连接设备（模板浏览与指南不需要）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- App 设置（简单层）----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("App 设置", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "蓝牙密码：连接雷达时用于鉴权，默认 HiLink（6 个字符，区分大小写）。“设备管理”里改密码会自动同步到这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                var draft by rememberSaveable(password) { mutableStateOf(password) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("密码") },
                        supportingText = {
                            if (draft.length != 6) Text("须为 6 个字符", color = MaterialTheme.colorScheme.error)
                        },
                    )
                    Button(
                        onClick = { viewModel.savePassword(draft) },
                        enabled = draft.length == 6 && draft != password,
                    ) { Text("保存") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 模拟数据
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("模拟数据（开发者选项）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "开启后首页可连接「模拟设备」，用合成数据体验全部界面（含配置功能），无需真实雷达",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = fakeEnabled, onCheckedChange = viewModel::setFakeDataEnabled)
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("关于", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "宿管雷达 DormRadar v0.5.4\n面向 HLK-LD2410C 毫米波雷达的监控/调参工具。\n新增：灵敏度页「无人基线定参」（一键把各门阈值压到刚好高于环境浮动）；距离诊断改为两步引导采样并自动算出误报率/检出率。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                // ---- Powered by Justin（小彩蛋：连点 5 次）----
                var eggClicks by rememberSaveable { mutableStateOf(0) }
                var showEgg by rememberSaveable { mutableStateOf(false) }
                Text(
                    "Powered by Justin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        eggClicks++
                        if (eggClicks >= 5) {
                            eggClicks = 0
                            showEgg = true
                        }
                    },
                )
                if (showEgg) {
                    AlertDialog(
                        onDismissRequest = { showEgg = false },
                        confirmButton = {
                            TextButton(onClick = { showEgg = false }) { Text("厉害") }
                        },
                        title = { Text("⚡ Powered by Justin") },
                        text = {
                            Text(
                                "你发现了这个 App 的小彩蛋！\n\n" +
                                    "从协议解析到像素级雷达图，每一行代码背后都是 Justin 的坚持。\n\n" +
                                    "DormRadar · 让宿舍管理多一点魔法 ✨",
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailEntry(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
}

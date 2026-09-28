package com.dormradar.app.feature.config

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dormradar.app.core.device.ReconnectRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 连接质量页（阶段 4 任务 4）：RSSI 曲线 + 收发统计 + 重连历史 */
@Composable
fun QualityScreen(
    onBack: () -> Unit,
    viewModel: QualityViewModel = hiltViewModel(),
) {
    val rssiSamples by viewModel.rssiSamples.collectAsStateWithLifecycle()
    val stats by viewModel.connStats.collectAsStateWithLifecycle()
    val reconnects by viewModel.reconnectHistory.collectAsStateWithLifecycle()
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()

    ConfigScaffold(title = "连接质量", onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (deviceInfo == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "设备未连接。连接后这里显示 RSSI 曲线、收发统计与重连历史。",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }
            if (deviceInfo?.isFake == true) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "模拟设备无蓝牙链路，RSSI 不采样；帧统计仍有效。",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- RSSI 曲线 ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("信号强度 (RSSI)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (rssiSamples.isEmpty()) "采样中…（每 5 秒一个点，最多 120 点）"
                        else "当前 ${rssiSamples.last().rssi} dBm · ${rssiSamples.size} 点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    RssiChart(rssiSamples.map { it.rssi }, Modifier.fillMaxWidth().height(120.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- 收发统计 ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("收发统计（本次连接）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    StatGrid(stats)
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- 重连历史 ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("断连 / 重连历史", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "App 存活期间累计，最多 30 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (reconnects.isEmpty()) {
                        Text(
                            "暂无记录（连接稳定就是最好的状态）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        reconnects.asReversed().forEach { ReconnectRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RssiChart(values: List<Int>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        // 网格：-40 ~ -100 dBm
        val minRssi = -100f
        val maxRssi = -40f
        for (rssi in intArrayOf(-50, -60, -70, -80, -90)) {
            val y = size.height * (maxRssi - rssi) / (maxRssi - minRssi)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (values.size < 2) return@Canvas
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, rssi ->
            val x = i * stepX
            val y = size.height * (maxRssi - rssi.coerceIn(minRssi.toInt(), maxRssi.toInt())) / (maxRssi - minRssi)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(width = 3f))
    }
}

@Composable
private fun StatGrid(stats: com.dormradar.app.core.device.ConnQualityStats) {
    val rows = listOf(
        "接收字节" to "${stats.rxBytes}",
        "解析帧总数" to "${stats.rxFrames}",
        "上报帧" to "${stats.reportFrames}",
        "命令 ACK" to "${stats.ackFrames}",
        "无法识别帧" to "${stats.unknownFrames}",
        "解码异常" to "${stats.decodeErrors}",
        "发送命令" to "${stats.txCommands}",
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ReconnectRow(record: ReconnectRecord) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.timeMs)),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(0.dp))
        Text(
            "  " + record.text,
            style = MaterialTheme.typography.bodySmall,
            color = if (record.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

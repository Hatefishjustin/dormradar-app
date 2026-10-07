package com.dormradar.app.feature.config

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dormradar.app.core.device.GateEnergies

val MOVE_COLOR = Color(0xFFDC2626)
val STATIC_COLOR = Color(0xFF9333EA)

/**
 * 每门双色能量柱状图卡片（工程模式数据，docs/06 阶段 2 任务 5）。
 * 供工程模式页与首页「工程能量图」卡共用（用户需求 2026-10-07）。
 * 数据未就绪时显示占位提示；Canvas 绘制逻辑与原 EngineeringScreen 内联版逐字一致。
 */
@Composable
fun EnergyBarsCard(energies: GateEnergies?, resolutionCm: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("距离门能量", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BarLegend("运动", MOVE_COLOR)
                    BarLegend("静止", STATIC_COLOR)
                }
            }

            if (energies == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("等待工程数据…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val gateCount = maxOf(energies.moveGateEnergies.size, energies.staticGateEnergies.size)
                Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                    val w = size.width
                    val h = size.height
                    val slot = w / gateCount
                    val barW = slot * 0.28f
                    for (g in 0 until gateCount) {
                        val moveE = energies.moveGateEnergies.getOrElse(g) { 0 }
                        val staticE = energies.staticGateEnergies.getOrElse(g) { 0 }
                        val moveH = moveE / 100f * (h - 8f)
                        val staticH = staticE / 100f * (h - 8f)
                        val cx = slot * g + slot / 2
                        drawRect(
                            color = MOVE_COLOR,
                            topLeft = Offset(cx - barW - 2f, h - moveH),
                            size = Size(barW, moveH),
                        )
                        drawRect(
                            color = STATIC_COLOR,
                            topLeft = Offset(cx + 2f, h - staticH),
                            size = Size(barW, staticH),
                        )
                    }
                }
                // 门号标签
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (g in 0 until gateCount) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("门$g", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "%.1fm".format(g * resolutionCm / 100.0),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // 数值摘要
                val maxMove = energies.moveGateEnergies.maxOrNull() ?: 0
                val maxStatic = energies.staticGateEnergies.maxOrNull() ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "运动峰值 $maxMove · 静止峰值 $maxStatic",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "最大门 运动${energies.maxMoveGate}/静止${energies.maxStaticGate}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun BarLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)),
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

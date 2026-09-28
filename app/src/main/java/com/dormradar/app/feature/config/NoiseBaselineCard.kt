package com.dormradar.app.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dormradar.app.core.deploy.NoiseBaselineAdvisor

/**
 * 无人基线定参卡（用户需求 2026-09-28）：
 * 无人时测出每门灵敏度的「正常浮动上界」，一键把阈值设在"上界 + 一点点"。
 *
 * 与设备侧底噪检测（0x000B）的关系：**功能互补、不可同时进行**（详见 [NoiseBaselineAdvisor]
 * 类注释）。设备侧检测结束时会自动写入它自己的建议表，会覆盖掉本功能写入的阈值，
 * 所以采样期间一旦发现检测在跑就作废本次样本。
 */
@Composable
fun NoiseBaselineCard(
    enabled: Boolean,
    sampling: Boolean,
    remainSeconds: Int,
    sampleSeconds: Int,
    info: String?,
    margin: Int,
    basis: NoiseBaselineAdvisor.Basis,
    suggestion: NoiseBaselineAdvisor.Suggestion?,
    applying: Boolean,
    resolutionCm: Int,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onMargin: (Int) -> Unit,
    onBasis: (NoiseBaselineAdvisor.Basis) -> Unit,
    onApply: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "无人基线定参",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "让门口保持无人、采样 $sampleSeconds 秒，测出每一门灵敏度的「正常浮动上界」，" +
                    "再把阈值设在「上界 + 一点点」。比设备自带的底噪检测更贴近实际噪声，所以更灵敏。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (sampling) {
                Text(
                    "采样中…请让门口保持无人，剩余 $remainSeconds 秒",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                LinearProgressIndicator(
                    progress = { (sampleSeconds - remainSeconds).toFloat() / sampleSeconds },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消采样") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStart, enabled = enabled && !applying) { Text("开始采样（保持无人）") }
                    if (suggestion != null) {
                        OutlinedButton(onClick = onApply, enabled = enabled && !applying) {
                            Text(if (applying) "正在写入…" else "一键应用")
                        }
                    }
                }
            }

            info?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val s = suggestion
            if (s != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                Text(
                    "浮动范围 → 建议阈值（边距 +${s.margin}，${s.basis.label}）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                s.profiles.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "门 ${p.gate}  " +
                                "%.1f~%.1fm".format(
                                    p.gate * resolutionCm / 100.0, (p.gate + 1) * resolutionCm / 100.0,
                                ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            suggestLine(
                                p,
                                s.motionSensitivities.getOrNull(p.gate) ?: 0,
                                s.staticSensitivities.getOrNull(p.gate) ?: 0,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                if (s.shieldedGates.isNotEmpty()) {
                    Text(
                        "已保持屏蔽（不动）：" + s.shieldedGates.joinToString("、") { "门 $it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (s.unusableGates.isNotEmpty()) {
                    Text(
                        "浮动上界过高、无区分度而改为屏蔽：" + s.unusableGates.joinToString("、") { "门 $it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "近门（门 0/1）的静止灵敏度部分固件不可写，写入被拒时会保留原值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Text("上界口径", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoiseBaselineAdvisor.Basis.entries.forEach { b ->
                    FilterChip(
                        selected = basis == b,
                        onClick = { onBasis(b) },
                        enabled = !sampling,
                        label = {
                            Text(
                                if (b == NoiseBaselineAdvisor.Basis.P95) "典型上界 p95" else "绝对最高值",
                            )
                        },
                    )
                }
            }
            Text("边距（在浮动上界之上再加多少，越小越灵敏）", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoiseBaselineAdvisor.MARGIN_OPTIONS.forEach { m ->
                    FilterChip(
                        selected = margin == m,
                        onClick = { onMargin(m) },
                        enabled = !sampling,
                        label = { Text("+$m") },
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                "与「底噪检测」的关系：两者不冲突，但不能同时进行——底噪检测结束后设备会自动写入它自己的" +
                    "建议灵敏度，会覆盖掉这里的设置。若刚跑完底噪检测觉得阈值偏高，正好用本功能压下来。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单门一行：浮动范围 → 建议值 */
private fun suggestLine(
    p: NoiseBaselineAdvisor.GateProfile,
    motion: Int,
    static: Int,
): String {
    if (p.samples == 0) return "无数据，保持原值"
    val m = if (motion >= 100) "屏蔽" else motion.toString()
    val s = if (static >= 100) "屏蔽" else static.toString()
    return "动 ${p.motionMin}~${p.motionMax}→$m  静 ${p.staticMin}~${p.staticMax}→$s"
}

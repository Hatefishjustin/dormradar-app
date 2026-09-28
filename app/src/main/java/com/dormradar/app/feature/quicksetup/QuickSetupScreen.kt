package com.dormradar.app.feature.quicksetup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dormradar.app.core.device.ConnState
import com.dormradar.app.core.device.PresetPacks
import com.dormradar.app.feature.config.ConfigScaffold

/**
 * 一键设置页（简单层）：三套推荐参数包，一键下发 + 回读校验（PRD 4.1 第 6 项）。
 */
@Composable
fun QuickSetupScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    viewModel: QuickSetupViewModel = hiltViewModel(),
) {
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val applying by viewModel.applying.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageIsError by viewModel.messageIsError.collectAsStateWithLifecycle()
    val connected = deviceInfo != null

    ConfigScaffold(title = "一键设置", onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "不知道从哪调起？选一套推荐参数即可，应用后自动回读校验，随时可换。\n" +
                        "（灵敏度数值越低越灵敏；完整参数表见「设置 → 详细设置」）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "应用后效果不对？看「调参指南」的症状对照表 →",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clickable { onNavigate("tuning") },
                )
            }

            if (message != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (messageIsError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                message!!,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (messageIsError) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            TextButton(onClick = viewModel::clearMessage) { Text("知道了") }
                        }
                    }
                }
            }

            items(PresetPacks.ALL.size) { index ->
                val pack = PresetPacks.ALL[index]
                PresetCard(
                    pack = pack,
                    isCurrent = deviceInfo?.params?.let { p ->
                        p.maxMoveGate == pack.moveMaxGate && p.unmannedSeconds == pack.unmannedSeconds
                    } == true,
                    applyingThis = applying == pack.id,
                    anyApplying = applying != null,
                    enabled = connected,
                    onApply = { viewModel.apply(pack) },
                )
            }

            // 当前生效参数摘要
            deviceInfo?.params?.let { p ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("当前设备参数", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            InfoLine("距离门", "运动 ${p.maxMoveGate} / 静止 ${p.maxStaticGate}")
                            InfoLine("无人持续时间", "${p.unmannedSeconds} 秒")
                            InfoLine("运动灵敏度", p.motionSensitivities.joinToString(" / "))
                            InfoLine("静止灵敏度", p.staticSensitivities.joinToString(" / "))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    pack: com.dormradar.app.core.device.PresetPack,
    isCurrent: Boolean,
    applyingThis: Boolean,
    anyApplying: Boolean,
    enabled: Boolean,
    onApply: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(pack.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (isCurrent) {
                    Text(
                        "当前",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                pack.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoLine("探测距离门", "运动 ${pack.moveMaxGate} / 静止 ${pack.staticMaxGate}")
            InfoLine("无人持续时间", "${pack.unmannedSeconds} 秒")
            Spacer(Modifier.height(2.dp))
            Button(onClick = onApply, enabled = enabled && !anyApplying) {
                if (applyingThis) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 6.dp).height(16.dp).width(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(if (applyingThis) "正在下发…" else if (isCurrent) "重新应用" else "应用此方案")
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

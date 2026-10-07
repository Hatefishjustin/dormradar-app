package com.dormradar.app.feature.deploy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.dormradar.app.feature.config.ConfigScaffold

/**
 * 部署向导页（阶段 4 任务 2）：不拆设备完成现场部署调参——
 * 位置建议 → 清场底噪采样 → 走动采样 → 自动建议参数 → 一键应用并存模板。
 */
@Composable
fun DeployScreen(
    onBack: () -> Unit,
    viewModel: DeployViewModel = hiltViewModel(),
) {
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val noiseSamples by viewModel.noiseSamples.collectAsStateWithLifecycle()
    val activitySamples by viewModel.activitySamples.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val sampling by viewModel.sampling.collectAsStateWithLifecycle()
    val remainSeconds by viewModel.remainSeconds.collectAsStateWithLifecycle()
    val unmannedSeconds by viewModel.unmannedSeconds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val applying by viewModel.applying.collectAsStateWithLifecycle()
    val engineeringOn by viewModel.engineeringMode.collectAsStateWithLifecycle()
    val archiveAvailable by viewModel.archiveAvailable.collectAsStateWithLifecycle()

    // 进页开工程模式（采样数据源），离开自动关（模式照抄灵敏度页：onDispose 时 VM 尚存活）
    LaunchedEffect(Unit) { viewModel.setEngineering(true) }
    DisposableEffect(Unit) {
        onDispose { viewModel.setEngineering(false) }
    }

    ConfigScaffold(title = "部署向导", onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (deviceInfo == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "设备未连接，请先回到首页连接雷达（模拟设备同样可以体验全流程）",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }
            if (message != null) {
                Text(
                    message!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }

            // ---- 快捷入口：复用诊断存档（采样档案共享，2026-10-07）----
            if (archiveAvailable && noiseSamples == null && activitySamples == null && sampling == null) {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "检测到「距离诊断」的存档采样",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "可以直接用上次两步引导的无人/有人数据生成建议，免去下面两段 30 秒采样。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = viewModel::useArchivedSamples) { Text("用存档直接生成建议") }
                    }
                }
            }

            // ---- 第 1 步：位置建议（静态引导） ----
            GuideCard(
                title = "① 安装位置建议",
                body = "· 雷达正面朝向走廊，探测扇面无金属遮挡\n" +
                    "· 离地 1.5~2.2 米，稍微下倾 10~30° 覆盖门口区域\n" +
                    "· 远离风扇、空调出风口、晃动的窗帘/门帘（微波对动目标敏感）\n" +
                    "· 避开正对电梯井/楼梯口（人流动线复杂会互相干扰）",
            )

            // ---- 第 2 步：底噪采样 ----
            GuideCard(
                title = "② 底噪采样（30 秒）",
                body = "让所有人离开探测区域（最好离开房间），确认走廊无活动后开始。",
            ) {
                SamplingAction(
                    kind = SampleKind.NOISE,
                    label = "开始底噪采样",
                    sampling = sampling,
                    remainSeconds = remainSeconds,
                    done = noiseSamples != null,
                    onStart = { viewModel.startSampling(SampleKind.NOISE) },
                )
            }

            // ---- 第 3 步：走动采样 ----
            GuideCard(
                title = "③ 走动采样（30 秒）",
                body = "以正常速度在门口到走廊之间来回走动 30 秒（覆盖预期活动范围）。",
            ) {
                SamplingAction(
                    kind = SampleKind.ACTIVITY,
                    label = "开始走动采样",
                    sampling = sampling,
                    remainSeconds = remainSeconds,
                    done = activitySamples != null,
                    onStart = { viewModel.startSampling(SampleKind.ACTIVITY) },
                )
            }

            // ---- 第 4 步：建议 + 应用 ----
            if (suggestion != null) {
                val s = suggestion!!
                GuideCard(title = "④ 自动建议参数", body = null) {
                    Text(
                        "最远运动门 ${s.maxMoveGate} · 最远静止门 ${s.maxStaticGate}" +
                            (if (s.maskedGates.isNotEmpty()) " · 屏蔽门 ${s.maskedGates}" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    s.rationale.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("无人持续时间：", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(0.dp))
                        var draft by rememberSaveable(unmannedSeconds) { mutableStateOf("${unmannedSeconds}") }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it.filter { c -> c.isDigit() }.take(4) },
                            modifier = Modifier.padding(horizontal = 8.dp).width(88.dp),
                            singleLine = true,
                        )
                        Text("秒", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = !applying,
                        onClick = { viewModel.applySuggestion { err -> } },
                    ) {
                        Text(if (applying) "应用中…" else "应用建议并存模板")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "应用后自动保存为模板（可到「配置模板」回滚）。灵敏度阈值可再到「灵敏度调节」微调。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (noiseSamples != null && activitySamples != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "两步采样都完成了，但没有任何距离门有区分度（活动能量没有明显高于底噪）。\n" +
                            "建议：确认走动采样时人真的在探测范围内活动；或设备安装位置不合适，调整后重试。",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuideCard(
    title: String,
    body: String?,
    content: (@Composable () -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (body != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content?.let {
                Spacer(Modifier.height(8.dp))
                it()
            }
        }
    }
}

@Composable
private fun SamplingAction(
    kind: SampleKind,
    label: String,
    sampling: SampleKind?,
    remainSeconds: Int,
    done: Boolean,
    onStart: () -> Unit,
) {
    Column {
        if (sampling == kind) {
            LinearProgressIndicator(
                progress = { (30 - remainSeconds) / 30f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "采样中… 剩余 ${remainSeconds}s（保持${if (kind == SampleKind.NOISE) "清场静止" else "正常走动"}）",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onStart, enabled = sampling == null) {
                    Text(if (done) "重新采样" else label)
                }
                if (done) {
                    Spacer(Modifier.width(8.dp))
                    Text("✓ 已完成", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

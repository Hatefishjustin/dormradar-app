package com.dormradar.app.feature.guide

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dormradar.app.feature.config.ConfigScaffold

/**
 * 调参指南（用户反馈 2026-09-28"不知道参数该怎么调"）：
 * 大白话讲清概念 + 症状对照表 + 标准流程 + 安装要点。纯静态页。
 */
@Composable
fun TuningGuideScreen(onBack: () -> Unit) {
    ConfigScaffold(title = "调参指南", onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ConceptCard()
            Spacer(Modifier.height(12.dp))
            SymptomCard()
            Spacer(Modifier.height(12.dp))
            ProcessCard()
            Spacer(Modifier.height(12.dp))
            InstallCard()
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "记住一句话：阈值是\"门槛\"——门槛比环境底噪还矮，风吹草动都报（误报）；" +
                            "门槛比人走路还高，人来了也不报（漏报）。调参就是用采样数据把门槛放中间。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "但有个前提：这个办法只在\"干扰比目标弱\"时成立。如果探测面和目标之间夹了介质" +
                            "（门板/玻璃/金属），介质反射比人体回波还强，那么任何阈值都无解——" +
                            "抬高门槛会连人一起屏蔽，压低则永远误报。这时先改安装，别在参数里打转。" +
                            "判断方法：到「距离诊断」走一次两步引导，看自动结论是\"近场饱和\"还是\"误报主导\"。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 三个核心概念 */
@Composable
private fun ConceptCard() {
    SectionCard(title = "先搞懂 3 个词") {
        GuideLine("距离门", "雷达把探测距离切成 0~8 共 9 段（每段 0.75 米），每段有独立的门槛。门 0 最近、门 8 最远。")
        GuideLine("灵敏度阈值", "每段门槛的高低（0~100）。注意：数值越小 = 门槛越矮 = 越灵敏；数值越大 = 越迟钝；100 = 这段直接不看（屏蔽）。")
        GuideLine("底噪", "没有任何人活动时，环境本身的能量（门板反射、电器干扰都算）。它是门槛的下限——门槛必须比它高，否则永远\"有人\"。")
    }
}

/** 症状对照表（覆盖用户 2026-09-28 实测全部症状） */
@Composable
private fun SymptomCard() {
    SectionCard(title = "症状对照：对号入座") {
        GuideLine(
            "没人也显示有人",
            "先看读数：距离被钉在几十厘米且无人也报有人 → 见下面「近场饱和」那条，那是安装问题。" +
                "读数分布在正常距离上只是偶尔误报 → 门槛比底噪矮了，处置：到「灵敏度调节 → 无人基线定参」" +
                "一键把阈值压到刚好高于环境浮动上界，或跑「部署向导」重新采样。",
        )
        GuideLine(
            "有人走动也不报",
            "门槛比人走路能量还高。底噪检测后尤其常见（设备写的是保守值）。" +
                "处置：用「灵敏度调节 → 无人基线定参」重新定一遍（它直接量环境浮动，比设备侧底噪检测更灵敏），" +
                "或把对应门阈值手动调小。",
        )
        GuideLine(
            "距离数字一直不变",
            "读数很小（几十厘米、贴最近的门）→ 见下一条「近场饱和」，那是安装问题。" +
                "读数在正常距离上只是不动 → 门板/墙的静态反射被当成了\"静止目标\"（反射物不动，距离自然恒定），" +
                "处置同\"没人也显示有人\"。",
        )
        GuideLine(
            "读数被钉在很小的值（如 30cm）、无人也报有人",
            "近场饱和。紧贴天线的介质（门板/玻璃/金属）反射能量强过门外人体回波，雷达总是优先报最强的回波，" +
                "于是距离被钉在最小值。这种情况调灵敏度救不回来：阈值是\"门槛\"，只能筛掉比它弱的信号——" +
                "干扰本身就比目标强时，抬高门槛会连人一起屏蔽，压低则永远误报。\n" +
                "处置按顺序试：① 让探测面与目标之间不要夹介质（把感应面直接朝外，实测最有效）；" +
                "② 换更薄、不含金属层的挡板；③ 改完到「距离诊断」走一次两步引导，看误报率是否降下来。",
        )
        GuideLine(
            "数据跳来跳去",
            "先查安装（见下方安装要点），再跑部署向导重新采样。数据在门槛附近反复横跳 = 门槛设在了底噪和活动能量之间太近的位置。",
        )
        GuideLine(
            "到底该调多少",
            "别手猜。用「部署向导」：清场采 30 秒底噪 + 走动采 30 秒，App 自动算每门的合理门槛。",
        )
    }
}

/** 标准调参流程 */
@Composable
private fun ProcessCard() {
    SectionCard(title = "推荐的调参流程（4 步）") {
        GuideLine("第 1 步", "设置 → 调试工具 → 部署向导：按提示完成位置检查 → 清场采底噪 → 走动采样 → 一键应用建议参数（自动存模板）。")
        GuideLine("第 2 步", "灵敏度调节页：对照每门的\"实时能量柱\"微调——能量柱顶超过虚线门限就会报有人。用「阈值编辑框」输入精确数值。")
        GuideLine("第 3 步", "事件页验证：故意制造一次活动/离开，点开事件看\"当时的能量快照\"，确认能量和门限的相对关系符合预期。")
        GuideLine("第 4 步", "满意后到「配置模板」把当前配置存个名字——以后调坏了随时一键回滚。")
    }
}

/** 安装要点（贴门板场景） */
@Composable
private fun InstallCard() {
    SectionCard(title = "安装要点（数据不稳先查这里）") {
        GuideLine(
            "第一原则：探测面与目标之间不要夹介质",
            "24GHz 穿透木板/塑料可行，但会衰减；复合门板、金属漆、金属网夹层、防盗门内衬会强烈屏蔽甚至完全挡死。" +
                "而紧贴天线的介质还会制造强近场反射——它的回波常常强过门外的人，于是距离被钉在几十厘米、无人也报有人（近场饱和）。" +
                "实测结论：把感应面直接朝外（不隔板）最可靠；反过来隔着板，只能靠「换更薄的板」碰运气。",
        )
        GuideLine("贴合", "确定朝向之后，雷达背面贴紧安装面，不能有空腔/缝隙——空腔会制造大量假反射。")
        GuideLine("朝向", "探测面直接朝向被探测区域（走廊），波束中心对准走廊中央；不要正对晃动的门帘、风扇、空调出风口。")
        GuideLine("高度", "离地 1.5~2.2 米，可稍微下倾 10~30° 让扇面覆盖门口。")
        GuideLine("远离", "远离金属（门锁盒、铁皮、金属贴膜）、大块玻璃与镜子；这些会强反射导致近距离「假目标」。")
        GuideLine(
            "线缆与供电",
            "USB 供电线尽量走门轴侧、避免被反复挤压；供电不足会表现为随机断连（事件页会有连续的断开/重连记录）。",
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun GuideLine(term: String, body: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(term, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

package com.dormradar.app.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.data.SettingsStore
import com.dormradar.app.core.device.ConnState
import com.dormradar.app.core.device.PrototypeEasterEgg
import com.dormradar.app.core.device.RadarConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 原型机一号机彩蛋（用户需求 2026-10-08）：首次连接 Justin 的第一台成品雷达
 * （MAC 见 PrototypeEasterEgg.TARGET_MAC）时弹隐藏成就弹窗；点「一般般」关不掉
 * （吐槽轮换 + 按钮逐级变化），只有点「太有实力了」才放行并永久记住该设备。
 */
@HiltViewModel
class EasterEggViewModel @Inject constructor(
    private val connection: RadarConnection,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _forceDismissed = MutableStateFlow(false)

    /** 弹窗展示与否：连接上了一号机 且 从未看过；「太有实力了」后强制消失 */
    val visible: StateFlow<Boolean> =
        combine(connection.connState, settings.easterEggSeenMacs, _forceDismissed) { state, seen, dismissed ->
            if (dismissed) return@combine false
            val mac = (state as? ConnState.Ready)?.mac
            mac != null && PrototypeEasterEgg.shouldShow(mac, seen)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 点「一般般」的累计次数（驱动吐槽文案轮换 + 按钮文案变化 + 抖动动画） */
    private val _humbleCount = MutableStateFlow(0)
    val humbleCount: StateFlow<Int> = _humbleCount.asStateFlow()

    /** 点「一般般」：不放行，只升级吐槽 */
    fun onHumble() {
        _humbleCount.value += 1
    }

    /** 点「太有实力了」：永久记住这台设备，弹窗关闭（以后不再弹） */
    fun onImpressed() {
        _forceDismissed.value = true
        viewModelScope.launch { settings.markEasterEggSeen(PrototypeEasterEgg.macToRemember()) }
    }
}

/** 挂在 AppRoot 顶层（与 UpdatePromptHost 同级），任何页面连接上一号机都会触发 */
@Composable
fun EasterEggHost(viewModel: EasterEggViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val humbleCount by viewModel.humbleCount.collectAsStateWithLifecycle()
    if (!visible) return

    // 点「一般般」→ 对话框左右抖动（越点越委屈）
    val shake = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(humbleCount) {
        if (humbleCount > 0) {
            for (i in 1..5) {
                val amp = (14f / i).coerceAtLeast(1.5f)
                shake.snapTo(if (i % 2 == 1) amp else -amp)
                delay(45)
            }
            shake.snapTo(0f)
        }
    }

    AlertDialog(
        onDismissRequest = { /* 玩梗核心：点外部/返回键一律不放行，必须选「太有实力了」 */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("🏆 隐藏成就已解锁") },
        text = {
            Column(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }) {
                Text(
                    "你连接的这台雷达，是 Justin 手里的第一台成品原型机（一号机）。\n\n" +
                        "它曾隔着门板修炼内功、在走廊里洞察人间烟火，如今终于等到了有缘人——" +
                        "请对它说点好听的，它才肯继续为你站岗。\n\nPowered by Justin",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (humbleCount > 0) {
                    Text(
                        PrototypeEasterEgg.humbleReply(humbleCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.onImpressed()
                Toast.makeText(context, "眼光不错！一号机已记住你。", Toast.LENGTH_SHORT).show()
            }) { Text("太有实力了！") }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onHumble) {
                Text(PrototypeEasterEgg.humbleButtonLabel(humbleCount))
            }
        },
    )
}

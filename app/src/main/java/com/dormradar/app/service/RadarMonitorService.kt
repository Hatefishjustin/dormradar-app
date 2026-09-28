package com.dormradar.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.dormradar.app.core.data.EventRepository
import com.dormradar.app.core.data.SettingsStore
import com.dormradar.app.core.device.ConnState
import com.dormradar.app.core.device.RadarConnection
import com.dormradar.app.core.device.RadarState
import com.dormradar.app.core.log.LogStore
import com.dormradar.app.core.monitor.MonitorMode
import com.dormradar.app.core.monitor.NotificationPolicy
import com.dormradar.app.core.monitor.PresenceTracker
import com.dormradar.app.core.monitor.RadarEventType
import com.dormradar.app.core.notification.Notifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 后台监控前台服务（docs/04 §3.4，docs/06 阶段 3 核心）：
 * - 持有单例 [RadarConnection]，把雷达状态流转成两类输出：**事件入库** + **通知评估**；
 * - 常驻通知显示当前状态（已连接·有人/无人·重连中）；
 * - 未连接时用记住的 MAC 自动连接（开机恢复场景由 BootReceiver 拉起本服务）；
 * - 用户主动断开设备连接 = 结束监控（自动停止本服务）。
 *
 * 生命周期：用户在首页开启"后台监控"才启动；START_STICKY 让系统被杀后尝试重建。
 */
@AndroidEntryPoint
class RadarMonitorService : Service() {

    companion object {
        private const val TAG = "RadarMonitorService"

        const val ACTION_START = "com.dormradar.app.monitor.START"
        const val ACTION_STOP = "com.dormradar.app.monitor.STOP"

        /** 开启监控（前台 UI 调用） */
        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, RadarMonitorService::class.java).setAction(ACTION_START),
            )
        }

        /** 停止监控（前台 UI 调用） */
        fun stop(context: Context) {
            context.startService(
                Intent(context, RadarMonitorService::class.java).setAction(ACTION_STOP),
            )
        }
    }

    @Inject lateinit var connection: RadarConnection
    @Inject lateinit var events: EventRepository
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var log: LogStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val policy = NotificationPolicy()
    private val tracker = PresenceTracker()

    private var connJob: Job? = null
    private var stateJob: Job? = null
    private var working = false
    private var seenReadyOnce = false

    /** 监控模式缓存（服务存活期间跟随设置变化） */
    @Volatile private var mode: MonitorMode = MonitorMode.ALERT

    /** 常驻通知上次文本（相同则不重复刷新） */
    @Volatile private var lastPersistentText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWork()
                return START_NOT_STICKY
            }
            else -> startWork() // 含 START_STICKY 重建（intent == null）
        }
        return START_STICKY
    }

    // ---- 工作循环 ----

    private fun startWork() {
        if (working) return
        working = true
        seenReadyOnce = false
        log.i(TAG, "后台监控开启")

        // targetSdk 35：必须带 foregroundServiceType 启动
        ServiceCompat.startForeground(
            this,
            Notifier.PERSISTENT_ID,
            notifier.buildPersistent("正在启动监控…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )

        // 模式缓存：跟随设置实时变化
        serviceScope.launch {
            settings.monitorMode.collect { mode = it }
        }

        // 连接状态 → 连接事件（建立/断开/重连成功/放弃）
        connJob = serviceScope.launch {
            var prev: ConnState = ConnState.Disconnected
            connection.connState.collect { state ->
                if (working) handleConnState(state, prev)
                prev = state
            }
        }

        // 雷达状态 → 有人出现/离开（沿检测）+ 常驻通知刷新
        stateJob = serviceScope.launch {
            connection.radarState.collect { s ->
                if (!working) return@collect
                val edge = tracker.update(s)
                if (edge != null && s != null) handlePresence(edge, s)
                updatePersistentIfChanged(persistentText())
            }
        }

        // 开启事件入库 + 未连接时自动连接（开机恢复/服务重建场景）
        serviceScope.launch {
            val current = connection.connState.value
            if (current is ConnState.Disconnected || current is ConnState.Failed) {
                val mac = settings.lastMac()
                if (mac != null) {
                    log.i(TAG, "监控启动时未连接，自动连接上次设备 $mac")
                    connection.connect(mac)
                }
            }
            events.record(RadarEventType.MONITOR_START, currentMac(), "后台监控已开启")
        }
    }

    private suspend fun handleConnState(state: ConnState, prev: ConnState) {
        when (state) {
            is ConnState.Ready -> {
                // 真实设备才记忆 MAC（模拟设备开机恢复无意义）
                if (connection.deviceInfo.value?.isFake != true) settings.setLastDeviceMac(state.mac)
                tracker.reset() // 新连接清空旧状态沿，避免跨连接误判"离开"
                val type = if (seenReadyOnce) RadarEventType.RECONNECTED else RadarEventType.CONNECT
                seenReadyOnce = true
                events.record(
                    type, state.mac,
                    if (type == RadarEventType.RECONNECTED) "断线后自动重连成功" else "监控连接建立",
                )
                evaluateNotify(type, state.mac)
            }

            is ConnState.Reconnecting -> {
                if (state.attempt == 1) {
                    // attempt==1 = 刚从 Ready 意外掉线（后续 attempt 只刷常驻通知）
                    events.record(RadarEventType.DISCONNECT, state.mac, "连接意外断开，开始自动重连")
                    evaluateNotify(RadarEventType.DISCONNECT, state.mac)
                }
            }

            is ConnState.Failed -> {
                // 鉴权失败等终态：自动重连已放弃（docs/03：需用户干预）
                events.record(RadarEventType.GIVE_UP, currentMac(), "连接失败：${state.reason}")
                evaluateNotify(RadarEventType.GIVE_UP, currentMac())
            }

            is ConnState.Disconnected -> {
                // 从活动状态转 Disconnected = 用户主动断开 → 结束监控
                if (prev is ConnState.Ready || prev is ConnState.Reconnecting || prev is ConnState.Failed) {
                    log.i(TAG, "用户主动断开，结束后台监控")
                    stopWork()
                }
            }

            else -> Unit
        }
        updatePersistentIfChanged(persistentText())
    }

    private suspend fun handlePresence(edge: com.dormradar.app.core.monitor.PresenceEdge, state: RadarState) {
        val mac = currentMac()
        when (edge.type) {
            RadarEventType.APPEAR -> {
                events.record(
                    RadarEventType.APPEAR, mac,
                    "有人出现，距离 ${formatCm(state.detectDistanceCm)}",
                    snapshotPayload(state),
                )
                val decision = policy.evaluate(RadarEventType.APPEAR, mode)
                if (decision is NotificationPolicy.Decision.Send) {
                    val mergedSuffix = if (decision.mergedCount > 1) {
                        "（近 1 分钟共 ${decision.mergedCount} 次活动）"
                    } else ""
                    notifier.notifyDetection("检测到有人活动，距离 ${formatCm(state.detectDistanceCm)}$mergedSuffix")
                }
            }

            RadarEventType.LEAVE -> {
                val seconds = edge.durationMs / 1000
                events.record(
                    RadarEventType.LEAVE, mac,
                    "有人离开，持续了 $seconds 秒",
                    snapshotPayload(state),
                )
                val decision = policy.evaluate(RadarEventType.LEAVE, mode)
                if (decision is NotificationPolicy.Decision.Send) {
                    notifier.notifyDetection("目标已离开（本次持续 $seconds 秒）")
                }
            }

            else -> Unit
        }
    }

    /** 事件发生瞬间的能量快照（阶段 4「事件回看调参」：点开事件即可对照当时的能量/门限） */
    private fun snapshotPayload(state: RadarState): String = buildString {
        append("目标状态=")
        append(
            when (state.targetState) {
                1 -> "运动"; 2 -> "静止"; 3 -> "运动+静止"; else -> "无人"
            },
        )
        append(" · 探测距离 ${formatCm(state.detectDistanceCm)}")
        append(" · 运动能量 ${state.moveEnergy}（${formatCm(state.moveDistanceCm)}）")
        append(" · 静止能量 ${state.staticEnergy}（${formatCm(state.staticDistanceCm)}）")
        connection.gateEnergies.value?.let { g ->
            append("\n各门能量 运动${g.moveGateEnergies.take(g.maxMoveGate + 1)} 静止${g.staticGateEnergies.take(g.maxStaticGate + 1)}")
        }
        connection.deviceInfo.value?.params?.let { p ->
            append("\n当前门限 运动${p.motionSensitivities.take(p.maxMoveGate + 1)} 静止${p.staticSensitivities.take(p.maxStaticGate + 1)}")
        }
    }

    private suspend fun evaluateNotify(type: RadarEventType, mac: String) {
        val decision = policy.evaluate(type, mode)
        if (decision is NotificationPolicy.Decision.Send) {
            val text = when (type) {
                RadarEventType.DISCONNECT -> "设备连接断开，正在自动重连"
                RadarEventType.GIVE_UP -> "自动重连已停止，需要打开 App 手动处理"
                RadarEventType.CONNECT, RadarEventType.RECONNECTED -> "设备已连接，监控运行中"
                else -> return
            }
            notifier.notifyDeviceStatus(text)
        }
    }

    // ---- 常驻通知 ----

    private fun persistentText(): String {
        val conn = connection.connState.value
        val radar = connection.radarState.value
        return when (conn) {
            is ConnState.Ready -> when {
                radar == null -> "已连接 · 等待数据"
                radar.hasTarget -> "已连接 · 有人"
                else -> "已连接 · 无人"
            }

            is ConnState.Reconnecting -> "连接断开 · 重连中（第 ${conn.attempt} 次）"

            is ConnState.Failed -> "连接失败 · ${conn.reason}"

            is ConnState.Connecting, is ConnState.Authenticating, is ConnState.Initializing ->
                "正在连接设备…"

            is ConnState.Disconnected -> "监控已停止"
        }
    }

    private fun updatePersistentIfChanged(text: String) {
        if (text != lastPersistentText) {
            lastPersistentText = text
            notifier.updatePersistent(text)
        }
    }

    // ---- 停止与销毁 ----

    private fun stopWork() {
        if (!working) {
            stopSelf()
            return
        }
        working = false
        log.i(TAG, "后台监控停止")
        connJob?.cancel()
        connJob = null
        stateJob?.cancel()
        stateJob = null
        val mac = currentMac()
        // NonCancellable：确保 stopSelf → onDestroy 砍掉 scope 前完成落库与设置回写
        serviceScope.launch {
            withContext(NonCancellable) {
                events.record(RadarEventType.MONITOR_STOP, mac, "后台监控已停止")
                settings.setMonitorEnabled(false)
            }
        }
        lastPersistentText = null
        notifier.cancelPersistent()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---- 工具 ----

    private fun currentMac(): String =
        connection.deviceInfo.value?.mac ?: "-"

    private fun formatCm(cm: Int): String =
        if (cm >= 100) "%.1f 米".format(cm / 100.0) else "$cm 厘米"
}

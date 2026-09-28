package com.dormradar.app.core.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dormradar.app.MainActivity
import com.dormradar.app.R
import com.dormradar.app.service.RadarMonitorService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知执行层（docs/04 §3.5）：渠道注册 + 发送。决策逻辑在 NotificationPolicy（纯 Kotlin）。
 *
 * 渠道（PRD §5.2 分级渠道，可在系统设置里独立调铃声/震动）：
 * - 检测：有人出现等，DEFAULT；
 * - 设备状态：断开/恢复/失败，HIGH（"死要见尸"）；
 * - 常驻：前台服务低优先级常驻通知，LOW。
 */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_DETECTION = "detection"
        const val CHANNEL_DEVICE_STATUS = "device_status"
        const val CHANNEL_PERSISTENT = "persistent"

        /** 常驻通知 id（固定，随状态更新同一条） */
        const val PERSISTENT_ID = 100

        /** 检测/设备状态通知 id：同类覆盖（新事件替换旧通知，避免堆积） */
        private const val DETECTION_ID = 200
        private const val DEVICE_STATUS_ID = 201

        private const val REQ_OPEN_APP = 1
        private const val REQ_STOP_MONITOR = 2
    }

    /** App 启动时注册渠道（幂等；API 26+ 必须在发通知前建好） */
    fun registerChannels() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DETECTION,
                "检测提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "有人出现等检测事件" },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DEVICE_STATUS,
                "设备状态",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "连接断开、重连成功与失败告警" },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PERSISTENT,
                "监控常驻",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "后台监控的常驻状态通知" },
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQ_OPEN_APP,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 常驻通知（前台服务用）：显示监控状态，附"停止监控"动作 */
    fun buildPersistent(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            REQ_STOP_MONITOR,
            Intent(context, RadarMonitorService::class.java).setAction(RadarMonitorService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_PERSISTENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DormRadar 监控运行中")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "停止监控", stopIntent)
            .build()
    }

    /** 更新常驻通知文本（服务在连接/有人状态变化时调用） */
    fun updatePersistent(text: String) {
        if (!hasNotificationPermission()) return
        NotificationManagerCompat.from(context).notify(PERSISTENT_ID, buildPersistent(text))
    }

    fun cancelPersistent() {
        NotificationManagerCompat.from(context).cancel(PERSISTENT_ID)
    }

    /** 检测事件通知（有人出现/离开） */
    fun notifyDetection(text: String) {
        notifyInternal(DETECTION_ID, CHANNEL_DETECTION, text)
    }

    /** 设备状态通知（断开/重连/失败） */
    fun notifyDeviceStatus(text: String) {
        notifyInternal(DEVICE_STATUS_ID, CHANNEL_DEVICE_STATUS, text)
    }

    private fun notifyInternal(id: Int, channel: String, text: String) {
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("宿管雷达")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

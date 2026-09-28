package com.dormradar.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dormradar.app.core.data.SettingsStore
import com.dormradar.app.core.log.LogStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * 开机恢复（docs/06 阶段 3 任务 5）：
 * 用户开启过后台监控（monitorEnabled）则重新拉起前台服务，
 * 服务内部会用记住的 lastDeviceMac 自动重连设备。
 * BOOT_COMPLETED 属于后台启动前台服务的豁免场景。
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var log: LogStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = runBlocking {
            settings.isMonitorEnabled() && settings.lastMac() != null
        }
        if (enabled) {
            log.i(TAG, "开机恢复：拉起后台监控服务")
            RadarMonitorService.start(context)
        } else {
            log.i(TAG, "开机恢复：监控未开启，跳过")
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}

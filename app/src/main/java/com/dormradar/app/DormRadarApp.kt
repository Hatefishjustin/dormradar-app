package com.dormradar.app

import android.app.Application
import com.dormradar.app.core.notification.Notifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application 入口（Hilt）。
 * 启动时注册通知渠道（检测/设备状态/常驻，docs/01 §5.2）。
 */
@HiltAndroidApp
class DormRadarApp : Application() {

    @Inject lateinit var notifier: Notifier

    override fun onCreate() {
        super.onCreate()
        notifier.registerChannels()
    }
}

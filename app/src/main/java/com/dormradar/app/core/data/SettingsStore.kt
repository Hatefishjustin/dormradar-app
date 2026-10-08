package com.dormradar.app.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dormradar.app.core.monitor.MonitorMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "dormradar_settings")

/** 设备蓝牙密码（默认 HiLink，docs/02 §2） */
const val DEFAULT_BLUETOOTH_PASSWORD = "HiLink"

/**
 * 应用设置（DataStore，docs/04 §3.6）。
 * 事件流水等结构化数据在 Room（EventRepository）；本类仅承载轻量键值设置。
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val bluetoothPassword = stringPreferencesKey("bluetooth_password")
        val fakeDataEnabled = booleanPreferencesKey("fake_data_enabled")

        // ---- 阶段 3：后台监控（docs/06 阶段 3）----
        val monitorMode = stringPreferencesKey("monitor_mode")
        val monitorEnabled = booleanPreferencesKey("monitor_enabled")
        val lastDeviceMac = stringPreferencesKey("last_device_mac")
        val keepAliveGuideShown = booleanPreferencesKey("keep_alive_guide_shown")

        // ---- 阶段 4：调试工具（docs/06 阶段 4）----
        val frameLogEnabled = booleanPreferencesKey("frame_log_enabled")

        // ---- 阶段 4 扩展：首页卡片自定义（2026-10-07）；更新源已写死 UpdateConfig（不再走 DataStore）----
        val homeCardLayout = stringPreferencesKey("home_card_layout")

        // ---- 阶段 4 扩展：采样档案共享（2026-10-07，"测一次、处处用"）----
        // 诊断两步引导（无人+有人）完成后自动存档的 CSV，供无人基线定参/部署向导复用，免重复采样
        val deployArchiveCsv = stringPreferencesKey("deploy_archive_csv")
        val deployArchiveMeta = stringPreferencesKey("deploy_archive_meta") // "savedAtMs,resolutionCm,rows"

        // ---- 彩蛋：原型机一号机已看过成就弹窗的 MAC 集合（2026-10-08，规范化 CSV）----
        val easterEggSeenMacs = stringPreferencesKey("easter_egg_seen_macs")
    }

    val bluetoothPassword: Flow<String> =
        context.dataStore.data.map { it[Keys.bluetoothPassword] ?: DEFAULT_BLUETOOTH_PASSWORD }

    val fakeDataEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.fakeDataEnabled] ?: false }

    /** 三档监控模式（PRD §5），默认"有人提醒" */
    val monitorMode: Flow<MonitorMode> =
        context.dataStore.data.map { MonitorMode.fromName(it[Keys.monitorMode]) }

    /** 用户是否希望后台监控保持开启（前台服务运行的意愿源；BootReceiver 据此开机恢复） */
    val monitorEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.monitorEnabled] ?: false }

    /** 上次成功连接的设备 MAC（开机自动恢复连接用；模拟设备不记录） */
    val lastDeviceMac: Flow<String?> =
        context.dataStore.data.map { it[Keys.lastDeviceMac] }

    /** MIUI 保活引导是否已展示过（只在首次开启监控时弹） */
    val keepAliveGuideShown: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.keepAliveGuideShown] ?: false }

    /** 原始帧记录开关（调试用，默认关；高频路径读 FrameLogStore.enabled 快门） */
    val frameLogEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.frameLogEnabled] ?: false }

    /** 首页卡片布局（显示/隐藏 + 顺序；脏数据自动回退标准布局） */
    val homeCardLayout: Flow<HomeCardLayout> =
        context.dataStore.data.map { HomeCardLayout.decode(it[Keys.homeCardLayout]) }

    /** 最近一次两步引导采样的档案（CSV；null = 无档案） */
    val deployArchiveCsv: Flow<String?> =
        context.dataStore.data.map { it[Keys.deployArchiveCsv] }

    /** 档案元信息："savedAtMs,resolutionCm,rows" */
    val deployArchiveMeta: Flow<String?> =
        context.dataStore.data.map { it[Keys.deployArchiveMeta] }

    suspend fun setPassword(value: String) = set(Keys.bluetoothPassword, value)
    suspend fun setFakeDataEnabled(value: Boolean) = set(Keys.fakeDataEnabled, value)
    suspend fun setMonitorMode(value: MonitorMode) = set(Keys.monitorMode, value.name)
    suspend fun setMonitorEnabled(value: Boolean) = set(Keys.monitorEnabled, value)
    suspend fun setKeepAliveGuideShown(value: Boolean) = set(Keys.keepAliveGuideShown, value)
    suspend fun setFrameLogEnabled(value: Boolean) = set(Keys.frameLogEnabled, value)
    suspend fun setHomeCardLayout(value: HomeCardLayout) = set(Keys.homeCardLayout, value.encode())

    /** 保存采样档案（覆盖式：只保留最近一次；rows 写进 meta 供 UI 显示） */
    suspend fun saveDeployArchive(csv: String, resolutionCm: Int, rows: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.deployArchiveCsv] = csv
            prefs[Keys.deployArchiveMeta] = "${System.currentTimeMillis()},$resolutionCm,$rows"
        }
    }

    suspend fun clearDeployArchive() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.deployArchiveCsv)
            prefs.remove(Keys.deployArchiveMeta)
        }
    }

    suspend fun setLastDeviceMac(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.lastDeviceMac) else prefs[Keys.lastDeviceMac] = value
        }
    }

    /** 已看过原型机彩蛋的 MAC 集合（规范化大写无分隔符） */
    val easterEggSeenMacs: Flow<Set<String>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.easterEggSeenMacs]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        }

    /** 永久记住某台设备已看过彩蛋（幂等追加） */
    suspend fun markEasterEggSeen(macNormalized: String) {
        context.dataStore.edit { prefs ->
            val old = prefs[Keys.easterEggSeenMacs]
                ?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            prefs[Keys.easterEggSeenMacs] = (old + macNormalized).joinToString(",")
        }
    }

    private suspend fun <T> set(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    /** 连接时一次性读取密码 */
    suspend fun password(): String = bluetoothPassword.first()

    /** 连接时一次性读取模拟开关 */
    suspend fun isFakeDataEnabled(): Boolean = fakeDataEnabled.first()

    /** BootReceiver 开机恢复用（一次性读取） */
    suspend fun isMonitorEnabled(): Boolean = monitorEnabled.first()

    suspend fun lastMac(): String? = lastDeviceMac.first()
}

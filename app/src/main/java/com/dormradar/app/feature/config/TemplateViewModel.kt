package com.dormradar.app.feature.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.data.ConfigCodec
import com.dormradar.app.core.data.ConfigDiff
import com.dormradar.app.core.data.ConfigDiffLine
import com.dormradar.app.core.data.ConfigRepository
import com.dormradar.app.core.data.ConfigSnapshotData
import com.dormradar.app.core.data.room.ConfigSnapshotEntity
import com.dormradar.app.core.device.OpResult
import com.dormradar.app.core.device.RadarConnection
import com.dormradar.app.core.log.LogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 配置模板（阶段 4 任务 3：保存/应用/差异对比；"回滚" = 应用旧模板，天然支持） */
@HiltViewModel
class TemplateViewModel @Inject constructor(
    private val repo: ConfigRepository,
    private val connection: RadarConnection,
    private val log: LogStore,
) : ViewModel() {

    val templates: StateFlow<List<ConfigSnapshotEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deviceInfo = connection.deviceInfo
    val configBusy = connection.configBusy

    /** 当前设备配置（未连接/未就绪 = null） */
    val currentConfig: ConfigSnapshotData? get() = ConfigCodec.fromDeviceInfo(deviceInfo.value)

    fun saveCurrent(name: String, onDone: (String?) -> Unit) {
        val info = deviceInfo.value
        val data = ConfigCodec.fromDeviceInfo(info)
        if (info == null || data == null) {
            onDone("设备未连接或参数未就绪")
            return
        }
        viewModelScope.launch {
            repo.save(info.mac, name.ifBlank { "未命名模板" }, data)
            log.i(TAG, "模板已保存：$name（${info.mac}）")
            onDone(null)
        }
    }

    /** 模板与当前设备的差异（模板损坏或未连接 = null） */
    fun diffWithCurrent(template: ConfigSnapshotEntity): List<ConfigDiffLine>? {
        val t = ConfigCodec.decode(template.configJson) ?: return null
        val c = currentConfig ?: return null
        return ConfigDiff.diff(t, c)
    }

    /** 应用模板到设备（参数 → 灵敏度；分辨率暂不自动切——影响门含义，提示用户手动切） */
    fun apply(template: ConfigSnapshotEntity, onDone: (String?) -> Unit) {
        val data = ConfigCodec.decode(template.configJson)
        if (data == null) {
            onDone("模板数据损坏，无法应用")
            return
        }
        viewModelScope.launch {
            val gateCount = data.motionSensitivities.size
            val paramsResult = connection.applyParams(
                data.maxMoveGate.coerceIn(2, 8),
                data.maxStaticGate.coerceIn(2, 8),
                data.unmannedSeconds,
            )
            if (paramsResult != OpResult.Ok) {
                onDone("参数写入失败：${(paramsResult as OpResult.Fail).reason}")
                return@launch
            }
            // 灵敏度表按当前设备门数截断/补齐（设备门数以连接后参数为准）
            val cur = deviceInfo.value?.params
            if (cur != null && cur.motionSensitivities.size != gateCount) {
                val n = cur.motionSensitivities.size
                val motion = IntArray(n) { data.motionSensitivities.getOrElse(it) { 100 } }
                val static = IntArray(n) { data.staticSensitivities.getOrElse(it) { 100 } }
                val r = connection.applySensitivityTable(motion, static)
                if (r != OpResult.Ok) {
                    onDone("灵敏度写入失败：${(r as OpResult.Fail).reason}")
                    return@launch
                }
            } else {
                val r = connection.applySensitivityTable(data.motionSensitivities, data.staticSensitivities)
                if (r != OpResult.Ok) {
                    onDone("灵敏度写入失败：${(r as OpResult.Fail).reason}")
                    return@launch
                }
            }
            log.i(TAG, "模板已应用：${template.name}")
            onDone(null)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    private companion object {
        const val TAG = "Template"
    }
}

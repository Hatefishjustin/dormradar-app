package com.dormradar.app.feature.deploy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.data.ConfigCodec
import com.dormradar.app.core.data.ConfigRepository
import com.dormradar.app.core.data.SettingsStore
import com.dormradar.app.core.deploy.DeployAdvisor
import com.dormradar.app.core.deploy.DeploySuggestion
import com.dormradar.app.core.deploy.DiagArchiveReuse
import com.dormradar.app.core.deploy.EnergySamples
import com.dormradar.app.core.deploy.GateSample
import com.dormradar.app.core.device.OpResult
import com.dormradar.app.core.device.RadarConnection
import com.dormradar.app.core.log.DistanceDiagFormats
import com.dormradar.app.core.log.LogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 采样种类：底噪（清场）/ 走动（正常活动） */
enum class SampleKind(val label: String) {
    NOISE("底噪"),
    ACTIVITY("走动"),
}

/**
 * 部署向导（阶段 4 任务 2，docs/06 §阶段 4 验收主线）：
 * 位置建议 → 底噪采样（清场）→ 走动采样 → [DeployAdvisor] 自动建议 → 应用并存模板。
 * 模拟设备可全流程体验（采样数据来自 FakeRadarSource 的工程帧）。
 */
@HiltViewModel
class DeployViewModel @Inject constructor(
    private val connection: RadarConnection,
    private val configRepo: ConfigRepository,
    private val settings: SettingsStore,
    private val log: LogStore,
) : ViewModel() {

    companion object {
        const val SAMPLE_SECONDS = 30
        private const val TAG = "Deploy"
    }

    val deviceInfo = connection.deviceInfo
    val gateEnergies = connection.gateEnergies
    val engineeringMode = connection.engineeringMode
    val configBusy = connection.configBusy

    private val _noiseSamples = MutableStateFlow<EnergySamples?>(null)
    val noiseSamples: StateFlow<EnergySamples?> = _noiseSamples.asStateFlow()

    private val _activitySamples = MutableStateFlow<EnergySamples?>(null)
    val activitySamples: StateFlow<EnergySamples?> = _activitySamples.asStateFlow()

    private val _suggestion = MutableStateFlow<DeploySuggestion?>(null)
    val suggestion: StateFlow<DeploySuggestion?> = _suggestion.asStateFlow()

    private val _sampling = MutableStateFlow<SampleKind?>(null)
    val sampling: StateFlow<SampleKind?> = _sampling.asStateFlow()

    private val _remainSeconds = MutableStateFlow(0)
    val remainSeconds: StateFlow<Int> = _remainSeconds.asStateFlow()

    private val _unmannedSeconds = MutableStateFlow(5)
    val unmannedSeconds: StateFlow<Int> = _unmannedSeconds.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    private var sampleJob: Job? = null

    /** 诊断两步引导的存档是否可用（采样档案共享 2026-10-07：测一次处处用） */
    val archiveAvailable: StateFlow<Boolean> = settings.deployArchiveCsv
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 开/关工程模式（采样数据源；页面退出时由 UI 调 false，模式照抄灵敏度页） */
    fun setEngineering(enabled: Boolean) {
        viewModelScope.launch {
            try {
                connection.setEngineeringMode(enabled)
            } catch (_: Exception) {
                // 未连接/超时：设备断电重连后工程模式本就复位
            }
        }
    }

    /**
     * 采样 [SAMPLE_SECONDS] 秒：每门聚合 max(运动能量, 静止能量) 的 avg/max。
     * 完成后存入对应样本流并重算建议。
     */
    fun startSampling(kind: SampleKind) {
        if (_sampling.value != null) return
        val gateCount = deviceInfo.value?.params?.motionSensitivities?.size ?: run {
            _message.value = "设备参数未就绪，无法采样"
            return
        }
        val sums = IntArray(gateCount)
        val maxes = IntArray(gateCount)
        val counts = IntArray(gateCount)
        _sampling.value = kind
        _remainSeconds.value = SAMPLE_SECONDS
        sampleJob = viewModelScope.launch {
            val collector = launch {
                gateEnergies.collect { ge ->
                    if (ge == null) return@collect
                    for (g in 0 until gateCount) {
                        val e = maxOf(
                            ge.moveGateEnergies.getOrElse(g) { 0 },
                            ge.staticGateEnergies.getOrElse(g) { 0 },
                        )
                        sums[g] += e
                        if (e > maxes[g]) maxes[g] = e
                        counts[g]++
                    }
                }
            }
            repeat(SAMPLE_SECONDS) { i ->
                delay(1_000)
                _remainSeconds.value = SAMPLE_SECONDS - i - 1
            }
            collector.cancel()
            val samples = EnergySamples((0 until gateCount).map { g ->
                GateSample(
                    avgEnergy = if (counts[g] > 0) sums[g] / counts[g] else 0,
                    maxEnergy = maxes[g],
                )
            })
            if (kind == SampleKind.NOISE) _noiseSamples.value = samples else _activitySamples.value = samples
            _sampling.value = null
            _remainSeconds.value = 0
            recomputeSuggestion()
        }
    }

    private fun recomputeSuggestion() {
        val n = _noiseSamples.value
        val a = _activitySamples.value
        _suggestion.value = if (n != null && a != null) DeployAdvisor.suggest(n, a) else null
    }

    /**
     * 复用「距离诊断」两步引导的存档（用户需求 2026-10-07：测一次处处用）：
     * 无人段 → 底噪样本、有人段 → 走动样本，直接生成部署建议，免两段 30s 采样。
     * 档案缺失或缺段时如实提示；与实时采样的聚合口径一致（每门 max(运动,静止) 的 avg/max）。
     */
    fun useArchivedSamples() {
        if (_sampling.value != null) return
        viewModelScope.launch {
            val csv = settings.deployArchiveCsv.first()
            if (csv.isNullOrBlank()) {
                _message.value = "还没有存档采样：先到「设置 → 调试工具 → 距离诊断」完成一次两步引导"
                return@launch
            }
            val meta = settings.deployArchiveMeta.first() ?: ""
            val ageMin = meta.split(',').firstOrNull()?.toLongOrNull()?.let {
                (System.currentTimeMillis() - it) / 60_000
            }
            val archived = DistanceDiagFormats.parseCsv(csv)
            val noPerson = DiagArchiveReuse.noPersonEngineering(archived)
            val person = DiagArchiveReuse.personEngineering(archived)
            val missing = buildList {
                if (noPerson.isEmpty()) add("无人")
                if (person.isEmpty()) add("有人")
            }
            if (missing.isNotEmpty()) {
                _message.value = "存档里缺少「${missing.joinToString("和")}阶段」的工程帧数据，无法复用——请重新走一次两步引导"
                return@launch
            }
            val gateCount = maxOf(DiagArchiveReuse.gateCountOf(noPerson), DiagArchiveReuse.gateCountOf(person))
            _noiseSamples.value = DiagArchiveReuse.toEnergySamples(noPerson, gateCount)
            _activitySamples.value = DiagArchiveReuse.toEnergySamples(person, gateCount)
            recomputeSuggestion()
            _message.value = "已复用 ${ageMin ?: "?"} 分钟前的诊断存档" +
                "（无人 ${noPerson.size} 帧 / 有人 ${person.size} 帧），建议已生成，可直接应用或重新采样覆盖"
        }
    }

    fun setUnmannedSeconds(seconds: Int) {
        _unmannedSeconds.value = seconds.coerceIn(0, 600)
    }

    /** 应用建议：灵敏度表 → 门参数/无人时长 → 存模板（应用即回滚手段，模板页可反悔） */
    fun applySuggestion(onDone: (String?) -> Unit) {
        val s = _suggestion.value
        if (s == null) {
            onDone("还没有可应用的建议（先完成两步采样）")
            return
        }
        if (_applying.value) return
        _applying.value = true
        viewModelScope.launch {
            val r1 = connection.applySensitivityTable(s.motionSensitivities, s.staticSensitivities)
            if (r1 != OpResult.Ok) {
                _applying.value = false
                onDone("灵敏度写入失败：${(r1 as OpResult.Fail).reason}")
                return@launch
            }
            val r2 = connection.applyParams(s.maxMoveGate, s.maxStaticGate, _unmannedSeconds.value)
            if (r2 != OpResult.Ok) {
                _applying.value = false
                onDone("门参数写入失败：${(r2 as OpResult.Fail).reason}")
                return@launch
            }
            val info = connection.deviceInfo.value
            val data = ConfigCodec.fromDeviceInfo(info)
            if (info != null && data != null) {
                val name = "部署向导 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
                configRepo.save(info.mac, name, data)
                log.i(TAG, "部署参数已应用并存模板：$name")
            }
            _applying.value = false
            onDone(null)
        }
    }

    override fun onCleared() {
        sampleJob?.cancel()
        super.onCleared()
    }
}

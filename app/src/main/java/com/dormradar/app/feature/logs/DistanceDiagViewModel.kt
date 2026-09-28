package com.dormradar.app.feature.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.device.RadarConnection
import com.dormradar.app.core.log.DiagPhase
import com.dormradar.app.core.log.DistanceDiagAnalysis
import com.dormradar.app.core.log.DistanceDiagRecorder
import com.dormradar.app.core.log.DistanceDiagSample
import com.dormradar.app.core.log.LogExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** 引导流程所处阶段 */
enum class GuideStage {
    /** 未开始 */
    IDLE,

    /** 第 1 步：请保持无人 */
    NO_PERSON,

    /** 第 2 步：请在走廊走动 */
    PERSON,

    /** 两步都完成，已出结论 */
    DONE,
}

/** 引导 UI 状态 */
data class GuideUi(val stage: GuideStage, val remainSeconds: Int) {
    val running: Boolean get() = stage == GuideStage.NO_PERSON || stage == GuideStage.PERSON
}

/**
 * 距离诊断页（取证工具，定位"首页距离跳动/不准"根因）。
 *
 * 第三轮改造（用户反馈 2026-09-28"我不知道什么时候要无人、什么时候要有人"）：
 * 去掉"自己安排两段"的自由记录，改为 **App 指挥的两步引导**——
 * 第 1 步明确要求保持无人、第 2 步明确要求在走廊走动，每步倒计时自动切换并自动打阶段标记。
 * 于是分析层能直接算出误报率与检出率，而不是靠设备自报状态循环论证。
 */
@HiltViewModel
class DistanceDiagViewModel @Inject constructor(
    private val recorder: DistanceDiagRecorder,
    private val connection: RadarConnection,
    private val exporter: LogExporter,
) : ViewModel() {

    val samples: StateFlow<List<DistanceDiagSample>> = recorder.samples
    val deviceInfo = connection.deviceInfo

    private val _guide = MutableStateFlow(GuideUi(GuideStage.IDLE, 0))
    val guide: StateFlow<GuideUi> = _guide.asStateFlow()

    private var guideJob: Job? = null

    /** 当前距离分辨率（cm）：0.2m 档 = 20，否则 0.75m 档 = 75 */
    val resolutionCm: Int get() = if (connection.deviceInfo.value?.resolution02m == true) 20 else 75

    /**
     * 实时分析结论。用 WhileSubscribed 限定只在诊断页可见时计算——
     * 单次 summarize 是 O(n log n)（最多 12000 行），不必要时不该每帧跑。
     */
    val summary: StateFlow<DistanceDiagAnalysis.Summary?> = recorder.samples
        .map { DistanceDiagAnalysis.summarize(it, resolutionCm) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 工程模式开关（诊断页进入自动开、离开自动关；顺带采集各门能量） */
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
     * 开始两步引导：清空旧数据 → 无人 [PHASE_SECONDS] 秒 → 有人 [PHASE_SECONDS] 秒 → 自动停止出结论。
     * 阶段标记在每步开始时设置，只影响之后写入的样本。
     */
    fun startGuide() {
        if (_guide.value.running) return
        recorder.clear()
        guideJob?.cancel()
        guideJob = viewModelScope.launch {
            recorder.setPhase(DiagPhase.NO_PERSON)
            recorder.start()
            runPhase(GuideStage.NO_PERSON)

            recorder.setPhase(DiagPhase.PERSON)
            runPhase(GuideStage.PERSON)

            recorder.stop()
            _guide.value = GuideUi(GuideStage.DONE, 0)
        }
    }

    fun cancelGuide() {
        guideJob?.cancel()
        guideJob = null
        recorder.stop()
        _guide.value = GuideUi(GuideStage.IDLE, 0)
    }

    fun clear() {
        cancelGuide()
        recorder.clear()
        _guide.value = GuideUi(GuideStage.IDLE, 0)
    }

    private suspend fun runPhase(stage: GuideStage) {
        for (remaining in PHASE_SECONDS downTo 1) {
            _guide.value = GuideUi(stage, remaining)
            delay(1_000)
        }
    }

    /** 单独导出诊断 CSV（FileProvider 分享），err 为 null 表示成功 */
    fun exportCsv(onReady: (File) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(exporter.exportDistanceCsv())
            } catch (e: Exception) {
                onError(e.message ?: "未知错误")
            }
        }
    }

    override fun onCleared() {
        // 离开页面必须停记录，否则会一直往后写、把环形缓冲挤满
        guideJob?.cancel()
        recorder.stop()
        super.onCleared()
    }

    companion object {
        /** 每个阶段的时长（秒） */
        const val PHASE_SECONDS = 45

        /** SharingStarted 策略统一（K2 下裸引用报错的历史教训） */
        val SHARING = SharingStarted.WhileSubscribed(5_000)
    }
}

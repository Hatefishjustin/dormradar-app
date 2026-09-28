package com.dormradar.app.feature.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.device.RadarConnection
import com.dormradar.app.core.log.DistanceDiagAnalysis
import com.dormradar.app.core.log.DistanceDiagRecorder
import com.dormradar.app.core.log.DistanceDiagSample
import com.dormradar.app.core.log.LogExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 距离诊断页（取证工具：每上报帧一行 CSV，定位"首页距离跳动/不准"根因）。
 *
 * 与 v0.5.2 的区别：不只给原始数据，还把 [DistanceDiagAnalysis] 的结论实时算出来展示——
 * 站在设备旁记 1~2 分钟就能看到判定，不必导出 CSV 再找人分析。
 */
@HiltViewModel
class DistanceDiagViewModel @Inject constructor(
    private val recorder: DistanceDiagRecorder,
    private val connection: RadarConnection,
    private val exporter: LogExporter,
) : ViewModel() {

    val recording: StateFlow<Boolean> = recorder.recording
    val samples: StateFlow<List<DistanceDiagSample>> = recorder.samples
    val deviceInfo = connection.deviceInfo

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

    fun start() = recorder.start()
    fun stop() = recorder.stop()
    fun clear() = recorder.clear()

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

    companion object {
        /** SharingStarted 策略统一（K2 下裸引用报错的历史教训） */
        val SHARING = SharingStarted.WhileSubscribed(5_000)
    }
}

package com.dormradar.app.feature.config

import androidx.lifecycle.ViewModel
import com.dormradar.app.core.device.ConnQualityStats
import com.dormradar.app.core.device.RadarConnection
import com.dormradar.app.core.device.ReconnectRecord
import com.dormradar.app.core.device.RssiSample
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** 连接质量页（阶段 4 任务 4：RSSI 曲线 / 帧统计 / 重连历史） */
@HiltViewModel
class QualityViewModel @Inject constructor(
    connection: RadarConnection,
) : ViewModel() {
    val rssiSamples: StateFlow<List<RssiSample>> = connection.rssiSamples
    val connStats: StateFlow<ConnQualityStats> = connection.connStats
    val reconnectHistory: StateFlow<List<ReconnectRecord>> = connection.reconnectHistory
    val deviceInfo = connection.deviceInfo
}

package com.dormradar.app.core.data

import com.dormradar.app.core.data.room.EventDao
import com.dormradar.app.core.data.room.RadarEventEntity
import com.dormradar.app.core.monitor.RadarEventType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** 时间线筛选组（UI 的 FilterChip 与 DAO 查询的桥梁） */
enum class EventFilter(val label: String, val types: List<RadarEventType>?) {
    ALL("全部", null),
    ACTIVITY("活动", listOf(RadarEventType.APPEAR, RadarEventType.LEAVE)),
    CONNECTION("连接", listOf(RadarEventType.CONNECT, RadarEventType.DISCONNECT, RadarEventType.RECONNECTED, RadarEventType.GIVE_UP)),
    MONITOR("监控", listOf(RadarEventType.MONITOR_START, RadarEventType.MONITOR_STOP)),
}

/**
 * 事件流水仓库：入库 + 查询 + 保留策略（docs/04 §3.6：默认保留 30 天，先到先删）。
 */
@Singleton
class EventRepository @Inject constructor(
    private val dao: EventDao,
) {
    companion object {
        private val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        private const val DEFAULT_LIMIT = 300
    }

    suspend fun record(type: RadarEventType, mac: String, detail: String, payload: String = "") {
        dao.insert(
            RadarEventEntity(
                mac = mac,
                timestamp = System.currentTimeMillis(),
                type = type.name,
                detail = detail,
                payload = payload,
            ),
        )
        dao.pruneBefore(System.currentTimeMillis() - RETENTION_MS)
    }

    fun observe(filter: EventFilter, limit: Int = DEFAULT_LIMIT): Flow<List<RadarEventEntity>> =
        dao.observe(filter.types?.map { it.name }, limit)

    /** 全量导出（日志 zip 的 events.csv，时间正序，上限保护） */
    suspend fun dumpAll(limit: Int = 20_000): List<RadarEventEntity> = dao.getAll(limit)

    suspend fun clearAll() = dao.clear()
}

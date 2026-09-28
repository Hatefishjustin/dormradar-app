package com.dormradar.app.core.data.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 事件流水（docs/04 §3.6：有人/离开/断连/重连等，全部带时间戳入库可查） */
@Entity(tableName = "radar_events", indices = [Index("timestamp")])
data class RadarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mac: String,
    val timestamp: Long,
    val type: String,
    val detail: String,
    /** 事件发生时的能量快照（阶段 4 事件回看调参用；人类可读文本） */
    val payload: String = "",
)

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: RadarEventEntity)

    /** types 为 null 时返回全部（事件时间线按筛选组查询） */
    @Query("SELECT * FROM radar_events WHERE (:types IS NULL OR type IN (:types)) ORDER BY id DESC LIMIT :limit")
    fun observe(types: List<String>?, limit: Int): Flow<List<RadarEventEntity>>

    /** 全量导出（日志 zip 的 events.csv 用，时间正序） */
    @Query("SELECT * FROM radar_events ORDER BY id ASC LIMIT :limit")
    suspend fun getAll(limit: Int): List<RadarEventEntity>

    @Query("DELETE FROM radar_events")
    suspend fun clear()

    @Query("DELETE FROM radar_events WHERE timestamp < :threshold")
    suspend fun pruneBefore(threshold: Long)
}

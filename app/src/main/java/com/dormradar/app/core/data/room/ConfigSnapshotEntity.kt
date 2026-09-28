package com.dormradar.app.core.data.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 配置快照（docs/04 §3.6：模板与历史快照）。
 * configJson 由 [com.dormradar.app.core.data.ConfigCodec] 编解码（纯 Kotlin，可单测）。
 */
@Entity(tableName = "config_snapshots", indices = [Index("deviceMac")])
data class ConfigSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceMac: String,
    val timestamp: Long,
    val name: String,
    val configJson: String,
)

@Dao
interface ConfigSnapshotDao {
    @Insert
    suspend fun insert(snapshot: ConfigSnapshotEntity): Long

    @Query("SELECT * FROM config_snapshots ORDER BY id DESC")
    fun observeAll(): Flow<List<ConfigSnapshotEntity>>

    @Query("SELECT * FROM config_snapshots WHERE id = :id")
    suspend fun getById(id: Long): ConfigSnapshotEntity?

    @Query("DELETE FROM config_snapshots WHERE id = :id")
    suspend fun deleteById(id: Long)
}

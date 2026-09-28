package com.dormradar.app.core.data

import com.dormradar.app.core.data.room.ConfigSnapshotDao
import com.dormradar.app.core.data.room.ConfigSnapshotEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 配置模板仓库（docs/04 §3.6 ConfigSnapshot）：
 * 保存当前配置为命名模板 / 列表 / 删除；应用与差异对比由 UI 层调 RadarConnection + [ConfigDiff] 完成。
 */
@Singleton
class ConfigRepository @Inject constructor(
    private val dao: ConfigSnapshotDao,
) {
    fun observeAll(): Flow<List<ConfigSnapshotEntity>> = dao.observeAll()

    suspend fun save(mac: String, name: String, data: ConfigSnapshotData): Long =
        dao.insert(
            ConfigSnapshotEntity(
                deviceMac = mac,
                timestamp = System.currentTimeMillis(),
                name = name,
                configJson = ConfigCodec.encode(data),
            ),
        )

    suspend fun get(id: Long): ConfigSnapshotEntity? = dao.getById(id)

    suspend fun delete(id: Long) = dao.deleteById(id)
}

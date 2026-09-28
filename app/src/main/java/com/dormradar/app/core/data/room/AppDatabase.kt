package com.dormradar.app.core.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 项目唯一数据库（v2：+config_snapshots 模板表；radar_events +payload 列） */
@Database(entities = [RadarEventEntity::class, ConfigSnapshotEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun configSnapshotDao(): ConfigSnapshotDao

    companion object {
        /** v1→v2（阶段 4）：配置模板表 + 事件能量快照列 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `config_snapshots` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`deviceMac` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`configJson` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_config_snapshots_deviceMac` ON `config_snapshots` (`deviceMac`)",
                )
                db.execSQL("ALTER TABLE `radar_events` ADD COLUMN `payload` TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}

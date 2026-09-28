package com.dormradar.app.di

import android.content.Context
import androidx.room.Room
import com.dormradar.app.core.data.room.AppDatabase
import com.dormradar.app.core.data.room.ConfigSnapshotDao
import com.dormradar.app.core.data.room.EventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 数据层依赖（Room 数据库与 DAO） */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "dormradar.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    @Provides
    fun provideConfigSnapshotDao(db: AppDatabase): ConfigSnapshotDao = db.configSnapshotDao()
}

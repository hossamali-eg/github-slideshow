package com.wifimanager.di

import android.content.Context
import androidx.room.Room
import com.wifimanager.data.api.RouterApiService
import com.wifimanager.data.db.AppDatabase
import com.wifimanager.data.db.ConnectedDeviceDao
import com.wifimanager.data.db.RouterConfigDao
import com.wifimanager.data.db.ScheduleRuleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "wifi_manager.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRouterConfigDao(db: AppDatabase): RouterConfigDao = db.routerConfigDao()

    @Provides
    fun provideConnectedDeviceDao(db: AppDatabase): ConnectedDeviceDao = db.connectedDeviceDao()

    @Provides
    fun provideScheduleRuleDao(db: AppDatabase): ScheduleRuleDao = db.scheduleRuleDao()

    @Provides
    @Singleton
    fun provideRouterApiService(): RouterApiService = RouterApiService()
}

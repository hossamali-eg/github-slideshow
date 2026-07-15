package com.wifimanager.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wifimanager.data.models.ConnectedDevice
import com.wifimanager.data.models.RouterConfig
import com.wifimanager.data.models.ScheduleRule

@Database(
    entities = [RouterConfig::class, ConnectedDevice::class, ScheduleRule::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routerConfigDao(): RouterConfigDao
    abstract fun connectedDeviceDao(): ConnectedDeviceDao
    abstract fun scheduleRuleDao(): ScheduleRuleDao
}

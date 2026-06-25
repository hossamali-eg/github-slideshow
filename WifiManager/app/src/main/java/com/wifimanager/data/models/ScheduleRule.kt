package com.wifimanager.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_rules")
data class ScheduleRule(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val ruleType: ScheduleRuleType,
    val targetMac: String = "",  // empty = all devices
    val action: ScheduleAction,
    val startTime: String,    // HH:mm
    val endTime: String = "", // HH:mm, empty = no end
    val daysOfWeek: String = "1,2,3,4,5,6,7",  // comma-separated day numbers
    val isEnabled: Boolean = true,
    val speedLimitDownload: Int = 0,
    val speedLimitUpload: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ScheduleRuleType {
    INTERNET_TOGGLE,
    DEVICE_BLOCK,
    SPEED_LIMIT,
    PARENTAL_CONTROL
}

enum class ScheduleAction {
    BLOCK,
    UNBLOCK,
    ENABLE_INTERNET,
    DISABLE_INTERNET,
    LIMIT_SPEED,
    UNLIMITED_SPEED
}

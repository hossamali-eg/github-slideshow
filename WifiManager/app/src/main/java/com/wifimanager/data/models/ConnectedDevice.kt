package com.wifimanager.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connected_devices")
data class ConnectedDevice(
    @PrimaryKey
    val macAddress: String,
    val ipAddress: String = "",
    val hostname: String = "",
    val customName: String = "",
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val isBlocked: Boolean = false,
    val isOnline: Boolean = true,
    val downloadSpeedLimit: Int = 0,  // kbps, 0 = unlimited
    val uploadSpeedLimit: Int = 0,    // kbps, 0 = unlimited
    val downloadSpeed: Double = 0.0,  // current speed in Mbps
    val uploadSpeed: Double = 0.0,    // current speed in Mbps
    val totalDataUsed: Long = 0L,     // bytes
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val scheduleEnabled: Boolean = false,
    val scheduleBlockFrom: String = "",  // HH:mm format
    val scheduleBlockTo: String = "",
    val signalStrength: Int = 0  // dBm
)

enum class DeviceType {
    UNKNOWN,
    PHONE,
    TABLET,
    LAPTOP,
    DESKTOP,
    TV,
    GAME_CONSOLE,
    SMART_HOME,
    ROUTER,
    CAMERA
}

data class DeviceStats(
    val macAddress: String,
    val downloadSpeed: Double,
    val uploadSpeed: Double,
    val totalDownload: Long,
    val totalUpload: Long
)

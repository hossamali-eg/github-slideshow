package com.wifimanager.data.models

data class NetworkStats(
    val isInternetEnabled: Boolean = true,
    val totalDownloadSpeed: Double = 0.0,  // Mbps
    val totalUploadSpeed: Double = 0.0,    // Mbps
    val totalDevices: Int = 0,
    val activeDevices: Int = 0,
    val blockedDevices: Int = 0,
    val downloadLimit: Int = 0,   // Mbps, 0 = unlimited
    val uploadLimit: Int = 0,     // Mbps, 0 = unlimited
    val pingMs: Int = 0,
    val ssid: String = "",
    val channel: Int = 0,
    val frequency: String = "2.4GHz",
    val signalStrength: Int = 0,
    val routerUptime: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

data class SpeedTestResult(
    val downloadSpeed: Double,
    val uploadSpeed: Double,
    val pingMs: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class RouterStatus(
    val isConnected: Boolean,
    val isAuthenticated: Boolean,
    val errorMessage: String = ""
)

package com.wifimanager.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.wifimanager.data.models.ConnectedDevice

@Dao
interface ConnectedDeviceDao {
    @Query("SELECT * FROM connected_devices ORDER BY lastSeen DESC")
    fun getAllDevices(): LiveData<List<ConnectedDevice>>

    @Query("SELECT * FROM connected_devices WHERE isOnline = 1 ORDER BY downloadSpeed DESC")
    fun getOnlineDevices(): LiveData<List<ConnectedDevice>>

    @Query("SELECT * FROM connected_devices WHERE isBlocked = 1")
    fun getBlockedDevices(): LiveData<List<ConnectedDevice>>

    @Query("SELECT * FROM connected_devices WHERE macAddress = :mac")
    suspend fun getDeviceByMac(mac: String): ConnectedDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: ConnectedDevice)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<ConnectedDevice>)

    @Update
    suspend fun update(device: ConnectedDevice)

    @Query("UPDATE connected_devices SET isBlocked = :blocked WHERE macAddress = :mac")
    suspend fun setBlocked(mac: String, blocked: Boolean)

    @Query("UPDATE connected_devices SET downloadSpeedLimit = :download, uploadSpeedLimit = :upload WHERE macAddress = :mac")
    suspend fun setSpeedLimit(mac: String, download: Int, upload: Int)

    @Query("UPDATE connected_devices SET isOnline = 0")
    suspend fun markAllOffline()

    @Query("UPDATE connected_devices SET isOnline = 1, lastSeen = :timestamp WHERE macAddress = :mac")
    suspend fun markOnline(mac: String, timestamp: Long)

    @Query("UPDATE connected_devices SET customName = :name WHERE macAddress = :mac")
    suspend fun renameDevice(mac: String, name: String)

    @Query("DELETE FROM connected_devices WHERE macAddress = :mac")
    suspend fun delete(mac: String)
}

package com.wifimanager.data.repository

import androidx.lifecycle.LiveData
import com.wifimanager.data.api.RouterApiService
import com.wifimanager.data.db.ConnectedDeviceDao
import com.wifimanager.data.db.RouterConfigDao
import com.wifimanager.data.db.ScheduleRuleDao
import com.wifimanager.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouterRepository @Inject constructor(
    private val routerApi: RouterApiService,
    private val routerConfigDao: RouterConfigDao,
    private val deviceDao: ConnectedDeviceDao,
    private val scheduleDao: ScheduleRuleDao
) {
    // ==================== Router Config ====================

    val allRouters: LiveData<List<RouterConfig>> = routerConfigDao.getAllRouters()

    suspend fun saveRouterConfig(config: RouterConfig): Long =
        routerConfigDao.insert(config)

    suspend fun getActiveRouter(): RouterConfig? =
        routerConfigDao.getActiveRouter()

    suspend fun setActiveRouter(id: Int) {
        routerConfigDao.deactivateAll()
        routerConfigDao.setActive(id)
    }

    suspend fun deleteRouter(config: RouterConfig) =
        routerConfigDao.delete(config)

    // ==================== Connection ====================

    suspend fun connect(config: RouterConfig): RouterStatus = withContext(Dispatchers.IO) {
        routerApi.configure(config.ipAddress)
        val status = when (config.routerType) {
            RouterType.TP_LINK -> routerApi.loginTPLink(config.username, config.password)
            else -> routerApi.loginGeneric(config.username, config.password)
        }
        if (status.isAuthenticated) {
            routerConfigDao.updateLastConnected(config.id, System.currentTimeMillis())
        }
        status
    }

    suspend fun testConnection(ip: String): RouterStatus =
        routerApi.testConnection(ip)

    // ==================== Devices ====================

    val allDevices: LiveData<List<ConnectedDevice>> = deviceDao.getAllDevices()
    val onlineDevices: LiveData<List<ConnectedDevice>> = deviceDao.getOnlineDevices()
    val blockedDevices: LiveData<List<ConnectedDevice>> = deviceDao.getBlockedDevices()

    suspend fun refreshDevices(): List<ConnectedDevice> {
        val devices = routerApi.getConnectedDevices()
        deviceDao.markAllOffline()
        devices.forEach { device ->
            val existing = deviceDao.getDeviceByMac(device.macAddress)
            val merged = if (existing != null) {
                device.copy(
                    customName = existing.customName,
                    isBlocked = existing.isBlocked,
                    downloadSpeedLimit = existing.downloadSpeedLimit,
                    uploadSpeedLimit = existing.uploadSpeedLimit,
                    scheduleEnabled = existing.scheduleEnabled,
                    scheduleBlockFrom = existing.scheduleBlockFrom,
                    scheduleBlockTo = existing.scheduleBlockTo,
                    firstSeen = existing.firstSeen,
                    totalDataUsed = existing.totalDataUsed + (device.downloadSpeed * 1024).toLong()
                )
            } else device
            deviceDao.insert(merged)
            deviceDao.markOnline(device.macAddress, System.currentTimeMillis())
        }
        return devices
    }

    suspend fun blockDevice(mac: String, block: Boolean): Boolean {
        val success = routerApi.blockDevice(mac, block)
        if (success) deviceDao.setBlocked(mac, block)
        return success
    }

    suspend fun setSpeedLimit(mac: String, downloadKbps: Int, uploadKbps: Int): Boolean {
        val success = routerApi.setDeviceSpeedLimit(mac, downloadKbps, uploadKbps)
        if (success) deviceDao.setSpeedLimit(mac, downloadKbps, uploadKbps)
        return success
    }

    suspend fun renameDevice(mac: String, name: String) =
        deviceDao.renameDevice(mac, name)

    suspend fun setDeviceSchedule(mac: String, fromTime: String, toTime: String, enabled: Boolean) {
        val device = deviceDao.getDeviceByMac(mac) ?: return
        deviceDao.update(device.copy(
            scheduleEnabled = enabled,
            scheduleBlockFrom = fromTime,
            scheduleBlockTo = toTime
        ))
    }

    // ==================== Internet Control ====================

    suspend fun setInternetEnabled(enabled: Boolean): Boolean =
        routerApi.setInternetEnabled(enabled)

    // ==================== Network Stats ====================

    suspend fun getNetworkStats(): NetworkStats =
        routerApi.getNetworkStats()

    // ==================== Schedule Rules ====================

    val allScheduleRules: LiveData<List<ScheduleRule>> = scheduleDao.getAllRules()

    suspend fun saveScheduleRule(rule: ScheduleRule): Long =
        scheduleDao.insert(rule)

    suspend fun updateScheduleRule(rule: ScheduleRule) =
        scheduleDao.update(rule)

    suspend fun deleteScheduleRule(rule: ScheduleRule) =
        scheduleDao.delete(rule)

    suspend fun toggleScheduleRule(id: Int, enabled: Boolean) =
        scheduleDao.setEnabled(id, enabled)

    suspend fun getActiveRules(): List<ScheduleRule> =
        scheduleDao.getActiveRules()
}

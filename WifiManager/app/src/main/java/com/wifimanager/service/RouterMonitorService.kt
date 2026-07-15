package com.wifimanager.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wifimanager.R
import com.wifimanager.WifiManagerApp
import com.wifimanager.data.repository.RouterRepository
import com.wifimanager.ui.dashboard.DashboardActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class RouterMonitorService : Service() {

    @Inject
    lateinit var repository: RouterRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("جاري مراقبة الشبكة..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    val devices = repository.refreshDevices()
                    val onlineCount = devices.count { it.isOnline }
                    updateNotification("$onlineCount جهاز متصل")
                    checkScheduleRules()
                } catch (e: Exception) {
                    // Silent fail - continue monitoring
                }
                delay(30_000L) // Check every 30 seconds
            }
        }
    }

    private suspend fun checkScheduleRules() {
        val rules = repository.getActiveRules()
        val now = java.util.Calendar.getInstance()
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(java.util.Calendar.MINUTE)
        val currentDay = now.get(java.util.Calendar.DAY_OF_WEEK)

        rules.forEach { rule ->
            val days = rule.daysOfWeek.split(",").map { it.trim().toIntOrNull() ?: 0 }
            if (currentDay !in days) return@forEach

            val (startH, startM) = rule.startTime.split(":").map { it.toInt() }
            val startMinutes = startH * 60 + startM
            val currentMinutes = currentHour * 60 + currentMinute

            if (currentMinutes == startMinutes) {
                when (rule.action) {
                    com.wifimanager.data.models.ScheduleAction.DISABLE_INTERNET ->
                        repository.setInternetEnabled(false)
                    com.wifimanager.data.models.ScheduleAction.ENABLE_INTERNET ->
                        repository.setInternetEnabled(true)
                    else -> {}
                }
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, DashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, WifiManagerApp.CHANNEL_MONITOR)
            .setContentTitle("WiFi Manager")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_wifi)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}

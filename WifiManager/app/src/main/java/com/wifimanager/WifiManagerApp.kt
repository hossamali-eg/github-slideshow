package com.wifimanager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WifiManagerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR,
                "مراقبة الشبكة",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات مراقبة حالة الشبكة"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "تنبيهات الشبكة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات مهمة عن الشبكة"
            }

            val scheduleChannel = NotificationChannel(
                CHANNEL_SCHEDULE,
                "جدولة الإنترنت",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات الجدولة الزمنية"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(monitorChannel, alertChannel, scheduleChannel))
        }
    }

    companion object {
        const val CHANNEL_MONITOR = "channel_monitor"
        const val CHANNEL_ALERTS = "channel_alerts"
        const val CHANNEL_SCHEDULE = "channel_schedule"
    }
}

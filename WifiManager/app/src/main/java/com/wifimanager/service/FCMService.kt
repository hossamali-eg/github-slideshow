package com.wifimanager.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

// Placeholder — Firebase FCM will be enabled when google-services.json is configured.
class FCMService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}

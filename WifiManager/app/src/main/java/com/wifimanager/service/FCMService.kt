package com.wifimanager.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wifimanager.data.repository.RouterRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FCMService : FirebaseMessagingService() {

    @Inject
    lateinit var repository: RouterRepository

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val action = data["action"] ?: return
        val mac = data["mac"] ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            when (action) {
                "DISABLE_INTERNET" -> repository.setInternetEnabled(false)
                "ENABLE_INTERNET" -> repository.setInternetEnabled(true)
                "BLOCK_DEVICE" -> if (mac.isNotEmpty()) repository.blockDevice(mac, true)
                "UNBLOCK_DEVICE" -> if (mac.isNotEmpty()) repository.blockDevice(mac, false)
                "REFRESH" -> repository.refreshDevices()
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Save token to Firebase for remote control
    }
}

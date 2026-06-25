package com.wifimanager.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Remote control commands arrive here when app is in background
    }

    override fun onNewToken(token: String) {
        // Token refresh — would send to server in a real deployment
    }
}

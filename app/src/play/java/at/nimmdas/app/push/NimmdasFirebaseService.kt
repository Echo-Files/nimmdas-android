package at.nimmdas.app.push

import android.util.Log
import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import at.nimmdas.app.NimmdasApp

class NimmdasFirebaseService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "NimmdasFCM"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        // Send token to backend
        val app = application as? NimmdasApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.apiClient.api.registerPushToken(mapOf("fcmToken" to token))
                Log.d(TAG, "FCM token registered on server")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register FCM token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received: ${message.data}")

        val data = message.data
        val type = data["type"]
        if (type == "incoming_call") {
            val callId = data["callId"] ?: return
            val callerName = data["callerName"] ?: "Jemand"
            val callerAvatar = data["callerAvatar"]
            val offerSdp = data["offerSdp"] ?: ""
            val listingTitle = data["listingTitle"]
            NotificationHelper.showCallNotification(this, callerName, callId, callerAvatar, offerSdp, listingTitle)
            return
        }

        if (type == "call_ended") {
            val callId = data["callId"] ?: return
            NotificationHelper.cancelCallNotification(this, callId)
            
            // Broadcast so UI can close if currently active
            val endIntent = Intent("at.nimmdas.app.CALL_ENDED")
            endIntent.putExtra("callId", callId)
            sendBroadcast(endIntent)
            return
        }

        val senderName = data["senderName"] ?: "Neue Nachricht"
        val content = data["content"] ?: ""
        val senderId = data["senderId"] ?: ""
        val listingId = data["listingId"] ?: ""
        val senderAvatar = data["senderAvatar"]
        val threadId = "${senderId}_${listingId}"

        if (senderId.isBlank() || content.isBlank()) return

        NotificationHelper.showMessageNotification(
            context = this,
            senderName = senderName,
            content = content,
            threadId = threadId,
            senderId = senderId,
            listingId = listingId,
            senderAvatar = senderAvatar
        )
    }
}

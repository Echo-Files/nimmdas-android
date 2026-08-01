package at.nimmdas.app.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import at.nimmdas.app.NimmdasApp
import at.nimmdas.app.data.model.SendMessageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles inline reply from the notification shade (WhatsApp-style).
 * Sends the reply directly to the backend without opening the app.
 */
class ReplyReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReplyReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(NotificationHelper.REPLY_KEY)?.toString()

        if (replyText.isNullOrBlank()) return

        val senderId = intent.getStringExtra(NotificationHelper.EXTRA_SENDER_ID) ?: return
        val listingId = intent.getStringExtra(NotificationHelper.EXTRA_LISTING_ID) ?: return
        val threadId = intent.getStringExtra(NotificationHelper.EXTRA_THREAD_ID) ?: ""

        Log.d(TAG, "Inline reply: \"$replyText\" to $senderId for listing $listingId")

        val app = context.applicationContext as? NimmdasApp ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = app.apiClient.api.sendMessage(
                    SendMessageRequest(
                        content = replyText,
                        receiverId = senderId,
                        listingId = listingId
                    )
                )

                if (response.isSuccessful) {
                    Log.d(TAG, "Reply sent successfully")
                    NotificationHelper.updateNotificationToSent(context, threadId)
                } else {
                    Log.e(TAG, "Failed to send reply: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending reply", e)
            }
        }
    }
}

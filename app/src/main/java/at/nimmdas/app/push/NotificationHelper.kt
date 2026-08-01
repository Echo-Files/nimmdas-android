package at.nimmdas.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import at.nimmdas.app.BuildConfig
import at.nimmdas.app.MainActivity
import at.nimmdas.app.R
import java.net.URL

object NotificationHelper {

    private const val CHANNEL_ID = "nimmdas_messages"
    private const val CHANNEL_NAME = "Nachrichten"
    private const val CALL_CHANNEL_ID = "nimmdas_calls"
    private const val CALL_CHANNEL_NAME = "Eingehende Anrufe"
    const val REPLY_KEY = "key_reply_text"
    const val EXTRA_THREAD_ID = "extra_thread_id"
    const val EXTRA_SENDER_ID = "extra_sender_id"
    const val EXTRA_LISTING_ID = "extra_listing_id"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)

            // Message Channel
            val msgChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chat-Nachrichten von Nimmdas"
                enableVibration(true)
                setShowBadge(true)
            }
            nm.createNotificationChannel(msgChannel)

            // Call Channel
            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                CALL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Eingehende Anrufe von Nimmdas"
                enableVibration(true)
                setShowBadge(true)
            }
            nm.createNotificationChannel(callChannel)
        }
    }

    fun showMessageNotification(
        context: Context,
        senderName: String,
        content: String,
        threadId: String,
        senderId: String,
        listingId: String,
        senderAvatar: String? = null
    ) {
        createChannel(context)

        val notificationId = threadId.hashCode()

        // Tap intent → open chat
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "chat")
            putExtra(EXTRA_THREAD_ID, threadId)
        }
        val tapPending = PendingIntent.getActivity(
            context, notificationId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Reply action (WhatsApp-style inline reply)
        val remoteInput = RemoteInput.Builder(REPLY_KEY)
            .setLabel("Antworten...")
            .build()

        val replyIntent = Intent(context, ReplyReceiver::class.java).apply {
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_SENDER_ID, senderId)
            putExtra(EXTRA_LISTING_ID, listingId)
        }
        val replyPending = PendingIntent.getBroadcast(
            context, notificationId + 1, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            "Antworten",
            replyPending
        ).addRemoteInput(remoteInput).build()

        // Load avatar as large icon (best effort)
        var largeIcon: Bitmap? = null
        if (!senderAvatar.isNullOrBlank()) {
            try {
                val url = if (senderAvatar.startsWith("http")) senderAvatar else "${BuildConfig.API_BASE_URL}$senderAvatar"
                val stream = URL(url).openStream()
                largeIcon = BitmapFactory.decodeStream(stream)
                stream.close()
            } catch (_: Exception) {}
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .addAction(replyAction)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(notificationId, builder.build())
    }

    fun showCallNotification(
        context: Context,
        callerName: String,
        callId: String,
        callerAvatar: String?,
        offerSdp: String,
        listingTitle: String? = null
    ) {
        createChannel(context)

        val notificationId = callId.hashCode()

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "incoming_call")
            putExtra("callId", callId)
            putExtra("callerName", callerName)
            putExtra("callerAvatar", callerAvatar)
            putExtra("offerSdp", offerSdp)
            putExtra("listingTitle", listingTitle)
            putExtra("autoAnswer", false)
        }
        val tapPending = PendingIntent.getActivity(
            context, notificationId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Auto-Answer tap intent for the action button
        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "incoming_call")
            putExtra("callId", callId)
            putExtra("callerName", callerName)
            putExtra("callerAvatar", callerAvatar)
            putExtra("offerSdp", offerSdp)
            putExtra("listingTitle", listingTitle)
            putExtra("autoAnswer", true)
        }
        val acceptPending = PendingIntent.getActivity(
            context, notificationId + 1, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE_CALL
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
        }
        val declinePending = PendingIntent.getBroadcast(
            context, notificationId, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val contentText = if (!listingTitle.isNullOrEmpty()) "$callerName ruft an wegen: $listingTitle" else "$callerName ruft an..."

        val builder = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Eingehender Anruf")
            .setContentText(contentText)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .setFullScreenIntent(tapPending, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(R.drawable.ic_notification, "Auflegen", declinePending)
            .addAction(R.drawable.ic_notification, "Abheben", acceptPending)

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(notificationId, builder.build())
    }

    fun cancelCallNotification(context: Context, callId: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(callId.hashCode())
    }

    fun updateNotificationToSent(context: Context, threadId: String) {
        val notificationId = threadId.hashCode()
        val nm = context.getSystemService(NotificationManager::class.java)

        createChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Nachricht gesendet ✓")
            .setContentText("Deine Antwort wurde gesendet.")
            .setAutoCancel(true)
            .setTimeoutAfter(3000)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        nm.notify(notificationId, notification)
    }
}

package at.nimmdas.app.push

import android.content.Context
import android.util.Log
import at.nimmdas.app.NimmdasApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Play-Store build: registers the device with Firebase Cloud Messaging.
 *
 * The F-Droid build ships a no-op twin of this file, so no caller has to know which
 * distribution it is running in.
 */
object PushRegistrar {

    private const val TAG = "NimmdasPush"

    /** True when this build can receive push notifications while closed. */
    const val SUPPORTS_PUSH = true

    fun register(context: Context) {
        val app = context.applicationContext as? NimmdasApp ?: return
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            app.apiClient.api.registerPushToken(mapOf("fcmToken" to token))
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to register push token", e)
                        }
                    }
                }
                .addOnFailureListener { e -> Log.e(TAG, "Failed to get push token", e) }
        } catch (e: Exception) {
            // Firebase may not be initialised yet; the next login retries.
            Log.e(TAG, "Push registration unavailable", e)
        }
    }
}

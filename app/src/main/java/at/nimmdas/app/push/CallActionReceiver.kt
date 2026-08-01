package at.nimmdas.app.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import at.nimmdas.app.NimmdasApp
import at.nimmdas.app.data.model.UpdateCallRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DECLINE_CALL = "at.nimmdas.app.DECLINE_CALL"
        const val EXTRA_CALL_ID = "extra_call_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DECLINE_CALL) {
            val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
            
            Log.d("CallActionReceiver", "Declining call $callId from notification action")

            // 1. Cancel the local notification immediately to stop ringing
            NotificationHelper.cancelCallNotification(context, callId)

            // 2. Broadcast CALL_ENDED to update any active UI
            val endIntent = Intent("at.nimmdas.app.CALL_ENDED")
            endIntent.putExtra("callId", callId)
            context.sendBroadcast(endIntent)

            // 3. Notify the backend that the call was declined
            val app = context.applicationContext as? NimmdasApp ?: return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = app.apiClient.api.updateCallStatus(
                        callId,
                        UpdateCallRequest("declined")
                    )
                    if (response.isSuccessful) {
                        Log.d("CallActionReceiver", "Successfully declined call via API")
                    } else {
                        Log.e("CallActionReceiver", "Failed to decline call via API: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("CallActionReceiver", "Error declining call via API", e)
                }
            }
        }
    }
}

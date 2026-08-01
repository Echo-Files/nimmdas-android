package at.nimmdas.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import at.nimmdas.app.navigation.AppNavigation
import at.nimmdas.app.ui.theme.NimmdasTheme
import at.nimmdas.app.push.PushRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Share new intents with Compose UI
    val intentFlow = MutableSharedFlow<Intent>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    // Observable state that Compose watches
    private val _googleToken = mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.d("Nimmdas", "Notification permission granted")
            registerFcmToken()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure screen wakes up and bypasses lock screen for incoming calls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        // Transparent system bars — the default draws an opaque scrim behind the
        // navigation bar, which shows up as a white strip below the nav capsule.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        // Check if launched from deep link
        val initialRoute = handleIntentForRoute(intent)

        // Social login returns via nimmdas://auth?token=… . If Android killed the process
        // while the browser was in front, the callback arrives here instead of
        // onNewIntent — without this the sign-in was silently lost.
        handleDeepLink(intent)

        // Request notification permission (Android 13+)
        requestNotificationPermission()

        // Register FCM token
        registerFcmToken()

        setContent {
            val googleToken by _googleToken

            NimmdasTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        onGoogleToken = googleToken,
                        initialRoute = initialRoute,
                        intentFlow = intentFlow
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
        intentFlow.tryEmit(intent)
    }

    private fun handleIntentForRoute(intent: Intent?): String? {
        if (intent == null) return null
        val navigateTo = intent.getStringExtra("navigate_to")
        if (navigateTo == "incoming_call") {
            val callId = intent.getStringExtra("callId") ?: return null
            val callerName = intent.getStringExtra("callerName") ?: ""
            val callerAvatar = intent.getStringExtra("callerAvatar")
            val offerSdp = intent.getStringExtra("offerSdp") ?: ""
            val listingTitle = intent.getStringExtra("listingTitle")
            val autoAnswer = intent.getBooleanExtra("autoAnswer", false)
            return at.nimmdas.app.navigation.Screen.IncomingCall.createRoute(callId, callerName, callerAvatar, offerSdp, listingTitle, autoAnswer)
        }
        if (navigateTo == "chat") {
            val threadId = intent.getStringExtra(at.nimmdas.app.push.NotificationHelper.EXTRA_THREAD_ID)
            if (threadId != null) {
                return "chat/$threadId"
            }
        }
        return null
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun registerFcmToken() {
        PushRegistrar.register(this)
    }



    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "nimmdas" && uri.host == "auth") {
            val token = uri.getQueryParameter("token")
            if (token != null) {
                Log.d("NimmdasAuth", "Received Google auth token via deep link")

                // Save token and update observable state
                val app = applicationContext as NimmdasApp
                CoroutineScope(Dispatchers.IO).launch {
                    app.apiClient.saveToken(token)
                    // Fetch user info with the new token
                    try {
                        val response = app.apiClient.api.getMe()
                        if (response.isSuccessful) {
                            val user = response.body()?.user
                            if (user != null) {
                                app.apiClient.saveUserInfo(
                                    user.id ?: "",
                                    user.name,
                                    user.email ?: "",
                                    user.avatar
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("NimmdasAuth", "Failed to fetch user after Google login", e)
                    }

                    // Update UI state on main thread
                    CoroutineScope(Dispatchers.Main).launch {
                        _googleToken.value = token
                    }
                }
            }
        }
    }
}


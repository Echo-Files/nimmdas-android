package at.nimmdas.app.ui.screens.messages

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import at.nimmdas.app.BuildConfig
import at.nimmdas.app.NimmdasApp
import at.nimmdas.app.webrtc.WebRtcManager
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun IncomingCallScreen(
    callId: String,
    callerName: String,
    callerAvatar: String?,
    offerSdp: String,
    listingTitle: String? = null,
    autoAnswer: Boolean = false,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NimmdasApp
    
    val webRtcManager = remember { WebRtcManager(context, app.apiClient) }
    // States: "incoming" -> "connecting" -> "active" -> "ended"
    var callPhase by remember { mutableStateOf("incoming") }
    var isMuted by remember { mutableStateOf(false) }
    var callDuration by remember { mutableIntStateOf(0) }
    var hasNavigatedAway by remember { mutableStateOf(false) }
    var actualOfferSdp by remember { mutableStateOf(offerSdp) }

    // If offerSdp is missing (e.g. from Push intent), fetch it
    LaunchedEffect(Unit) {
        if (actualOfferSdp.isBlank()) {
            try {
                val response = app.apiClient.api.getIncomingCall()
                if (response.isSuccessful) {
                    val call = response.body()?.incomingCall
                    if (call?.id == callId) {
                        actualOfferSdp = call.offerSDP
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NimmdasCall", "Failed to fetch missing offerSdp", e)
            }
        }
    }

    // Ringtone for incoming call (loud - this is the RECEIVER side)
    val ringtonePlayer = remember {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            MediaPlayer.create(context, uri)?.apply { isLooping = true }
        } catch (_: Exception) { null }
    }

    fun safeEnd() {
        if (!hasNavigatedAway) {
            hasNavigatedAway = true
            try { ringtonePlayer?.stop(); ringtonePlayer?.release() } catch (_: Exception) {}
            webRtcManager.endCall()
            onCallEnded()
        }
    }

    // Listen for the FCM 'call_ended' broadcast from NimmdasFirebaseService
    DisposableEffect(callId) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "at.nimmdas.app.CALL_ENDED") {
                    val endedCallId = intent.getStringExtra("callId")
                    if (endedCallId == callId) {
                        safeEnd()
                    }
                }
            }
        }
        val filter = IntentFilter("at.nimmdas.app.CALL_ENDED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Play ringtone while incoming
    LaunchedEffect(callPhase) {
        if (callPhase == "incoming") {
            try { ringtonePlayer?.start() } catch (_: Exception) {}
        } else {
            try { 
                if (ringtonePlayer?.isPlaying == true) ringtonePlayer.stop()
            } catch (_: Exception) {}
        }
    }

    // Duration timer when active
    LaunchedEffect(callPhase) {
        if (callPhase == "active") {
            while (true) {
                delay(1000)
                callDuration++
            }
        }
    }

    // Listen for WebRTC state changes — register immediately, not conditionally
    LaunchedEffect(Unit) {
        webRtcManager.onCallStateChanged = { state ->
            when (state) {
                "active" -> callPhase = "active"
                "ended", "failed", "declined" -> safeEnd()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { ringtonePlayer?.stop(); ringtonePlayer?.release() } catch (_: Exception) {}
            webRtcManager.endCall()
        }
    }

    BackHandler { safeEnd() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted && actualOfferSdp.isNotBlank()) {
            callPhase = "connecting"
            webRtcManager.answerCall(callId, actualOfferSdp)
        } else {
            safeEnd()
        }
    }

    LaunchedEffect(autoAnswer, actualOfferSdp) {
        if (autoAnswer && callPhase == "incoming" && actualOfferSdp.isNotBlank()) {
            try { ringtonePlayer?.stop() } catch (_: Exception) {}
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                callPhase = "connecting"
                webRtcManager.answerCall(callId, actualOfferSdp)
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        }
    }

    fun acceptCall() {
        if (actualOfferSdp.isBlank()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            callPhase = "connecting"
            webRtcManager.answerCall(callId, actualOfferSdp)
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    fun declineCall() {
        // Report the decline explicitly — the manager has no callId yet because the call
        // was never answered, so plain endCall() would leave the caller ringing.
        if (!hasNavigatedAway) {
            hasNavigatedAway = true
            try { ringtonePlayer?.stop(); ringtonePlayer?.release() } catch (_: Exception) {}
            webRtcManager.declineCall(callId)
            onCallEnded()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1A1A2E)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(64.dp))
                
                Box(
                    Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            if (callPhase == "incoming") Color(0xFF1B4332) else Color(0xFF16213E)
                        )
                ) {
                    if (callerAvatar != null) {
                        val avatarUrl = if (callerAvatar.startsWith("http")) callerAvatar else "${BuildConfig.API_BASE_URL}$callerAvatar"
                        AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(callerName.take(1).uppercase(), fontSize = 48.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                if (!listingTitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Wegen: $listingTitle",
                        color = Color.LightGray,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = when (callPhase) {
                        "incoming" -> "Eingehender Anruf..."
                        "connecting" -> "Verbinde..."
                        "active" -> {
                            val mins = callDuration / 60
                            val secs = callDuration % 60
                            String.format("%02d:%02d", mins, secs)
                        }
                        else -> "Anruf beendet"
                    },
                    color = when(callPhase) {
                        "incoming" -> Color(0xFF4ADE80)
                        "active" -> Color(0xFF4ADE80)
                        else -> Color(0xFF94A3B8)
                    },
                    fontSize = 16.sp
                )
            }

            // Bottom Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (callPhase == "incoming") {
                    // ── Incoming: Decline / Accept ──
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { declineCall() },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Filled.CallEnd, "Ablehnen", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Ablehnen", color = Color(0xFFEF4444), fontSize = 11.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { acceptCall() },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        ) {
                            Icon(Icons.Filled.Call, "Annehmen", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Annehmen", color = Color(0xFF22C55E), fontSize = 11.sp)
                    }
                } else {
                    // ── Active call: Mute / End ──
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { 
                                isMuted = !isMuted 
                                webRtcManager.setMicrophoneMute(isMuted)
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (isMuted) Color.White else Color(0xFF2A2A4A))
                        ) {
                            Icon(
                                if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic, 
                                contentDescription = "Stummschalten",
                                tint = if (isMuted) Color.Black else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(if (isMuted) "Stumm" else "Mikrofon", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { safeEnd() },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Filled.CallEnd, "Auflegen", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Auflegen", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

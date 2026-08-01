package at.nimmdas.app.ui.screens.messages

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
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
fun CallScreen(
    partnerId: String,
    partnerName: String,
    partnerAvatar: String?,
    listingId: String?,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NimmdasApp
    
    val webRtcManager = remember { WebRtcManager(context, app.apiClient) }
    var callState by remember { mutableStateOf("connecting") }
    var isMuted by remember { mutableStateOf(false) }
    var callDuration by remember { mutableIntStateOf(0) }
    var hasPermission by remember { mutableStateOf(false) }
    var callStarted by remember { mutableStateOf(false) }
    var hasNavigatedAway by remember { mutableStateOf(false) }

    // Ringback tone (soft beeping for caller side)
    val toneGenerator = remember {
        try { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 40) } catch (_: Exception) { null }
    }

    fun safeEnd() {
        if (!hasNavigatedAway) {
            hasNavigatedAway = true
            try { toneGenerator?.stopTone() } catch (_: Exception) {}
            webRtcManager.endCall()
            onCallEnded()
        }
    }

    // Listen for the FCM 'call_ended' broadcast from NimmdasFirebaseService
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "at.nimmdas.app.CALL_ENDED") {
                    // Outgoing call screen might not know its callId immediately,
                    // so we just safely end if any call_ended comes in for us.
                    safeEnd()
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) safeEnd()
    }

    // Request mic permission
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Start the call once permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission && !callStarted) {
            callStarted = true
            webRtcManager.onCallStateChanged = { state ->
                callState = state
                if (state == "ended" || state == "declined" || state == "failed") {
                    safeEnd()
                }
            }
            webRtcManager.startCall(partnerId, listingId) { /* call created */ }
        }
    }

    // Ringback tone while ringing (separate effect so it doesn't interfere with timer)
    LaunchedEffect(callState) {
        if (callState == "ringing" || callState == "connecting") {
            while (true) {
                try { toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1000) } catch (_: Exception) {}
                delay(4000)
            }
        } else {
            try { toneGenerator?.stopTone() } catch (_: Exception) {}
        }
    }

    // Separate timer effect
    LaunchedEffect(callState) {
        if (callState == "active") {
            while (true) {
                delay(1000)
                callDuration++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { toneGenerator?.stopTone(); toneGenerator?.release() } catch (_: Exception) {}
            webRtcManager.endCall()
        }
    }

    BackHandler { safeEnd() }

    val formatDuration = { seconds: Int ->
        String.format("%02d:%02d", seconds / 60, seconds % 60)
    }

    var listingData by remember { mutableStateOf<at.nimmdas.app.data.model.Listing?>(null) }
    
    // Fetch Listing Data
    LaunchedEffect(listingId) {
        if (!listingId.isNullOrEmpty() && listingId != "null") {
            try {
                val response = app.apiClient.api.getListingById(listingId)
                if (response.isSuccessful) {
                    listingData = response.body()
                }
            } catch (e: Exception) {
                // ignore
            }
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // Listing Context Card
                if (listingData != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x66000000)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, top = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                            val img = listingData!!.images?.firstOrNull()
                            if (img != null) {
                                val url = if (img.startsWith("http")) img else "${BuildConfig.API_BASE_URL}$img"
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                )
                            } else {
                                Box(modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(Color.DarkGray))
                            }
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(listingData!!.title, color = Color.White, maxLines = 1, fontSize = 14.sp)
                                Text("€ ${listingData!!.price?.toInt() ?: 0}", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            IconButton(onClick = { /* show quick actions */ }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Optionen", tint = Color.White)
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(64.dp))
                }
                
                Box(
                    Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF16213E))
                ) {
                    if (partnerAvatar != null) {
                        val avatarUrl = if (partnerAvatar.startsWith("http")) partnerAvatar else "${BuildConfig.API_BASE_URL}$partnerAvatar"
                        AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(partnerName.take(1).uppercase(), fontSize = 48.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                Text(partnerName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when(callState) {
                        "connecting" -> "Verbinden..."
                        "ringing" -> "Es klingelt..."
                        "active" -> formatDuration(callDuration)
                        "failed" -> "Verbindung fehlgeschlagen"
                        "declined" -> "Abgelehnt"
                        else -> "Anruf beendet"
                    },
                    color = when(callState) {
                        "active" -> Color(0xFF4ADE80)
                        "failed", "declined" -> Color(0xFFEF4444)
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
                // Mute Button
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
                    Text(
                        if (isMuted) "Stumm" else "Mikrofon",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                // End Call Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { safeEnd() },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    ) {
                        Icon(
                            Icons.Filled.CallEnd, 
                            contentDescription = "Auflegen",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Auflegen", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            }
        }
    }
}

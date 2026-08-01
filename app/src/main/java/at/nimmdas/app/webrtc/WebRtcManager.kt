package at.nimmdas.app.webrtc

import android.content.Context
import android.util.Log
import at.nimmdas.app.data.api.ApiClient
import at.nimmdas.app.data.model.IceCandidateData
import at.nimmdas.app.data.model.UpdateCallRequest
import kotlinx.coroutines.*
import org.webrtc.*

class WebRtcManager(
    private val context: Context,
    private val apiClient: ApiClient
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var eglBase: EglBase? = null

    private var callId: String? = null
    private var isInitiator = false
    private var savedAudioMode = android.media.AudioManager.MODE_NORMAL
    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // Bug 7 fix: track how many ICE candidates we've already applied
    private var appliedCandidateCount = 0

    var onCallStateChanged: ((String) -> Unit)? = null
    var onRemoteTrackReceived: (() -> Unit)? = null

    companion object {
        private const val TAG = "WebRtcManager"
        // Free Google STUN server
        private val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:nimmdas.at:3478")
                .setUsername("nimmdas")
                .setPassword("nimmdas_turn_2026")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:nimmdas.at:3478?transport=tcp")
                .setUsername("nimmdas")
                .setPassword("nimmdas_turn_2026")
                .createIceServer()
        )
    }

    /**
     * Puts the device into voice-call audio mode. Without this WebRTC plays through the
     * media stream: wrong routing, wrong gain and no echo cancellation — the call sounds
     * silent or very quiet.
     */
    private fun startAudioSession() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            savedAudioMode = am.mode
            am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = false // earpiece by default, like a normal phone call
        } catch (e: Exception) {
            Log.e(TAG, "Could not start audio session", e)
        }
    }

    private fun stopAudioSession() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            am.mode = savedAudioMode
            am.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.e(TAG, "Could not stop audio session", e)
        }
    }

    /** Toggles between earpiece and loudspeaker during an active call. */
    fun setSpeakerphone(on: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            am.isSpeakerphoneOn = on
        } catch (e: Exception) {
            Log.e(TAG, "Could not switch speakerphone", e)
        }
    }

    fun initialize() {
        if (peerConnectionFactory != null) return

        startAudioSession()
        eglBase = EglBase.create()

        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()

        // Create Audio Track
        val audioConstraints = MediaConstraints()
        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        audioTrack = peerConnectionFactory?.createAudioTrack("AUDIO_TRACK_ID", audioSource)
        audioTrack?.setEnabled(true)
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE Connection State: $state")
                    if (state == PeerConnection.IceConnectionState.DISCONNECTED || 
                        state == PeerConnection.IceConnectionState.FAILED ||
                        state == PeerConnection.IceConnectionState.CLOSED) {
                        endCall()
                        scope.launch(Dispatchers.Main) {
                            onCallStateChanged?.invoke("ended")
                        }
                    } else if (state == PeerConnection.IceConnectionState.CONNECTED) {
                        scope.launch(Dispatchers.Main) {
                            onCallStateChanged?.invoke("active")
                        }
                    }
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidate(candidate: IceCandidate) {
                    Log.d(TAG, "New local ICE candidate generated")
                    sendIceCandidate(candidate)
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(dataChannel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    Log.d(TAG, "Remote track added")
                    scope.launch(Dispatchers.Main) {
                        onRemoteTrackReceived?.invoke()
                    }
                }
            }
        )

        audioTrack?.let {
            peerConnection?.addTrack(it, listOf("AUDIO_STREAM_ID"))
        }
    }

    // ── Caller: Start Call ──
    fun startCall(receiverId: String, listingId: String?, onCallCreated: (String) -> Unit) {
        isEnding = false // a previous call left this latched, which would block hang-up
        initialize()
        createPeerConnection()
        isInitiator = true
        onCallStateChanged?.invoke("ringing")

        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                
                // Send Offer to backend
                scope.launch {
                    try {
                        val response = apiClient.api.createCall(
                            at.nimmdas.app.data.model.CreateCallRequest(
                                receiverId = receiverId,
                                listingId = listingId,
                                offerSDP = sdp.description
                            )
                        )
                        if (response.isSuccessful) {
                            callId = response.body()?.callId
                            appliedCandidateCount = 0
                            callId?.let { 
                                onCallCreated(it)
                                startPollingForAnswer() 
                            }
                        } else {
                            scope.launch(Dispatchers.Main) {
                                onCallStateChanged?.invoke("failed")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start call", e)
                        scope.launch(Dispatchers.Main) {
                            onCallStateChanged?.invoke("failed")
                        }
                    }
                }
            }
        }, MediaConstraints())
    }

    // ── Receiver: Answer Call ──
    fun answerCall(cId: String, offerSdpStr: String) {
        isEnding = false // see startCall
        callId = cId
        appliedCandidateCount = 0
        initialize()
        createPeerConnection()
        isInitiator = false

        val offer = SessionDescription(SessionDescription.Type.OFFER, offerSdpStr)
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                peerConnection?.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                        
                        // Send Answer to backend
                        scope.launch {
                            try {
                                apiClient.api.updateCallStatus(
                                    callId!!,
                                    UpdateCallRequest("active", sdp.description)
                                )
                                startPollingForIceCandidates()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to answer call", e)
                            }
                        }
                    }
                }, MediaConstraints())
            }
        }, offer)
    }

    private var isEnding = false

    /**
     * Rejects an incoming call that was never answered. [callId] is only assigned in
     * [answerCall]/[startCall], so without passing the id explicitly a decline would
     * reach nobody and the caller would keep ringing.
     */
    fun declineCall(cId: String) {
        scope.launch {
            try {
                apiClient.api.updateCallStatus(cId, UpdateCallRequest("declined"))
            } catch (e: Exception) {
                Log.e(TAG, "Error declining call", e)
            }
        }
        endCall()
    }

    fun endCall() {
        if (isEnding) return
        isEnding = true
        pollingJob?.cancel()
        stopAudioSession()
        val endId = callId
        callId = null // prevent further ICE sends
        
        // Close peer connection first (stops ICE agent)
        try { peerConnection?.close() } catch (_: Exception) {}
        peerConnection = null
        
        // Send ended status to server
        endId?.let { id ->
            scope.launch {
                try {
                    apiClient.api.updateCallStatus(id, UpdateCallRequest("ended"))
                } catch (e: Exception) {
                    Log.e(TAG, "Error ending call", e)
                }
            }
        }
        
        // Dispose resources after a short delay to let coroutines finish. Capture the
        // instances *now* and clear the fields immediately: if a new call starts within
        // the delay it installs fresh objects, and disposing those would crash natively.
        val oldAudioSource = audioSource
        val oldFactory = peerConnectionFactory
        val oldEglBase = eglBase
        audioSource = null
        peerConnectionFactory = null
        eglBase = null
        scope.launch {
            delay(500)
            try { oldAudioSource?.dispose() } catch (_: Exception) {}
            try { oldFactory?.dispose() } catch (_: Exception) {}
            try { oldEglBase?.release() } catch (_: Exception) {}
        }
    }

    // ── Signaling Polling ──
    private fun startPollingForAnswer() {
        pollingJob = scope.launch {
            while (isActive && callId != null && isInitiator) {
                try {
                    val response = apiClient.api.getCallStatus(callId!!)
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body?.status == "declined") {
                            scope.launch(Dispatchers.Main) {
                                onCallStateChanged?.invoke("declined")
                            }
                            endCall()
                            break
                        }
                        if (body?.status == "ended") {
                            scope.launch(Dispatchers.Main) {
                                onCallStateChanged?.invoke("ended")
                            }
                            endCall()
                            break
                        }
                        if (body?.answerSDP != null && peerConnection?.remoteDescription == null) {
                            val answer = SessionDescription(SessionDescription.Type.ANSWER, body.answerSDP)
                            peerConnection?.setRemoteDescription(SdpObserverAdapter(), answer)
                            startPollingForIceCandidates()
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error", e)
                }
                delay(500)
            }
        }
    }

    // Bug 7 fix: only apply NEW candidates by tracking count
    private fun startPollingForIceCandidates() {
        scope.launch {
            while (isActive && callId != null) {
                try {
                    val currentCallId = callId ?: break
                    val response = apiClient.api.getIceCandidates(currentCallId)
                    if (response.isSuccessful) {
                        val candidates = response.body()?.candidates ?: emptyList()
                        // Only process candidates we haven't seen yet
                        if (candidates.size > appliedCandidateCount) {
                            val newCandidates = candidates.subList(appliedCandidateCount, candidates.size)
                            for (cand in newCandidates) {
                                val ice = IceCandidate(cand.sdpMid, cand.sdpMLineIndex, cand.candidate)
                                peerConnection?.addIceCandidate(ice)
                                Log.d(TAG, "Applied ICE candidate #$appliedCandidateCount")
                                appliedCandidateCount++
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ICE polling error", e)
                }
                delay(500)
            }
        }
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        callId?.let { id ->
            scope.launch {
                try {
                    apiClient.api.addIceCandidate(
                        id,
                        IceCandidateData(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending ICE", e)
                }
            }
        }
    }

    fun setMicrophoneMute(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
    }

    open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(reason: String) {}
        override fun onSetFailure(reason: String) {}
    }
}

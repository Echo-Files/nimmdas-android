package at.nimmdas.app.webrtc

/**
 * In-memory holder for SDP data that is too large to pass via Navigation arguments.
 * Navigation args have URI-length limits and SDP strings (~2KB) with special characters
 * routinely corrupt or truncate during URL encoding.
 */
object PendingCallData {
    var offerSdp: String? = null
    var callerName: String? = null
    var callerAvatar: String? = null
    var callId: String? = null
    var listingTitle: String? = null
    var autoAnswer: Boolean = false

    fun set(
        callId: String,
        callerName: String,
        callerAvatar: String?,
        offerSdp: String,
        listingTitle: String?,
        autoAnswer: Boolean = false
    ) {
        this.callId = callId
        this.callerName = callerName
        this.callerAvatar = callerAvatar
        this.offerSdp = offerSdp
        this.listingTitle = listingTitle
        this.autoAnswer = autoAnswer
    }

    fun clear() {
        offerSdp = null
        callerName = null
        callerAvatar = null
        callId = null
        listingTitle = null
        autoAnswer = false
    }
}

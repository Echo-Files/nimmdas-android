package at.nimmdas.app.data.api

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Broadcasts "the server rejected our token" so the UI can send the user back to the
 * login screen. Without this an expired JWT leaves the app looking logged in while
 * every request quietly fails with 401.
 */
object SessionEvents {
    private val _expired = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val expired: SharedFlow<Unit> = _expired

    fun notifyExpired() {
        _expired.tryEmit(Unit)
    }

    /** Called once the UI has navigated to login, so a later login doesn't re-trigger. */
    fun reset() {
        _expired.resetReplayCache()
    }
}

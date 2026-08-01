package at.nimmdas.app.data

import at.nimmdas.app.data.api.ApiClient
import at.nimmdas.app.data.model.WatchlistRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-wide set of saved listing ids.
 *
 * Every card can show its own heart without each screen fetching the watchlist, and a
 * toggle anywhere updates all of them at once.
 */
class WatchlistStore(private val apiClient: ApiClient) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _savedIds = MutableStateFlow<Set<String>>(emptySet())
    val savedIds: StateFlow<Set<String>> = _savedIds.asStateFlow()

    private var loaded = false

    /** Loads once per session; call freely from screens. */
    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        refresh()
    }

    fun refresh() {
        scope.launch {
            try {
                val r = apiClient.api.getWatchlist()
                if (r.isSuccessful) {
                    _savedIds.value = r.body()?.savedListings?.map { it.id }?.toSet() ?: emptySet()
                }
            } catch (_: Exception) { /* not signed in, or offline */ }
        }
    }

    /**
     * Flips the saved state. The UI updates immediately and is corrected from the
     * server response, so tapping the heart never feels laggy.
     */
    fun toggle(listingId: String, onUnauthenticated: () -> Unit = {}) {
        val wasSaved = listingId in _savedIds.value
        _savedIds.value = if (wasSaved) _savedIds.value - listingId else _savedIds.value + listingId
        scope.launch {
            try {
                val r = apiClient.api.toggleWatchlist(WatchlistRequest(listingId))
                if (r.isSuccessful) {
                    val saved = r.body()?.saved == true
                    _savedIds.value =
                        if (saved) _savedIds.value + listingId else _savedIds.value - listingId
                } else {
                    // Roll back the optimistic change.
                    _savedIds.value =
                        if (wasSaved) _savedIds.value + listingId else _savedIds.value - listingId
                    if (r.code() == 401) onUnauthenticated()
                }
            } catch (_: Exception) {
                _savedIds.value =
                    if (wasSaved) _savedIds.value + listingId else _savedIds.value - listingId
            }
        }
    }

    /** Clears local state on logout so the next user doesn't inherit these hearts. */
    fun clear() {
        _savedIds.value = emptySet()
        loaded = false
    }
}

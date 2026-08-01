package at.nimmdas.app

import android.app.Application
import at.nimmdas.app.data.api.ApiClient
import at.nimmdas.app.push.NotificationHelper

class NimmdasApp : Application() {
    lateinit var apiClient: ApiClient
        private set

    /** Shared saved-listings state, so every card can render its own heart. */
    lateinit var watchlist: at.nimmdas.app.data.WatchlistStore
        private set

    override fun onCreate() {
        super.onCreate()
        apiClient = ApiClient(this)
        watchlist = at.nimmdas.app.data.WatchlistStore(apiClient)
        NotificationHelper.createChannel(this)
    }
}

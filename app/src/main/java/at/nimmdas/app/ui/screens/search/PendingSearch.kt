package at.nimmdas.app.ui.screens.search

/**
 * In-memory hand-off for a search the user started somewhere else (e.g. tapping a
 * category chip on the home screen). The Search tab is a bottom-nav destination with a
 * fixed route, so the selection is parked here instead of being encoded as a nav argument.
 */
object PendingSearch {
    private var category: String? = null

    fun requestCategory(cat: String) {
        category = cat
    }

    /** Returns the requested category once and forgets it. */
    fun consumeCategory(): String? {
        val c = category
        category = null
        return c
    }
}

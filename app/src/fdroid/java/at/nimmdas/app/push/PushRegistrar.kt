package at.nimmdas.app.push

import android.content.Context

/**
 * F-Droid build: no push service.
 *
 * F-Droid only ships apps that build from source without proprietary libraries, so
 * Firebase Cloud Messaging is not part of this variant. New messages and calls are
 * picked up by the in-app polling that runs while the app is open.
 */
object PushRegistrar {

    /** True when this build can receive push notifications while closed. */
    const val SUPPORTS_PUSH = false

    fun register(context: Context) {
        // Nothing to register — see the class comment.
    }
}

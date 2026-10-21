package dev.apexnative.navigation.react

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import androidx.core.content.ContextCompat

/**
 * "R R" of the developer tools: two releases of R less than [windowMillis] apart, while the focus isn't in a text
 * field, reload the JavaScript. A release in a text field is typing, and doesn't count.
 */
internal class DoubleTapReload(private val windowMillis: Long = WINDOW_MILLIS) {
    private var lastRelease: Long? = null

    /** A key was released at [now] (milliseconds); true when this release of R is the second one in time. */
    fun onKeyUp(keyCode: Int, inTextField: Boolean, now: Long): Boolean {
        if (keyCode != KeyEvent.KEYCODE_R || inTextField) return false
        val last = lastRelease
        val reload = last != null && now - last < windowMillis
        lastRelease = if (reload) null else now
        return reload
    }

    private companion object {
        const val WINDOW_MILLIS = 1000L
    }
}

/**
 * The reload broadcast of the developer tools: end-to-end scripts send [ACTION] through adb. One receiver per activity,
 * registered while it is resumed and only with the developer support on (no receiver in a release build: any app could
 * reset the navigation), exported as Android 14 requires of a receiver of other apps' broadcasts; removed only where it
 * was registered. Once [release]d, its action does nothing. Main thread only.
 */
internal class ReloadBroadcast(private val context: Context, action: () -> Unit) {
    private var action: () -> Unit = action
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = this@ReloadBroadcast.action()
    }

    /** The activity resumed with the developer support on. */
    fun register() {
        if (registered) return
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        registered = true
    }

    /** The activity paused. */
    fun unregister() {
        if (!registered) return
        registered = false
        context.unregisterReceiver(receiver)
    }

    /** The activity is destroyed: the receiver goes, and its action becomes "do nothing", never none. */
    fun release() {
        unregister()
        action = {}
    }

    companion object {
        /** The action of the reload broadcast. */
        const val ACTION = "dev.apexnative.navigation.broadcast.RELOAD"
    }
}

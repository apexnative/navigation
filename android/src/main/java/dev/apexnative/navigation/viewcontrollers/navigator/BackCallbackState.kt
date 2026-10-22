package dev.apexnative.navigation.viewcontrollers.navigator

import android.util.Log
import androidx.activity.OnBackPressedCallback
import dev.apexnative.navigation.utils.NavigationLog
import java.lang.ref.WeakReference

/**
 * Keeps the back callback of each navigation activity enabled only while back has something to do in the app: its
 * navigation can go back ([Navigator.canHandleBack]), or JavaScript listens to it (`BackHandler`). Otherwise the
 * callback is disabled and the system does its own back. The system plays its back animation (to the home screen, to
 * another task) only when no callback of the app is enabled as the gesture starts, and it decides that before the app
 * hears of the gesture.
 *
 * A stale "enabled" only loses that animation; a stale "disabled" would leave the app where back has to go back. So
 * the value is computed again whenever what back does can have changed: before each frame of the activity's window,
 * when a command is done, when JavaScript reports its listeners, and when a side menu, the panes of a split view or a
 * search open or close (the first two after their last frame). Each activity has its own navigator and callback; the
 * listeners of JavaScript count for all of them. Main thread only.
 */
internal object BackCallbackState {
    private class Entry(navigator: Navigator, callback: OnBackPressedCallback) {
        val navigator = WeakReference(navigator)
        val callback = WeakReference(callback)
    }

    private val entries = mutableListOf<Entry>()
    // JavaScript has `BackHandler` listeners. New JavaScript (a reload) starts without them and reports its own.
    private var jsListeners = false
    private var failureLogged = false

    /** The navigator and the back callback of an activity, until [detach]. */
    fun attach(navigator: Navigator, callback: OnBackPressedCallback) {
        detach(navigator)
        val entry = Entry(navigator, callback)
        entries += entry
        update(entry)
    }

    fun detach(navigator: Navigator) {
        entries.removeAll { it.navigator.get().let { attached -> attached == null || attached === navigator } }
    }

    /** JavaScript listens to back (`BackHandler`): it hears a press before the navigation does. */
    val hasJsListeners: Boolean
        get() = jsListeners

    /** `setBackListeners` of JavaScript; false again when its JavaScript goes. */
    fun setJsListeners(active: Boolean) {
        jsListeners = active
        update()
    }

    /** The callback of every activity: after a change that can be in any of them. */
    fun update() {
        for (entry in entries.toList()) update(entry)
    }

    /** The callback of the activity of [navigator]: before its frames and at its back presses. */
    fun update(navigator: Navigator) {
        entries.firstOrNull { it.navigator.get() === navigator }?.let(::update)
    }

    // Enables or disables the callback when what back does changed.
    private fun update(entry: Entry) {
        val callback = entry.callback.get() ?: return
        val enabled = jsListeners || canHandleBack(entry.navigator.get())
        if (callback.isEnabled != enabled) callback.isEnabled = enabled
    }

    // A layout that fails to answer leaves back to the navigation, as before: this runs before each frame, and an
    // exception here would end the app.
    private fun canHandleBack(navigator: Navigator?): Boolean = try {
        navigator?.canHandleBack() == true
    } catch (e: RuntimeException) {
        if (!failureLogged) {
            failureLogged = true
            Log.e(NavigationLog.TAG, "canHandleBack failed: the back callback stays enabled", e)
        }
        true
    }
}

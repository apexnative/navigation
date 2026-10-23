package dev.apexnative.navigation.viewcontrollers.navigator

import android.util.Log
import androidx.activity.OnBackPressedCallback
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.utils.NavigationLog
import java.lang.ref.WeakReference

/**
 * Keeps the back callback of each navigation activity enabled only while back has something to do in the app: its
 * navigation can go back ([Navigator.canHandleBack]), the screen back acts on keeps it from the system
 * (`hardwareBackButton.systemAnimation: false`, [Navigator.keepsBackFromSystem]), or JavaScript listens to it
 * (`BackHandler`). Otherwise the callback is disabled and the system does its own back. The system plays its back
 * animation (to the home screen, to another task) only when no callback of the app is enabled as the gesture starts,
 * and it decides that before the app hears of the gesture.
 *
 * A stale "enabled" only loses that animation; a stale "disabled" would leave the app where back has to go back. So
 * the value is computed again on every event that can change what back does, never by walking the layouts before
 * each frame:
 * - the end of each command's main-thread task, `setDefaultOptions` and `mergeOptions` too (`CommandRunner`);
 * - a stack's operation accepted, its current child changed, and the operation ended or rejected
 *   (`StackController`'s queue host), which covers a push or a `setStackRoot` that waited for its screen, a prepared
 *   push, back of the user and the back arrow;
 * - a layout that changed the screens it shows (`Navigator.shownScreensChanged`: the tabs, a side menu, a split view,
 *   a pair) and the end of a tab's switch;
 * - every change of the modals (`ModalCoordinator.Host.backChanged`): a show, a dismissal by a command, by back or by
 *   the user (a sheet dragged down, its scrim), a failure, a `setRoot` that takes them;
 * - a root that changed or showed, and the layers that went (a reload: `Navigator.destroy`);
 * - a side menu, the panes of a split view or a search that opened or closed (the first two after their last frame),
 *   and a split view's width that changed;
 * - the release and the cancel of a back gesture, and each back press;
 * - JavaScript's listeners ([setJsListeners]), and the activity resumed or focused, as a safety net.
 *
 * Overlays don't count: back never goes to them. Each activity has its own navigator and callback; the listeners of
 * JavaScript count for all of them. Debug builds check the triggers ([mismatchOf], `BackCallbackCheck`). Main thread
 * only.
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

    /** The callback of the activity of [navigator]: after a change in its navigation. */
    fun update(navigator: Navigator) {
        entries.firstOrNull { it.navigator.get() === navigator }?.let(::update)
    }

    /**
     * Debug builds only: a callback of [navigator]'s activity that differs from what back does now — a change without
     * its trigger —, described with the screen on top; null when it is right or not attached. It changes nothing.
     */
    fun mismatchOf(navigator: Navigator): String? {
        val callback = entries.firstOrNull { it.navigator.get() === navigator }?.callback?.get() ?: return null
        val expected = jsListeners || takesBack(navigator)
        if (callback.isEnabled == expected) return null
        val top = navigator.visibleLayout?.let { AppearanceDispatcher.visibleNode(it).id } ?: "none"
        val state = if (callback.isEnabled) "enabled" else "disabled"
        val back = if (expected) "back stays in the app" else "back leaves the app"
        return "the back callback is $state where $back (on top: '$top')"
    }

    // Enables or disables the callback when what back does changed.
    private fun update(entry: Entry) {
        val callback = entry.callback.get() ?: return
        val enabled = jsListeners || takesBack(entry.navigator.get())
        if (callback.isEnabled != enabled) callback.isEnabled = enabled
    }

    // A layout that fails to answer leaves back to the navigation, as before: this runs from the triggers of layouts
    // and animators, and an exception here would end the app.
    private fun takesBack(navigator: Navigator?): Boolean = try {
        navigator != null && (navigator.canHandleBack() || navigator.keepsBackFromSystem())
    } catch (e: RuntimeException) {
        if (!failureLogged) {
            failureLogged = true
            Log.e(NavigationLog.TAG, "canHandleBack failed: the back callback stays enabled", e)
        }
        true
    }
}

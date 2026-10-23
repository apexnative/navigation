package dev.apexnative.navigation.viewcontrollers.navigator

import android.util.Log
import dev.apexnative.navigation.utils.NavigationLog

/**
 * The check of [BackCallbackState]'s triggers in debug builds (`NavigationActivity` makes it only when
 * `BuildConfig.DEBUG`): it compares an activity's back callback with what back does now
 * ([BackCallbackState.mismatchOf]) and writes a mismatch to the log once per state, with the screen on top. It never
 * changes the callback, so a missing trigger behaves as in a release build. A [sample] before a frame runs at most
 * every [SAMPLE_MILLIS] and reports a mismatch only when the sample before saw the same one: a change whose trigger
 * comes at the end of its motion (a pane that slides, a menu dragged open) isn't reported while it moves. A back event
 * ([atBack]) reports at once. Main thread only.
 */
internal class BackCallbackCheck(
    private val log: (String) -> Unit = { Log.w(NavigationLog.TAG, it) },
) {
    private var lastSample: Long? = null
    private var previous: String? = null
    private val logged = HashSet<String>()

    /** Before a frame of the activity of [navigator], at [now] (`SystemClock.uptimeMillis`). */
    fun sample(navigator: Navigator, now: Long) {
        val last = lastSample
        if (last != null && now - last < SAMPLE_MILLIS) return
        lastSample = now
        val mismatch = BackCallbackState.mismatchOf(navigator)
        if (mismatch != null && mismatch == previous) report(mismatch)
        previous = mismatch
    }

    /** A back event reached the callback of the activity of [navigator], before it acts. */
    fun atBack(navigator: Navigator) {
        BackCallbackState.mismatchOf(navigator)?.let(::report)
    }

    // Once per state, and not without end.
    private fun report(mismatch: String) {
        if (logged.size >= MAX_LOGGED || !logged.add(mismatch)) return
        log("[ApexNavigation] $mismatch: a change of what back does came without its trigger")
    }

    companion object {
        /** The least time between two samples. */
        const val SAMPLE_MILLIS = 500L

        // The states logged at most: a debug check, not a second log.
        private const val MAX_LOGGED = 32
    }
}

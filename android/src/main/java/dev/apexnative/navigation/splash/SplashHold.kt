package dev.apexnative.navigation.splash

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.annotation.RequiresApi
import dev.apexnative.navigation.NavigationActivity

/**
 * Keeps the splash on screen until the activity's first root has drawn: the system splash (Android 12+) or the
 * starting window leaves with the activity's first frame, and the activity's window doesn't draw until then. An
 * activity holds from its onCreate, unless it is recreated from saved state: it has no starting window then, and while
 * the start still runs its root doesn't come at once.
 *
 * Another window of the activity ends the hold at once. A dialog (React Native's red box and LogBox, an alert shown
 * during `willMount`, the developer menu) would stay hidden behind the splash, which stays above the activity's windows
 * until the activity has drawn. A popup (React Native's "Loading from Metro" banner in a debug build) counts as the
 * activity's first frame: the system splash leaves when it draws, and the held window would show black until the
 * first root. The app sees its windows from Android 10 on (`WindowInspector`); before, the window isn't held.
 */
internal class SplashHold private constructor(private val activity: NavigationActivity) :
    ViewTreeObserver.OnPreDrawListener {
    private val content: View = activity.findViewById(android.R.id.content)
    private var held = true

    // Before each frame of the window. A frame held back is tried again on the next one.
    override fun onPreDraw(): Boolean {
        if (!held) return true
        val done = activity.navigator?.isFirstRootDrawn == true ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && otherWindowShown())
        if (!done) return false
        release()
        return true
    }

    /** The window draws from its next frame on. Main thread only. */
    fun release() {
        if (!held) return
        held = false
        val observer = content.viewTreeObserver
        if (observer.isAlive) observer.removeOnPreDrawListener(this)
    }

    // A dialog of the activity is a window with the activity's token; a popup or a panel has the token of the
    // activity's window, which it belongs to.
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun otherWindowShown(): Boolean {
        val decor = activity.window.decorView
        val activityToken = (decor.layoutParams as? WindowManager.LayoutParams)?.token ?: return false
        val windowToken = decor.windowToken
        return WindowInspector.getGlobalWindowViews().any { view ->
            val token = (view.layoutParams as? WindowManager.LayoutParams)?.token
            view !== decor && view.isShown && token != null && (token == activityToken || token == windowToken)
        }
    }

    companion object {
        /** Holds the window of [activity] from now on, from Android 10; null before. In onCreate, after the content. */
        @JvmStatic
        fun start(activity: NavigationActivity): SplashHold? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            return SplashHold(activity).also { it.content.viewTreeObserver.addOnPreDrawListener(it) }
        }
    }
}

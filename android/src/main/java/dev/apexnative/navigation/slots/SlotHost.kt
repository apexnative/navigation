package dev.apexnative.navigation.slots

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.interfaces.fabric.ReactSurface
import java.lang.ref.WeakReference

/**
 * The hidden host of the app's React tree: one React surface for the process, so the app's providers outlive what
 * Android does to activities (back on the first screen finishes the activity, the system recreates it). It starts with
 * the first slot screen, when JavaScript runs; each activity takes its view over.
 */
object SlotHost {
    const val MODULE_NAME = "apexnative.slots"

    private var reactHost: ReactHost? = null
    private var surface: ReactSurface? = null
    // The host's views find their context through this wrapper, which always points at the current activity.
    private var context: MutableContextWrapper? = null
    private var activity: WeakReference<Activity>? = null
    private val main = Handler(Looper.getMainLooper())

    /** A slot screen exists, so the host must run: starts it the first time, starts it again when React dropped it. */
    fun ensureStarted(viewContext: Context) {
        val activity = activityOf(viewContext) ?: return
        if (reactHost == null) reactHost = (activity.application as ReactApplication).reactHost
        if (surface == null) {
            attach(activity)
            return
        }
        // React Native forgets every surface when it destroys its instance (a failed bundle load). A surface asked for
        // before the instance was ready starts a little later, so the check waits a moment.
        main.postDelayed({
            val current = surface ?: return@postDelayed
            if (current.isRunning) return@postDelayed
            (current.view?.parent as? ViewGroup)?.removeView(current.view)
            current.detach()
            surface = null
            this.activity?.get()?.let(::attach)
        }, 500)
    }

    /** The activity the user sees takes the host's view; the first slot screen starts the host. */
    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
        val wrapper = context ?: MutableContextWrapper(activity).also { context = it }
        wrapper.baseContext = activity
        if (surface == null) {
            val host = reactHost ?: return
            surface = host.createSurface(wrapper, MODULE_NAME, null).also { it.start() }
        }
        placeView(activity)
    }

    /** A new activity takes the view of a host that runs; without slot screens there is no host. */
    fun attachIfStarted(activity: Activity) {
        if (surface != null) attach(activity)
    }

    fun detach(activity: Activity) {
        if (this.activity?.get() !== activity) return
        val view = surface?.view
        (view?.parent as? ViewGroup)?.removeView(view)
        // The finished activity must not stay alive through the wrapper.
        context?.baseContext = activity.applicationContext
    }

    private fun placeView(activity: Activity) {
        val view = surface?.view ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        view.visibility = View.INVISIBLE
        activity.findViewById<ViewGroup>(android.R.id.content).addView(view, 0, ViewGroup.LayoutParams(1, 1))
    }

    private fun activityOf(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}

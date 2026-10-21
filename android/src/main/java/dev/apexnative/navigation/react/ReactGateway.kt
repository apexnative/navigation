package dev.apexnative.navigation.react

import android.app.Activity
import com.facebook.react.ReactHost
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import java.util.Collections
import java.util.WeakHashMap

/**
 * The app's React host, as the navigation runs it: one for every screen, started when the first navigation activity
 * resumes. `NavigationApplication.createReactGateway` gives another one. It tells [ReloadCoordinator] when the
 * JavaScript goes (`ReactHost.addBeforeDestroyListener`: a reload, or the host destroyed) and when a React context is
 * ready (`ReactInstanceEventListener.onReactContextInitialized`), once per host.
 */
open class ReactGateway(
    /** The React host of the app (`ReactApplication.reactHost`). */
    val host: ReactHost,
) {
    init {
        if (subscribedHosts.add(host)) {
            host.addBeforeDestroyListener { ReloadCoordinator.onJavaScriptGoes() }
            host.addReactInstanceEventListener(object : ReactInstanceEventListener {
                override fun onReactContextInitialized(context: ReactContext) =
                    ReloadCoordinator.onReactContextReady(context)
            })
            // A host the app started itself before the navigation came has its context ready already.
            host.currentReactContext?.let(ReloadCoordinator::onReactContextReady)
        }
    }

    /** The React context of the JavaScript that runs now; none before the bundle has run. */
    val currentReactContext: ReactContext?
        get() = host.currentReactContext

    /** The activity was created: nothing starts yet. */
    open fun onActivityCreated(activity: Activity) = Unit

    /** The activity resumed: the host goes on with it, and starts the first time. */
    open fun onActivityResumed(activity: Activity, backHandler: DefaultHardwareBackBtnHandler) {
        host.onHostResume(activity, backHandler)
        host.start()
    }

    /** The activity paused. */
    open fun onActivityPaused(activity: Activity) {
        host.onHostPause(activity)
    }

    /** The activity is destroyed. */
    open fun onActivityDestroyed(activity: Activity) {
        host.onHostDestroy(activity)
    }

    /** Back that nothing in the navigation handles: React Native's `BackHandler` hears it first. */
    open fun onBackPressed(): Boolean {
        host.onBackPressed()
        return true
    }

    private companion object {
        // The hosts whose listeners are added: a second gateway of one host doesn't add them again.
        val subscribedHosts: MutableSet<ReactHost> =
            Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))
    }
}

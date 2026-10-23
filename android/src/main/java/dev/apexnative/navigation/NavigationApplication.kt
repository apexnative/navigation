package dev.apexnative.navigation

import android.app.Application
import android.util.Log
import com.facebook.react.ReactApplication
import com.facebook.soloader.SoLoader
import dev.apexnative.navigation.nativescreen.NativeScreenCreator
import dev.apexnative.navigation.react.ReactGateway
import dev.apexnative.navigation.utils.NavigationLog

/**
 * The application class of an app with the navigation: it keeps one React host for every screen ([reactGateway]) and
 * the native screens by name. The app gives the host (`reactHost`) and loads React Native in its `onCreate` as the
 * React Native template does, so the load follows the app's Gradle settings (the New Architecture, edge-to-edge):
 *
 * ```kotlin
 * override fun onCreate() {
 *     super.onCreate()
 *     loadReactNative(this)
 *     registerNativeScreen("Profile") { activity, context -> ProfileView(activity, context) }
 * }
 * ```
 *
 * The gateway, and with it the host, is made the first time something needs it (the first activity), after the app's
 * `onCreate` loaded React Native.
 */
abstract class NavigationApplication : Application(), ReactApplication {
    // Written on the main thread as the app registers, read on the module thread by the layout checks: under its lock.
    private val nativeScreens = LinkedHashMap<String, NativeScreenCreator>()

    // Made once, by the first activity ([reactGateway]); [reactGatewayOrNull] only reads it.
    private val gateway: Lazy<ReactGateway> = lazy {
        check(SoLoader.isInitialized()) {
            "NavigationApplication needs React Native loaded first: call loadReactNative(this) in onCreate, " +
                "as the React Native template does"
        }
        createReactGateway()
    }

    /**
     * The app's React host as the navigation runs it, made when it is first asked for (the first activity's
     * `onCreate`). React Native must be loaded by then: the app calls `loadReactNative(this)` in its `onCreate`.
     */
    val reactGateway: ReactGateway by gateway

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerGeneratedScreens()
    }

    /** The gateway to the app's React host; another one for a host of the app's own. */
    protected open fun createReactGateway(): ReactGateway =
        ReactGateway(requireNotNull(reactHost) { "NavigationApplication needs the app's reactHost" })

    /**
     * A permission request of a navigation activity was answered; the activity gives the answer to the listener of
     * `requestPermissions` after this.
     */
    open fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) = Unit

    /** Registers the native screen [name], which [creator] makes; a name registered again takes the last creator. */
    fun registerNativeScreen(name: String, creator: NativeScreenCreator) {
        val previous = synchronized(nativeScreens) { nativeScreens.put(name, creator) }
        if (previous != null && BuildConfig.DEBUG) {
            Log.w(NavigationLog.TAG, "The native screen '$name' is registered again: the last registration counts")
        }
    }

    /** The native screens by name. */
    fun getNativeScreens(): Map<String, NativeScreenCreator> = synchronized(nativeScreens) { nativeScreens.toMap() }

    /** Whether a native screen is registered as [name]. Any thread: the layout checks ask it on the module thread. */
    internal fun isNativeScreenRegistered(name: String): Boolean =
        synchronized(nativeScreens) { nativeScreens.containsKey(name) }

    /**
     * [reactGateway] once the first activity made it; none before. It never makes it: its callers (the log, the
     * events, a React root) may run first, also off the main thread, and the app's `reactHost` isn't theirs to make.
     * Any thread.
     */
    internal val reactGatewayOrNull: ReactGateway?
        get() = if (gateway.isInitialized()) gateway.value else null

    // The list of `npx apex-navigation codegen`, found by its name: an app without native screens has none.
    private fun registerGeneratedScreens() {
        val screens = try {
            Class.forName(GENERATED_SCREENS)
        } catch (e: ClassNotFoundException) {
            return
        }
        try {
            screens.getMethod("register", NavigationApplication::class.java).invoke(null, this)
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException("The generated native screens couldn't register", e)
        }
    }

    companion object {
        private const val GENERATED_SCREENS = "dev.apexnative.navigation.generated.ApexNavigationGeneratedScreens"

        /** The application, from its `onCreate` on. */
        @JvmStatic
        lateinit var instance: NavigationApplication
            private set

        /** The application once its `onCreate` ran; none before, and in unit tests. */
        internal val instanceOrNull: NavigationApplication?
            get() = if (::instance.isInitialized) instance else null
    }
}

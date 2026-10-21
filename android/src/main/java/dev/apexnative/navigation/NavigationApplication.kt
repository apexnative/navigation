package dev.apexnative.navigation

import android.app.Application
import android.util.Log
import com.facebook.react.ReactApplication
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader
import dev.apexnative.navigation.nativescreen.NativeScreenCreator
import dev.apexnative.navigation.react.ReactGateway
import dev.apexnative.navigation.utils.NavigationLog

/**
 * The application class of an app with the navigation: it loads React Native itself (the app doesn't call
 * `loadReactNative`), keeps one React host for every screen ([reactGateway]) and the native screens by name. The app
 * gives the host (`reactHost`) and registers its native screens in its `onCreate`, after this one.
 */
abstract class NavigationApplication : Application(), ReactApplication {
    // Written on the main thread as the app registers, read on the module thread by the layout checks: under its lock.
    private val nativeScreens = LinkedHashMap<String, NativeScreenCreator>()

    /** The app's React host as the navigation runs it. */
    lateinit var reactGateway: ReactGateway
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        SoLoader.init(this, OpenSourceMergedSoMapping)
        DefaultNewArchitectureEntryPoint.load()
        reactGateway = createReactGateway()
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

    /** [reactGateway] once `onCreate` made it. */
    internal val reactGatewayOrNull: ReactGateway?
        get() = if (::reactGateway.isInitialized) reactGateway else null

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

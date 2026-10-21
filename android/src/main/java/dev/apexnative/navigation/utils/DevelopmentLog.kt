package dev.apexnative.navigation.utils

import android.util.Log
import com.facebook.react.devsupport.interfaces.DevSupportManager
import com.facebook.react.util.RNLog
import dev.apexnative.navigation.NavigationApplication

/**
 * Warnings and errors the app's developer must see: in development a warning goes to React Native's LogBox (`RNLog.w`)
 * and an error shows React Native's red screen (`DevSupportManager.showNewJavaError`), which can be closed and the app
 * goes on; in release both are only lines of the log with the tag `ApexNavigation`. Any thread.
 */
internal object DevelopmentLog {
    /** Hears every message besides the log, for the unit tests: `w` or `e`, and the text. */
    @Volatile
    internal var listener: ((level: Char, text: String) -> Unit)? = null

    /** The app runs in development: React Native's developer support is on. */
    val isDevelopment: Boolean
        get() = devSupport()?.devSupportEnabled == true

    /** The address of the JavaScript bundle in development (Metro's), none in release. */
    val bundleUrl: String?
        get() = devSupport()?.takeIf { it.devSupportEnabled }?.sourceUrl

    /** A warning: the log, and LogBox in development. */
    fun warn(text: String) {
        listener?.invoke('w', text)
        Log.w(NavigationLog.TAG, text)
        if (!isDevelopment) return
        val context = NavigationApplication.instanceOrNull?.reactGatewayOrNull?.currentReactContext
        RNLog.w(context, "[ApexNavigation] $text")
    }

    /** An error that doesn't stop the app: the log, and React Native's red screen in development. */
    fun error(text: String, cause: Throwable? = null) {
        listener?.invoke('e', text)
        Log.e(NavigationLog.TAG, text, cause)
        val devSupport = devSupport()?.takeIf { it.devSupportEnabled } ?: return
        devSupport.showNewJavaError("[ApexNavigation] $text", cause ?: IllegalStateException(text))
    }

    private fun devSupport(): DevSupportManager? =
        NavigationApplication.instanceOrNull?.reactGatewayOrNull?.host?.devSupportManager
}

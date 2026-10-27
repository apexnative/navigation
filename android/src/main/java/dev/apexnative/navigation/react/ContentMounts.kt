package dev.apexnative.navigation.react

import com.facebook.react.bridge.UIManager
import com.facebook.react.bridge.UIManagerListener
import com.facebook.react.common.annotations.UnstableReactNativeAPI
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.common.UIManagerType
import dev.apexnative.navigation.NavigationApplication

/**
 * The handlers of a screen's content that hear each mount of Fabric (`IReactView.setOnContentMounted`), by key, while
 * [hears] holds (the content is in a window and not destroyed). Fabric tells every mount of the app, on the main
 * thread, not those of one surface: the handlers measure and compare, so a mount elsewhere costs only that. It listens
 * only while a handler waits. Main thread only.
 */
internal class ContentMounts(private val hears: () -> Boolean) {
    private val handlers = LinkedHashMap<String, Runnable>()
    // The UI manager that tells the mounts; none while no handler waits.
    private var uiManager: UIManager? = null

    // React Native marks the listener unstable; it is the one place that tells a mount.
    @OptIn(UnstableReactNativeAPI::class)
    private val listener = object : UIManagerListener {
        override fun willDispatchViewUpdates(uiManager: UIManager) = Unit

        override fun willMountItems(uiManager: UIManager) = Unit

        override fun didMountItems(uiManager: UIManager) {
            if (hears()) run()
        }

        override fun didDispatchMountItems(uiManager: UIManager) = Unit

        override fun didScheduleMountItems(uiManager: UIManager) = Unit
    }

    /** [handler] runs after each mount under [key]; null removes the handler of [key]. */
    fun set(key: String, handler: Runnable?) {
        if (handler == null) handlers.remove(key) else handlers[key] = handler
        if (handlers.isEmpty()) stop() else start()
    }

    /** Runs the handlers now: a mount the content tells itself. */
    fun run() = handlers.values.toList().forEach(Runnable::run)

    /** The content goes: no handler runs again. */
    fun clear() {
        handlers.clear()
        stop()
    }

    @OptIn(UnstableReactNativeAPI::class)
    private fun start() {
        if (uiManager != null) return
        val reactContext = NavigationApplication.instanceOrNull?.reactGatewayOrNull?.currentReactContext ?: return
        uiManager = UIManagerHelper.getUIManager(reactContext, UIManagerType.FABRIC)?.also {
            it.addUIManagerEventListener(listener)
        }
    }

    @OptIn(UnstableReactNativeAPI::class)
    private fun stop() {
        uiManager?.removeUIManagerEventListener(listener)
        uiManager = null
    }
}

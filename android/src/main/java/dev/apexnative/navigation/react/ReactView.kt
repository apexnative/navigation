package dev.apexnative.navigation.react

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.facebook.react.interfaces.fabric.ReactSurface
import com.facebook.react.runtime.ReactSurfaceImpl
import dev.apexnative.navigation.NavigationApplication
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.react.events.ComponentType
import dev.apexnative.navigation.react.events.ScreenAppearance
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.viewcontroller.IReactView
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener

/**
 * A React root of its own: the content of a screen, or a component of a bar or a tab, rendered from the module
 * registered as [componentName], with [componentId] as its initial prop (the props stay in JavaScript); a cell of the
 * tabs' custom row gets its state from the native side as props too ([initialProps], [updateProps]). Its React surface
 * is made with the view, from the app's React host, and fills it; it starts once, as the view joins a window, and stops
 * once, on the main thread, as the view is destroyed.
 *
 * Ready once the safe area view of the screen told [ScreenReadiness] that the content rendered into it; a screen
 * without a safe area view (no view ever told anything of its id) once the root view of its surface has children, read
 * on this view's own layout passes. Ready once, ready for good; what waits for it ([whenReady]) runs then, once.
 * Without either, the limits of what waits decide: the view has no timer of its own. It owns the record of its
 * appearance, which the dispatcher of the appearance events holds until the content is ready (`AppearanceDispatcher`).
 */
@SuppressLint("ViewConstructor")
class ReactView @JvmOverloads constructor(
    context: Context,
    override val componentId: String,
    override val componentName: String,
    /** Props given by the native side, over `componentId`: the state of a tab's cell. */
    initialProps: Bundle? = null,
) : FrameLayout(context), IReactView {
    private val scrollEventListener = ScrollEventListener()
    private var destroyed = false
    private var started = false
    private var ready = false
    // What waited for the readiness was told; a question of isReady alone tells nothing, so nothing runs inside it.
    private var readyTold = false
    private val readyListeners = mutableListOf<Runnable>()
    private var readinessListener: ScreenReadiness.Listener? = null

    // None without the app's host (the unit tests).
    private val surface: ReactSurface?

    /** The appearance of the component; its type is the one of the first appearance call. */
    internal val appearance = ScreenAppearance(componentId, componentName, ComponentType.Component, this)
    private var typeTaken = false

    /**
     * How many children the root view of the surface has: only read, for a screen without a safe area view. The unit
     * tests give their own count.
     */
    internal var contentChildCount: () -> Int = { (surface?.view as? ViewGroup)?.childCount ?: 0 }

    init {
        require(componentId.isNotEmpty()) { "a React root needs the id of its screen" }
        val props = Bundle().apply {
            initialProps?.let(::putAll)
            putString(COMPONENT_ID, componentId)
        }
        surface = NavigationApplication.instanceOrNull?.reactGatewayOrNull?.host
            ?.createSurface(context, componentName, props)
        surface?.view?.let { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (started || destroyed) return
        started = true
        surface?.start()
    }

    // The children of the surface's root are read after each layout; what waits (a root's transition, which moves
    // views of the root layer) runs on the next turn of the main loop, not inside the layout pass.
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!destroyed && !readyTold && isReady()) post { checkReadiness() }
    }

    override fun asView(): ViewGroup = this

    // The surface's own view: React Native's touch target search starts there.
    override val touchRoot: ViewGroup?
        get() = surface?.view as? ViewGroup

    override fun isReady(): Boolean {
        if (!ready && hasRendered()) ready = true
        return ready
    }

    override fun isRendered(): Boolean = isReady()

    /** Runs [listener] once the content is ready ([isReady]); at once when it is. */
    override fun whenReady(listener: Runnable) {
        if (destroyed) return
        if (readyTold) return listener.run()
        readyListeners += listener
        if (isReady()) return checkReadiness()
        if (readinessListener != null) return
        val readiness = object : ScreenReadiness.Listener {
            override fun onLaidOut() = Unit

            override fun onContentRendered() = checkReadiness()
        }
        readinessListener = readiness
        ScreenReadiness.setListener(componentId, readiness)
    }

    /**
     * The content may have become ready: the safe area view told it rendered, or the surface's root got its first
     * children (a screen without a safe area view). What waits runs once.
     */
    internal fun checkReadiness() {
        if (destroyed || readyTold || !isReady()) return
        readyTold = true
        stopListening()
        val waiting = readyListeners.toList()
        readyListeners.clear()
        waiting.forEach(Runnable::run)
    }

    // The safe area view said so; without one, the root view of the surface has children.
    private fun hasRendered(): Boolean = when (ScreenReadiness.isContentRendered(componentId)) {
        true -> true
        false -> false
        null -> contentChildCount() > 0
    }

    private fun stopListening() {
        val readiness = readinessListener ?: return
        readinessListener = null
        ScreenReadiness.removeListener(componentId, readiness)
    }

    /**
     * Stops the content, once, on the main thread: its appearance ends first (DidDisappear if its WillAppear went,
     * else what is held goes with it), then the surface stops and leaves the host, so a reload doesn't start it again
     * in the new JavaScript.
     */
    override fun destroy() {
        if (destroyed) return
        AppearanceDispatcher.didDisappear(appearance)
        destroyed = true
        readyListeners.clear()
        stopListening()
        val surface = surface ?: return
        if (started) surface.stop()
        surface.detach()
    }

    // The screen sends ANN.NavigationButtonPressed itself (`ComponentViewController`): its content has nothing to do.
    override fun sendOnNavigationButtonPressed(buttonId: String) = Unit

    override fun getScrollEventListener(): ScrollEventListener = scrollEventListener

    override fun dispatchTouchEventToJs(event: MotionEvent) = Unit

    override fun sendComponentWillStart(type: ComponentType) = AppearanceDispatcher.willAppear(appearanceOf(type))

    override fun sendComponentStart(type: ComponentType) = AppearanceDispatcher.didAppear(appearanceOf(type))

    override fun sendComponentStop(type: ComponentType) = AppearanceDispatcher.didDisappear(appearanceOf(type))

    private fun appearanceOf(type: ComponentType): ScreenAppearance {
        if (!typeTaken) {
            typeTaken = true
            appearance.componentType = type
        }
        return appearance
    }

    /**
     * The props the native side gives change: [props] replace them whole, `componentId` kept, as an update of the same
     * root (the props JavaScript keeps by the id stay). Main thread.
     */
    fun updateProps(props: Bundle) {
        if (destroyed) return
        val all = Bundle(props).apply { putString(COMPONENT_ID, componentId) }
        (surface as? ReactSurfaceImpl)?.updateInitProps(all)
    }

    /** The view was destroyed: its surface is stopped. */
    val isDestroyed: Boolean
        get() = destroyed

    private companion object {
        // The one initial prop of a React root: the props of the screen stay in JavaScript by this id.
        const val COMPONENT_ID = "componentId"
    }
}

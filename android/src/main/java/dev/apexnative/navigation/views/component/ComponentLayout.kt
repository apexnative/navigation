package dev.apexnative.navigation.views.component

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.IReactView
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener

/**
 * The view of a screen (`ComponentViewController`): its content ([reactView] — a React view, or the container of a
 * slot screen) filling it. The screen keeps the room for the bars as its margins and what the system covers as its
 * padding. While it is in a window it hears each layout of the window ([onWindowLayout]) through a listener of its own,
 * which leaves both the observer it was added to and the one it has now as it leaves the window or is destroyed: a
 * detached view has an observer of its own, and the window's would keep the screen. It owns the screen's one scroll
 * listener, which hears the lists inside it.
 */
@SuppressLint("ViewConstructor")
class ComponentLayout(context: Context, val reactView: IReactView) : FrameLayout(context), Renderable {
    /** The scroll of the screen's own vertical lists, which the bars follow: one per screen. */
    val scrollEventListener: ScrollEventListener = reactView.getScrollEventListener() ?: ScrollEventListener()

    /** The content has rendered once. */
    val isReady: Boolean
        get() = reactView.isReady()

    override val isRendered: Boolean
        get() = reactView.isRendered()

    /** Runs as the view joins a window. */
    internal var onAttached: (() -> Unit)? = null

    /** Runs after each layout of the window while the view is in it. */
    internal var onWindowLayout: (() -> Unit)? = null

    // The observer the layout listener was added to; none while it isn't added.
    private var layoutObserver: ViewTreeObserver? = null
    private val windowLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { onWindowLayout?.invoke() }
    private var destroyed = false

    init {
        addView(reactView.asView(), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        scrollEventListener.watch(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (destroyed) return
        listenToWindowLayout()
        scrollEventListener.attachIfNeeded()
        onAttached?.invoke()
    }

    override fun onDetachedFromWindow() {
        stopListeningToWindowLayout()
        super.onDetachedFromWindow()
    }

    fun sendOnNavigationButtonPressed(buttonId: String) = reactView.sendOnNavigationButtonPressed(buttonId)

    fun dispatchTouchEventToJs(event: MotionEvent) = reactView.dispatchTouchEventToJs(event)

    /** The screen is destroyed: its content stops, and it listens to nothing any more. */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        stopListeningToWindowLayout()
        scrollEventListener.release()
        onAttached = null
        onWindowLayout = null
        reactView.destroy()
    }

    private fun listenToWindowLayout() {
        if (layoutObserver != null) return
        val observer: ViewTreeObserver? = viewTreeObserver
        observer?.addOnGlobalLayoutListener(windowLayoutListener)
        layoutObserver = observer
    }

    private fun stopListeningToWindowLayout() {
        val current: ViewTreeObserver? = viewTreeObserver
        for (observer in listOfNotNull(layoutObserver, current).distinct()) {
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(windowLayoutListener)
        }
        layoutObserver = null
    }
}

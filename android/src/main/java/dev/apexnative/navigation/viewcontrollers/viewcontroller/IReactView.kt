package dev.apexnative.navigation.viewcontrollers.viewcontroller

import android.util.Size
import android.view.MotionEvent
import android.view.ViewGroup
import dev.apexnative.navigation.react.events.ComponentType

/**
 * What a screen shows: the core's React view (`ReactView`), the slot screen of the slots module (`SlotScreenView`, for
 * names starting with `slot:`), or a view that stands for another screen's content (the view of a pair stands for its
 * primary). The core asks it only these: its id, the appearance of its component (with the component's type), whether
 * it is ready and when it gets so, the content mounted, the stop, touches, the size of its content. The answers with a
 * body are those of a view that only stands for content: it is ready, and has nothing of its own to tell.
 */
interface IReactView {
    /** The view to put where the screen's content goes. */
    fun asView(): ViewGroup

    /** The content has rendered once: the screen can show. */
    fun isReady(): Boolean

    /** The content is drawn as it is now: what a transition into the screen waits for. */
    fun isRendered(): Boolean

    /** Stops the content: its React surface goes, once. Main thread. */
    fun destroy()

    /** A button of the bar over the screen was pressed: `ANN.NavigationButtonPressed` with the screen's id. */
    fun sendOnNavigationButtonPressed(buttonId: String)

    /** The scroll of the content, which the bars follow; none for content that doesn't scroll. */
    fun getScrollEventListener(): ScrollEventListener?

    /** A touch that began outside the content (a bar's view over it) goes to its JavaScript. */
    fun dispatchTouchEventToJs(event: MotionEvent)

    /** The id of the screen the content belongs to; none for a view that stands for another screen's content. */
    val componentId: String?
        get() = null

    /** The name the screen was registered with, without `slot:`. */
    val componentName: String?
        get() = null

    /**
     * The content's component starts to appear, as a component of [type]: the content tells its appearance record to
     * the dispatcher of the appearance events (`AppearanceDispatcher`), which sends `ANN.ComponentWillAppear` once per
     * appearance, when the content is ready or the hold limit passed. A view that stands for another screen's
     * content tells nothing.
     */
    fun sendComponentWillStart(type: ComponentType) = Unit

    /**
     * The content's component has appeared: `ANN.ComponentDidAppear` through the dispatcher, after its WillAppear
     * (asked first when it wasn't), once per appearance.
     */
    fun sendComponentStart(type: ComponentType) = Unit

    /**
     * The content's component has disappeared: `ANN.ComponentDidDisappear` through the dispatcher, only after its
     * WillAppear went; when that is still held, nothing goes for the appearance.
     */
    fun sendComponentStop(type: ComponentType) = Unit

    /** Runs [listener] once the content is ready ([isReady]); at once when it is. */
    fun whenReady(listener: Runnable) = listener.run()

    /** Runs [listener] once the content has mounted into its container; at once for content that mounts by itself. */
    fun whenSlotContentMounted(listener: Runnable) = listener.run()

    /**
     * Calls [handler] after each mount of the content while the screen is in the window, under [key]; null removes
     * the handler of [key].
     */
    fun setOnContentMounted(key: String, handler: Runnable?) = Unit

    /** Touches outside the content go to what is under the screen (an overlay). */
    fun setPassThroughTouches(passThrough: Boolean) = Unit

    /** The size of the content as it lays out for [maxWidth] pixels, for a sheet as tall as its content; null without. */
    fun contentSize(maxWidth: Int): Size? = null

    /**
     * The root view of the React content, where React Native's own touch target search starts (an overlay's touches): a
     * React surface's view, a slot's content; none without content to search.
     */
    val touchRoot: ViewGroup?
        get() = null
}

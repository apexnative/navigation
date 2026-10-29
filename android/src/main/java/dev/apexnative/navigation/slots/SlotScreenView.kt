package dev.apexnative.navigation.slots

import android.annotation.SuppressLint
import android.content.Context
import android.util.Size
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.facebook.react.uimanager.PixelUtil.dpToPx
import dev.apexnative.navigation.react.ContentHeights
import dev.apexnative.navigation.react.ContentMounts
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.react.events.ComponentType
import dev.apexnative.navigation.react.events.ScreenAppearance
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.viewcontroller.IReactView
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener

/**
 * What a slot screen (`slot:<Name>`) shows: the empty container its slot of the app's React tree moves its children
 * into, under the screen's id ([SlotContainers]), told of each change of its size. It is the screen's content for the
 * core: ready once the slot's content has mounted into it and, when the screen has a safe area view, the screen
 * rendered into that view; its mounted handlers run at each mount of Fabric and as the content mounts into it. Its
 * content measures without a height limit in the slot's own layout ([contentSize]). Its appearance events go through
 * its record to the core's dispatcher (`AppearanceDispatcher`), which holds them until it is ready or the hold limit.
 * Destroyed while the id is still its own, it tells JavaScript to take the slot's content away (`ANN.SlotDestroyed`).
 */
@SuppressLint("ViewConstructor")
class SlotScreenView(
    context: Context,
    override val componentId: String,
    /** The screen's name without `slot:`, as the events give it. */
    override val componentName: String,
) : FrameLayout(context), IReactView, SlotContainer {
    private val scrollEventListener = ScrollEventListener()
    private val whenMounted = mutableListOf<Runnable>()
    private val whenRendered = mutableListOf<Runnable>()
    private val mounts = ContentMounts { !destroyed && isAttachedToWindow }
    private var listening = false

    // The safe area view of a slot renders in the app's one React tree: every signal of the id is the screen's.
    private val readiness = object : ScreenReadiness.Listener {
        override fun onContentRendered() = checkReadiness()
    }
    private var mounted = false
    private var readyTold = false
    private var destroyed = false

    /**
     * The appearance of the screen: ready with the slot's mount and its render ([whenReady]), or when a command's
     * readiness wait ended ([ScreenAppearance.markReady]).
     */
    internal val appearance = ScreenAppearance(componentId, componentName, ComponentType.Component, this)

    /**
     * The screen was prepared for a push (`preparePush`) and isn't shown yet: destroyed so, it sends no
     * `ANN.SlotDestroyed`, as JavaScript decides about the slot itself.
     */
    var isPreparedPush = false

    init {
        SlotContainers.put(componentId, this)
    }

    override fun asView(): ViewGroup = this

    // The slot's content the slot moved in here: React Native's touch target search starts there.
    override val touchRoot: ViewGroup?
        get() = if (childCount > 0) getChildAt(0) as? ViewGroup else null

    // Mounted, and rendered into the screen's safe area view; a screen without one (nothing told of its id) once
    // mounted. Ready once, ready for good.
    override fun isReady(): Boolean =
        readyTold || (mounted && ScreenReadiness.isContentRendered(componentId) != false)

    override fun isRendered(): Boolean = isReady()

    // The slot lays its children out at the container's size: each change of it goes to the slot.
    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (destroyed || SlotContainers.get(componentId) !== this) return
        SlotContainers.changed(componentId)
    }

    override fun destroy() {
        if (destroyed) return
        // Its appearance ends with it: DidDisappear if its WillAppear went, else nothing held goes later.
        AppearanceDispatcher.didDisappear(appearance)
        destroyed = true
        whenMounted.clear()
        whenRendered.clear()
        mounts.clear()
        stopListening()
        // A later screen with the same id keeps the slot.
        if (SlotContainers.get(componentId) !== this) return
        ContentHeights.forgetSlot(componentId)
        SlotContainers.remove(componentId)
        if (!isPreparedPush) AppearanceDispatcher.slotDestroyed(componentId, appearance.generation)
    }

    override fun sendOnNavigationButtonPressed(buttonId: String) = Unit

    override fun getScrollEventListener(): ScrollEventListener = scrollEventListener

    override fun dispatchTouchEventToJs(event: MotionEvent) = Unit

    override fun sendComponentWillStart(type: ComponentType) = AppearanceDispatcher.willAppear(appearance)

    override fun sendComponentStart(type: ComponentType) = AppearanceDispatcher.didAppear(appearance)

    override fun sendComponentStop(type: ComponentType) = AppearanceDispatcher.didDisappear(appearance)

    /** Runs [listener] once the content has mounted and rendered ([isReady]); at once when it has. */
    override fun whenReady(listener: Runnable) {
        if (destroyed) return
        if (readyTold) return listener.run()
        whenRendered += listener
        if (isReady()) return checkReadiness()
        if (listening) return
        listening = true
        ScreenReadiness.addListener(componentId, this, readiness)
    }

    override fun whenSlotContentMounted(listener: Runnable) {
        if (mounted) listener.run() else whenMounted += listener
    }

    override fun setOnContentMounted(key: String, handler: Runnable?) {
        if (!destroyed) mounts.set(key, handler)
    }

    // The slot measures its content in its own layout: the first question asks for it and lays the slot out again; the
    // height comes with that layout, before the mount the handlers hear. The width is the container's, at which the
    // slot lays its content out.
    override fun contentSize(maxWidth: Int): Size? {
        if (destroyed) return null
        if (!ContentHeights.isSlotRequested(componentId)) {
            ContentHeights.requestSlot(componentId)
            SlotContainers.changed(componentId)
            return null
        }
        val height = ContentHeights.slotHeight(componentId)?.takeIf { it > 0f } ?: return null
        return Size(if (width > 0) width else maxWidth, Math.round(height.dpToPx()))
    }

    // Only the container that holds the slot of the id: a destroyed one, or one a later screen replaced, takes no
    // slot back.
    override fun setPassThroughTouches(passThrough: Boolean) {
        if (destroyed || SlotContainers.get(componentId) !== this) return
        SlotContainers.put(componentId, this, passThrough)
    }

    override fun slotContentDidMount() {
        if (destroyed) return
        mounted = true
        val waiting = whenMounted.toList()
        whenMounted.clear()
        waiting.forEach { it.run() }
        mounts.run()
        checkReadiness()
    }

    // Both signals are in: what waits for the readiness runs, once.
    private fun checkReadiness() {
        if (destroyed || readyTold || !isReady()) return
        readyTold = true
        stopListening()
        val waiting = whenRendered.toList()
        whenRendered.clear()
        waiting.forEach { it.run() }
    }

    private fun stopListening() {
        if (!listening) return
        listening = false
        ScreenReadiness.removeListener(componentId, this)
    }
}

package dev.apexnative.navigation.slots

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.facebook.react.R
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.common.annotations.UnstableReactNativeAPI
import com.facebook.react.uimanager.JSTouchDispatcher
import com.facebook.react.uimanager.PixelUtil.pxToDp
import com.facebook.react.uimanager.RootView
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.EventDispatcher
import com.facebook.react.views.view.ReactViewGroup
import dev.apexnative.navigation.react.ContentHeights

/**
 * The React children of a slot. They live outside the views of the host's surface, in the container of a screen, so
 * this view dispatches their touches itself, as React Native's Modal does.
 */
@SuppressLint("ViewConstructor")
class SlotContentView(private val reactContext: ThemedReactContext) : ReactViewGroup(reactContext), RootView {
    private val touchDispatcher = JSTouchDispatcher(this)
    var passThrough = false

    private val eventDispatcher: EventDispatcher?
        get() = UIManagerHelper.getEventDispatcher(reactContext)

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!passThrough) eventDispatcher?.let { touchDispatcher.handleTouchEvent(event, it, reactContext) }
        return super.onInterceptTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A pass-through container leaves every touch outside the content to the screens below.
        if (passThrough) return false
        eventDispatcher?.let { touchDispatcher.handleTouchEvent(event, it, reactContext) }
        super.onTouchEvent(event)
        return true
    }

    @OptIn(UnstableReactNativeAPI::class)
    override fun onChildStartedNativeGesture(childView: View?, ev: MotionEvent) {
        eventDispatcher?.let { touchDispatcher.onChildStartedNativeGesture(ev, it, reactContext) }
    }

    override fun onChildEndedNativeGesture(childView: View, ev: MotionEvent) {
        eventDispatcher?.let { touchDispatcher.onChildEndedNativeGesture(ev, it) }
    }

    override fun handleException(t: Throwable) {
        reactContext.reactApplicationContext.handleException(RuntimeException(t))
    }

    // Keep getting onInterceptTouchEvent when a child asks to stop it.
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) = Unit
}

/**
 * A slot of the app's React tree. It stays in the hidden host with nothing of its own; its children move into the
 * container with the same slot id (`nativeID`), and lay out at the container's size, which goes to the slot's state.
 */
@SuppressLint("ViewConstructor")
class SlotView(reactContext: ThemedReactContext) : ReactViewGroup(reactContext) {
    val content = SlotContentView(reactContext)
    var stateWrapper: StateWrapper? = null
        set(value) {
            field = value
            // The size the state holds: the one this view sent last, or the one the slot started with (`initialSize`, a
            // screen rendered before it is shown). The same size isn't sent again: the state has it.
            val data = value?.stateData ?: return
            if (data.hasKey("width") && data.hasKey("height")) {
                lastWidth = data.getDouble("width").toFloat()
                lastHeight = data.getDouble("height").toFloat()
            }
        }
    private var lastWidth = -1f
    private var lastHeight = -1f
    // The slot was laid out once more, at the same size, for the measure of its content (`ContentHeights`).
    private var measureAsked = false

    private val slotId: String?
        get() = getTag(R.id.view_tag_native_id) as? String

    init {
        content.visibility = INVISIBLE
        addView(content)
    }

    override fun setId(id: Int) {
        super.setId(id)
        // Touches that hit no child go to the slot, as on an empty screen.
        content.id = id
    }

    fun containerChanged(changedSlotId: String) {
        if (changedSlotId == slotId) attachContent()
    }

    fun attachContent() {
        // The view hears the changes of the container under the id it has now, and no other.
        val id = slotId ?: return SlotContainers.unregister(this)
        SlotContainers.register(id, this)
        val container = SlotContainers.get(id)
        val target: ViewGroup = container ?: this
        if (content.parent !== target) {
            (content.parent as? ViewGroup)?.removeView(content)
            target.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        content.visibility = if (container != null) VISIBLE else INVISIBLE
        content.passThrough = SlotContainers.passesTouchesThrough(id)
        contentChanged()

        val state = stateWrapper
        if (container != null && state != null && container.width > 0 && container.height > 0) {
            val width = container.width.toFloat().pxToDp()
            val height = container.height.toFloat().pxToDp()
            // A container that measures the content gets one layout more for it, with the same size, until the height
            // came.
            val requested = ContentHeights.isSlotRequested(id)
            // A request that went (its screen did) and comes again gets its layout again.
            if (!requested) measureAsked = false
            val measures = !measureAsked && requested && ContentHeights.slotHeight(id) == null
            if (width != lastWidth || height != lastHeight || measures) {
                measureAsked = measureAsked || measures
                lastWidth = width
                lastHeight = height
                state.updateState(
                    WritableNativeMap().apply {
                        putDouble("width", width.toDouble())
                        putDouble("height", height.toDouble())
                    }
                )
            }
        }
    }

    /** The content got children: the container learns once it shows them. */
    fun contentChanged() {
        val container = slotId?.let(SlotContainers::get) ?: return
        if (content.parent === container && content.childCount > 0) (container as? SlotContainer)?.slotContentDidMount()
    }

    fun drop() {
        measureAsked = false
        SlotContainers.unregister(this)
        (content.parent as? ViewGroup)?.removeView(content)
    }
}

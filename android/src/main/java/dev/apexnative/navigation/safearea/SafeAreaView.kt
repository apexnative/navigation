package dev.apexnative.navigation.safearea

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.views.view.ReactViewGroup
import dev.apexnative.navigation.react.ScreenInsets
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness

/**
 * The safe area view (`ANNSafeAreaView`): keeps its content out of its screen's safe area. The padding goes to its
 * state, which lays it out in React's layout, so the content moves without a render.
 */
@SuppressLint("ViewConstructor")
class SafeAreaView(reactContext: ThemedReactContext) : ReactViewGroup(reactContext) {
    private var stopListening: (() -> Unit)? = null
    private var applied: ScreenInsets.Insets? = null
    private var readySent = false
    private var contentSignalled = false

    var stateWrapper: StateWrapper? = null
        set(value) {
            field = value
            // The padding the state holds: the one this view sent last, or the one the view started with
            // (`initialPadding`, a screen rendered before it is shown). The same padding isn't sent again: each update
            // comes back as a new state, and sending what it holds went round and round.
            applied = value?.stateData?.let(::paddingOf)
            applyPadding()
        }

    /** The screen is rendered into this view, also when it renders nothing (`null`): the navigation waits for this. */
    var contentRendered = false
        set(value) {
            if (field == value) return
            field = value
            if (value) signalContent()
        }

    var screenId: String? = null
        set(value) {
            if (field == value) return
            stopListening?.invoke()
            field = value
            applied = null
            stopListening = value?.let { id -> ScreenInsets.listenToPadding(id) { applyPadding() } }
            // The screen has a safe area view: it counts as rendered once this view says so.
            value?.let(ScreenReadiness::wrapped)
            applyPadding()
        }

    private fun applyPadding() {
        val state = stateWrapper ?: return
        val padding = screenId?.let(ScreenInsets::padding) ?: return
        if (padding == applied) return
        applied = padding
        state.updateState(
            WritableNativeMap().apply {
                putDouble("top", padding.top)
                putDouble("right", padding.right)
                putDouble("bottom", padding.bottom)
                putDouble("left", padding.left)
            }
        )
    }

    private fun paddingOf(data: ReadableMap): ScreenInsets.Insets? {
        if (!data.hasKey("top")) return null
        return ScreenInsets.Insets(data.getDouble("top"), data.getDouble("right"), data.getDouble("bottom"),
            data.getDouble("left"))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sendReadyIfLaidOut()
        if (contentRendered || childCount > 0) signalContent()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        sendReadyIfLaidOut()
    }

    // The screen renders its content once this view is laid out in its place with a size, as
    // react-native-safe-area-context does: what is drawn before the bars are laid out may have to move. The screen's
    // container hears it too.
    private fun sendReadyIfLaidOut() {
        if (readySent || !isAttachedToWindow) return
        if (waitsForReadiness() && (width == 0 || height == 0)) return
        val reactContext = context as? ReactContext ?: return
        val dispatcher = UIManagerHelper.getEventDispatcher(reactContext) ?: return
        readySent = true
        dispatcher.dispatchEvent(SafeAreaReadyEvent(UIManagerHelper.getSurfaceId(this), id))
        screenId?.let(ScreenReadiness::laidOut)
    }

    // The navigation tells whether this view's screen waits: a component of a bar (title, button, background, tab)
    // renders at once and shows with the bar; a title sized by its content would never get a size to wait for. A view
    // without a screen waits for its size, as before.
    private fun waitsForReadiness(): Boolean = screenId?.let(ScreenReadiness::waitsForReadiness) ?: true

    // The first content: the screen's container hears that the screen is drawn (a push or a modal waits for it).
    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        signalContent()
    }

    // Once, by the screen's id: the container asks the screen's view for the layout React mounts the content without.
    // On attach too: content added before this view was attached is signalled then.
    private fun signalContent() {
        if (contentSignalled) return
        val id = screenId ?: return
        contentSignalled = true
        ScreenReadiness.contentRendered(id)
    }

    fun drop() {
        stopListening?.invoke()
        stopListening = null
        readySent = false
        contentSignalled = false
        contentRendered = false
    }

    companion object {
        /**
         * Whether the screen [screenId], shown in [root], is rendered into its safe area view, also when it renders
         * nothing (`null`), as the view told the navigation; a screen without one (the old API) by the children of its
         * root.
         */
        @JvmStatic
        fun isContentDrawn(root: ViewGroup, screenId: String): Boolean =
            ScreenReadiness.isContentRendered(screenId) ?: (root.childCount > 0)
    }
}

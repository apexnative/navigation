package dev.apexnative.navigation.viewcontrollers.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.facebook.react.uimanager.TouchTargetHelper
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The views of the overlays of one activity ([OverlayCoordinator.Views]): an [OverlayContainer] per overlay in the
 * overlays layer, later above earlier, the layer visible only while it holds one. An overlay's touches are decided by
 * React Native's public touch target search over each screen's React content (`TouchTargetHelper`), never by the core
 * walking React Native's views. Main thread only.
 */
internal class OverlayViews(
    // The activity's context: the containers' views are made with it.
    private val context: Context,
    private val layer: FrameLayout,
) : OverlayCoordinator.Views {
    private val containers = HashMap<OverlayRecord, OverlayContainer>()

    override fun place(record: OverlayRecord) {
        val container = OverlayContainer(context)
        container.isWaiting = true
        container.alpha = 0f
        container.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        try {
            val view = record.root.view
            (view.parent as? ViewGroup)?.removeView(view)
            container.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
            layer.addView(container, FrameLayout.LayoutParams(MATCH, MATCH))
        } catch (e: Throwable) {
            (container.parent as? ViewGroup)?.removeView(container)
            throw e
        }
        containers[record] = container
        layer.visibility = View.VISIBLE
    }

    override fun show(record: OverlayRecord) {
        val container = containers[record] ?: return
        container.takesTouch = { takes(record, container, it) }
        container.isWaiting = false
        container.alpha = 1f
        container.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        container.bringToFront()
    }

    override fun remove(record: OverlayRecord) {
        val container = containers.remove(record) ?: return
        (container.parent as? ViewGroup)?.removeView(container)
        if (layer.childCount == 0) layer.visibility = View.GONE
    }

    override fun update(record: OverlayRecord, options: Options) {
        containers[record]?.let { TestIds.apply(it, options.overlay.testID.value?.ifEmpty { null }) }
    }

    override fun requestLayout(record: OverlayRecord) {
        containers[record]?.requestLayout()
    }

    // Where the down of a gesture landed, and React Native's target there when it is on a screen's content.
    private fun takes(record: OverlayRecord, container: OverlayContainer, event: MotionEvent): Boolean {
        if (record.intercepts) return true
        val origin = locationOf(container)
        val x = origin[0] + event.x
        val y = origin[1] + event.y
        for (screen in shownScreens(record.root)) {
            val screenView = screen.peekView() ?: continue
            if (!contains(screenView, x, y)) continue
            // The bar or the tabs over the screen's content (`drawBehind`) are the overlay's own views.
            val layout = screen.parentController
            if (OverlayTouches.isOnBars(y, layout?.headerBottomOf(screen), layout?.tabsTopOf(screen))) {
                return OverlayTouches.takes(false, OverlayTouches.Place.OwnView, NO_TARGET, NO_TARGET)
            }
            // A native screen is the overlay's own view.
            val content = (screen as? ComponentViewController)?.content ?: return true
            val root = content.touchRoot
            if (root == null || !contains(root, x, y)) {
                return OverlayTouches.takes(false, OverlayTouches.Place.Screen, NO_TARGET, NO_TARGET)
            }
            val at = locationOf(root)
            val target = TouchTargetHelper.findTargetTagForTouch(x - at[0], y - at[1], root)
            return OverlayTouches.takes(false, OverlayTouches.Place.Content, target, root.id)
        }
        val place = if (record.root.peekView()?.let { contains(it, x, y) } == true) {
            OverlayTouches.Place.OwnView
        } else {
            OverlayTouches.Place.Outside
        }
        return OverlayTouches.takes(false, place, NO_TARGET, NO_TARGET)
    }

    // The screens the overlay shows, down the visible children of its layouts.
    private fun shownScreens(node: ViewController<*>): List<ViewController<*>> =
        if (node is ParentController<*>) node.visibleChildren().flatMap(::shownScreens) else listOf(node)

    private fun locationOf(view: View): IntArray = IntArray(2).also(view::getLocationInWindow)

    private fun contains(view: View, x: Float, y: Float): Boolean {
        if (!view.isShown) return false
        val at = locationOf(view)
        return x >= at[0] && x < at[0] + view.width && y >= at[1] && y < at[1] + view.height
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val NO_TARGET = View.NO_ID
    }
}

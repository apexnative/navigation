package dev.apexnative.navigation.viewcontrollers.bottomtabs

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.apexnative.navigation.NavigationActivity
import dev.apexnative.navigation.utils.UiUtils
import dev.apexnative.navigation.viewcontrollers.modal.ModalContainer
import dev.apexnative.navigation.views.bottomtabs.BottomTabsLayout
import dev.apexnative.navigation.views.bottomtabs.CustomTabRow
import java.util.WeakHashMap

/**
 * Puts the custom rows of the tabs in their place, as it works now: an observer of every activity of the process that
 * touches only the navigation's activities; on each layout pass of such a window it finds the tabs with a row through
 * the view tree, and puts each row in the window's content right above the layer holding its tabs (after the rows
 * already there) — tabs in a modal: in the modal's own view, right above its root's view (or its sheet), so the row
 * moves with the modal and newer modals cover it —, over the bar as [CustomTabRowGeometry] says — applied only when it
 * changed, so UI tests reach idle. A row whose tabs left the window leaves it too, and comes back to its place as they
 * do. Before each frame the row takes its tabs' alpha (a root that waits under the old one is transparent) and the
 * motion of the bar's container, and stays out of accessibility and touches while they wait. Main thread only.
 */
internal object CustomTabRowPlacer : Application.ActivityLifecycleCallbacks {
    private var registered = false
    // The listeners of each observed activity's window.
    private val observed = WeakHashMap<Activity, Listeners>()
    // The rows placed at the last layout pass of each activity, with their tabs.
    private val rows = WeakHashMap<CustomTabRow, BottomTabsLayout>()

    private class Listeners(
        val observer: ViewTreeObserver,
        val layout: ViewTreeObserver.OnGlobalLayoutListener,
        val draw: ViewTreeObserver.OnPreDrawListener,
    )

    /** [activity] has tabs with a row: its window is observed from now on, and the process's later activities. */
    fun track(activity: Activity) {
        if (!registered) {
            registered = true
            activity.application?.registerActivityLifecycleCallbacks(this)
        }
        observe(activity)
    }

    /** The tabs of [row] go: the row leaves the window. */
    fun forget(row: CustomTabRow) {
        rows.remove(row)
        (row.parent as? ViewGroup)?.removeView(row)
    }

    override fun onActivityResumed(activity: Activity) = observe(activity)

    override fun onActivityDestroyed(activity: Activity) {
        val listeners = observed.remove(activity) ?: return
        if (!listeners.observer.isAlive) return
        listeners.observer.removeOnGlobalLayoutListener(listeners.layout)
        listeners.observer.removeOnPreDrawListener(listeners.draw)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    // Only the navigation's activities: another activity's views are never looked for.
    private fun observe(activity: Activity) {
        if (activity !is NavigationActivity || observed.containsKey(activity)) return
        val decor = activity.window?.decorView ?: return
        val observer = decor.viewTreeObserver
        val layout = ViewTreeObserver.OnGlobalLayoutListener { place(activity) }
        val draw = ViewTreeObserver.OnPreDrawListener {
            follow(activity)
            true
        }
        observer.addOnGlobalLayoutListener(layout)
        observer.addOnPreDrawListener(draw)
        observed[activity] = Listeners(observer, layout, draw)
    }

    private fun contentOf(activity: Activity): ViewGroup? =
        activity.window?.decorView?.findViewById(android.R.id.content)

    // Each layout pass: the tabs with a row in the content, and the rows in their places.
    private fun place(activity: NavigationActivity) {
        val content = contentOf(activity) ?: return
        val found = ArrayList<BottomTabsLayout>()
        collect(content, found)
        val placed = found.mapNotNull { tabs -> tabs.row?.also { place(activity, content, tabs, it) } }.toSet()
        // A row whose tabs left the window leaves it too.
        rows.entries.filter { (row, tabs) -> row !in placed && tabs.context === activity }.forEach { (row, _) ->
            (row.parent as? ViewGroup)?.removeView(row)
        }
    }

    private fun collect(view: View, found: MutableList<BottomTabsLayout>) {
        if (view is CustomTabRow) return
        if (view is BottomTabsLayout && view.row != null) found += view
        if (view is ViewGroup) for (index in 0 until view.childCount) collect(view.getChildAt(index), found)
    }

    private fun place(activity: NavigationActivity, content: ViewGroup, tabs: BottomTabsLayout, row: CustomTabRow) {
        rows[row] = tabs
        val (host, layer) = placeIn(content, tabs) ?: return
        if (row.parent !== host) {
            (row.parent as? ViewGroup)?.removeView(row)
            // Margins of the host's own kind: the host converts them (a frame or a coordinator layout).
            host.addView(row, indexAbove(host, layer, row), ViewGroup.MarginLayoutParams(0, 0))
            row.place = null
        } else if (host.indexOfChild(row) != indexAbove(host, layer, row)) {
            val params = row.layoutParams
            host.removeView(row)
            host.addView(row, indexAbove(host, layer, row), params)
        }
        row.showWith(tabs.bar)
        if (tabs.bar.visibility == View.GONE || tabs.bar.width == 0) return
        val place = placeOf(activity, host, tabs, row)
        if (!CustomTabRowGeometry.needsPlacing(row.place, place)) return
        row.place = place
        val params = row.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        params.width = place.right - place.left
        params.height = place.bottom - place.top
        params.leftMargin = place.left
        params.topMargin = place.top
        row.layoutParams = params
    }

    // Over the bar: its edges less the margins, its bottom, the height of the content and the margin below it, in the
    // coordinates of the row's [host].
    private fun placeOf(
        activity: Activity,
        host: ViewGroup,
        tabs: BottomTabsLayout,
        row: CustomTabRow,
    ): CustomTabRowGeometry.Place {
        val bar = IntArray(2).also(tabs.bar::getLocationInWindow)
        val origin = IntArray(2).also(host::getLocationInWindow)
        val options = row.options
        val margin = UiUtils.dpToPixels(
            activity,
            options.horizontalMargin.get(CustomTabRowGeometry.HORIZONTAL_MARGIN_DP.toDouble()),
        )
        val height = options.height.get(null)?.let { UiUtils.dpToPixels(activity, it) }
        val extra = UiUtils.dpToPixels(activity, CustomTabRowGeometry.EXTRA_HEIGHT_DP.toDouble())
        // Where the bar rests: its container's motion (a ride, a slide, the hide on scroll) the row takes before each
        // frame as its own translation.
        val left = bar[0] - origin[0] - tabs.container.translationX.toInt()
        val top = bar[1] - origin[1] - tabs.container.translationY.toInt()
        // Never over the navigation bar's buttons: the bar is above them, and so is the row.
        val navigationBar = if ((activity as? NavigationActivity)?.isEdgeToEdge == true) navigationBarOf(host) else 0
        return CustomTabRowGeometry.place(
            barLeft = left,
            barRight = left + tabs.bar.width,
            barBottom = minOf(top + tabs.bar.height, host.height - navigationBar),
            margin = margin,
            content = CustomTabRowGeometry.content(tabs.bar.height, height, extra),
            bottomMargin = UiUtils.dpToPixels(activity, options.bottomMargin.get(0.0)),
        )
    }

    // The navigation bar at the bottom of the window, through the compat insets on every version.
    private fun navigationBarOf(view: View): Int =
        ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0

    // Where the row of [tabs] goes, and the child there it goes right above: tabs in a modal, the modal's own view and
    // the view in it that holds them (its root's, or its sheet's container); else the content and the layer of the
    // root or of the overlays. A sheet's drag moves it without a layout: the row doesn't follow a drag.
    private fun placeIn(content: ViewGroup, tabs: View): Pair<ViewGroup, View>? {
        var current: View = tabs
        while (true) {
            val parent = current.parent as? ViewGroup ?: return null
            if (parent is ModalContainer || parent === content) return parent to current
            current = parent
        }
    }

    // Right above [layer], after the rows already there.
    private fun indexAbove(host: ViewGroup, layer: View, row: CustomTabRow): Int {
        var index = host.indexOfChild(layer) + 1
        while (index < host.childCount) {
            val child = host.getChildAt(index)
            if (child !is CustomTabRow || child === row) break
            index++
        }
        return index
    }

    // Before each frame: the row takes the alpha of its tabs up to the view it is in and the container's translation,
    // and leaves accessibility while they wait to show.
    private fun follow(activity: Activity) {
        val content = contentOf(activity) ?: return
        for ((row, tabs) in rows) {
            val host = row.parent as? View ?: continue
            if (host !== content && host !is ModalContainer) continue
            var alpha = tabs.container.alpha
            var current: View? = tabs
            while (current != null && current !== host) {
                alpha *= current.alpha
                current = current.parent as? View
            }
            if (row.alpha != alpha) row.alpha = alpha
            // The row goes with the bar's container: a ride, a slide, the hide on scroll.
            val container = tabs.container
            if (row.translationX != container.translationX) row.translationX = container.translationX
            if (row.translationY != container.translationY) row.translationY = container.translationY
            val mode = if (row.hiddenFromAccessibility()) {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            }
            if (row.importantForAccessibility != mode) row.importantForAccessibility = mode
        }
    }
}

package dev.apexnative.navigation.viewcontrollers.stack.fab

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowInsetsCompat
import dev.apexnative.navigation.options.FabOptions
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.stack.StackLayout
import dev.apexnative.navigation.views.stack.fab.Fab
import dev.apexnative.navigation.views.stack.fab.FabMenu

/**
 * The FAB of one stack (`fab.*`), over the module's `Fab` and `FabMenu`: one FAB or one menu at a time, from the
 * resolved `fab` of the screen on top (its options over the stack's, its parents' and the defaults). It follows the top
 * at the end of each transition and the merges of `fab` into it; a press sends `ANN.NavigationButtonPressed` with the
 * screen whose options showed it, an action with its own id. Its views are children of the stack's view, over the
 * screens and the bar. Main thread only.
 */
internal class FabPresenter(
    private val activity: Activity,
    /** The tab bar at rest under the stack, by the options of its screen on top; none out of tabs. */
    private val tabsAtRest: () -> FabPlacement.Tabs? = { null },
) {
    private var fab: Fab? = null
    private var menu: FabMenu? = null
    private var shownId: String? = null
    private var layout: StackLayout? = null
    // `fab.alignHorizontally: 'left'`: at the start of the line.
    private var alignStart = false

    /** The screen whose options show the FAB now; none. */
    var source: ViewController<*>? = null
        private set

    private val view: View?
        get() = menu ?: fab

    /**
     * The screen on top became [source], with its resolved [options]: the same id updates the FAB that shows and grows
     * it again, another id replaces it, none takes it away; the scroll of [scroll] hides it with `hideOnScroll`.
     * Nothing when [source] already shows it.
     */
    fun showFor(layout: StackLayout, source: ViewController<*>?, options: FabOptions, scroll: ScrollEventListener?) {
        if (source === this.source && this.layout === layout) return
        this.layout = layout
        disableCollapse()
        this.source = source
        when (FabPlan.step(shownId, menu != null, options)) {
            FabPlan.Step.None -> Unit
            FabPlan.Step.Remove -> remove()
            FabPlan.Step.Create -> create(layout, options, scroll)
            FabPlan.Step.Replace -> {
                remove()
                create(layout, options, scroll)
            }
            FabPlan.Step.Update -> {
                apply(options, scroll)
                // A new top whose options don't hide it shows it, even where the screen before hid it.
                if (!options.visible.isFalse) {
                    fab?.show()
                    menu?.show()
                }
                view?.let {
                    it.bringToFront()
                    grow(it)
                }
            }
        }
    }

    /**
     * `mergeOptions` of `fab` into the screen on top or into the stack: [options] are the top's resolved options now.
     * The same id updates the FAB at once, another id replaces it, none takes it away.
     */
    fun merge(layout: StackLayout, source: ViewController<*>?, options: FabOptions, scroll: ScrollEventListener?) {
        this.layout = layout
        this.source = source
        when (FabPlan.step(shownId, menu != null, options)) {
            FabPlan.Step.None -> Unit
            FabPlan.Step.Remove -> remove()
            FabPlan.Step.Create -> create(layout, options, scroll)
            FabPlan.Step.Replace -> {
                remove()
                create(layout, options, scroll)
            }
            FabPlan.Step.Update -> apply(options, scroll)
        }
    }

    /**
     * Light or dark changed: the colors of the options, of the new mode, go again over those the buttons read again
     * from their style.
     */
    fun repaint(options: FabOptions) {
        fab?.let { applyColors(it, options) }
        menu?.let { menu ->
            applyColors(menu.main, options)
            options.actions.orEmpty().zip(menu.actions).forEach { (action, button) -> applyColors(button, action) }
        }
    }

    /** The window insets or the stack's bottom room changed: the FAB takes its place again. */
    fun place() {
        val layout = layout ?: return
        val view = view ?: return
        val params = view.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        val insets = layout.lastInsets
        val navigationBar = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())
        val cutout = insets?.getInsets(WindowInsetsCompat.Type.displayCutout())
        val place = FabPlacement.place(
            navigationBar = navigationBar?.let { FabPlacement.Edges(it.left, it.top, it.right, it.bottom) }
                ?: FabPlacement.Edges(),
            cutout = cutout?.let { FabPlacement.Edges(it.left, it.top, it.right, it.bottom) } ?: FabPlacement.Edges(),
            tabs = tabsAtRest(),
            density = activity.resources.displayMetrics.density,
            rtl = layout.layoutDirection == View.LAYOUT_DIRECTION_RTL,
            alignStart = alignStart,
            shadowInset = menu?.shadowInset ?: 0,
        )
        params.gravity = Gravity.BOTTOM or if (place.onLeft) Gravity.LEFT else Gravity.RIGHT
        params.leftMargin = if (place.onLeft) place.sideMargin else 0
        params.rightMargin = if (place.onLeft) 0 else place.sideMargin
        params.bottomMargin = place.bottomMargin
        view.layoutParams = params
    }

    /** [node] was destroyed: a FAB it showed sends nothing more. */
    fun forget(node: ViewController<*>) {
        if (source === node) source = null
    }

    /** The stack goes: the FAB and its menu go at once, the scrim first. */
    fun destroy() {
        disableCollapse()
        menu?.let {
            it.removeScrim()
            (it.parent as? ViewGroup)?.removeView(it)
        }
        fab?.let { (it.parent as? ViewGroup)?.removeView(it) }
        menu = null
        fab = null
        shownId = null
        source = null
        layout = null
    }

    private fun create(layout: StackLayout, options: FabOptions, scroll: ScrollEventListener?) {
        val id = FabPlan.idOf(options) ?: return
        shownId = id
        val params = CoordinatorLayout.LayoutParams(WRAP, WRAP)
        if (FabPlan.isMenu(options)) {
            val menu = FabMenu(activity, id)
            this.menu = menu
            layout.addOverAll(menu, params)
        } else {
            val fab = Fab(activity, id)
            this.fab = fab
            layout.addOverAll(fab, params)
        }
        apply(options, scroll)
        view?.let(::grow)
    }

    // Every option of the FAB that shows; the collapse before the visibility, which `disableCollapse` would undo.
    private fun apply(options: FabOptions, scroll: ScrollEventListener?) {
        alignStart = options.alignHorizontally.get("right") == "left"
        val source = source
        fab?.let { fab ->
            applyButton(fab, options, mayWait = fab.drawable != null || options.visible.isFalse)
            TestIds.apply(fab, options.testID.takeIf { it.hasValue() }?.get())
            val id = shownId.orEmpty()
            fab.setOnClickListener { press(source, id) }
        }
        menu?.let { menu ->
            applyButton(menu.main, options, mayWait = menu.main.drawable != null || options.visible.isFalse)
            TestIds.apply(menu.main, options.testID.takeIf { it.hasValue() }?.get())
            menu.scrimColor = options.scrimColor.get(activity, null)
            menu.setActions(options.actions.orEmpty().map { action ->
                val actionId = action.id.get("")
                Fab(activity, actionId).also { button ->
                    // The actions show only while the menu is open.
                    applyButton(button, action, mayWait = !menu.isOpen)
                    TestIds.apply(button, action.testID.takeIf { it.hasValue() }?.get())
                    button.setOnClickListener {
                        press(source, actionId)
                        menu.close()
                    }
                }
            })
        }
        if (options.hideOnScroll.isTrue && scroll != null) enableCollapse(scroll) else disableCollapse()
        val view = view
        if (options.visible.isFalse) {
            fab?.hide()
            menu?.hide()
        } else if (view != null && options.visible.isTrue) {
            fab?.show()
            menu?.show()
        }
        place()
    }

    // [mayWait]: the button can take its icon when it is ready (it shows one, or it stays hidden), see [Fab.applyIcon].
    private fun applyButton(button: Fab, options: FabOptions, mayWait: Boolean) {
        val icon = options.icon.takeIf { it.hasValue() }?.get()?.ifEmpty { null }
        if (icon != null) button.applyIcon(icon, options.iconColor, mayWait)
        applyColors(button, options)
        button.setMini(options.size.get("regular") == "mini")
    }

    private fun applyColors(button: Fab, options: FabOptions) {
        // iconColor alone, without a new icon, tints the icon there is.
        options.iconColor.get(activity, null)?.let(button::setIconColor)
        val background = options.backgroundColor.get(activity, null)
        val pressed = options.clickColor.get(activity, null)
        if (background != null || pressed != null) button.setColors(background, pressed)
        // A container of the options without iconColor: the icon keeps its own colors.
        if (background != null && !options.iconColor.hasValue()) button.keepIconColors()
        options.rippleColor.get(activity, null)?.let(button::applyRippleColor)
    }

    private fun enableCollapse(scroll: ScrollEventListener) {
        fab?.enableCollapse(scroll)
        menu?.enableCollapse(scroll)
    }

    private fun disableCollapse() {
        fab?.disableCollapse()
        menu?.disableCollapse()
    }

    // The FAB that shows goes: a button shrinks to 0 in 200 ms; a menu goes at once, its scrim first.
    private fun remove() {
        menu?.let {
            it.removeScrim()
            (it.parent as? ViewGroup)?.removeView(it)
        }
        fab?.let { fab ->
            fab.animate().cancel()
            if (!fab.isAttachedToWindow) {
                (fab.parent as? ViewGroup)?.removeView(fab)
            } else {
                fab.animate().scaleX(0f).scaleY(0f).setDuration(ANIMATION_MILLIS)
                    .withEndAction { (fab.parent as? ViewGroup)?.removeView(fab) }.start()
            }
        }
        menu = null
        fab = null
        shownId = null
    }

    // It comes to the top and grows from 60 % in 200 ms.
    private fun grow(view: View) {
        val target = (view as? FabMenu)?.main ?: view
        target.animate().cancel()
        target.scaleX = GROW_FROM
        target.scaleY = GROW_FROM
        target.animate().scaleX(1f).scaleY(1f).setDuration(ANIMATION_MILLIS).start()
    }

    companion object {
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val ANIMATION_MILLIS = 200L
        private const val GROW_FROM = 0.6f

        /**
         * A press on the FAB, or on an action of its menu, of [source] (EVT-NavigationButtonPressed): nothing once
         * [source] is destroyed — its FAB still fades for 200 ms.
         */
        fun press(source: ViewController<*>?, buttonId: String) {
            if (source == null || source.isDestroyed) return
            AppearanceDispatcher.navigationButtonPressed(source, buttonId)
        }
    }
}

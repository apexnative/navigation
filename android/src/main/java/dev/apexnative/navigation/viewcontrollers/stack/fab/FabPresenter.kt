package dev.apexnative.navigation.viewcontrollers.stack.fab

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowInsetsCompat
import dev.apexnative.navigation.options.FabOptions
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.FadeThrough
import dev.apexnative.navigation.views.animations.SystemTransition
import dev.apexnative.navigation.views.stack.StackLayout
import dev.apexnative.navigation.views.stack.fab.Fab
import dev.apexnative.navigation.views.stack.fab.FabCopy
import dev.apexnative.navigation.views.stack.fab.FabMenu

/**
 * The FAB of one stack (`fab.*`), over the module's `Fab` and `FabMenu`: one FAB or one menu at a time, from the
 * resolved `fab` of the screen on top (its options over the stack's, its parents' and the defaults). It follows the top
 * from the start of each transition, going with the screens by [FabMotion], and the merges of `fab` into it; a press
 * sends `ANN.NavigationButtonPressed` with the screen whose options showed it, an action with its own id. Its views are
 * children of the stack's view, over the screens and the bar; a FAB that leaves goes as a copy ([FabCopy]). Main
 * thread only.
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

    // The ride of the FAB that shows as it arrives; none at rest.
    private var ride: Animator? = null
    // The copies of the FABs that leave, each with its ride.
    private val copies = LinkedHashMap<FabCopy, Animator>()

    // The top's FAB while a back gesture's preview shows the screen under the top with its own: out of the stack, as
    // it was (hidden by the scroll, say).
    private class Aside(
        val fab: Fab?,
        val menu: FabMenu?,
        val shownId: String?,
        var source: ViewController<*>?,
        val alignStart: Boolean,
    )

    private var aside: Aside? = null

    private val view: View?
        get() = menu ?: fab

    /** The view of the FAB or of its menu that shows; none. */
    val shownView: View?
        get() = view

    /**
     * The screen on top became [source], with its resolved [options], in a transition that goes by [move]: the same id
     * updates the FAB that shows, where it stays as it is, another id replaces it, none takes it away — the new FAB
     * arrives and a copy of the old one leaves by [move]; the scroll of [scroll] hides it with `hideOnScroll`. An open
     * menu closes. The copies of an older transition go, and with [FabMotion.Move.AtOnce] a FAB still riding comes to
     * rest. Nothing when [source] already shows it.
     */
    fun showFor(
        layout: StackLayout,
        source: ViewController<*>?,
        options: FabOptions,
        scroll: ScrollEventListener?,
        move: FabMotion.Move = FabMotion.Move.Swap,
    ) {
        // At once, what still moves comes to rest, also when the top stays (a failed push).
        if (move == FabMotion.Move.AtOnce) {
            dropCopies()
            stopRide(rest = true)
        }
        if (source === this.source && this.layout === layout) return
        this.layout = layout
        dropCopies()
        this.source = source
        when (FabPlan.step(shownId, menu != null, options)) {
            FabPlan.Step.None -> Unit
            FabPlan.Step.Remove -> leave(layout, move)
            FabPlan.Step.Create -> {
                create(layout, options, scroll)
                arrive(move)
            }
            FabPlan.Step.Replace -> {
                leave(layout, move)
                create(layout, options, scroll)
                arrive(move)
            }
            FabPlan.Step.Update -> {
                disableCollapse()
                menu?.close()
                apply(options, scroll)
                // A new top whose options don't hide it shows it, even where the screen before hid it.
                if (!options.visible.isFalse) {
                    fab?.show()
                    menu?.show()
                }
                view?.bringToFront()
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
            FabPlan.Step.Remove -> leave(layout, FabMotion.Move.Swap)
            FabPlan.Step.Create -> {
                create(layout, options, scroll)
                arrive(FabMotion.Move.Swap)
            }
            FabPlan.Step.Replace -> {
                leave(layout, FabMotion.Move.Swap)
                create(layout, options, scroll)
                arrive(FabMotion.Move.Swap)
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

    /**
     * A back gesture's preview shows the screen under the top: the FAB that shows goes aside, out of the stack but as it
     * is, its menu closing, until [bringBack] or [dropAside]; the screen's own FAB shows next ([showFor]).
     */
    fun setAside() {
        if (aside != null) return
        dropCopies()
        stopRide(rest = true)
        menu?.close()
        view?.let { (it.parent as? ViewGroup)?.removeView(it) }
        aside = Aside(fab, menu, shownId, source, alignStart)
        fab = null
        menu = null
        shownId = null
        source = null
    }

    /** The gesture was cancelled: the FAB the preview showed goes, and the one set aside is back as it was. */
    fun bringBack() {
        val kept = aside ?: return
        aside = null
        val layout = layout ?: return discard(kept)
        leave(layout, FabMotion.Move.AtOnce)
        fab = kept.fab
        menu = kept.menu
        shownId = kept.shownId
        source = kept.source
        alignStart = kept.alignStart
        view?.let { layout.addOverAll(it, it.layoutParams as? CoordinatorLayout.LayoutParams ?: params()) }
        place()
    }

    /** The gesture was released: the FAB set aside goes with the top that leaves. */
    fun dropAside() {
        val kept = aside ?: return
        aside = null
        discard(kept)
    }

    /** [node] was destroyed: a FAB it showed sends nothing more. */
    fun forget(node: ViewController<*>) {
        if (source === node) source = null
        aside?.let { if (it.source === node) it.source = null }
    }

    /** The stack goes: the FAB and its menu go at once, the scrim first, and the copies that leave. */
    fun destroy() {
        aside?.let(::discard)
        aside = null
        dropCopies()
        stopRide(rest = false)
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
        val params = params()
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

    // The FAB that shows goes with its screen: a copy of it leaves by [move] while the button, or the menu, goes from the
    // stack at once; an open menu closes, its scrim fading out by itself. At once nothing leaves, the scrim neither.
    private fun leave(layout: StackLayout, move: FabMotion.Move) {
        val view = view ?: return
        val departure = FabMotion.departure(move, isRtl(layout))
        val copy = departure?.let { FabCopy.of((view as? FabMenu)?.main ?: view, view, layout) }
        stopRide(rest = false)
        disableCollapse()
        menu?.let {
            if (departure != null) it.closeToLeave() else it.removeScrim()
            (it.parent as? ViewGroup)?.removeView(it)
        }
        fab?.let { (it.parent as? ViewGroup)?.removeView(it) }
        menu = null
        fab = null
        shownId = null
        if (copy == null) return
        copies[copy] = play(copy, departure, fromCurrent = true) { copies.remove(copy)?.let { removeCopy(copy) } }
    }

    // The new FAB arrives with its screen by [move]; one its options hide (`visible: false`) has nothing to show.
    private fun arrive(move: FabMotion.Move) {
        val view = view ?: return
        if (((view as? FabMenu)?.main ?: view).visibility != View.VISIBLE) return
        val arrival = FabMotion.arrival(move, layout?.let(::isRtl) ?: false) ?: return
        ride = play(view, arrival, fromCurrent = false) { animator -> if (ride === animator) ride = null }
    }

    // A ride stops where it is; [rest]: the FAB is in its place again, opaque and whole.
    private fun stopRide(rest: Boolean) {
        val running = ride ?: return
        ride = null
        running.cancel()
        if (!rest) return
        view?.apply {
            translationX = 0f
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
        }
    }

    // The copies of an older transition go at once.
    private fun dropCopies() {
        if (copies.isEmpty()) return
        val dropped = copies.toList()
        copies.clear()
        dropped.forEach { (copy, animator) ->
            animator.cancel()
            removeCopy(copy)
        }
    }

    private fun removeCopy(copy: FabCopy) {
        (copy.parent as? ViewGroup)?.removeView(copy)
    }

    // Plays [ride] on [view], from its start values or [fromCurrent] from where the view is; [end] at its end or stop.
    private fun play(view: View, ride: FabMotion.Ride, fromCurrent: Boolean, end: (Animator) -> Unit): Animator {
        val density = activity.resources.displayMetrics.density
        if (!fromCurrent) {
            view.translationX = ride.fromX * density
            view.alpha = ride.fromAlpha
            view.scaleX = ride.fromScale
            view.scaleY = ride.fromScale
        }
        val move = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, ride.toX * density),
            PropertyValuesHolder.ofFloat(View.SCALE_X, ride.toScale),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, ride.toScale),
        ).apply {
            duration = ride.duration
            interpolator = when (ride.curve) {
                FabMotion.Curve.System -> SystemTransition.CURVE
                FabMotion.Curve.FadeThrough -> FadeThrough.CURVE
                FabMotion.Curve.Material -> AccelerateDecelerateInterpolator()
            }
        }
        val animator = AnimatorSet()
        if (ride.alphaDuration > 0) {
            val fade = ObjectAnimator.ofFloat(view, View.ALPHA, ride.toAlpha).apply {
                startDelay = ride.alphaDelay
                duration = ride.alphaDuration
                interpolator = LinearInterpolator()
            }
            animator.playTogether(move, fade)
        } else {
            animator.play(move)
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = end(animation)
        })
        animator.start()
        return animator
    }

    // A FAB set aside that won't come back: it follows no scroll, its scrim goes.
    private fun discard(kept: Aside) {
        kept.fab?.disableCollapse()
        kept.menu?.disableCollapse()
        kept.menu?.removeScrim()
    }

    private fun params() = CoordinatorLayout.LayoutParams(WRAP, WRAP)

    private fun isRtl(layout: StackLayout): Boolean = layout.layoutDirection == View.LAYOUT_DIRECTION_RTL

    companion object {
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        /**
         * A press on the FAB, or on an action of its menu, of [source] (EVT-NavigationButtonPressed): nothing once
         * [source] is destroyed.
         */
        fun press(source: ViewController<*>?, buttonId: String) {
            if (source == null || source.isDestroyed) return
            AppearanceDispatcher.navigationButtonPressed(source, buttonId)
        }
    }
}

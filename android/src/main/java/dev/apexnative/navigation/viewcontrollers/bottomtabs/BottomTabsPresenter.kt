package dev.apexnative.navigation.viewcontrollers.bottomtabs

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import dev.apexnative.navigation.options.BottomTabsAnimationOptions
import dev.apexnative.navigation.options.BottomTabsEnterExitAnimationOptions
import dev.apexnative.navigation.options.BottomTabsOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.LayoutDirection
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.options.parsers.TypefaceLoader
import dev.apexnative.navigation.system.SystemUiOwner
import dev.apexnative.navigation.utils.ImageRequests
import dev.apexnative.navigation.utils.OptionImages
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.UiUtils
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.AnimationRules
import dev.apexnative.navigation.views.animations.SideAnimator
import dev.apexnative.navigation.views.animations.SystemTransition
import dev.apexnative.navigation.views.bottomtabs.BottomTabs
import dev.apexnative.navigation.views.bottomtabs.BottomTabsContainer
import dev.apexnative.navigation.views.bottomtabs.BottomTabsLayout

/**
 * The presenter of bottom tabs on Android: the look of the bar from the options it shows with — those of the screen the
 * selected tab shows — on its container (background, top line, shadow, elevation, corners, `layoutStyle`,
 * `bottomMargin`), its titles' mode, `testID` and direction; the items through [items]; and the bar's visibility, at
 * once, with its slide of 300 ms, or riding with the screens of a stack's transition. Only what changed is set. The
 * controller keeps the tabs; the presenter tells it when the bar's room changed ([onBarChanged]). Main thread only.
 */
internal open class BottomTabsPresenter(activity: Activity) : Presenter(activity, Options()) {
    /** The items of the bar. */
    val items = TabItems { colorOf(it) }

    /** The bar's place or visibility changed: the screens' room and the FAB follow. */
    var onBarChanged: (() -> Unit)? = null

    /** `layoutStyle: 'compact'` applies. */
    var isCompact = false
        private set

    /** `bottomMargin` as it applies, in pixels. */
    var bottomMargin = 0
        private set

    /** Where the bar is or goes: shown or hidden. */
    var isShownTarget = true
        private set

    /**
     * Whether the bar's items carry their tabs' `testID`: not under the custom row, whose places carry them
     * (the hidden buttons keep none).
     */
    var itemTestIds = true

    private var layout: BottomTabsLayout? = null
    private val typefaces = TypefaceLoader(activity)
    // The icons of the items by item and kind (selected or not): those of the cache at once, the others as below.
    private val icons = ImageRequests<Pair<Int, Boolean>>(OptionImages.source(activity))
    private val applied = HashMap<Pair<Int, Class<*>>, TabItems.Call>()
    private var motion: Animator? = null
    private var anyIcon = false
    private var testId: String? = null
    private var titleMode: BottomTabs.TitleState? = null
    private var direction: LayoutDirection? = null

    /** The tabs' view: the presenter draws on its bar from now on. */
    fun bind(layout: BottomTabsLayout) {
        this.layout = layout
        layout.bar.onVisibilityChanged = { visibility ->
            layout.container.visibility = visibility
            onBarChanged?.invoke()
        }
    }

    /** The tabs go: a motion stops where it is. */
    fun unbind() {
        motion?.cancel()
        motion = null
        layout = null
        applied.clear()
        icons.clear()
    }

    /** Whether some tab has an icon, which the automatic mode of the titles needs. */
    fun setAnyIcon(value: Boolean) {
        anyIcon = value
    }

    /**
     * Runs [calls] on the bar's items; a call equal to the last of its kind for its item is skipped (an icon isn't
     * loaded and set again as a screen appears). The badge always goes: its default colors are read at each set.
     */
    open fun run(calls: List<TabItems.Call>) {
        val bar = layout?.bar ?: return
        for (call in calls) {
            if (call is TabItems.Call.TestId && !itemTestIds) continue
            val key = call.index to call.javaClass
            val before = applied[key]
            if (call !is TabItems.Call.Badge && call !is TabItems.Call.Font && before == call) continue
            applied[key] = call
            run(bar, call, isChange = before != null)
        }
    }

    private fun run(bar: BottomTabs, call: TabItems.Call, isChange: Boolean) {
        val index = call.index
        when (call) {
            is TabItems.Call.Title -> bar.setText(index, call.text)
            is TabItems.Call.Icon -> icon(bar, index, call.uri, false, isChange)
            is TabItems.Call.SelectedIcon -> icon(bar, index, call.uri, true, isChange)
            is TabItems.Call.Colors -> {
                bar.setIconActiveColor(index, call.iconActive)
                bar.setIconInactiveColor(index, call.iconInactive)
                bar.setTitleActiveColor(index, call.titleActive)
                bar.setTitleInactiveColor(index, call.titleInactive)
            }
            is TabItems.Call.IconSize -> {
                bar.setIconWidth(index, call.width)
                bar.setIconHeight(index, call.height)
            }
            is TabItems.Call.Font -> bar.setTitleTypeface(index, call.font.getTypeface(typefaces, null))
            is TabItems.Call.FontSizes -> {
                bar.setTitleActiveTextSizeInSp(index, call.active)
                bar.setTitleInactiveTextSizeInSp(index, call.inactive)
            }
            is TabItems.Call.TestId -> bar.setTabTestId(index, call.testId)
            is TabItems.Call.TintDisabled -> bar.setIconTintDisabled(index, call.unselected, call.selected)
            is TabItems.Call.Badge -> if (call.text.isNotEmpty()) {
                bar.setBadge(index, call.text, call.color)
            } else {
                bar.setDot(index, call.dot, call.dotColor)
            }
        }
    }

    /**
     * The bar shows with [options], those of the screen the selected tab shows (`bottomTabs`): its look, `layoutStyle`,
     * `bottomMargin`, the titles' mode, `testID`; the direction of [direction]. The navigation bar over the screens
     * takes the tabs' color when the screen has none of its own: the activity's system UI owner reads it again
     * (`SystemUiRules`), the orientation as it is applied — the bar also applies in the repaint of new default options;
     * a tab switch and a merge ask for the orientation themselves.
     */
    open fun applyBar(options: BottomTabsOptions, direction: LayoutDirection) {
        val layout = layout ?: return
        SystemUiOwner.of(activity)?.invalidateKeepingOrientation()
        layout.container.apply(lookOf(options, layout))
        val compact = options.layoutStyle.get(null) == COMPACT
        val margin = UiUtils.dpToPixels(activity, options.bottomMargin.get(0.0))
        val placeChanged = compact != isCompact || margin != bottomMargin
        isCompact = compact
        bottomMargin = margin
        layout.setCompact(compact)
        layout.barOffset = TabsRoom.Bar(0, layout.navigationBarInset, compact, margin).offset
        layout.container.setPadding(0, 0, 0, if (compact) 0 else layout.navigationBarInset)
        val mode = TabItems.titleState(options.titleDisplayMode, anyIcon)
        if (mode != titleMode) {
            titleMode = mode
            layout.bar.titleState = mode
        }
        val id = options.testID.get(null)
        if (id != testId) {
            testId = id
            TestIds.apply(layout.bar, id)
        }
        if (direction != this.direction) {
            this.direction = direction
            layout.bar.setLayoutDirection(direction)
        }
        if (placeChanged) onBarChanged?.invoke()
    }

    /** The window's insets changed: the room under a stretched bar and the offset of a compact one follow. */
    fun insetsChanged() {
        val layout = layout ?: return
        layout.barOffset = TabsRoom.Bar(0, layout.navigationBarInset, isCompact, bottomMargin).offset
        layout.container.setPadding(0, 0, 0, if (isCompact) 0 else layout.navigationBarInset)
        onBarChanged?.invoke()
    }

    /**
     * The bar shows or hides: with [animate], a slide of the container by its height, 300 ms, `fast_out_slow_in`;
     * without, at once. A hidden bar is `GONE`, and its container with it.
     */
    open fun setVisible(shown: Boolean, animate: Boolean) {
        val layout = layout ?: return run { isShownTarget = shown }
        if (shown == isShownTarget && motion == null && layout.bar.isHidden != shown) return
        stopMotion()
        isShownTarget = shown
        if (!animate || !layout.isAttachedToWindow) return showNow(layout, shown)
        val slide = TabBarMotion.slide(shown)
        if (shown) layout.bar.restoreBottomNavigation(false)
        // A bar that never laid out (hidden from the first screen on) slides by the height its room counts.
        val height = layout.container.height.takeIf { it > 0 } ?: (UiUtils.dpToPx(activity, BAR_HEIGHT_DP) +
            if (isCompact) 0 else layout.navigationBarInset)
        val distance = (height + layout.barOffset).toFloat()
        layout.container.translationY = slide.fromY * distance
        play(ObjectAnimator.ofFloat(layout.container, View.TRANSLATION_Y, slide.toY * distance).apply {
            duration = slide.duration
            interpolator = FAST_OUT_SLOW_IN
        })
        onBarChanged?.invoke()
    }

    /**
     * The bar goes with the screens of a stack's transition: [move] of the queue, to [shown] or hidden; [animate] false
     * (`bottomTabs.animate` of the new top) at once. With a slide, [side] (the transition's `animations.*.bottomTabs`)
     * plays its `enter` to show or its `exit` to hide, or turns it off (`enabled: false`: at once); without that side
     * the slide of its height. Its end comes with the transition's ([rest]).
     */
    open fun transition(
        move: TabBarMotion.Move,
        shown: Boolean,
        animate: Boolean,
        side: BottomTabsEnterExitAnimationOptions? = null,
    ) {
        val layout = layout ?: return run { isShownTarget = shown }
        if (shown == isShownTarget && motion == null) return
        val rtl = layout.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val ride = TabBarMotion.ride(move, shown, rtl)
        val own = AnimationRules.barSide(side, shown)
        when {
            !animate || !layout.isAttachedToWindow -> setVisible(shown, false)
            AnimationRules.isTurnedOff(own) -> setVisible(shown, false)
            own != null && AnimationRules.sidePlays(own) -> playSide(layout, own, shown)
            ride != null -> ride(layout, ride, shown)
            move == TabBarMotion.Move.Slide -> setVisible(shown, true)
            else -> setVisible(shown, false)
        }
    }

    // The bar shows or hides by the side of the transition's own, on its container; at the end it rests as the
    // options put it.
    private fun playSide(layout: BottomTabsLayout, side: BottomTabsAnimationOptions, shown: Boolean) {
        val animator = SideAnimator.build(layout.container, side) ?: return setVisible(shown, true)
        stopMotion()
        isShownTarget = shown
        if (shown) {
            layout.bar.restoreBottomNavigation(false)
            layout.container.visibility = layout.bar.visibility
        }
        play(animator)
        onBarChanged?.invoke()
    }

    /**
     * The transition ended, or a newer one took the bar over: it rests where its options put it — no translation,
     * opaque, shown or `GONE` — so the next options can show or hide it.
     */
    open fun rest(shown: Boolean) {
        val layout = layout ?: return run { isShownTarget = shown }
        stopMotion()
        isShownTarget = shown
        showNow(layout, shown)
    }

    // The bar rides along x with the screens and fades after its hold; the stack's end brings it to rest.
    private fun ride(layout: BottomTabsLayout, ride: TabBarMotion.Ride, shown: Boolean) {
        stopMotion()
        isShownTarget = shown
        val container = layout.container
        if (shown) layout.bar.restoreBottomNavigation(false)
        val dp = activity.resources.displayMetrics.density
        container.translationX = ride.fromX * dp
        container.alpha = ride.fromAlpha
        val move = ObjectAnimator.ofFloat(container, View.TRANSLATION_X, ride.toX * dp).apply {
            duration = ride.duration
            interpolator = SystemTransition.CURVE
        }
        val fade = ObjectAnimator.ofFloat(container, View.ALPHA, ride.toAlpha).apply {
            startDelay = ride.alphaDelay
            duration = ride.alphaDuration
            interpolator = LinearInterpolator()
        }
        play(AnimatorSet().apply { playTogether(move, fade) })
        onBarChanged?.invoke()
    }

    private fun play(animator: Animator) {
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (motion !== animation) return
                motion = null
                if (!cancelled) layout?.let { showNow(it, isShownTarget) }
            }
        })
        motion = animator
        animator.start()
    }

    // A motion stops where it is; the bar rests after it.
    private fun stopMotion() {
        val running = motion ?: return
        motion = null
        running.cancel()
        layout?.container?.let(SideAnimator::rest)
    }

    private fun showNow(layout: BottomTabsLayout, shown: Boolean) {
        val container = layout.container
        SideAnimator.rest(container)
        if (shown) layout.bar.restoreBottomNavigation(false) else layout.bar.hideBottomNavigation(false)
        layout.container.visibility = layout.bar.visibility
        onBarChanged?.invoke()
    }

    // Light or dark changed, or new default options: the tabs apply again what holds a color.
    override fun onConfigurationChanged(controller: ViewController<*>, options: Options) {
        (controller as? BottomTabsController)?.repaint(options)
    }

    /** The look of the container drawn again: colors of the new mode. */
    open fun repaintBar(options: BottomTabsOptions, direction: LayoutDirection) {
        titleMode = null
        applyBar(options, direction)
    }

    private fun lookOf(options: BottomTabsOptions, layout: BottomTabsLayout): BottomTabsContainer.Look = TabBarLook.of(
        options = options,
        color = ::colorOf,
        defaultBackground = MaterialColors.getColor(
            layout.container.context,
            MaterialR.attr.colorSurfaceContainer,
            Color.WHITE,
        ),
        densityDpi = activity.resources.displayMetrics.densityDpi,
    )

    private fun colorOf(colour: ThemeColour): Int? = colour.get(activity, null)

    // Icons never throw: one that doesn't load leaves the item without it (without its selected icon, the item shows
    // its icon when selected). An item's first icon loads at once, its tab's first frame shows it; a change of it on
    // the bar that shows loads off the main thread when it can, and the item takes it when ready, unless a newer icon
    // of the item came first.
    private fun icon(bar: BottomTabs, index: Int, uri: String, selected: Boolean, isChange: Boolean) {
        icons.request(index to selected, uri, mayWait = isChange && bar.isAttachedToWindow) { icon ->
            val shown = layout?.bar ?: return@request
            if (selected) shown.setSelectedIcon(index, icon) else shown.setIcon(index, icon)
        }
    }

    private companion object {
        const val COMPACT = "compact"
        const val BAR_HEIGHT_DP = 80

        // fast_out_slow_in.
        val FAST_OUT_SLOW_IN = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }
}

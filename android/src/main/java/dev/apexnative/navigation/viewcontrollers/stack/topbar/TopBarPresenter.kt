package dev.apexnative.navigation.viewcontrollers.stack.topbar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowInsetsCompat
import dev.apexnative.navigation.R
import dev.apexnative.navigation.options.ButtonMenuOptions
import dev.apexnative.navigation.options.ButtonOptions
import dev.apexnative.navigation.options.FontOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.SearchBarOptions
import dev.apexnative.navigation.options.TitleComponentOptions
import dev.apexnative.navigation.options.TopBarAnimationOptions
import dev.apexnative.navigation.options.params.LayoutDirection
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.options.parsers.TypefaceLoader
import dev.apexnative.navigation.react.ReactView
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.react.events.ComponentType
import dev.apexnative.navigation.system.AppDirection
import dev.apexnative.navigation.utils.DevelopmentLog
import dev.apexnative.navigation.utils.ImageLoader
import dev.apexnative.navigation.utils.ImageLoadingListenerAdapter
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.UiUtils
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.navigator.BackCallbackState
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.BarMotion
import dev.apexnative.navigation.viewcontrollers.stack.search.StackSearchPresenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.IReactView
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.SideAnimator
import dev.apexnative.navigation.views.animations.SystemTransition
import dev.apexnative.navigation.views.component.ComponentLayout
import dev.apexnative.navigation.views.stack.StackLayout
import dev.apexnative.navigation.views.stack.topbar.LargeTitleView
import dev.apexnative.navigation.views.stack.topbar.TitleBar
import dev.apexnative.navigation.views.stack.topbar.TopBar
import dev.apexnative.navigation.views.stack.topbar.TopBarDefaults
import dev.apexnative.navigation.views.stack.topbar.titlebar.BarButtonComponentCell
import dev.apexnative.navigation.views.stack.topbar.titlebar.ButtonBar
import dev.apexnative.navigation.views.stack.topbar.titlebar.IconBackgroundDrawable

/**
 * What the bar of one stack shows: the options of the screen on top of the stack, resolved (a pair's options screen),
 * as it becomes the top ([apply], at once also without appearance events), at its `mergeOptions` ([merge]: the parts
 * the merge names, with the values resolved after it) and at a repaint ([repaint]: light/dark, `setDefaultOptions`). A
 * view property is set only when its value changed, so the second apply of the same screen (at its WillAppear) changes
 * nothing. It keeps the React components of the bar by screen ([components]), draws a pushed screen's title and
 * background in advance ([stage]), plays the row's part of the default transitions ([beginRowTransition]), shows and
 * hides the bar, follows the screen's list for the hide on scroll and the large title, and plugs in the search module
 * ([search]). Main thread only.
 */
internal class TopBarPresenter(private val activity: Activity, private val host: Host) {
    /** What the presenter asks of its stack. */
    interface Host {
        /** The stack's entries, bottom to top. */
        val entries: List<ViewController<*>>

        /** The screen whose options [entry] shows with: itself, or a pair's options screen. */
        fun optionsScreen(entry: ViewController<*>): ViewController<*>

        /** The options [entry] shows with. */
        fun resolvedOf(entry: ViewController<*>): Options

        /** The back button was pressed. */
        fun backPressed()

        /** The bar changed what the screen on top keeps at its top: its room and safe area are counted again. */
        fun roomChanged()
    }

    /** The search of the stack's screens (module). */
    val search = StackSearchPresenter(activity, ::loadIcon)

    /** The React components of the bar, by screen. */
    val components = BarComponents(::makeRoot)

    private var layout: StackLayout? = null
    private var bar: TopBar? = null
    private var staging: FrameLayout? = null
    private var leftButtons: ButtonBar? = null
    private var rightButtons: ButtonBar? = null
    private var visibility: TopBarVisibility? = null
    private var rowTransition: BarRowTransition? = null
    private var collapse: TopBarCollapseBehavior? = null
    private val largeTitleFollower = LargeTitleFollower()
    private val typefaces = TypefaceLoader(activity)
    // The cells of React buttons, by screen and `componentId`: a button of the stack's or the defaults' options has one
    // `componentId` on every screen, and each screen shows its own root in its own cell.
    private val cells = ButtonCells({ BarButtonComponentCell(activity) }) { it.content = null }

    // What shows now, and the keys of what was applied: a part applies again only when its key changed.
    private var shownEntry: ViewController<*>? = null
    private var shownScreen: ViewController<*>? = null
    private var shownState = Appearance.None
    private val keys = HashMap<Part, String>()
    private var shownBackground: BarComponents.Root? = null
    private var backgroundAppeared = false
    private var motion: BarMotion? = null
    // How the bar shows or hides in the transition that brought the current child.
    private var visibilityChange = StackDisplay.BarVisibility.AT_ONCE
    private var largeTitleShown = false
    private var followed: ScrollEventListener? = null
    private var rowTarget: RowTarget? = null
    // The picture of the part under the row in a leaving screen's overlay: the screen and the picture.
    private var underRow: Pair<ViewController<*>, Drawable>? = null

    private enum class Part { Look, Title, Left, Right, Back, LargeTitle, Visible, TestId }

    private enum class Appearance { None, Appearing, Appeared }

    /** The stack's view is made: the bar and the place for the components drawn in advance. */
    fun bind(layout: StackLayout) {
        this.layout = layout
        val bar = layout.topBar
        this.bar = bar
        leftButtons = ButtonBar(bar.leftButtonBar, true, ::loadIcon, Buttons())
        rightButtons = ButtonBar(bar.rightButtonBar, false, ::loadIcon, Buttons())
        visibility = TopBarVisibility(VisibilityTarget(bar) { collapse?.reset() })
        rowTransition = BarRowTransition(RowTarget(bar).also { rowTarget = it })
        collapse = TopBarCollapseBehavior(bar)
        bar.leftButtonBar.setNavigationOnClickListener { host.backPressed() }
        search.bindTopBar(bar)
        search.setOnActiveChanged {
            shownEntry?.let { entry -> applyHideOnScroll(host.resolvedOf(entry), entry) }
            BackCallbackState.update()
        }
        staging = Staging(activity).also {
            layout.addOverAll(it, androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
    }

    // The stack's current child and its transition.

    /**
     * The stack's current child changes to [incoming] ([motion]: the default transition that brings it, none at once):
     * the row's picture is taken before the bar takes the new screen's options. A push from a large title to a screen
     * without one makes the bar lower at once: the part under the row rides with the leaving screen as a picture in its
     * overlay, and the row's picture holds the row only. So does a pop back to a screen whose large title is collapsed.
     */
    fun beginRowTransition(
        motion: BarMotion?,
        incoming: ViewController<*>?,
        visibility: StackDisplay.BarVisibility = StackDisplay.BarVisibility.AT_ONCE,
    ) {
        this.motion = motion
        this.visibilityChange = visibility
        closeMenus()
        dropPictureUnderRow()
        val bar = bar
        val leaving = shownEntry
        val lowers = bar != null && bar.isLargeTitleShown && incoming != null && leaving != null &&
            lowersBar(motion, incoming, host.resolvedOf(incoming))
        rowTarget?.withLargeTitle = !lowers
        if (lowers) pictureUnderRow(bar, leaving)
        rowTransition?.begin(motion)
    }

    /** The transition that brought the current child ended: the row at rest, the picture under it gone. */
    fun endRowTransition() {
        motion = null
        visibilityChange = StackDisplay.BarVisibility.AT_ONCE
        rowTransition?.end()
        dropPictureUnderRow()
    }

    /**
     * Shows [entry]'s bar: every part from its resolved options; none clears the bar's screen. A part whose values
     * didn't change isn't set again.
     */
    fun apply(entry: ViewController<*>?) {
        val bar = bar ?: return
        if (entry == null) return clear()
        val screen = host.optionsScreen(entry)
        if (screen !== shownScreen) {
            if (shownScreen != null) closeMenus()
            shownScreen = screen
            shownEntry = entry
            shownState = Appearance.None
            keys.clear()
        }
        shownEntry = entry
        // The screen that left with the picture under the row shows again: the picture goes.
        if (underRow?.first === entry) dropPictureUnderRow()
        val options = host.resolvedOf(entry)
        applyAll(bar, entry, screen, options, merge = false)
    }

    /**
     * `mergeOptions` into the stack or its screen on top ([delta]): what the user sees of it changes now, with the
     * values resolved after it. What a merge into the screen keeps goes into its own options first ([writeOwn]).
     */
    fun merge(entry: ViewController<*>, delta: Options) {
        val bar = bar ?: return
        val screen = host.optionsScreen(entry)
        if (screen !== shownScreen) return apply(entry)
        val options = host.resolvedOf(entry)
        applyAll(bar, entry, screen, options, merge = true, delta = delta)
    }

    /**
     * Light or dark changed, or new default options: the bar takes the theme's colors and the options as they resolve
     * now.
     */
    fun repaint(entry: ViewController<*>?) {
        keys.clear()
        apply(entry)
        val options = entry?.let(host::resolvedOf) ?: return
        search.onConfigurationChanged(options.topBar.searchBar.tintColor)
    }

    /**
     * New window insets: the strip, the side insets and the places of the right buttons are counted again (rotation,
     * cutout); each applies only when it changed (the keys hold the strip, the sides and the places), and nothing else
     * of the bar (the search) applies again.
     */
    fun onInsets() {
        val bar = bar ?: return
        val entry = shownEntry ?: return
        val screen = shownScreen ?: return
        val options = host.resolvedOf(entry)
        applyLook(bar, entry, options)
        applyButtons(bar, screen, options, merge = false)
    }

    /**
     * The nodes of the stack now (their options screens): the bar's components of any other screen go — a node that
     * left the stack, also one destroyed without its view.
     */
    fun retainScreens(screens: Set<ViewController<*>>) {
        components.screens().filter { it !in screens }.forEach { screenDestroyed(it, null) }
    }

    // Readiness and staging.

    /**
     * Draws [entry]'s title and background in advance, in an invisible place in the stack's view, so React renders them
     * with the screen; the bar takes the same views when it shows it.
     */
    fun stage(entry: ViewController<*>) {
        val staging = staging ?: return
        val screen = host.optionsScreen(entry)
        val options = host.resolvedOf(entry)
        titleComponentOf(screen, options)?.let { comp ->
            components.title(screen, comp)?.let {
                place(staging, it.view.asView(), ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        components.background(screen, options.topBar.background.component)?.let {
            place(staging, it.view.asView(), ViewGroup.LayoutParams.MATCH_PARENT, stagedBarHeight(entry, options))
        }
        pruneStaging()
    }

    /** The components of [entry]'s bar have drawn (`StackController.areBarComponentsDrawn`). */
    fun isDrawn(entry: ViewController<*>): Boolean = components.isDrawn(host.optionsScreen(entry), host.resolvedOf(entry))

    /** Calls [listener] once the components of [entry]'s bar have drawn. */
    fun whenDrawn(entry: ViewController<*>, listener: () -> Unit) =
        components.whenDrawn(host.optionsScreen(entry), host.resolvedOf(entry), listener)

    // Appearance events of the bar's components.

    /** [screen] starts to appear: the components of its bar too, when the bar shows them. */
    fun screenWillAppear(screen: ViewController<*>) {
        if (screen !== shownScreen) return
        shownState = Appearance.Appearing
        tellComponents()
    }

    /** [screen] has appeared. */
    fun screenDidAppear(screen: ViewController<*>) {
        if (screen !== shownScreen) return
        shownState = Appearance.Appeared
        tellComponents()
    }

    /** [screen] disappeared: its title and buttons too; the background when the bar shows it no more. */
    fun screenDisappeared(screen: ViewController<*>) {
        components.titleOf(screen)?.view?.sendComponentStop(ComponentType.TopBarTitle)
        components.buttonsOf(screen).forEach { it.view.sendComponentStop(ComponentType.TopBarButton) }
        if (screen !== shownScreen) return
        shownState = Appearance.None
        shownBackground?.let {
            it.view.sendComponentStop(ComponentType.TopBarBackground)
            backgroundAppeared = false
        }
    }

    /** [screen] is destroyed: its components and its search go. */
    fun screenDestroyed(screen: ViewController<*>, entryView: View?) {
        if (screen === shownScreen) {
            shownScreen = null
            shownEntry = null
            keys.clear()
        }
        cells.screenDestroyed(screen)
        components.screenDestroyed(screen)
        pruneStaging()
        val background = shownBackground
        if (background != null && !components.holds(background)) {
            shownBackground = null
            backgroundAppeared = false
            bar?.setBackgroundComponent(null)
        }
        entryView?.let(search::onScreenDestroyed)
    }

    /** The stack is destroyed: every component stops. */
    fun destroy() {
        rowTransition?.end()
        largeTitleFollower.stop()
        collapse?.disableCollapse()
        components.destroyAll()
        cells.clear()
    }

    /** The search of the screen on top is open. */
    fun isSearchOpen(): Boolean = search.isActive()

    /** Back closes the open search first. */
    fun closeSearch(): Boolean = search.closeSearch()

    // The room.

    /** The bar's parts for [entry] with [options], in pixels. */
    fun barOf(entry: ViewController<*>, options: Options): TopBarRoom.Bar {
        val insets = insets()
        val inSheet = entry.isInSheet
        val topMargin = dp(options.topBar.topMargin.get(0.0))
        return TopBarRoom.Bar(
            strip = TopBarRoom.strip(options, insets, inSheet),
            margin = TopBarRoom.margin(options, insets, inSheet, topMargin),
            topMargin = topMargin,
            height = dp(options.topBar.height.get(DEFAULT_HEIGHT_DP)),
        )
    }

    /** The window's insets for the bar: the status bar's height and the cutout's at the top. */
    fun insets(): TopBarRoom.Insets {
        val insets = layout?.lastInsets ?: return TopBarRoom.Insets(0, 0)
        return TopBarRoom.Insets(
            insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top,
            insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top,
        )
    }

    /** The height of the search under the row of [entry] when it is the screen the bar shows; 0 otherwise. */
    fun searchHeightOf(entry: ViewController<*>): Int =
        if (entry === shownEntry) bar?.searchAreaHeight ?: 0 else 0

    /**
     * Where the bar's bottom is in its stack's view, at its place in the layout; none while it is hidden. A bar that
     * the large title's collapse, `hideOnScroll` or a slide moves still covers from there: no transform of the bar
     * counts, so the insets don't change as it moves and a screen padded with them doesn't jump.
     */
    fun barBottomInStack(): Int? {
        val bar = bar ?: return null
        if (bar.visibility != View.VISIBLE || !bar.isAttachedToWindow) return null
        return bar.top + bar.height
    }

    // The parts.

    private fun applyAll(
        bar: TopBar,
        entry: ViewController<*>,
        screen: ViewController<*>,
        options: Options,
        merge: Boolean,
        delta: Options? = null,
    ) {
        applyLook(bar, entry, options)
        applyTitle(bar, screen, options)
        // The search first: its magnifier takes a place among the right buttons. It gets a copy of the group: it
        // writes its tint into what it gets.
        val searchOptions = SearchBarOptions().apply { mergeWith(options.topBar.searchBar) }
        if (merge && delta != null) {
            if (delta.topBar.searchBar.hasAnyValue()) search.merge(entry, delta.topBar.searchBar, searchOptions)
        } else {
            search.apply(entry, searchOptions)
        }
        applyButtons(bar, screen, options, merge)
        applyBack(bar, options)
        applyBackground(bar, screen, options)
        applyLargeTitle(bar, entry, options)
        applyVisibility(options, merge)
        applyHideOnScroll(options, entry)
        // Every height change of this apply (the row, the strip, the large title, the search) in the same frame, so no
        // frame shows an empty band or a hole.
        bar.layoutNow()
        tellComponents()
    }

    // The bar's look.
    private fun applyLook(bar: TopBar, entry: ViewController<*>, options: Options) {
        val topBar = options.topBar
        val insets = layout?.lastInsets
        val sides = insets?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val room = barOf(entry, options)
        val color = topBar.background.color.get(bar.context, null) ?: TopBarDefaults.backgroundColor(bar.context)
        val borderColor = topBar.borderColor.get(bar.context, null) ?: Color.BLACK
        val direction = directionOf(options)
        val key = listOf(
            room.strip, room.margin, room.height, color, borderColor, topBar.borderHeight.get(0.0),
            topBar.elevation.get(0.0), dp(topBar.title.height.get(DEFAULT_HEIGHT_DP)),
            dp(topBar.title.topMargin.get(0.0)), sides?.left, sides?.right, direction, topBar.testID.get(null),
        ).joinToString()
        if (keys[Part.Look] == key) return
        keys[Part.Look] = key
        bar.layoutDirection = direction
        bar.topPadding = room.strip
        bar.setMargin(room.margin)
        bar.setBarHeight(room.height)
        bar.setRow(dp(topBar.title.height.get(DEFAULT_HEIGHT_DP)), dp(topBar.title.topMargin.get(0.0)))
        bar.titleBar.sideInsets(sides?.left ?: 0, sides?.right ?: 0)
        bar.setBarColor(color)
        bar.setShadow(UiUtils.dpToPx(bar.context, topBar.elevation.get(0.0).toFloat()))
        bar.setBorder(borderColor, dp(topBar.borderHeight.get(0.0)))
        TestIds.apply(bar, topBar.testID.get(null))
        // New window insets come before the window's measure; within an apply, applyAll lays the bar out at once.
        bar.requestLayout()
    }

    // The title (`topBar.title.*`,.subtitle.*).
    private fun applyTitle(bar: TopBar, screen: ViewController<*>, options: Options) {
        val title = options.topBar.title
        val subtitle = options.topBar.subtitle
        val component = titleComponentOf(screen, options)
        val context = bar.context
        val titleColor = title.color.get(context, null) ?: TopBarDefaults.titleColor(context)
        val subtitleColor = subtitle.color.get(context, null) ?: TopBarDefaults.subtitleColor(context)
        val testId = options.topBar.testID.get(null)
        val key = listOf(
            screen.id, component?.name?.get(null), component?.componentId?.get(null), component?.alignment?.get(null),
            title.text.get(null), title.fontSize.get(null), titleColor, fontKey(title.font), title.alignment.get(null),
            subtitle.text.get(null), subtitle.fontSize.get(null), subtitleColor, fontKey(subtitle.font),
            subtitle.alignment.get(null), testId,
        ).joinToString()
        if (keys[Part.Title] == key) return
        keys[Part.Title] = key
        val titleBar = bar.titleBar
        val root = components.title(screen, component ?: TitleComponentOptions())
        if (root != null && component != null) {
            titleBar.componentAlignment =
                if (component.alignment.get(null) == FILL) TitleBar.Alignment.Fill else TitleBar.Alignment.Center
            titleBar.titleComponent = root.view.asView()
            if (screen.options.topBar.title.text.hasValue() && screen.options.topBar.title.component.hasValue()) {
                DevelopmentLog.warn("the screen '${screen.id}' gives topBar.title a text and a component: the text " +
                    "and the subtitle are dropped")
            }
        } else {
            titleBar.titleComponent = null
        }
        val titleView = titleBar.titleView
        titleView.text = title.text.get(null).orEmpty()
        titleView.textSize = title.fontSize.get(DEFAULT_TITLE_SP).toFloat()
        titleView.setTextColor(titleColor)
        titleView.typeface = title.font.getTypeface(typefaces, titleBar.initialTitleTypeface)
        titleBar.titleAlignment = alignmentOf(title.alignment.get(null))
        val subtitleView = titleBar.subtitleView
        subtitleView.textSize = subtitle.fontSize.get(DEFAULT_SUBTITLE_SP).toFloat()
        subtitleView.setTextColor(subtitleColor)
        subtitleView.typeface = subtitle.font.getTypeface(typefaces, titleBar.initialSubtitleTypeface)
        titleBar.subtitleAlignment =
            if (subtitle.alignment.get(null) == CENTER) TitleBar.Alignment.Center else TitleBar.Alignment.Start
        titleBar.setSubtitle(subtitle.text.get(null))
        TestIds.apply(titleView, testId?.let { "$it.title" })
        TestIds.apply(subtitleView, testId?.let { "$it.subtitle" })
    }

    // The buttons.
    private fun applyButtons(bar: TopBar, screen: ViewController<*>, options: Options, merge: Boolean) {
        val topBar = options.topBar
        val context = bar.context
        val left = topBar.buttons.left.orEmpty().filter(::hasId).map {
            withSide(it, topBar.leftButtonColor, topBar.leftButtonDisabledColor, topBar.leftButtonBackgroundColor)
        }
        val right = topBar.buttons.right.orEmpty().filter(::hasId).map {
            withSide(it, topBar.rightButtonColor, topBar.rightButtonDisabledColor, topBar.rightButtonBackgroundColor)
        }
        val roots = components.buttons(screen, left + right)
        // The cells of React buttons removed by a merge go with their roots.
        cells.retain(screen, roots.keys)
        val leftKey = screen.id + left.joinToString { buttonKey(context, it) }
        if (keys[Part.Left] != leftKey) {
            keys[Part.Left] = leftKey
            reportWithoutId(topBar.buttons.left, "leftButtons")
            leftButtons?.set(
                left.map { button(screen, it, roots) },
                emptyList(),
                null,
                merge && topBar.animateLeftButtons.isTrue,
            )
        }
        val overflow = topBar.overflowMenu?.takeIf { it.items.isNotEmpty() }
        val menu = bar.rightButtonBar.menu
        search.applyMagnifier()
        val taken = (0 until menu.size()).count { menu.getItem(it).itemId != Menu.NONE }
        val configuration = context.resources.configuration
        val plan = ButtonPlaces.plan(
            right.map { ButtonPlaces.Button(ButtonPlaces.priorityOf(it.priority.get(null)), it.pinned.isTrue) },
            ButtonPlaces.maxActionButtons(
                configuration.screenWidthDp,
                configuration.screenHeightDp,
                configuration.smallestScreenWidthDp,
            ),
            taken,
            overflow?.items?.size ?: 0,
        )
        val overflowColor = topBar.rightButtonColor.get(context, null) ?: TopBarDefaults.overflowIconColor(context)
        val rightKey = listOf(
            screen.id, right.joinToString { buttonKey(context, it) }, plan.inBar, overflow?.let(::menuKey), taken,
            overflowColor,
        ).joinToString()
        if (keys[Part.Right] != rightKey) {
            keys[Part.Right] = rightKey
            reportWithoutId(topBar.buttons.right, "rightButtons")
            rightButtons?.set(
                plan.inBar.map { button(screen, right[it], roots) },
                plan.inOverflow.map { button(screen, right[it], roots) },
                overflow,
                merge && topBar.animateRightButtons.isTrue,
            )
            search.applyMagnifier()
            rightButtons?.setOverflowColor(overflowColor)
        }
    }

    // The back button (`topBar.backButton.*`).
    private fun applyBack(bar: TopBar, options: Options) {
        val back = options.topBar.buttons.back
        val leftWithId = options.topBar.buttons.left?.any { it.id.hasValue() } == true
        val shown = back.visible.isTrue && !leftWithId
        val context = bar.context
        val direction = directionOf(options)
        val color = back.color.get(context, null)
        val label = back.accessibilityLabel.get(null) ?: DEFAULT_BACK_LABEL
        val testId = back.testID.get(null) ?: BACK_BUTTON_TEST_ID
        val key = listOf(
            shown, back.icon.get(null), color, TopBarDefaults.navigationIconColor(context),
            back.iconBackground.color.get(context, null), back.iconBackground.cornerRadius.get(null),
            back.iconBackground.width.get(null), back.iconBackground.height.get(null), label, testId, direction,
        ).joinToString()
        if (keys[Part.Back] == key) return
        keys[Part.Back] = key
        val toolbar = bar.leftButtonBar
        if (!shown) {
            toolbar.navigationIcon = null
            return
        }
        val own = back.icon.get(null)?.let(::loadIcon)?.mutate()
        val icon = own ?: AppCompatResources.getDrawable(context, R.drawable.apex_navigation_ic_arrow_back)?.mutate()
            ?: return
        // The arrow always has a color; an own icon only with `color`.
        (color ?: if (own == null) TopBarDefaults.navigationIconColor(context) else null)?.let {
            DrawableCompat.setTint(icon, it)
        }
        // The bar's direction as the arrow takes it: `locale` is the device's language now.
        val arrowDirection = AppDirection.resolvedViewDirection(
            direction,
            AppDirection.isLocaleRtl(),
            ((context.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SUPPORTS_RTL) != 0,
        )
        DrawableCompat.setLayoutDirection(icon, arrowDirection)
        val background = back.iconBackground
        val drawable = background.color.get(context, null)?.let {
            IconBackgroundDrawable(
                icon,
                it,
                UiUtils.dpToPx(context, background.cornerRadius.get(0.0).toFloat()),
                dp(background.width.get(0.0)),
                dp(background.height.get(0.0)),
            )
        } ?: icon
        toolbar.navigationIcon = drawable
        // Taking the icon, the navigation button gives it its own direction, which is stale while the button is new
        // or the bar's direction has just changed; the toolbar corrects it only after the icon was drawn. So the arrow
        // gets the bar's direction at once, on the drawable the button shows; a background passes it to the arrow.
        val held = toolbar.navigationIcon ?: drawable
        DrawableCompat.setLayoutDirection(held, arrowDirection)
        if (held !== drawable) DrawableCompat.setLayoutDirection(drawable, arrowDirection)
        toolbar.navigationContentDescription = label
        // The toolbar uses its navigation button again from screen to screen: its id is set every time.
        for (index in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(index)
            if ((child as? ImageView)?.drawable === held) TestIds.apply(child, testId)
        }
    }

    // The background (`topBar.background.component`).
    private fun applyBackground(bar: TopBar, screen: ViewController<*>, options: Options) {
        val root = components.background(screen, options.topBar.background.component)
        if (root === shownBackground) return
        val old = shownBackground
        shownBackground = root
        if (old != null && backgroundAppeared) old.view.sendComponentStop(ComponentType.TopBarBackground)
        backgroundAppeared = false
        bar.setBackgroundComponent(root?.view?.asView())
    }

    // The large title. Whether the large title shows over [entry]: `automatic` as the entry below it.
    private fun largeTitleShownFor(entry: ViewController<*>, options: Options): Boolean {
        val entries = host.entries
        val chain = entries.indexOf(entry).takeIf { it >= 0 }?.let { entries.subList(0, it + 1) } ?: (entries + entry)
        val modes = chain.map { host.resolvedOf(it).topBar.largeTitle.mode() }
        return LargeTitleCollapse.isShown(modes, modes.size - 1, !options.topBar.visible.isFalse)
    }

    // Whether [incoming] shows less of the bar under the row than the large title now: a push to a screen without a
    // large title, or a pop back to one whose large title is collapsed. The lower bar would cut the row's picture.
    private fun lowersBar(motion: BarMotion?, incoming: ViewController<*>, options: Options): Boolean = when (motion) {
        BarMotion.Push -> !largeTitleShownFor(incoming, options)
        BarMotion.Pop -> largeTitleShownFor(incoming, options) && collapseOf(incoming, options) > 0f
        else -> false
    }

    // How far [entry]'s large title is collapsed as it shows: by the offset its list had last, unless it doesn't follow
    // the list.
    private fun collapseOf(entry: ViewController<*>, options: Options): Float =
        if (options.topBar.largeTitle.collapseOnScroll.isFalse) 0f else scrollOf(entry)?.lastOffset ?: 0f

    private fun pictureUnderRow(bar: TopBar, leaving: ViewController<*>) {
        val view = leaving.peekView() ?: return
        val (bitmap, top) = bar.pictureUnderRow() ?: return
        val barPlace = IntArray(2)
        val viewPlace = IntArray(2)
        bar.getLocationInWindow(barPlace)
        view.getLocationInWindow(viewPlace)
        val picture = BitmapDrawable(bar.resources, bitmap)
        val left = barPlace[0] - viewPlace[0]
        val y = barPlace[1] + top - viewPlace[1]
        picture.setBounds(left, y, left + bitmap.width, y + bitmap.height)
        view.overlay.add(picture)
        underRow = leaving to picture
    }

    private fun dropPictureUnderRow() {
        val (screen, picture) = underRow ?: return
        underRow = null
        screen.peekView()?.overlay?.remove(picture)
    }

    private fun applyLargeTitle(bar: TopBar, entry: ViewController<*>, options: Options) {
        val shown = largeTitleShownFor(entry, options) && !bar.searchTakesRow
        val large = options.topBar.largeTitle
        val color = TopBarDefaults.titleColor(bar.context)
        val testId = options.topBar.testID.get(null)
        val key = listOf(shown, options.topBar.title.text.get(null), large.color.get(bar.context, null), color,
            large.fontSize.get(null), fontKey(large.font), large.collapseOnScroll.get(null), testId).joinToString()
        if (keys[Part.LargeTitle] == key) return
        keys[Part.LargeTitle] = key
        largeTitleShown = shown
        bar.setLargeTitleShown(shown)
        largeTitleFollower.stop()
        if (!shown) {
            bar.titleBar.titleView.alpha = 1f
            bar.titleBar.titleComponent?.alpha = 1f
            scrollOf(entry)?.forgetOffset()
            return
        }
        val view = bar.largeTitle
        view.text = options.topBar.title.text.get(null).orEmpty()
        view.applyOptions(large, typefaces, color)
        TestIds.apply(view, testId?.let { "$it.largeTitle" })
        // Collapsed as the screen's list left it, before the bar's layout of this apply, so the bar is in place in the
        // same frame: back to the screen, after light or dark changed, after a merge.
        if (large.collapseOnScroll.isFalse) scrollOf(entry)?.forgetOffset()
        largeTitleFollower.collapse(collapseOf(entry, options))
        if (!large.collapseOnScroll.isFalse) scrollOf(entry)?.let(largeTitleFollower::follow)
    }

    // Shown or hidden: with the transition by its side, or by `animate` in a merge.
    private fun applyVisibility(options: Options, merge: Boolean) {
        val visible = !options.topBar.visible.isFalse
        val key = visible.toString()
        if (keys[Part.Visible] == key) return
        keys[Part.Visible] = key
        val change = visibilityChange
        val animated = !options.topBar.animate.isFalse && (merge || change.animated)
        visibility?.apply(visible, animated, if (merge) null else change.side)
    }

    // The hide on scroll: not under a large title, not while the search is open, not for a screen without a list to
    // follow (a native screen).
    private fun applyHideOnScroll(options: Options, entry: ViewController<*>) {
        val collapse = collapse ?: return
        val wanted = options.topBar.hideOnScroll.isTrue || options.topBar.searchBar.hideOnScroll.isTrue
        val listener = scrollOf(entry)
        if (wanted && !largeTitleShown && !search.isActive() && listener != null && !options.topBar.visible.isFalse) {
            collapse.enableCollapse(listener)
        } else {
            // A bar that hides by `visible: false` keeps sliding away; the show resets it.
            collapse.disableCollapse(reset = !options.topBar.visible.isFalse)
        }
    }

    // The appearance events of the components the bar shows for the screen on top: they go with the screen's own
    // moments; one set while the screen shows sends both at once. The dispatcher tells each appearance once.
    private fun tellComponents() {
        val screen = shownScreen ?: return
        val state = shownState
        if (state == Appearance.None) return
        val roots = ArrayList<Pair<IReactView, ComponentType>>()
        components.titleOf(screen)?.let { roots += it.view to ComponentType.TopBarTitle }
        components.buttonsOf(screen).forEach { roots += it.view to ComponentType.TopBarButton }
        shownBackground?.let { roots += it.view to ComponentType.TopBarBackground }
        for ((view, type) in roots) {
            view.sendComponentWillStart(type)
            if (state == Appearance.Appeared) view.sendComponentStart(type)
        }
        if (shownBackground != null) backgroundAppeared = true
    }

    private fun clear() {
        shownScreen = null
        shownEntry = null
        keys.clear()
        bar?.leftButtonBar?.navigationIcon = null
    }

    private fun closeMenus() {
        leftButtons?.closeMenus()
        rightButtons?.closeMenus()
    }

    /**
     * What a merge into [screen] writes into its own options: a side color into its own buttons, so it survives a new
     * appearance and a theme change; a merged title text takes the component out. A merge into the stack writes nothing
     * here: its color stays the stack's.
     */
    fun writeOwn(screen: ViewController<*>, delta: Options) {
        val topBar = delta.topBar
        val right = topBar.rightButtonColor.hasValue() || topBar.rightButtonDisabledColor.hasValue() ||
            topBar.rightButtonBackgroundColor.hasValue()
        val left = topBar.leftButtonColor.hasValue() || topBar.leftButtonDisabledColor.hasValue() ||
            topBar.leftButtonBackgroundColor.hasValue()
        val dropComponent = topBar.title.text.hasValue() && !topBar.title.component.hasValue() &&
            screen.options.topBar.title.component.hasValue()
        if (!right && !left && !dropComponent) return
        screen.changeOptions { own ->
            if (left) own.topBar.buttons.left?.forEach {
                sideInto(it, topBar.leftButtonColor, topBar.leftButtonDisabledColor, topBar.leftButtonBackgroundColor)
            }
            if (right) own.topBar.buttons.right?.forEach {
                sideInto(it, topBar.rightButtonColor, topBar.rightButtonDisabledColor, topBar.rightButtonBackgroundColor)
            }
            if (dropComponent) own.topBar.title.component = TitleComponentOptions()
        }
    }

    private fun sideInto(button: ButtonOptions, color: ThemeColour, disabled: ThemeColour, background: ThemeColour) {
        if (color.hasValue()) button.color = color
        if (disabled.hasValue()) button.disabledColor = disabled
        if (background.hasValue() && button.icon.hasValue()) button.iconBackground.color = background
    }

    // A copy of [button] with the side's colors where it has none of its own: the buttons of the options are shared by
    // many screens and never take them.
    private fun withSide(button: ButtonOptions, color: ThemeColour, disabled: ThemeColour, background: ThemeColour) =
        button.copy().also {
            if (!it.color.hasValue() && color.hasValue()) it.color = color
            if (!it.disabledColor.hasValue() && disabled.hasValue()) it.disabledColor = disabled
            if (!it.iconBackground.color.hasValue() && background.hasValue() && it.icon.hasValue()) {
                it.iconBackground.color = background
            }
        }

    // Only a button with an `id` is placed.
    private fun hasId(button: ButtonOptions): Boolean = button.id.get(null)?.isNotEmpty() == true

    // A button without an `id` is an error the developer sees (a red screen in development), each time the side's
    // buttons apply: at the screen's appearance and at a merge of them.
    private fun reportWithoutId(buttons: List<ButtonOptions>?, side: String) {
        if (buttons.orEmpty().all(::hasId)) return
        DevelopmentLog.error("a button of topBar.$side has no id: it is not placed")
    }

    private fun button(
        screen: ViewController<*>,
        options: ButtonOptions,
        roots: Map<String, BarComponents.Root>,
    ): ButtonBar.Button {
        val componentId = options.component.componentId.get(null) ?: options.component.id.get(null)
        val root = componentId?.takeIf { options.component.hasValue() }?.let(roots::get)
            ?: return ButtonBar.Button(options, null)
        val cell = cells.cellOf(screen, root.componentId)
        cell.content = root.view.asView()
        cell.contentWidth = options.component.width.get(null)?.let(::dp)
        cell.contentHeight = options.component.height.get(null)?.let(::dp)
        return ButtonBar.Button(options, cell)
    }

    // Which title component shows: the screen's own over the stack's over the defaults'; in one layer the component.
    private fun titleComponentOf(screen: ViewController<*>, options: Options): TitleComponentOptions? {
        val resolved = options.topBar.title
        if (!resolved.component.hasValue()) return null
        val own = screen.options.topBar.title
        if (own.text.hasValue() && !own.component.hasValue()) return null
        return resolved.component
    }

    private fun makeRoot(componentId: String, name: String): IReactView {
        // A component of the bar renders at once, without waiting for its layout, from before its surface starts.
        ScreenReadiness.setRendersAtOnce(componentId, true)
        // Another live root has this id (a component of the stack's options on two screens): what it rendered isn't
        // this root's, which answers by its own render.
        if (BarComponents.isLive(componentId)) ScreenReadiness.forgetRender(componentId)
        return ReactView(activity, componentId, name)
    }

    private fun loadIcon(uri: String): Drawable? {
        var loaded: Drawable? = null
        ImageLoader().loadIcon(activity, uri, object : ImageLoadingListenerAdapter() {
            override fun onComplete(drawable: Drawable) {
                loaded = drawable
            }
        })
        return loaded
    }

    private fun scrollOf(entry: ViewController<*>): ScrollEventListener? =
        (host.optionsScreen(entry).peekView() as? ComponentLayout)?.scrollEventListener

    // The views of stopped roots leave the place of the components drawn in advance.
    private fun pruneStaging() {
        val staging = staging ?: return
        for (index in staging.childCount - 1 downTo 0) {
            if ((staging.getChildAt(index) as? ReactView)?.isDestroyed == true) staging.removeViewAt(index)
        }
    }

    // Into the place of the components drawn in advance, unless it is somewhere already (the bar shows it).
    private fun place(staging: FrameLayout, view: View, width: Int, height: Int) {
        if (view.parent != null) return
        staging.addView(view, FrameLayout.LayoutParams(width, height))
    }

    // The height of a staged background: the new screen's bar with its strip.
    private fun stagedBarHeight(entry: ViewController<*>, options: Options): Int {
        val room = barOf(entry, options)
        return room.strip + room.height
    }

    // `layout.direction` of the options the bar shows with; without it React Native's direction, not left to right.
    private fun directionOf(options: Options): Int = when (options.layout.layoutDirection) {
        LayoutDirection.RTL -> View.LAYOUT_DIRECTION_RTL
        LayoutDirection.LOCALE -> View.LAYOUT_DIRECTION_LOCALE
        LayoutDirection.LTR -> View.LAYOUT_DIRECTION_LTR
        LayoutDirection.DEFAULT ->
            if (AppDirection.isReactNativeRtl(activity)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    }

    private fun alignmentOf(value: String?): TitleBar.Alignment = when (value) {
        CENTER -> TitleBar.Alignment.Center
        FILL -> TitleBar.Alignment.Fill
        else -> TitleBar.Alignment.Start
    }

    private fun dp(value: Double): Int = UiUtils.dpToPx(activity, value.toFloat()).toInt()

    // What makes a button's look, as a text: the parts that differ make it apply again.
    private fun buttonKey(context: Context, button: ButtonOptions): String = listOf(
        button.id.get(null), button.text.get(null), button.icon.get(null), button.systemItem.get(null),
        button.color.get(context, null), button.disabledColor.get(context, null), button.enabled.isFalse,
        button.allCaps.get(null), fontKey(button.font), button.fontSize.get(null), button.disableIconTint.isTrue,
        button.iconBackground.color.get(context, null), button.iconBackground.disabledColor.get(context, null),
        button.iconBackground.cornerRadius.get(null), button.iconBackground.width.get(null),
        button.iconBackground.height.get(null), button.accessibilityLabel.get(null), button.testID.get(null),
        button.component.name.get(null), button.component.componentId.get(null), button.component.width.get(null),
        button.component.height.get(null), button.menu != null, button.menuOnLongPress.isTrue, button.pinned.isTrue,
        button.priority.get(null),
    ).joinToString("|")

    private fun menuKey(menu: ButtonMenuOptions): String =
        menu.items.joinToString("|") { "${it.title}:${it.icon}:${it.testID}" } + menu.testID

    private fun fontKey(font: FontOptions): String =
        listOf(font.fontFamily, font.fontStyle, font.fontWeight).joinToString { it.get(null).toString() }

    // The buttons' presses and picks.
    private inner class Buttons : ButtonBar.Host {
        override fun pressed(buttonId: String) {
            val screen = shownScreen ?: return
            AppearanceDispatcher.navigationButtonPressed(screen, buttonId)
        }

        override fun picked(buttonId: String?, itemId: String) {
            val screen = shownScreen ?: return
            val entry = shownEntry ?: return
            MenuChecks.write(screen, host.resolvedOf(entry), buttonId, itemId)
            AppearanceDispatcher.navigationButtonPressed(screen, itemId)
        }

        override fun menuOf(buttonId: String): ButtonMenuOptions? {
            val entry = shownEntry ?: return null
            val buttons = host.resolvedOf(entry).topBar.buttons
            return (buttons.left.orEmpty() + buttons.right.orEmpty()).firstOrNull { it.id.get(null) == buttonId }?.menu
        }

        // The search's magnifier, the one item of the bar with an id of its own: `<topBar.searchBar.testID>.button`.
        override fun testIdOf(item: MenuItem): String? {
            if (item.itemId == Menu.NONE) return null
            val entry = shownEntry ?: return null
            return host.resolvedOf(entry).topBar.searchBar.testID.get(null)?.let { "$it.button" }
        }
    }

    // The large title follows the list of the screen on top: the bar moves up by the list's offset, the large title
    // fades and is cut under the row, the row's title comes in.
    private inner class LargeTitleFollower : ScrollEventListener.OnScrollListener, ScrollEventListener.OnDragListener {
        fun follow(listener: ScrollEventListener) {
            followed = listener
            listener.register(null, this, this)
        }

        fun stop() {
            followed?.unregister(this)
            followed = null
        }

        fun collapse(offset: Float) {
            val bar = bar ?: return
            val range = UiUtils.dpToPx(bar.context, LargeTitleView.HEIGHT)
            val fraction = LargeTitleCollapse.fraction(offset, range)
            bar.setLargeTitleCollapse(LargeTitleCollapse.shift(offset, range).toInt())
            bar.largeTitle.alpha = LargeTitleCollapse.largeTitleAlpha(fraction)
            val rowAlpha = LargeTitleCollapse.rowTitleAlpha(fraction)
            bar.titleBar.titleView.alpha = rowAlpha
            bar.titleBar.titleComponent?.alpha = rowAlpha
            val large = LargeTitleCollapse.largeTitleHasAccessibility(fraction)
            bar.largeTitle.importantForAccessibility =
                if (large) View.IMPORTANT_FOR_ACCESSIBILITY_YES else View.IMPORTANT_FOR_ACCESSIBILITY_NO
            bar.titleBar.titleView.importantForAccessibility =
                if (large) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        override fun onScrollOffset(offsetY: Float) {
            if (largeTitleShown) collapse(offsetY)
        }

        override fun onScrollUp(nextTranslation: Float) = Unit

        override fun onScrollDown(nextTranslation: Float) = Unit

        override fun onShow() = Unit

        override fun onHide() = Unit
    }

    // The bar's views for the row's transition.
    private class RowTarget(private val bar: TopBar) : BarRowTransition.Target {
        // The picture holds the large title too, unless the bar lowers.
        var withLargeTitle = true

        override fun takeSnapshot(): Boolean = bar.takeRowSnapshot(withLargeTitle)

        override fun dropSnapshot() = bar.dropRowSnapshot()

        override fun show(frame: RowTimeline.Frame, rtl: Boolean) {
            val density = bar.resources.displayMetrics.density * if (rtl) -1f else 1f
            bar.showRowFrame(frame.snapshotAlpha, frame.snapshotShift * density, frame.rowAlpha, frame.rowShift * density)
        }

        override fun rest() = bar.restRow()

        override val isRtl: Boolean
            get() = bar.layoutDirection == View.LAYOUT_DIRECTION_RTL

        override val curve: (Float) -> Float = SystemTransition.CURVE::getInterpolation

        override fun play(duration: Long, onFrame: (elapsed: Long) -> Unit): () -> Unit {
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                interpolator = LinearInterpolator()
                addUpdateListener { onFrame((it.animatedFraction * duration).toLong()) }
            }
            animator.start()
            return { animator.cancel() }
        }
    }

    // The bar's view for showing and hiding.
    private class VisibilityTarget(private val bar: TopBar, private val reset: () -> Unit) : TopBarVisibility.Target {
        override val hiddenOffset: Float
            get() = bar.hiddenOffset

        override var translationY: Float
            get() = bar.translationY
            set(value) {
                bar.translationY = value
            }

        override var isGone: Boolean
            get() = bar.visibility == View.GONE
            set(value) {
                bar.visibility = if (value) View.GONE else View.VISIBLE
            }

        override fun slide(to: Float, duration: Long, onEnd: () -> Unit): () -> Unit {
            val animator = bar.animate().translationY(to).setDuration(duration).setInterpolator(FAST_OUT_SLOW_IN)
                .setUpdateListener(null).withEndAction(onEnd)
            animator.start()
            return { bar.animate().cancel() }
        }

        // The side of the options on the bar's view; its end and its stop leave the bar at rest.
        override fun play(side: TopBarAnimationOptions, onEnd: () -> Unit): () -> Unit {
            bar.animate().cancel()
            val animator = SideAnimator.build(bar, side)
            if (animator == null) {
                onEnd()
                return {}
            }
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    SideAnimator.rest(bar)
                    onEnd()
                }
            })
            animator.start()
            return {
                animator.removeAllListeners()
                animator.cancel()
                SideAnimator.rest(bar)
            }
        }

        override fun resetCollapse() = reset()
    }

    // The invisible place of the components drawn in advance: in the window, so React renders them, without touches.
    private class Staging(context: Context) : FrameLayout(context) {
        init {
            alpha = 0f
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        override fun dispatchTouchEvent(ev: MotionEvent?): Boolean = false
    }

    private companion object {
        const val DEFAULT_HEIGHT_DP = 64.0
        const val DEFAULT_TITLE_SP = 22.0
        const val DEFAULT_SUBTITLE_SP = 14.0
        const val CENTER = "center"
        const val FILL = "fill"
        const val DEFAULT_BACK_LABEL = "Navigate Up"
        const val BACK_BUTTON_TEST_ID = "BackButton"

        // AndroidX's FastOutSlowInInterpolator, the same curve.
        val FAST_OUT_SLOW_IN = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }
}

// The search module's group has no check of its own: any of its fields set.
private fun SearchBarOptions.hasAnyValue(): Boolean = visible.hasValue() || focus.hasValue() ||
    hideOnScroll.hasValue() || hideTopBarOnFocus.hasValue() || obscuresBackgroundDuringPresentation.hasValue() ||
    backgroundColor.hasValue() || tintColor.hasValue() || placeholder.hasValue() || text.hasValue() ||
    placement.hasValue() || scopes != null || selectedScope.hasValue() || scopeBarActivation.hasValue() ||
    suggestions != null || resultsComponent.hasValue() || resultsActivation.hasValue()

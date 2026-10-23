package dev.apexnative.navigation.viewcontrollers.bottomtabs

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.app.Activity
import android.graphics.Color
import android.util.Log
import android.view.View
import com.google.android.material.color.MaterialColors
import dev.apexnative.navigation.NavigationActivity
import dev.apexnative.navigation.customrow.BottomTabsCustomRowOptions
import dev.apexnative.navigation.options.BottomTabOptions
import dev.apexnative.navigation.options.BottomTabsEnterExitAnimationOptions
import dev.apexnative.navigation.options.BottomTabsOptions
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.LayoutDirection
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.ReactView
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.system.AppDirection
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.utils.UiUtils
import dev.apexnative.navigation.utils.scrollToTop
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.component.RestPlace
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.navigator.BackCallbackState
import dev.apexnative.navigation.viewcontrollers.navigator.Navigator
import dev.apexnative.navigation.viewcontrollers.parent.ContainerBackground
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.stack.StackController
import dev.apexnative.navigation.viewcontrollers.stack.fab.FabPlacement
import dev.apexnative.navigation.viewcontrollers.stack.topbar.BarComponents
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.FadeThrough
import dev.apexnative.navigation.views.bottomtabs.BottomTabItem
import dev.apexnative.navigation.views.bottomtabs.BottomTabsLayout
import dev.apexnative.navigation.views.bottomtabs.CustomBottomTabItemView
import dev.apexnative.navigation.views.bottomtabs.CustomTabRow
import dev.apexnative.navigation.views.component.ComponentLayout

/**
 * Bottom tabs on Android: a layout per tab, in the order of the layout, the selected one shown over the Material
 * navigation bar of the tabs module ([BottomTabsLayout]). The core owns the selection: a tap on the bar or a cell of
 * the custom row comes here, which sends the tabs' events and selects silently; a choice from code is a `mergeOptions`
 * with `bottomTabs.currentTabIndex` or `currentTabId`; back goes to the selected tab, then by
 * `hardwareBackButton.bottomTabsOnPress`. The views of the tabs join as `tabsAttachMode` says; the bar shows with the
 * options of the screen the selected tab shows ([BottomTabsPresenter]), its items with their tabs' ([TabItems]); the
 * screens keep their room for it ([TabsRoom]). Every index is in layout order. Main thread only.
 */
class BottomTabsController(
    activity: Activity,
    childRegistry: ChildControllersRegistry,
    id: String,
    presenter: Presenter,
    initialOptions: Options,
    private val tabs: List<ViewController<*>>,
) : ParentController<BottomTabsLayout>(activity, childRegistry, id, presenter, initialOptions) {
    /** The index of the selected tab, in layout order; the navigation state reads it. */
    var selectedIndex = 0
        private set

    private val bar: BottomTabsPresenter = presenter as? BottomTabsPresenter ?: BottomTabsPresenter(activity)
    private val attachMode: TabsAttach.Mode
    private val usesRow: Boolean
    private val history = TabHistory()
    private val collapse = BottomTabsCollapseBehavior { peekView()?.container }
    private var cells: List<CustomBottomTabItemView> = emptyList()
    // `ANN.BottomTabSelected` of a press that waits for the new tab's screen to appear.
    private var pendingSelected: (() -> Unit)? = null
    // Ends the fade through of a switch still running, with its events.
    private var endSwitch: (() -> Unit)? = null

    // The made tabs that didn't show at the last repaint (new default options, a light/dark switch): each is repainted
    // as it is selected, not while hidden.
    private val staleTabs = mutableSetOf<ViewController<*>>()
    // The bar goes with a stack's transition: the options of the new top don't move it themselves.
    private var riding = false
    private var restAttached = false

    private val navigator: Navigator?
        get() = (activity as? NavigationActivity)?.navigator

    init {
        tabs.forEach { it.parentController = this }
        val defaults = DefaultOptions.current
        attachMode = TabsAttach.modeOf(resolveCurrentOptions(defaults).bottomTabs.tabsAttachMode)
        // The first tab, from the layout's own options only.
        when (val choice = TabSelection.choice(initialOptions.bottomTabs, tabs.size, ::indexOfId)) {
            is TabSelection.Choice.Tab -> selectedIndex = choice.index
            is TabSelection.Choice.OutOfRange ->
                Log.w(NavigationLog.TAG, TabSelection.outOfRangeText(choice.index, tabs.size))
            TabSelection.Choice.None -> Unit
        }
        // Cells drawn by components, decided once.
        val components = tabs.count { it.resolveShownOptions(defaults).bottomTab.component.name.hasValue() }
        usesRow = TabBarLook.usesRow(components, tabs.size)
        TabBarLook.mixedWarning(components, tabs.size)?.let { Log.w(NavigationLog.TAG, it) }
        bar.itemTestIds = !usesRow
        bar.onBarChanged = ::barChanged
    }

    override fun getChildControllers(): List<ViewController<*>> = tabs

    override fun getCurrentChild(): ViewController<*>? = tabs.getOrNull(selectedIndex)

    // A root of tabs waits for the selected tab's screens and the bar of its stack; with `together` also for the
    // content of the other tabs, within 0.5 s.
    override fun waitedChildren(): List<ViewController<*>> = listOfNotNull(currentChild)

    override fun waitedUnseenChildren(): List<ViewController<*>> =
        TabsAttach.unseenWaited(attachMode, selectedIndex, tabs.size).map(tabs::get)

    // The view of the tabs: the tabs of the attach mode join, the selected one shows; the items and the bar take their
    // options; the cells and the row when every tab has a component.
    override fun createView(): BottomTabsLayout {
        val layout = BottomTabsLayout(activity)
        bar.bind(layout)
        layout.onInsets = bar::insetsChanged
        // The bar measured another height than the one the screens counted: their room follows, after this pass.
        layout.container.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) layout.post(::barChanged)
        }
        makeItems(layout)
        layout.bar.setOnTabSelectedListener { index, _ ->
            onTabPressed(index)
            false
        }
        layout.bar.setOnTabLongPressedListener(::onTabLongPressed)
        if (usesRow) makeRow(layout)
        tabViews = LayoutTabViews(layout)
        attachAtCreation()
        val shown = resolveShownOptions(DefaultOptions.current)
        bar.applyBar(shown.bottomTabs, directionOf(shown))
        // The first show without animation: a bar the first screen hides never shows.
        bar.setVisible(!shown.bottomTabs.visible.isFalse, false)
        return layout
    }

    private fun makeItems(layout: BottomTabsLayout) {
        val options = tabs.indices.map(::itemOptions)
        // Under the custom row the hidden items keep no testID: the row's places carry them.
        layout.bar.addItems(
            options.map { BottomTabItem(it.text.get(""), null, null, if (usesRow) null else it.testID.get(null)) },
        )
        bar.setAnyIcon(options.any { it.icon.hasValue() })
        options.forEachIndexed { index, it -> bar.run(bar.items.create(index, it)) }
        layout.bar.setCurrentItem(selectedIndex, false)
    }

    /** Where the views of the tabs join: the tabs' view, once it is made; the unit tests give their own. */
    internal interface TabViews {
        /** The view of [tab] is in the tabs' view. */
        fun holds(tab: ViewController<*>): Boolean

        /** The view of [tab] joins, or is there already: [visible], or invisible but laid out. */
        fun attach(tab: ViewController<*>, visible: Boolean)

        /**
         * The view of [shown] becomes visible and that of [hidden] invisible: at once, or with Material's fade through
         * when [animated]; then [onEnd]. The function returned ends a switch still running at once, [onEnd] included.
         */
        fun switch(shown: ViewController<*>, hidden: ViewController<*>, animated: Boolean, onEnd: () -> Unit): () -> Unit
    }

    /** The views of the tabs; none before the tabs' view is made. */
    internal var tabViews: TabViews? = null

    // The views of the tabs in the tabs' view.
    private class LayoutTabViews(private val layout: BottomTabsLayout) : TabViews {
        override fun holds(tab: ViewController<*>): Boolean = layout.holdsTab(tab.peekView())

        override fun attach(tab: ViewController<*>, visible: Boolean) {
            if (!holds(tab)) layout.addTab(tab.view)
            tab.view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        }

        override fun switch(
            shown: ViewController<*>,
            hidden: ViewController<*>,
            animated: Boolean,
            onEnd: () -> Unit,
        ): () -> Unit {
            val coming = shown.view
            coming.visibility = View.VISIBLE
            val going = hidden.peekView()
            if (!animated || going == null) {
                going?.visibility = View.INVISIBLE
                onEnd()
                return {}
            }
            val exit = FadeThrough.exit(going)
            // Faded out, the tab that goes takes no more touches and no accessibility focus.
            exit.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    going.visibility = View.INVISIBLE
                }
            })
            val set = AnimatorSet()
            set.playTogether(exit, FadeThrough.enter(coming))
            set.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    set.removeAllListeners()
                    // The tab that went keeps its look for its next selection; only its visibility hides it.
                    going.visibility = View.INVISIBLE
                    going.alpha = 1f
                    coming.alpha = 1f
                    coming.scaleX = 1f
                    coming.scaleY = 1f
                    onEnd()
                }
            })
            set.start()
            return set::end
        }
    }

    /** The tabs of the attach mode join as the tabs' view is made. */
    internal fun attachAtCreation() = TabsAttach.atCreation(attachMode, selectedIndex, tabs.size).forEach(::attach)

    // The view of the tab at [index] joins the tabs' view, visible when it is the selected one (once).
    private fun attach(index: Int) {
        val tab = tabs[index]
        if (tab.isDestroyed) return
        tabViews?.attach(tab, index == selectedIndex)
    }

    // Options.

    // The options of the tab at [index] its item shows: its shown screen's over its own, the tabs' and the defaults.
    private fun itemOptions(index: Int): BottomTabOptions =
        tabs[index].resolveShownOptions(DefaultOptions.current).bottomTab

    // The screen the selected tab shows appears: the bar, its room and its scroll follow its options; its tab's item
    // its look. What isn't the tabs' goes on up.
    override fun applyChildOptions(options: Options, child: ViewController<*>) {
        if (child !== currentChild) return
        ContainerBackground.apply(this, options)
        bar.applyBar(options.bottomTabs, directionOf(options))
        if (!riding) bar.setVisible(!options.bottomTabs.visible.isFalse, false)
        bar.run(bar.items.look(selectedIndex, itemOptions(selectedIndex)))
        updateCollapse()
        roomChanged()
        performOnParentController { it.applyChildOptions(withoutBottomTabs(options), this) }
    }

    // `mergeOptions` with the tabs' id: their own options, then what they show, then the layouts around without
    // `bottomTabs`; the one-shot options act once, also while the view isn't shown.
    override fun mergeOptions(options: Options) {
        mergeOwnOptions(options)
        applyMerge(options, null)
        performOnParentController { it.mergeChildOptions(withoutBottomTabs(options), this) }
    }

    // `mergeOptions` of a tab or a screen in it: its tab's item at once, a choice from code, the bar when it is the
    // selected tab.
    override fun mergeChildOptions(options: Options, child: ViewController<*>) {
        applyMerge(options, child)
        performOnParentController { it.mergeChildOptions(withoutBottomTabs(options), this) }
    }

    private fun applyMerge(options: Options, child: ViewController<*>?) {
        if (isDestroyed) return
        chooseFromCode(options.bottomTabs)
        if (options.bottomTab.hasValue()) {
            // Into the tabs' own options: under every tab's own, as they resolve; into a tab or a screen of it: the
            // merge's fields over its tab's.
            val indexes = if (child == null) tabs.indices.toList() else listOf(tabs.indexOf(child))
            indexes.filter { it >= 0 }.forEach { mergeItem(it, options.bottomTab, overlay = child != null) }
        }
        if (child != null && child !== currentChild) return
        if (ContainerBackground.isTouchedBy(options)) {
            ContainerBackground.apply(this, resolveShownOptions(DefaultOptions.current))
        }
        val merged = options.bottomTabs
        if (!merged.hasValue()) return
        val shown = resolveShownOptions(DefaultOptions.current)
        bar.applyBar(shown.bottomTabs, directionOf(shown))
        if (merged.visible.hasValue() && !riding) {
            bar.setVisible(!shown.bottomTabs.visible.isFalse, !shown.bottomTabs.animate.isFalse && isOnScreen)
        }
        if (merged.customRow.hasValue()) peekView()?.bar?.customRowOptions = shown.bottomTabs.customRow.copy()
        if (merged.hideOnScroll.hasValue() || merged.visible.hasValue()) updateCollapse()
        roomChanged()
    }

    // A merge's `bottomTab` into the tab at [index]: the fields it names, its badge and dot held.
    private fun mergeItem(index: Int, merge: BottomTabOptions, overlay: Boolean) {
        val resolved = if (overlay) itemOptions(index).copy().also { it.mergeWith(merge) } else itemOptions(index)
        bar.run(bar.items.merge(index, merge, resolved))
        if (merge.badge.hasValue() || merge.dotIndicator.hasValue()) cells.getOrNull(index)?.update(cellState(index))
        // The cell's place in the row carries the tab's testID.
        if (merge.testID.hasValue()) peekView()?.row?.setTestId(index, resolved.testID.get(null))
    }

    // The choice from code: from the merge's own options, never from resolved ones.
    private fun chooseFromCode(options: BottomTabsOptions) {
        when (val choice = TabSelection.choice(options, tabs.size, ::indexOfId)) {
            is TabSelection.Choice.Tab -> {
                flushPendingSelected()
                select(choice.index, How.Code)
            }
            is TabSelection.Choice.OutOfRange ->
                Log.w(NavigationLog.TAG, TabSelection.outOfRangeText(choice.index, tabs.size))
            TabSelection.Choice.None -> Unit
        }
    }

    // Selection.

    private enum class How { Press, Code, Back }

    /**
     * A tap on the tab at [index], on the bar or on its cell of the custom row, the one path of both: a switch still
     * running ends first, with its `ANN.BottomTabSelected`; then this press's `ANN.BottomTabPressed`, then the
     * selection (`ANN.BottomTabSelected` after the new screen's DidAppear), the repeat (the pop's own events), or
     * nothing, as its resolved options say. The same order as `-[ANNBottomTabsController pressTabAtIndex:source:]`.
     */
    internal fun onTabPressed(index: Int) {
        if (isDestroyed || index !in tabs.indices) return
        flushPendingSelected()
        val tab = tabs[index]
        val wasSelected = index == selectedIndex
        AppearanceDispatcher.bottomTabPressed(this, index, screenOf(tab)?.id, wasSelected)
        val options = itemOptions(index)
        when (TabSelection.press(wasSelected, !options.selectTabOnPress.isFalse, !options.popToRootOnPress.isFalse)) {
            TabSelection.Action.Select -> select(index, How.Press)
            // The row keeps showing the tab the core selected.
            TabSelection.Action.Stay -> peekView()?.bar?.onCustomItemViewSelectionChanged(selectedIndex)
            TabSelection.Action.Repeat -> repeat(tab)
            TabSelection.Action.Nothing -> Unit
        }
    }

    /** A long press on the tab at [index]: its event only, nothing is selected. */
    internal fun onTabLongPressed(index: Int) {
        if (isDestroyed || index !in tabs.indices) return
        flushPendingSelected()
        AppearanceDispatcher.bottomTabLongPressed(this, index)
    }

    // The selected tab again: its stack back to its first screen, the screen that holds it holding it; on the first
    // screen, its content scrolls to the top.
    private fun repeat(tab: ViewController<*>) {
        if ((tab as? StackController)?.popToFirstByUser() == true) return
        screenOf(tab)?.peekView()?.let(::scrollToTop)
    }

    /**
     * Selects the tab at [index]: the bar shows it at once; on screen the tab it left fades through to it (Material's
     * fade through), off screen one container becomes invisible and the other visible at once; then [switched]. The
     * history of back follows.
     */
    private fun select(index: Int, how: How) {
        if (index == selectedIndex || index !in tabs.indices || isDestroyed) return
        val previous = selectedIndex
        val left = tabs[previous]
        val shown = tabs[index]
        if (how != How.Back && backMode() == PREVIOUS) history.left(previous)
        val views = tabViews
        val onScreen = isOnScreen
        if (riding) {
            riding = false
            bar.rest(bar.isShownTarget)
        }
        selectedIndex = index
        attach(index)
        // A tab hidden at a repaint takes it now, before its screens appear: its bar's parts, its search's and its
        // FAB's colors follow the theme and the defaults only through a repaint.
        if (staleTabs.remove(shown)) Navigator.repaintLayer(shown)
        peekView()?.bar?.let {
            it.setCurrentItem(index, false)
            it.onCustomItemViewSelectionChanged(index)
        }
        // A layer that waits for the tabs waits for the tab they show now.
        navigator?.shownScreensChanged(this)
        if (onScreen) AppearanceDispatcher.beginTransition(listOf(shown))
        // From code and by back the state goes at once, as on iOS: `selectTab` resolves on it, and the command after
        // it must act in this tab, not in the one still fading out.
        if (how != How.Press) AppearanceDispatcher.reportState()
        if (views == null) return switched(index, previous, how, onScreen, hasViews = false)
        endSwitch = views.switch(shown, left, animated = onScreen) {
            endSwitch = null
            if (!isDestroyed) switched(index, previous, how, onScreen, hasViews = true)
        }
    }

    /**
     * The switch to the tab at [index] from [previous] ended: what went disappears, what came appears — unless the tabs
     * stopped showing during the fade (a modal over them): it appears when they show again; then for a press
     * `ANN.BottomTabSelected` right after the new screen's DidAppear and the left tab's `popToRootOnBlur`, with the state
     * as one update after them; from code and by back, `popToRootOnBlur` and the state, which went at the start too
     * ([select]). [onScreen]: the switch started on screen.
     */
    private fun switched(index: Int, previous: Int, how: How, onScreen: Boolean, hasViews: Boolean) {
        val left = tabs[previous]
        val shown = tabs[index]
        if (onScreen) {
            val showing = if (isOnScreen) listOf(shown) else emptyList()
            AppearanceDispatcher.endTransition(listOf(left), showing, null, null, null, reportsState = false)
        } else if (hasViews) {
            // Not on screen: the bar takes the new tab's options as they are, without appearance events.
            val options = resolveShownOptions(DefaultOptions.current)
            bar.applyBar(options.bottomTabs, directionOf(options))
            bar.setVisible(!options.bottomTabs.visible.isFalse, false)
            roomChanged()
        }
        BackCallbackState.update()
        if (how != How.Press) {
            popToRootOnBlur(left)
            AppearanceDispatcher.reportState()
            return
        }
        lateinit var finish: () -> Unit
        finish = {
            if (pendingSelected === finish) {
                pendingSelected = null
                AppearanceDispatcher.bottomTabSelected(this, index, previous)
                popToRootOnBlur(left)
                AppearanceDispatcher.state.setNeedsUpdate()
            }
        }
        pendingSelected = finish
        val screen = screenOf(shown)
        if (screen == null) finish() else AppearanceDispatcher.afterDidAppear(screen, finish)
    }

    // A switch still running ends at once and a press's `ANN.BottomTabSelected` still waiting goes, before anything
    // newer on these tabs.
    private fun flushPendingSelected() {
        endSwitch?.invoke()
        pendingSelected?.invoke()
    }

    // `bottomTab.popToRootOnBlur` of the tab that was left, read now: its stack, hidden already, back to its first
    // screen without animation and appearance events.
    private fun popToRootOnBlur(left: ViewController<*>) {
        if (left.isDestroyed) return
        if (!left.resolveShownOptions(DefaultOptions.current).bottomTab.popToRootOnBlur.isTrue) return
        (left as? StackController)?.popToFirstSilently()
    }

    // Back (`hardwareBackButton.bottomTabsOnPress`).

    // The selected tab first; then the option of the shown screen over the tabs and the defaults.
    override fun handleBack(listener: CommandListener): Boolean {
        if (getCurrentChild()?.handleBack(listener) == true) return true
        val target = when (backMode()) {
            PREVIOUS -> history.takeFor(selectedIndex)
            FIRST -> 0.takeIf { selectedIndex != 0 }
            else -> null
        } ?: return false
        flushPendingSelected()
        select(target, How.Back)
        return true
    }

    // The mirror of handleBack, without copying options and without a view (patches 1118, 1121).
    override fun canHandleBack(): Boolean {
        if (getCurrentChild()?.canHandleBack() == true) return true
        return when (backMode()) {
            PREVIOUS -> history.hasOtherThan(selectedIndex)
            FIRST -> selectedIndex != 0
            else -> false
        }
    }

    private fun backMode(): String? =
        resolveShownOptions(DefaultOptions.current).hardwareBackButton.bottomTabsOnPress.get(null)

    // Appearance: the screens of the selected tab (ParentController), and the cells with the tabs. With
    // `afterInitialTab` the other tabs join once the first one appeared.

    override fun onViewWillAppear() {
        super.onViewWillAppear()
        cells.forEach { it.willAppear() }
    }

    override fun onViewDidAppear() {
        super.onViewDidAppear()
        cells.forEach { it.didAppear() }
        if (restAttached || tabViews == null) return
        restAttached = true
        TabsAttach.afterFirstAppearance(attachMode, tabs.size).forEach(::attach)
    }

    override fun onViewDisappear() {
        super.onViewDisappear()
        cells.forEach { it.didDisappear() }
    }

    /**
     * Whether the tabs show: in the window and not in a layer that waits, so their screens appear as the selection
     * changes. The unit tests give their own.
     */
    internal var shows: () -> Boolean = { isViewShown && navigator?.isInWaitingLayer(this) != true }

    private val isOnScreen: Boolean
        get() = shows()

    // The bar and stacks' transitions.

    override fun tabBarTransition(
        child: ViewController<*>,
        move: TabBarMotion.Move,
        side: BottomTabsEnterExitAnimationOptions?,
    ) {
        if (child !== currentChild || !isOnScreen) return
        val options = resolveShownOptions(DefaultOptions.current).bottomTabs
        val shown = !options.visible.isFalse
        if (shown == bar.isShownTarget && !riding) return
        riding = move != TabBarMotion.Move.AtOnce
        bar.transition(move, shown, !options.animate.isFalse, side)
    }

    override fun tabBarSettled(child: ViewController<*>) {
        if (child !== currentChild || !riding) return
        riding = false
        bar.rest(!resolveShownOptions(DefaultOptions.current).bottomTabs.visible.isFalse)
        updateCollapse()
        roomChanged()
    }

    // `bottomTabs.hideOnScroll` of the shown screen: the scroll of that screen, another screen's replacing it; off, the
    // bar shows again.
    private fun updateCollapse() {
        val options = resolveShownOptions(DefaultOptions.current).bottomTabs
        if (!options.hideOnScroll.isTrue || options.visible.isFalse) return collapse.disableCollapse()
        val screen = currentChild?.let(::screenOf)
        collapse.enableCollapse((screen?.peekView() as? ComponentLayout)?.scrollEventListener)
    }

    // The room of the screens.

    override fun getBottomInset(child: ViewController<*>, screen: ViewController<*>): Int {
        val around = parentController?.getBottomInset(this, screen) ?: 0
        if (peekView() == null) return around
        return TabsRoom.bottomInset(room(), screenOptions(screen), around)
    }

    override fun tabsTopOf(child: ViewController<*>, screen: ViewController<*>): Int? {
        val layout = peekView() ?: return super.tabsTopOf(child, screen)
        if (layout.height == 0) return super.tabsTopOf(child, screen)
        // The tabs' place at rest, as the screen's own: a modal sliding in or a sheet dragged doesn't move it.
        val bottom = RestPlace.topInWindow(layout) + layout.height
        return TabsRoom.tabsTop(room(), screenOptions(screen), bottom)
    }

    override fun leavesBottomEdgeFree(child: ViewController<*>, screen: ViewController<*>): Boolean =
        TabsRoom.leavesBottomEdgeFree(screenOptions(screen)) || super.leavesBottomEdgeFree(child, screen)

    override fun tabsAtRestOf(child: ViewController<*>, screen: ViewController<*>): FabPlacement.Tabs? {
        if (peekView() == null) return super.tabsAtRestOf(child, screen)
        return TabsRoom.fabTabs(room(), screenOptions(screen))
    }

    // A made tab that isn't selected lays out invisible: its screens measure their safe area.
    override fun measuresThoughNotShown(child: ViewController<*>): Boolean =
        child !== currentChild && tabViews?.holds(child) == true

    private fun screenOptions(screen: ViewController<*>): TabsRoom.Screen {
        val options = screen.resolveShownOptions(DefaultOptions.current).bottomTabs
        return TabsRoom.Screen(
            hidden = options.visible.isFalse,
            drawBehind = options.drawBehind.isTrue,
            hideOnScroll = options.hideOnScroll.isTrue,
        )
    }

    // The bar as its options set it: 80 dp or as it measured, the navigation bar, `layoutStyle`, `bottomMargin`, and
    // what the custom row covers above it.
    private fun room(): TabsRoom.Bar {
        val layout = peekView()
        val height = layout?.bar?.height?.takeIf { it > 0 } ?: UiUtils.dpToPx(activity, BAR_HEIGHT_DP)
        val overshoot = if (usesRow) rowOvershoot(height) else 0
        return TabsRoom.Bar(height, layout?.navigationBarInset ?: 0, bar.isCompact, bar.bottomMargin, overshoot)
    }

    private fun rowOvershoot(barHeight: Int): Int {
        val options = peekView()?.bar?.customRowOptions ?: return 0
        val height = options.height.get(null)?.let { UiUtils.dpToPixels(activity, it) }
        val extra = UiUtils.dpToPixels(activity, CustomTabRowGeometry.EXTRA_HEIGHT_DP.toDouble())
        val content = CustomTabRowGeometry.content(barHeight, height, extra)
        val bottomMargin = UiUtils.dpToPixels(activity, options.bottomMargin.get(0.0))
        return CustomTabRowGeometry.overshoot(barHeight, content, bottomMargin)
    }

    // The bar's place or visibility changed: the screens' room, their safe area and the FABs follow.
    private fun barChanged() {
        if (!isDestroyed) roomChanged()
    }

    private fun roomChanged() {
        for (tab in tabs) {
            if (!tab.isViewCreated || tab.isDestroyed) continue
            val screen = screenOf(tab) ?: continue
            screen.applyBottomInset()
            (screen as? ComponentViewController)?.updateSafeArea()
            (tab as? StackController)?.tabBarChanged()
        }
    }

    // The repaint: the bar, the items of every tab, the row. Only what shows is repainted (the pass over what shows
    // reaches the selected tab); a made tab that doesn't show is repainted when it is selected ([staleTabs]).
    internal fun repaint(options: Options) {
        if (isDestroyed) return
        ContainerBackground.apply(this, options)
        bar.repaintBar(options.bottomTabs, directionOf(options))
        tabs.indices.forEach { bar.run(bar.items.repaint(it, itemOptions(it))) }
        peekView()?.let { layout ->
            if (usesRow) layout.bar.customRowOptions = options.bottomTabs.customRow.copy()
        }
        cells.forEachIndexed { index, cell -> cell.update(cellState(index)) }
        tabs.forEach { if (it !== currentChild && it.isViewCreated) staleTabs += it }
    }

    // The custom row: the cells first into the bar's external host, then the row holds them; its look follows the tabs'
    // resolved `customRow`.
    private fun makeRow(layout: BottomTabsLayout) {
        val made = tabs.indices.map(::makeCell)
        cells = made
        layout.bar.setExternalCustomItemViewHost(true)
        layout.bar.setCustomItemViews(made)
        val row = CustomTabRow(activity)
        row.hold(made, tabs.indices.map { itemOptions(it).testID.get(null) })
        row.onPress = ::onTabPressed
        row.onLongPress = ::onTabLongPressed
        row.hiddenFromAccessibility = { navigator?.isInWaitingLayer(this) == true }
        layout.row = row
        layout.bar.customRowOptionsListener = { applyRow(row, it) }
        layout.bar.customRowOptions = resolveShownOptions(DefaultOptions.current).bottomTabs.customRow.copy()
        CustomTabRowPlacer.track(activity)
    }

    private fun applyRow(row: CustomTabRow, options: BottomTabsCustomRowOptions) {
        row.options = options
        row.place = null
        val theme = MaterialColors.getColor(row.context, android.R.attr.colorBackground, Color.WHITE)
        row.apply(
            CustomTabRow.Look(
                background = CustomTabRowGeometry.background(
                    options.backgroundColor.get(activity, null),
                    options.effect,
                    theme,
                ),
                cornerRadius = UiUtils.dpToPixels(
                    activity,
                    options.cornerRadius.get(CustomTabRowGeometry.CORNER_RADIUS_DP.toDouble()),
                ).toFloat(),
                elevation = UiUtils.dpToPx(activity, CustomTabRowGeometry.ELEVATION_DP),
                testId = options.testID.get(null),
            ),
        )
        row.requestLayout()
        roomChanged()
    }

    // A cell: a React root of its own, rendering at once, with its tab's state as its first props.
    private fun makeCell(index: Int): CustomBottomTabItemView {
        val component = itemOptions(index).component
        val componentId = component.componentId.get(null) ?: "$id.$index"
        ScreenReadiness.setRendersAtOnce(componentId, true)
        // Another live root has the id (the same layout set again before the old one went): what it rendered isn't
        // this one's; the id's readiness goes only with its last root (as the bar's roots).
        if (BarComponents.isLive(componentId)) ScreenReadiness.forgetRender(componentId)
        BarComponents.rootMade(componentId)
        val cell = CustomBottomTabItemView(activity)
        val state = cellState(index)
        cell.show(ReactView(activity, componentId, component.name.get(""), cell.bundleOf(state)), state)
        cell.onDestroyed = { if (BarComponents.rootStopped(componentId)) ScreenReadiness.remove(componentId) }
        return cell
    }

    private fun cellState(index: Int): TabCellState {
        val dot = bar.items.dotOf(index)
        return TabCellState.of(
            index = index,
            selected = index == selectedIndex,
            badge = bar.items.badgeOf(index),
            dotVisible = dot.visible.isTrue,
            dotColor = dot.color.get(activity, null),
        )
    }

    /**
     * Destroys the tabs once: every tab and the registry first, then the cells (each stops its root once), the row out
     * of the window, the scroll it followed. A press's event still waiting goes with them.
     */
    override fun destroy() {
        if (isDestroyed) return
        pendingSelected = null
        staleTabs.clear()
        super.destroy()
        // A switch still running ends with its views at rest, without events: the tabs are gone.
        endSwitch?.invoke()
        peekView()?.let { layout ->
            layout.bar.destroyCustomItemViews()
            layout.row?.let(CustomTabRowPlacer::forget)
            layout.row = null
        }
        cells = emptyList()
        collapse.disableCollapse()
        bar.unbind()
        history.clear()
    }

    // The tab that is [id] or holds a layout with it, at any depth.
    private fun indexOfId(id: String): Int? = tabs.indexOfFirst { holds(it, id) }.takeIf { it >= 0 }

    private fun holds(node: ViewController<*>, id: String): Boolean =
        node.id == id || (node as? ParentController<*>)?.childControllers?.any { holds(it, id) } == true

    // The screen [node] shows, down its current children; none for a layout without one.
    private fun screenOf(node: ViewController<*>): ViewController<*>? {
        var current: ViewController<*> = node
        while (current is ParentController<*>) current = current.currentChild ?: return null
        return current
    }

    // `layout.direction` of the options the bar shows with; without it React Native's direction. A change of the app's
    // direction reaches the bar at once through the repaint.
    private fun directionOf(options: Options): LayoutDirection {
        val direction = options.layout.layoutDirection
        if (direction != LayoutDirection.DEFAULT) return direction
        return if (AppDirection.isReactNativeRtl(activity)) LayoutDirection.RTL else LayoutDirection.LTR
    }

    private companion object {
        const val PREVIOUS = "previous"
        const val FIRST = "first"
        const val BAR_HEIGHT_DP = 80

        // What the tabs give the layouts around: everything but the tabs' own group.
        fun withoutBottomTabs(options: Options): Options = options.copy().apply { bottomTabs = BottomTabsOptions() }
    }
}

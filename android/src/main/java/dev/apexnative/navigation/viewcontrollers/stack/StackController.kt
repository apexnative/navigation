package dev.apexnative.navigation.viewcontrollers.stack

import android.app.Activity
import android.util.Log
import android.view.View
import dev.apexnative.navigation.NavigationActivity
import dev.apexnative.navigation.options.AnimationsOptions
import dev.apexnative.navigation.options.BottomTabsEnterExitAnimationOptions
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.FabOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.TopBarOptions
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.PendingCalls
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.utils.UiThread
import dev.apexnative.navigation.utils.UiUtils
import dev.apexnative.navigation.viewcontrollers.bottomtabs.TabBarMotion
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.component.ContentReadiness
import dev.apexnative.navigation.viewcontrollers.component.Readiness
import dev.apexnative.navigation.viewcontrollers.component.RenderWait
import dev.apexnative.navigation.viewcontrollers.component.RestPlace
import dev.apexnative.navigation.viewcontrollers.navigator.BackCallbackState
import dev.apexnative.navigation.viewcontrollers.navigator.Navigator
import dev.apexnative.navigation.viewcontrollers.parent.ContainerBackground
import dev.apexnative.navigation.viewcontrollers.parent.OptionsScreen
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.parent.ReturnsScreenFromColumn
import dev.apexnative.navigation.viewcontrollers.parent.nodeWith
import dev.apexnative.navigation.viewcontrollers.stack.fab.FabPresenter
import dev.apexnative.navigation.viewcontrollers.stack.topbar.LargeTitleCollapse
import dev.apexnative.navigation.viewcontrollers.stack.topbar.TopBarPresenter
import dev.apexnative.navigation.viewcontrollers.stack.topbar.TopBarRoom
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.AnimationRules
import dev.apexnative.navigation.views.component.ComponentLayout
import dev.apexnative.navigation.views.stack.StackLayout
import dev.apexnative.navigation.views.stack.topbar.LargeTitleView

/**
 * A stack: screens one over the other under one top bar, the last one on top. Every command, back and container's
 * return goes through its one queue ([StackQueue]); its view is a [StackLayout] with the screens, the bar
 * ([TopBarPresenter]) and the FAB over them. It works the same wherever it sits (the root, a tab, a modal, a column, a
 * side menu's center): the containers call its hooks.
 *
 * Its own options lie under its screens': the bar, the animations and the FAB come from the screen on top and go no
 * further up; the rest goes up to the layouts around. Its screens' room at the top is the bar's height as the options
 * set it, not as it lays out. Main thread only.
 */
class StackController(
    activity: Activity,
    childRegistry: ChildControllersRegistry,
    id: String,
    presenter: Presenter,
    initialOptions: Options,
) : ParentController<StackLayout>(activity, childRegistry, id, presenter, initialOptions) {
    // The keys of the entries (`data.key`): a pair's is its primary's.
    private val keys = HashMap<ViewController<*>, String>()

    // The FAB keeps clear of the tab bar at rest under the stack, by the options of the screen on top.
    internal val fab = FabPresenter(activity) { parentController?.tabsAtRestOf(this, queue.currentChild ?: this) }

    /** What the bar shows: the options of the screen on top. */
    internal val topBar = TopBarPresenter(activity, BarHost())

    private val barHost = BarHost()

    // What a `mergeOptions` gave while the stack was hidden, applied as its top shows (`mergeOptions`). A merge with
    // the stack's own id goes to whatever screen is on top then. A merge into a child is kept with that child and
    // applies only if it is still the top then: another top must not take its values (a search text, a focus); the
    // child's own options hold the merge and apply as it appears.
    private var keptStackMerge: Options? = null
    private val keptChildMerges = HashMap<ViewController<*>, Options>()

    /** The screens prepared for a push in the stack's activity; the unit tests give their own. */
    internal var preparedPushes: () -> PreparedPushes? = { navigator?.preparedPushes }

    /** The one queue of the stack. */
    // The readiness of the screens reaches the queue's waits on the next turn of the main loop: a signal that comes
    // inside a layout pass or a view's attach moves no view of the container there. A pushed screen's mount reaches
    // its push behind the calls JavaScript made before it (`PendingCalls`), on a later turn too.
    internal var queue = StackQueue(
        display = StackViews(this),
        readiness = ContentReadiness,
        scheduler = { delay, task -> AppearanceDispatcher.scheduler.schedule(delay, task) },
        host = QueueHost(),
        deliver = { signal -> UiThread.post { signal() } },
        afterCalls = PendingCalls::after,
    )
        private set

    /**
     * The unit tests run the stack over their own display, readiness and host (views are stubs there), before any
     * screen joins it; the readiness signals come at once there, and a push's behind the calls as [afterCalls] says.
     */
    internal fun useQueueParts(
        display: StackDisplay,
        readiness: Readiness,
        host: StackQueue.Host?,
        afterCalls: (() -> Unit) -> Unit = { it() },
    ) {
        queue = StackQueue(
            display = display,
            readiness = readiness,
            scheduler = { delay, task -> AppearanceDispatcher.scheduler.schedule(delay, task) },
            host = host ?: QueueHost(),
            afterCalls = afterCalls,
        )
    }

    private val navigator: Navigator?
        get() = (activity as? NavigationActivity)?.navigator

    /**
     * The stack is in a layer that still waits to show (a pending root; a modal in its stage): it counts as off screen.
     */
    internal val isInWaitingLayer: Boolean
        get() = navigator?.isInWaitingLayer(this) == true

    override fun getChildControllers(): List<ViewController<*>> = queue.nodes()

    override fun getCurrentChild(): ViewController<*>? = queue.currentChild

    // The view of the screen on top is made with the stack's, so a root of a stack waits for it. Its title and
    // background are drawn in advance with it, so a root of a stack waits for them too.
    override fun createView(): StackLayout {
        val layout = StackLayout(activity)
        layout.onInsets = ::onInsets
        topBar.bind(layout)
        queue.currentChild?.let {
            layout.placeUnderBar(it.view)
            topBar.stage(it)
        }
        return layout
    }

    /**
     * Whether the components of the bar over [screen] have drawn: its title (unless its `waitForRender` is `false`)
     * and its background (the same), which are drawn in advance; its React buttons render during the transition and
     * don't count.
     */
    fun areBarComponentsDrawn(screen: ViewController<*>): Boolean = topBar.isDrawn(entryOf(screen) ?: screen)

    /** Calls [listener] once [areBarComponentsDrawn]; at once when they are. */
    internal fun whenBarComponentsDrawn(screen: ViewController<*>, listener: () -> Unit) =
        topBar.whenDrawn(entryOf(screen) ?: screen, listener)

    /**
     * The split view's call: replaces the screens of the stack with [screens], the last one on top, without animation
     * and without waiting for them; [listener] once the top shows, or with the reason of a rejection.
     */
    // Without animation whatever [animated] says: the split view shows the screen at once (`showInColumn`).
    fun setRoot(
        screens: List<ViewController<*>>,
        @Suppress("UNUSED_PARAMETER") animated: Boolean,
        listener: CommandListener,
    ) {
        if (isDestroyed || screens.isEmpty()) {
            listener.onError(ErrorTexts.screenNotShown(screens.lastOrNull()?.id ?: id))
            return
        }
        screens.forEachIndexed { index, screen -> join(screen, first = index == 0, key = null) }
        queue.replace(
            nodes = screens,
            animated = false,
            asPush = false,
            answer = null,
            listener = listener,
            componentId = id,
            what = "set the screens of the stack '$id'",
            waits = false,
        )
    }

    // The screens a stack is made with; the last shows.
    internal fun setInitialChildren(screens: List<ViewController<*>>, keys: List<String?>) {
        screens.forEachIndexed { index, screen -> join(screen, first = index == 0, key = keys.getOrNull(index)) }
        queue.setInitial(screens)
    }

    /**
     * [node] joins the stack (a push, a `setStackRoot`, the screens it is made with): its layout is the stack from
     * now on, and the back arrow's rule writes its own options: the first screen gets `topBar.backButton.visible:
     * false` unless it sets it; another gets `true` unless it has its own `topBar.leftButtons` or its resolved
     * `visible` is `false`. A pair's options are its primary's.
     */
    internal fun join(node: ViewController<*>, first: Boolean, key: String?) {
        node.parentController = this
        key?.let { keys[node] = it }
        backRule(node, first)
    }

    private fun backRule(node: ViewController<*>, first: Boolean) {
        val screen = optionsScreen(node)
        val back = screen.options.topBar.buttons.back
        if (first) {
            if (!back.visible.hasValue()) screen.changeOptions { it.topBar.buttons.back.setHidden() }
            return
        }
        if (screen.options.topBar.buttons.left != null) return
        if (screen.resolveCurrentOptions(DefaultOptions.current).topBar.buttons.back.visible.isFalse) return
        screen.changeOptions { it.topBar.buttons.back.setVisible() }
    }

    /** The key of [entry]; none without one. */
    internal fun keyOf(entry: ViewController<*>): String? = keys[entry]

    /** The entry of the stack [node] is in: itself, or the pair it is a screen of; none out of this stack. */
    internal fun entryOf(node: ViewController<*>): ViewController<*>? {
        var current: ViewController<*>? = node
        while (current != null) {
            if (current.parentController === this) return current
            current = current.parentController
        }
        return null
    }

    /** The id of the screen an entry stands for: a pair's primary. */
    internal fun screenIdOf(entry: ViewController<*>): String = optionsScreen(entry).id

    /**
     * The id a command of [kind] answers with for [entry], the one rule of the stack's answers: `push` answers the
     * entry's own id (a pair's), every other command the screen it stands for ([screenIdOf], a pair's primary).
     */
    internal fun answerIdOf(entry: ViewController<*>, kind: StackQueue.AnswerKind): String =
        if (kind == StackQueue.AnswerKind.Push) entry.id else screenIdOf(entry)

    /** The options [entry] shows with: its own over the stack's, its parents' and the defaults. */
    internal fun resolvedOf(entry: ViewController<*>): Options =
        optionsScreen(entry).resolveCurrentOptions(DefaultOptions.current)

    /** A screen prepared for a push into this stack. */
    internal fun isPrepared(child: ViewController<*>): Boolean =
        preparedPushes()?.isPreparedFor(child, this) == true

    // Back.

    /**
     * Back of the user (system back, after JavaScript): with two entries or more, the screen the user sees holds it
     * (`hardwareBackButton.popStackOnPress: false`: `ANN.NavigationButtonPressed` with
     * `ApexNavigation.hardwareBackButton`) or the top leaves with its `animations.pop`; with one, the first screen of a
     * sliding pane closes it. False when the stack has nothing to do: back goes on down the order.
     */
    override fun handleBack(listener: CommandListener): Boolean {
        // The open search of the screen on top closes first.
        if (topBar.closeSearch()) return true
        val entries = queue.entries
        if (entries.size >= 2) {
            val seen = queue.currentChild ?: return false
            if (resolvedOf(seen).hardwareBackButton.popStackOnPress.isFalse) {
                AppearanceDispatcher.navigationButtonPressed(optionsScreen(seen), HARDWARE_BACK_BUTTON_ID)
                return true
            }
            queue.cut(entries[entries.size - 2], null, id, "go back in the stack '$id'", null)
            return true
        }
        return returnsFromColumn { listener.onSuccess(entries.lastOrNull()?.id ?: id) }
    }

    /**
     * Whether [handleBack] would do something: an open search of the screen on top, or a stack that can cut (a holding
     * screen counts).
     */
    override fun canHandleBack(): Boolean = topBar.isSearchOpen() || queue.entries.size >= 2

    /**
     * The default pop of the top is what back does now and it can be previewed: two entries or more, nothing running
     * or waiting, no open search, the top doesn't hold back, pops with the default transition and doesn't turn the
     * preview off (`animations.pop.preview: false`), the screen under it has its view, and the stack shows.
     */
    override fun stackPoppedByBack(): StackController? {
        if (topBar.isSearchOpen() || !queue.isAtRest) return null
        val entries = queue.entries
        val top = queue.currentChild ?: return null
        val target = entries.getOrNull(entries.size - 2) ?: return null
        if (top !== entries.last() || target !in queue.shown || target.peekView() == null) return null
        if (!queue.isRendered(target) || backGesture != null) return null
        val options = resolvedOf(top)
        val pop = options.animations.pop
        if (options.hardwareBackButton.popStackOnPress.isFalse || pop.enabled.isFalse) return null
        if (pop.preview.isFalse || AnimationRules.stackIsOwn(pop)) return null
        val display = queue.display
        return this.takeIf { display.isOnScreen && display.canAnimate }
    }

    /**
     * Starts the preview of the pop a back gesture would make ([stackPoppedByBack] said yes): the top shrinks toward
     * the gesture's side ([fromLeft]) and follows the finger from [touchY]; the screen under it shows with its bar.
     * None when it can't be shown.
     */
    internal fun startBackGesture(fromLeft: Boolean, touchY: Float): BackGesture? {
        val views = queue.display as? StackViews ?: return null
        val top = queue.currentChild ?: return null
        val target = queue.entries.getOrNull(queue.entries.size - 2) ?: return null
        val gesture = BackGesture(views, top, target)
        val preview = views.startBackPreview(top, target, fromLeft, touchY, gesture::reveal) ?: return null
        gesture.preview = preview
        backGesture = gesture
        return gesture
    }

    // The back gesture over this stack whose preview hasn't ended yet; none.
    private var backGesture: BackGesture? = null

    /**
     * A back gesture over the top of this stack ([startBackGesture]): its progress, its cancel, and its release, which
     * pops the top through the queue with the preview's end. A command accepted meanwhile, or the stack's destroy,
     * ends the preview at once ([abort]).
     */
    internal inner class BackGesture(
        private val views: StackViews,
        private val top: ViewController<*>,
        private val target: ViewController<*>,
    ) {
        lateinit var preview: BackPreview
        // How the screen under the top was before the preview showed it: in the container and seen, its alpha and
        // accessibility.
        private var targetWasPlaced = false
        private var targetAlpha = 1f
        private var targetAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        private var revealed = false
        private var aborted = false

        /** The gesture moved: [progress] 0 … 1, the finger at [touchY]. */
        fun progress(progress: Float, touchY: Float) {
            if (!aborted) preview.progress(progress, touchY)
        }

        /** Cancelled: the preview springs back, then the screen under the top and the bar are as they were. */
        fun cancel() {
            if (!aborted) preview.cancel(::restore)
        }

        /**
         * Released: the top pops, the preview playing its end; the default pop when its picture isn't there yet. False
         * when the stack changed since the gesture started (a command ran, the stack went): back goes the usual way.
         */
        fun commit(): Boolean {
            if (aborted || isDestroyed) return false
            if (!preview.isReady) {
                abort()
                queue.cut(target, null, id, "go back in the stack '$id'", null)
                return true
            }
            if (backGesture === this) backGesture = null
            queue.cut(target, null, id, "go back in the stack '$id'", null, gesture = true)
            return true
        }

        /** The stack changed or goes: the preview ends at once and the stack is as it was. */
        fun abort() {
            if (aborted) return
            aborted = true
            preview.endAtOnce()
            restore()
        }

        // The preview's picture is there: the screen under the top shows under it, and the bar shows that screen.
        internal fun reveal() {
            val view = target.peekView() ?: return
            targetWasPlaced = view.parent === peekView() && view.visibility == View.VISIBLE
            targetAlpha = view.alpha
            targetAccessibility = view.importantForAccessibility
            revealed = true
            views.reveal(target, top)
            view.alpha = 1f
            topBar.apply(target)
        }

        private fun restore() {
            views.previewEnded(preview)
            if (backGesture === this) backGesture = null
            if (!revealed || isDestroyed) return
            revealed = false
            if (target in queue.shown && target !== queue.currentChild) {
                val view = target.peekView()
                if (targetWasPlaced && view != null) {
                    view.alpha = targetAlpha
                    view.importantForAccessibility = targetAccessibility
                } else {
                    views.detach(target)
                }
            }
            // The bar shows the top again, its components appeared again.
            val shown = queue.currentChild
            topBar.apply(shown)
            shown?.let {
                topBar.screenWillAppear(optionsScreen(it))
                topBar.screenDidAppear(optionsScreen(it))
            }
            BackCallbackState.update()
        }
    }

    /**
     * The back arrow of the bar was pressed: the screen the user sees holds it (`topBar.backButton.popStackOnPress:
     * false`: `ANN.NavigationButtonPressed` with `topBar.backButton.id` or `ApexNavigation.back`); on the first screen
     * of a sliding pane the pane closes; else the top leaves.
     */
    internal fun onBackArrowPressed() {
        val seen = queue.currentChild ?: return
        val back = resolvedOf(seen).topBar.buttons.back
        if (back.popStackOnPress.isFalse) {
            AppearanceDispatcher.navigationButtonPressed(optionsScreen(seen), back.id.get(BACK_BUTTON_ID))
            return
        }
        val entries = queue.entries
        if (entries.size < 2) {
            returnsFromColumn { BackCallbackState.update() }
            return
        }
        queue.cut(entries[entries.size - 2], null, id, "go back in the stack '$id'", null)
    }

    /**
     * The re-tap of the selected tab: back to the first entry, unless the screen the user sees holds it (the back
     * button's event, the stack stays). False with one entry.
     */
    fun popToFirstByUser(): Boolean {
        val entries = queue.entries
        if (entries.size < 2) return false
        val seen = queue.currentChild ?: return false
        val back = resolvedOf(seen).topBar.buttons.back
        if (back.popStackOnPress.isFalse) {
            AppearanceDispatcher.navigationButtonPressed(optionsScreen(seen), back.id.get(BACK_BUTTON_ID))
            return true
        }
        queue.cut(entries.first(), null, id, "go back in the stack '$id'", null)
        return true
    }

    /**
     * `bottomTab.popToRootOnBlur`: back to the first entry, without animation and without appearance events; each
     * screen that leaves gets `ANN.ScreenPopped`.
     */
    fun popToFirstSilently() {
        val first = queue.entries.firstOrNull() ?: return
        if (queue.entries.size < 2) return
        queue.cut(first, null, id, "go back in the stack '$id'", null, silent = true)
    }

    /**
     * The only screen of the stack goes back to the column before it: a layout around that returns screens from columns
     * takes the stack itself; [done] once the column stopped moving. False when no layout does it now.
     */
    internal fun returnsFromColumn(done: () -> Unit): Boolean {
        val parent = parentController ?: return false
        val returns = nodeWith<ReturnsScreenFromColumn>(parent) ?: return false
        return returns.returnsScreenFromColumn(this, Runnable { done() })
    }

    // Options.

    // Only the top's options reach the bar and the FAB, and the rest of them goes up, without the stack's groups.
    override fun applyChildOptions(options: Options, child: ViewController<*>) {
        if (child !== queue.currentChild) return
        ContainerBackground.apply(this, options)
        updateBar()
        takeKeptMerge(child)?.let(::applyTopMerge)
        if (queue.isAtRest) showFab()
        performOnParentController { it.applyChildOptions(withoutStackGroups(options), this) }
    }

    // A screen prepared for a push is no child yet: its merge stays in its options for its show.
    override fun mergeChildOptions(options: Options, child: ViewController<*>) {
        if (isPrepared(child)) return
        // A merge into the screen writes what it must keep into its own options: a side color into its own buttons, a
        // title text in place of its component. A merge into the stack doesn't.
        topBar.writeOwn(optionsScreen(child), options)
        if (child === queue.currentChild) applyOrKeep(options, child)
        performOnParentController { it.mergeChildOptions(withoutStackGroups(options), this) }
    }

    // `mergeOptions` with the stack's id: its own options, what it shows, then the layouts around.
    override fun mergeOptions(options: Options) {
        mergeOwnOptions(options)
        applyOrKeep(options, null)
        performOnParentController { it.mergeChildOptions(withoutStackGroups(options), this) }
    }

    // A merge the stack shows now applies now; a hidden stack (an unselected tab, a view not made) keeps it until its
    // top shows again (`mergeOptions`): with [child], the entry it was merged into; without, the stack.
    private fun applyOrKeep(options: Options, child: ViewController<*>?) {
        if (isViewShown) return applyMergedOptions(options)
        if (child == null) {
            keptStackMerge = (keptStackMerge ?: Options()).mergeWith(options)
        } else {
            keptChildMerges[child] = (keptChildMerges[child] ?: Options()).mergeWith(options)
        }
    }

    // What was kept for [top] as it shows: the stack's own merges, with those into [top] over them, as [top]'s own
    // options lie over the stack's; the merges into other entries go (they show with their own options).
    private fun takeKeptMerge(top: ViewController<*>): Options? {
        val stackMerge = keptStackMerge
        val childMerge = keptChildMerges[top]
        keptStackMerge = null
        keptChildMerges.clear()
        if (childMerge == null) return stackMerge
        return stackMerge?.mergeWith(childMerge) ?: childMerge
    }

    /** `mergeOptions` into the stack or its top: the bar, the FAB and the room of the top take it (StackPresenter). */
    internal fun applyTopMerge(options: Options) {
        if (ContainerBackground.isTouchedBy(options)) {
            ContainerBackground.apply(this, resolveShownOptions(DefaultOptions.current))
        }
        val top = queue.currentChild
        if (top != null && peekView() != null) topBar.merge(top, options)
        // What the room of the screen on top depends on changed: it is counted again.
        if (TopBarRoom.isTouchedBy(options)) barHost.roomChanged()
        if (options.fab.hasValue()) mergeFab()
    }

    /**
     * Light or dark changed, or new default options: the bar and the FAB take the colors of the new mode and the
     * options as they resolve now.
     */
    internal fun repaintTop() {
        val top = queue.currentChild
        if (peekView() != null) topBar.repaint(top)
        top ?: return
        fab.repaint(resolvedOf(top).fab)
    }

    // A screen of the stack is destroyed: what the stack kept for it goes — its FAB, its key, the components of its bar
    // and its search.
    override fun onChildDestroyed(child: ViewController<*>) {
        fab.forget(optionsScreen(child))
        keys.remove(child)
        keptChildMerges.remove(child)
        topBar.screenDestroyed(optionsScreen(child), child.peekView())
    }

    // The appearance of the screen the bar shows: the components of its bar go with it.

    /** [screen] of this stack starts to appear. */
    internal fun barScreenWillAppear(screen: ViewController<*>) = topBar.screenWillAppear(screen)

    /** [screen] of this stack has appeared. */
    internal fun barScreenDidAppear(screen: ViewController<*>) = topBar.screenDidAppear(screen)

    /** [screen] of this stack disappeared. */
    internal fun barScreenDisappeared(screen: ViewController<*>) = topBar.screenDisappeared(screen)

    // The room of the screens.

    /**
     * The room [child] keeps at its top: the bar as its own options set it — the strip, `topBar.topMargin`,
     * `topBar.height` (64 dp by default), the search under the row —, not as the bar lays out, and the room of the
     * layouts around; none for the bar when the child is under it.
     */
    override fun getTopInset(child: ViewController<*>): Int {
        val around = parentController?.getTopInset(this) ?: 0
        val options = resolvedOf(child)
        val entry = entryOf(child) ?: child
        return around + TopBarRoom.topInset(isUnderBar(child), topBar.barOf(entry, options), topBar.searchHeightOf(entry))
    }

    // Under the bar with `topBar.drawBehind`, `visible: false`, `hideOnScroll` of the bar or the search, or a large
    // title shown; from the child's own options, never from where the bar is now.
    private fun isUnderBar(child: ViewController<*>): Boolean =
        TopBarRoom.isUnderBar(resolvedOf(child), largeTitleShown(child))

    // Whether the large title shows over [child]: `automatic` as the entry below it; a prepared screen counts as the
    // next top.
    private fun largeTitleShown(child: ViewController<*>): Boolean {
        val entries = queue.entries
        val entry = entryOf(child)
        val chain = if (entry != null && entry in entries) entries.subList(0, entries.indexOf(entry) + 1) else entries + child
        val modes = chain.map { resolvedOf(it).topBar.largeTitle.mode() }
        return LargeTitleCollapse.isShown(modes, modes.size - 1, !resolvedOf(child).topBar.visible.isFalse)
    }

    /**
     * The bar keeps the room of the status bar for [child]: it shows and is drawn under the status bar, so the screen
     * keeps none of its own.
     */
    override fun keepsStatusBarRoomOf(child: ViewController<*>): Boolean {
        val options = resolvedOf(child)
        val entry = entryOf(child) ?: child
        return TopBarRoom.keepsStatusBarRoom(options, topBar.barOf(entry, options)) || super.keepsStatusBarRoomOf(child)
    }

    /**
     * The safe area of [child] leaves its top edge free with the hide on scroll of the bar or the search, or a large
     * title shown.
     */
    override fun leavesTopEdgeFree(child: ViewController<*>): Boolean =
        TopBarRoom.leavesTopEdgeFree(resolvedOf(child), largeTitleShown(child)) || super.leavesTopEdgeFree(child)

    /** A screen under another doesn't compute its safe area again; the prepared and waiting do. */
    override fun isTopOfStack(child: ViewController<*>): Boolean =
        child === queue.currentChild || child === queue.waitingScreen || isPrepared(child)

    /**
     * The bar's bottom in the window while [child] is under it and it shows, from the stack's place in the window: for
     * the screen on top that the bar shows, where the bar is laid out (a move of the bar doesn't count); for another
     * child (a prepared one) and for a top the bar hasn't shown yet (in a tab not selected yet, which measures though
     * invisible) as its own options set the bar, so its content doesn't move as the bar takes them.
     */
    override fun headerBottomOf(child: ViewController<*>): Int? {
        if (!isUnderBar(child)) return super.headerBottomOf(child)
        val options = resolvedOf(child)
        if (options.topBar.visible.isFalse) return null
        val entry = entryOf(child) ?: child
        val layout = peekView() ?: return null
        // The stack's place at rest, as the screen's own: a modal sliding in or a sheet dragged doesn't move it.
        val top = RestPlace.topInWindow(layout)
        if (entry === queue.currentChild && topBar.shows(entry)) return topBar.barBottomInStack()?.let { top + it }
        val bar = topBar.barOf(entry, options)
        val large = if (largeTitleShown(child)) UiUtils.dpToPx(activity, LargeTitleView.HEIGHT).toInt() else 0
        return top + bar.margin + bar.strip + bar.height + large
    }

    /**
     * A screen prepared for a push measures its safe area though it doesn't show; so does the top of a stack in a made
     * tab that isn't selected, which lays out invisible.
     */
    override fun measuresThoughNotShown(child: ViewController<*>): Boolean = isPrepared(child) ||
        (child === queue.currentChild && parentController?.measuresThoughNotShown(this) == true)

    /**
     * The tab bar under the stack changed where it rests (its visibility, `layoutStyle`, `bottomMargin`, its height):
     * the FAB takes its place again.
     */
    internal fun tabBarChanged() = fab.place()

    // Appearance.

    // The lower screens make their content once the top appeared.
    override fun onViewDidAppear() {
        super.onViewDidAppear()
        queue.renderLowerScreens()
    }

    // Off screen, nothing waits for a screen to render there.
    override fun onViewDisappear() {
        super.onViewDisappear()
        queue.wentOffScreen()
    }

    override fun destroy() {
        if (isDestroyed) return
        backGesture?.abort()
        queue.destroy()
        preparedPushes()?.discardFor(this)
        fab.destroy()
        topBar.destroy()
        super.destroy()
        keys.clear()
        keptChildMerges.clear()
        keptStackMerge = null
    }

    // What the stack shows of its top.

    // The bar shows the screen on top: what changed of it applies.
    private fun updateBar() {
        if (peekView() == null) return
        topBar.apply(queue.currentChild)
    }

    // The FAB follows the top at the end of a transition.
    private fun showFab() {
        val layout = peekView() ?: return
        val top = queue.currentChild
        val screen = top?.let(::optionsScreen)
        fab.showFor(layout, screen, top?.let(::resolvedOf)?.fab ?: FabOptions(), scrollOf(screen))
    }

    private fun mergeFab() {
        val layout = peekView() ?: return
        val top = queue.currentChild ?: return
        val screen = optionsScreen(top)
        fab.merge(layout, screen, resolvedOf(top).fab, scrollOf(screen))
    }

    // A native screen has no scroll listener: no `hideOnScroll` for it.
    private fun scrollOf(screen: ViewController<*>?) = (screen?.peekView() as? ComponentLayout)?.scrollEventListener

    // New window insets: the bar's strip and sides, the places of its buttons (a rotation), the FAB.
    private fun onInsets() {
        topBar.onInsets()
        fab.place()
    }

    private fun optionsScreen(node: ViewController<*>): ViewController<*> =
        (node as? OptionsScreen)?.optionsScreen ?: node

    private inner class QueueHost : StackQueue.Host {
        override fun operationAccepted() {
            backGesture?.abort()
        }

        // The bar takes the options of the new top at once, also without appearance events.
        override fun currentChildChanged() {
            updateBar()
            navigator?.shownScreensChanged(this@StackController)
        }

        // Before the bar takes the new top's options: the row's picture or the bar's travel, and how it shows or hides.
        override fun barTransition(
            motion: StackDisplay.BarMotion?,
            visibility: StackDisplay.BarVisibility,
            travel: StackDisplay.BarTravel?,
        ) {
            if (peekView() != null) topBar.beginRowTransition(motion, queue.currentChild, visibility, travel)
        }

        // The tab bar of the tabs around goes with the transition; a takeover doesn't bring it to rest.
        override fun tabBarTransition(move: TabBarMotion.Move, side: BottomTabsEnterExitAnimationOptions?) {
            parentController?.tabBarTransition(this@StackController, move, side)
        }

        override fun operationEnded(topSettled: Boolean) {
            BackCallbackState.update()
            if (topSettled) parentController?.tabBarSettled(this@StackController)
            // The keys of entries that left go with them.
            val nodes = queue.nodes()
            if (keys.isNotEmpty()) keys.keys.retainAll(nodes.toSet())
            // The bar's components of nodes that left go with them, also of a node destroyed without its view (a pair).
            topBar.retainScreens(nodes.mapTo(HashSet()) { optionsScreen(it) })
            if (topSettled) {
                topBar.endRowTransition()
                showFab()
            }
        }

        // A node that will show as the top (a push, the new top of a `setStackRoot`): its title and background start
        // to render now, with it; the lower ones draw theirs as they appear.
        override fun willShow(node: ViewController<*>) {
            if (peekView() != null) topBar.stage(node)
        }

        override fun hasReactContent(node: ViewController<*>): Boolean =
            RenderWait.waitedScreens(node).any { it is ComponentViewController }

        override fun log(text: String) {
            Log.e(NavigationLog.TAG, text)
        }

        override fun answerIdOf(entry: ViewController<*>, kind: StackQueue.AnswerKind): String =
            this@StackController.answerIdOf(entry, kind)
    }

    // What the bar asks of its stack.
    private inner class BarHost : TopBarPresenter.Host {
        override val entries: List<ViewController<*>>
            get() = queue.entries

        override fun optionsScreen(entry: ViewController<*>): ViewController<*> = this@StackController.optionsScreen(entry)

        override fun resolvedOf(entry: ViewController<*>): Options = this@StackController.resolvedOf(entry)

        override fun backPressed() = onBackArrowPressed()

        override fun roomChanged() {
            val top = queue.currentChild ?: return
            top.applyTopInset()
            (optionsScreen(top) as? ComponentViewController)?.updateSafeArea()
        }
    }

    companion object {
        /** The `buttonId` of the back button's event when `topBar.backButton.id` isn't set. */
        const val BACK_BUTTON_ID = "ApexNavigation.back"

        /** The `buttonId` of system back's event. */
        const val HARDWARE_BACK_BUTTON_ID = "ApexNavigation.hardwareBackButton"

        // The options a stack's screen gives the layouts around: without the bar, the animations and the FAB, which
        // stay at the stack.
        internal fun withoutStackGroups(options: Options): Options = options.copy().apply {
            topBar = TopBarOptions()
            animations = AnimationsOptions()
            fab = FabOptions()
        }
    }
}

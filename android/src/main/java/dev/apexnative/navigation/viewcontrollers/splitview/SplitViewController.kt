package dev.apexnative.navigation.viewcontrollers.splitview

import android.app.Activity
import android.os.Build
import android.transition.AutoTransition
import android.transition.Transition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.core.view.doOnNextLayout
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import dev.apexnative.navigation.NavigationActivity
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.SplitViewGroupOptions
import dev.apexnative.navigation.options.SplitViewOptions
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.CommandListenerAdapter
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.events.EventEmitter
import dev.apexnative.navigation.system.SystemUiOwner
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.UiThread
import dev.apexnative.navigation.utils.UiUtils
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.navigator.BackCallbackState
import dev.apexnative.navigation.viewcontrollers.navigator.Navigator
import dev.apexnative.navigation.viewcontrollers.parent.ChildOffsetFromRest
import dev.apexnative.navigation.viewcontrollers.parent.OptionsScreen
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.parent.ReturnsScreenFromColumn
import dev.apexnative.navigation.viewcontrollers.parent.ShownChildren
import dev.apexnative.navigation.viewcontrollers.parent.ShowsColumns
import dev.apexnative.navigation.viewcontrollers.stack.StackController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import kotlin.math.roundToInt

/**
 * A split view: the primary pane beside the secondary one when the width takes both (a tablet, a phone in landscape).
 * On a narrower screen the compact layout shows, or without it the secondary pane goes over the primary one, and back
 * or the back button of its first screen takes it away. Android has no supplementary or inspector column.
 */
class SplitViewController(
    activity: Activity,
    childRegistry: ChildControllersRegistry,
    id: String,
    presenter: Presenter,
    initialOptions: Options,
    private var split: SplitViewOptions,
    // The panes' content; `showInColumn` may give a pane a new one.
    private var primary: ViewController<*>,
    private var secondary: ViewController<*>,
    private val compact: ViewController<*>?,
    private val eventEmitter: EventEmitter,
) : ParentController<SplitViewLayout>(activity, childRegistry, id, presenter, initialOptions),
    ReturnsScreenFromColumn, ChildOffsetFromRest, ShowsColumns {
    // Done when the panes stop moving.
    private val whenMoved = mutableListOf<Runnable>()
    // The children told they appeared, and the panes' moves told in the dispatcher's order. The split view appeared
    // (its show ended) until it disappears: only then do the panes' moves tell its children that they appear or
    // disappear; before, its own appearance does.
    private val shownChildren = ShownChildren(::visibleChildren)
    // The app hid the primary pane beside the secondary one.
    private var primaryHidden = false
    private var measured = false
    // What JS was last told of the panes one over the other; null until the first layout.
    private var sentStacked: Boolean? = null
    // A `compactTopColumn` that came after creation, for the next switch into panes one over the other.
    private var laterTopColumn: String? = null
    // The children shown and the current child as the navigator and the system UI were last told.
    private var lastVisible: List<ViewController<*>>? = null
    private var lastCurrent: ViewController<*>? = null
    // The panes have the ids of a `splitView.testID` now.
    private var panesNamed = false

    private val navigator: Navigator?
        get() = (activity as? NavigationActivity)?.navigator

    init {
        for (child in childControllers) child.setParentController(this)
    }

    override fun getChildControllers(): Collection<ViewController<*>> = listOfNotNull(primary, secondary, compact)

    override fun getCurrentChild(): ViewController<*> {
        if (!isViewCreated) return secondary
        val layout = getView()
        return when {
            layout.isCompact -> compact ?: secondary
            isSliding && !layout.panes.isOpen -> primary
            else -> secondary
        }
    }

    /**
     * The children that show: the compact layout while it shows; side by side the primary pane, unless the app hid it,
     * and the secondary one; one over the other the open pane, the secondary when it lies over the primary one, else
     * the primary. Before the view measures, the current child: the split view tells the navigator when they change.
     */
    override fun visibleChildren(): List<ViewController<*>> {
        val layout = peekView() ?: return listOf(getCurrentChild())
        val wide = layout.isWide ?: return listOf(getCurrentChild())
        return when {
            layout.isCompact -> listOf(compact ?: secondary)
            wide -> if (primaryHidden) listOf(secondary) else listOf(primary, secondary)
            layout.panes.isOpen -> listOf(secondary)
            else -> listOf(primary)
        }
    }

    // A wait for the split view (as a root, a modal) waits for what it shows: a pane that lies closed or is hidden
    // renders nothing.
    override fun waitedChildren(): List<ViewController<*>> = visibleChildren()

    // A split view laid out unseen (in a made tab that isn't selected, in a closed side of a side menu) asks its
    // parent for the children it shows: their screens measure their safe area there too.
    override fun measuresThoughNotShown(child: ViewController<*>): Boolean =
        child in visibleChildren() && parentController?.measuresThoughNotShown(this) == true

    // One pane over the other: the width doesn't take them side by side, and there is no compact layout.
    private val isSliding: Boolean
        get() = isViewCreated && getView().isWide == false && !getView().isCompact

    override fun createView(): SplitViewLayout {
        val primaryWidth = dp(split.width)
        val secondaryWidth = dp(SplitViewOptions.PANE_WIDTH)
        val layout = SplitViewLayout(activity, primaryWidth + secondaryWidth)
        val panes = layout.panes
        panes.addView(primary.getView(), SlidingPaneLayout.LayoutParams(primaryWidth, MATCH_PARENT))
        panes.addView(
            secondary.getView(),
            SlidingPaneLayout.LayoutParams(secondaryWidth, MATCH_PARENT).apply { weight = 1f }
        )
        compact?.let { layout.setCompact(it.getView()) }
        panes.lockMode = lockMode()
        panes.addPanelSlideListener(object : SlidingPaneLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View, slideOffset: Float) {
                revealPanes()
                // The pane that comes into view starts to appear as it starts to slide.
                shownChildren.moveStarted(listOf(primary, secondary))
            }

            override fun onPanelOpened(panel: View) = onPanesMoved()
            override fun onPanelClosed(panel: View) = onPanesMoved()
        })
        layout.onWidthChanged = Runnable { onWidthChanged(layout) }
        applyTestIds(primary.getView(), secondary.getView())
        return layout
    }

    private fun lockMode(): Int =
        if (split.presentsWithGesture) SlidingPaneLayout.LOCK_MODE_UNLOCKED else SlidingPaneLayout.LOCK_MODE_LOCKED

    private fun onWidthChanged(layout: SplitViewLayout) {
        primary.getView().visibility = if (primaryHidden && layout.isWide == true) View.GONE else View.VISIBLE
        // Compact, side by side or one over the other decides what back does from this measure on: the callback only
        // takes its value here, nothing moves; the panes' end below computes it again.
        BackCallbackState.update()
        // Where a later `compactTopColumn` puts the pane over the other at this switch: true open, false closed.
        val settle = if (measured && isSliding) laterTopColumn?.let { it == SECONDARY } else null
        if (!measured) {
            measured = true
            // The first layout one over the other takes the value as it is now; side by side, a value that came after
            // creation waits for the first switch into panes one over the other.
            if (isSliding) laterTopColumn = null
            // Before the first layout the pane opens without moving.
            if (isSliding && split.compactTopColumn == SECONDARY) layout.panes.openPane()
        } else if (settle != null) {
            laterTopColumn = null
            // Asked before the panes measure, as at the first layout: a layout that follows a change of their width
            // lays the pane out there without moving; otherwise it moves there once the panes have laid out.
            if (settle) layout.panes.openPane() else layout.panes.closePane()
        }
        layout.post {
            if (!isDestroyed) {
                updateBackButton()
                sendStacked()
                onPanesMoved()
                settle?.let(::settlePane)
            }
        }
    }

    // The pane comes to the place a later `compactTopColumn` gives, with its own motion, when the layout didn't put it
    // there; the end of its move tells the children.
    private fun settlePane(open: Boolean) {
        val panes = getView().panes
        if (!isSliding || panes.isOpen == open) return
        revealPanes()
        if (open) panes.openPane() else panes.closePane()
    }

    /**
     * New `splitView` options, resolved with the defaults: after a `mergeOptions` into the split view or into a screen
     * inside it, and after `setDefaultOptions` (its presenter's repaint). `width` and `presentsWithGesture` apply at
     * once; a new `compactTopColumn` at the next switch into panes one over the other; `testID` names the panes again.
     * Main thread only.
     */
    internal fun setSplitOptions(next: SplitViewOptions) {
        if (isDestroyed) return
        if (next.compactTopColumn != split.compactTopColumn) laterTopColumn = next.compactTopColumn ?: PRIMARY
        split = next
        val layout = peekView() ?: return
        applyTestIds(primary.getView(), secondary.getView())
        layout.panes.lockMode = lockMode()
        val primaryWidth = dp(next.width)
        val primaryView = primary.getView()
        primaryView.layoutParams?.let { params ->
            if (params.width != primaryWidth) {
                params.width = primaryWidth
                primaryView.layoutParams = params
            }
        }
        layout.panesWidth = primaryWidth + dp(SplitViewOptions.PANE_WIDTH)
    }

    // The split view's own `splitView`, with what the screens inside merged into it and the defaults.
    private fun resolvedSplit(): SplitViewGroupOptions = resolveCurrentOptions(presenter.defaultOptions).splitView

    // `splitView.testID` names the two panes: `<testID>.primary`, `<testID>.secondary`; without it they have none.
    private fun applyTestIds(primaryView: View, secondaryView: View) {
        val testId = resolvedSplit().testID.get(null)?.ifEmpty { null }
        nameView(primaryView, testId?.let { "$it.primary" })
        nameView(secondaryView, testId?.let { "$it.secondary" })
        panesNamed = testId != null
    }

    // Over the view's own accessibility delegate, which a column's layout may have (a drawer). Android 10+ gives that
    // delegate; before it the id replaces it, so the pane keeps its `resource-id`, and a pane never named is left as
    // it is.
    private fun nameView(view: View, testId: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            TestIds.applyOver(view, testId)
        } else if (testId != null || panesNamed) {
            TestIds.apply(view, testId)
        }
    }

    // `mergeOptions` with the split view's id: its own options, the layouts around, then its new `splitView`.
    override fun mergeOptions(options: Options) {
        super.mergeOptions(options)
        if (options.splitView.hasValue()) setSplitOptions(resolvedSplit().toSplitViewOptions())
    }

    // `mergeOptions` of a screen inside: the `splitView` it names goes into the split view's own options, which every
    // later apply reads, and applies; the rest goes on to the layouts around.
    override fun mergeChildOptions(options: Options, child: ViewController<*>) {
        if (options.splitView.hasValue()) {
            val merged = options.splitView
            changeOptions { it.splitView.mergeWith(merged) }
            setSplitOptions(resolvedSplit().toSplitViewOptions())
        }
        val rest = options.copy().apply { splitView = SplitViewGroupOptions() }
        performOnParentController { it.mergeChildOptions(rest, this) }
    }

    // JS learns whether the panes show one over the other: the first screen of the secondary pane can pop then, and
    // the pane goes. The first value always goes, as a layout sent again with the same ids starts anew in JS.
    private fun sendStacked() {
        val stacked = isSliding
        if (stacked == sentStacked) return
        sentStacked = stacked
        eventEmitter.emitSplitViewColumnsStacked(id, stacked)
    }

    // Those that left view disappeared and the children that came into it appeared, once the split view appeared, and
    // the calls waiting for the panes are done. A pane hidden under the other one or brought back is not laid out
    // again: its screens learn it here. The panes' state decides what shows ([visibleChildren]), not the views, which
    // show nothing while the activity is stopped.
    private fun onPanesMoved() {
        if (isViewCreated && !isDestroyed) hideCoveredPane()
        // The pane over the other is open or closed only once it stops, after its last frame: back closes it or not.
        BackCallbackState.update()
        shownChildren.moved()
        val done = whenMoved.toList()
        whenMoved.clear()
        done.forEach { it.run() }
        tellShownChanged()
    }

    // A layer that waits to show the split view waits for the panes that show now, a modal's sheet holds the screen
    // shown now; the system UI follows the current child, which changes with no screen appearing when the panes move or
    // the width changes.
    private fun tellShownChanged() {
        if (isDestroyed) return
        val visible = visibleChildren()
        if (visible != lastVisible) {
            lastVisible = visible
            navigator?.shownScreensChanged(this)
        }
        val current = getCurrentChild()
        if (current !== lastCurrent) {
            lastCurrent = current
            SystemUiOwner.of(activity)?.invalidate()
        }
    }

    // One pane over the other, the one under it is hidden: it disappears, and accessibility doesn't read it.
    private fun hideCoveredPane() {
        val open = getView().panes.isOpen
        val primaryView = primary.getView()
        primaryView.visibility = when {
            getView().isWide == true -> if (primaryHidden) View.GONE else View.VISIBLE
            isSliding && open -> View.INVISIBLE
            else -> View.VISIBLE
        }
        secondary.getView().visibility = if (isSliding && !open) View.INVISIBLE else View.VISIBLE
    }

    // The panes move: both show while they do.
    private fun revealPanes() {
        for (pane in listOf(primary.getView(), secondary.getView())) {
            if (pane.visibility == View.INVISIBLE) pane.visibility = View.VISIBLE
        }
    }

    // The children its appearance told it appeared ([visibleChildren]): a pane that the panes' moves hide afterwards
    // disappears.
    override fun onViewWillAppear() {
        super.onViewWillAppear()
        shownChildren.containerWillAppear()
    }

    override fun onViewDidAppear() {
        shownChildren.containerDidAppear()
        super.onViewDidAppear()
        onPanesMoved()
    }

    override fun onViewDisappear() {
        shownChildren.containerDisappeared()
        super.onViewDisappear()
    }

    // The calls waiting for the panes are answered as the split view goes (a newer root, the activity going):
    // JavaScript lives on and waits for them. Posted, so the navigation state they send is read once the teardown is
    // over. A JS reload drops them: the JavaScript that sent them is gone.
    override fun destroy() {
        val waiting = whenMoved.toList()
        whenMoved.clear()
        super.destroy()
        if (waiting.isEmpty() || Navigator.isReloading()) return
        UiThread.post { waiting.forEach { it.run() } }
    }

    override fun sendOnNavigationButtonPressed(buttonId: String) {
        getCurrentChild().sendOnNavigationButtonPressed(buttonId)
    }

    override fun handleBack(listener: CommandListener): Boolean {
        if (!isViewCreated) return super.handleBack(listener)
        val layout = getView()
        val handled = when {
            layout.isCompact -> compact?.handleBack(listener) == true
            isSliding && !layout.panes.isOpen -> primary.handleBack(listener)
            isSliding -> secondary.handleBack(listener) || closeSecondary(secondary)
            else -> secondary.handleBack(listener) || primary.handleBack(listener)
        }
        return handled || super.handleBack(listener)
    }

    // As handleBack; the secondary pane over the primary one, open, is taken away by closeSecondary.
    override fun canHandleBack(): Boolean {
        if (!isViewCreated) return super.canHandleBack()
        val layout = getView()
        val can = when {
            layout.isCompact -> compact?.canHandleBack() == true
            isSliding && !layout.panes.isOpen -> primary.canHandleBack()
            isSliding -> true
            else -> secondary.canHandleBack() || primary.canHandleBack()
        }
        return can || super.canHandleBack()
    }

    // As handleBack: the pane whose back goes first decides; an open sliding pane that would close previews nothing.
    override fun stackPoppedByBack(): StackController? {
        if (!isViewCreated) return super.stackPoppedByBack()
        val layout = getView()
        return when {
            layout.isCompact -> compact?.stackPoppedByBack()
            isSliding && !layout.panes.isOpen -> primary.stackPoppedByBack()
            isSliding -> if (secondary.canHandleBack()) secondary.stackPoppedByBack() else null
            secondary.canHandleBack() -> secondary.stackPoppedByBack()
            else -> primary.stackPoppedByBack()
        }
    }

    /**
     * How far the pane of [child] is from where it rests: the secondary pane sliding over the primary one rests over
     * it, closed or on its way. Zero for the other children.
     */
    fun paneOffset(child: ViewController<*>): Int {
        if (child != secondary || !isSliding || !child.isViewCreated) return 0
        val panes = getView().panes
        var pane: View = child.getView()
        while (pane.parent !== panes) pane = pane.parent as? View ?: return 0
        return if (panes.layoutDirection == View.LAYOUT_DIRECTION_RTL) panes.width - pane.right else -pane.left
    }

    // The capabilities the core asks for: the pop of the first screen of the secondary pane closes it, and the safe
    // area of a screen of the pane counts where the pane comes to rest.
    override fun returnsScreenFromColumn(screen: ViewController<*>, done: Runnable): Boolean =
        closeSecondary(screen, done)

    override fun childOffsetFromRest(child: ViewController<*>): Int = paneOffset(child)

    /**
     * The first screen of the secondary pane over the primary one goes back: the pane goes away, and [done] runs once
     * it has stopped moving. False elsewhere, and [done] doesn't run.
     */
    @JvmOverloads
    fun closeSecondary(from: ViewController<*>, done: Runnable? = null): Boolean {
        if (from != secondary || !isSliding || !getView().panes.isOpen) return false
        done?.let { whenMoved += it }
        revealPanes()
        val panes = getView().panes
        when {
            !panes.closePane() -> onPanesMoved()
            // Before its first layout since it was attached the pane doesn't move: it is laid out closed, and nothing
            // tells it. Until then it still reads as open, so the panes have moved once they have laid out.
            !panes.isLaidOut -> {
                panes.doOnNextLayout { if (!isDestroyed) onPanesMoved() }
                panes.requestLayout()
            }
        }
        return true
    }

    /**
     * Shows [screen] in [column] (`primary` or `secondary`; Android has no other) in place of what the pane shows, and
     * brings the pane into view. A stack pane's screens are replaced by it, unless it is a container
     * (![goesInStack]): `columnIsStack`. Any other pane is taken by it, and its content goes. A column the split view
     * doesn't have: `noSuchColumn`. False with the compact layout on screen: the screen goes over [from], the screen
     * the command comes from.
     */
    override fun showScreen(
        screen: ViewController<*>,
        column: String,
        goesInStack: Boolean,
        from: ViewController<*>,
        listener: CommandListener,
    ): Boolean {
        if (isViewCreated && getView().isCompact) return false
        val pane = when (column) {
            PRIMARY -> primary
            SECONDARY -> secondary
            else -> null
        }
        when {
            pane == null -> listener.onError(ErrorTexts.noSuchColumn(from.id, column))
            pane is StackController && !goesInStack -> listener.onError(ErrorTexts.columnIsStack(from.id, column))
            pane is StackController -> pane.setRoot(listOf(screen), false, object : CommandListenerAdapter() {
                override fun onSuccess(childId: String) {
                    updateBackButton()
                    bringIntoView(column) { listener.onSuccess(childId) }
                }

                override fun onError(message: String) = listener.onError(message)
            })
            else -> {
                replacePane(pane, screen)
                updateBackButton()
                val shownId = ((screen as? OptionsScreen)?.optionsScreen ?: screen).id
                bringIntoView(column) { listener.onSuccess(shownId) }
            }
        }
        return true
    }

    // [next] takes the pane of [old], a screen alone or a container, as iOS puts a screen in place of a column's: its
    // view goes where the old one's was, the children shown are told as a move of the panes tells them (DidDisappear
    // of what left, DidAppear of what came), and [old] goes with its screens.
    private fun replacePane(old: ViewController<*>, next: ViewController<*>) {
        next.setParentController(this)
        if (old === primary) primary = next else secondary = next
        peekView()?.let { layout ->
            layout.panes.replacePane(old.getView(), next.getView())
            applyTestIds(primary.getView(), secondary.getView())
            hideCoveredPane()
            shownChildren.moved()
            tellShownChanged()
        }
        old.destroy()
    }

    // The pane of [column] comes into view; [done] once the panes stopped.
    private fun bringIntoView(column: String, done: Runnable) {
        if (column == PRIMARY) setColumnShown(PRIMARY, true, done) else showSecondary(done)
    }

    private fun showSecondary(done: Runnable) {
        if (!isSliding || getView().panes.isOpen) {
            done.run()
            return
        }
        whenMoved += done
        revealPanes()
        if (!getView().panes.openPane()) onPanesMoved()
    }

    /** `showColumn` and `hideColumn` of the primary pane; Android has no other column to show or hide. */
    override fun setColumnShown(column: String, shown: Boolean, done: Runnable) {
        if (column != "primary" || !isViewCreated || getView().isCompact || getView().isWide == null) {
            if (column == "primary") primaryHidden = !shown
            done.run()
            return
        }
        val layout = getView()
        if (!isSliding) {
            primaryHidden = !shown
            val view = primary.getView()
            whenMoved += done
            if ((view.visibility == View.VISIBLE) == shown) {
                onPanesMoved()
                return
            }
            // Done when the transition ends: while it runs a hidden pane still counts as shown.
            TransitionManager.beginDelayedTransition(layout.panes, AutoTransition().addListener(object :
                Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) {}
                override fun onTransitionEnd(transition: Transition) = onPanesMoved()
                override fun onTransitionCancel(transition: Transition) = onPanesMoved()
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            }))
            view.visibility = if (shown) View.VISIBLE else View.GONE
            layout.panes.requestApplyInsets()
            return
        }
        // One pane over the other: the primary one shows when the secondary one goes.
        if (shown != layout.panes.isOpen) {
            done.run()
            return
        }
        whenMoved += done
        revealPanes()
        val moving = if (shown) layout.panes.closePane() else layout.panes.openPane()
        if (!moving) onPanesMoved()
    }

    // The first screen of the secondary pane over the primary one has the back button, which takes the pane away.
    private fun updateBackButton() {
        val root = (secondary as? StackController)?.childControllers?.firstOrNull() ?: return
        if (root.initialOptions.topBar.buttons.back.hasValue() || root.options.topBar.buttons.left != null) return
        val visible = isSliding
        if (root.options.topBar.buttons.back.visible.isTrue() == visible) return
        val options = Options()
        if (visible) options.topBar.buttons.back.setVisible() else options.topBar.buttons.back.setHidden()
        root.mergeOptions(options)
    }

    private fun dp(value: Double) = UiUtils.dpToPx(activity, value.toFloat()).roundToInt()

    companion object {
        private const val PRIMARY = "primary"
        private const val SECONDARY = "secondary"

        /** The split view [controller] is in, if it is in one. */
        @JvmStatic
        fun of(controller: ViewController<*>): SplitViewController? {
            var parent: ViewController<*>? = controller
            while (parent != null) {
                if (parent is SplitViewController) return parent
                parent = parent.parentController
            }
            return null
        }
    }
}

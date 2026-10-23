package dev.apexnative.navigation.viewcontrollers.arrangement

import android.animation.Animator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Rect
import android.view.ViewGroup
import androidx.window.layout.WindowMetricsCalculator
import dev.apexnative.navigation.NavigationActivity
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.react.ArrangementStates
import dev.apexnative.navigation.react.events.EventEmitter
import dev.apexnative.navigation.utils.SystemUiUtils
import dev.apexnative.navigation.utils.WindowFolds
import dev.apexnative.navigation.viewcontrollers.child.ChildController
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.component.ScreenFrame
import dev.apexnative.navigation.viewcontrollers.parent.KeepsStatusBarRoom
import dev.apexnative.navigation.viewcontrollers.parent.OptionsScreen
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.parent.ScreensLeavingWithNode
import dev.apexnative.navigation.viewcontrollers.parent.ShownChildren
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.component.ComponentLayout

/**
 * A pair of screens (`defineArrangement`, or a screen with `arrangement`), React or native: one entry of a stack, side
 * by side or one above the other (`split`), or the primary over the secondary (`overlay`), as the size of the window,
 * the shape of the pair and the fold of a foldable decide ([ArrangementPlanner]). The current child is the primary:
 * the top bar, the status bar, the navigation bar and the animations of the push take its options, and the
 * secondary's have no effect there. A React screen knows how the pair lies (`useArrangement`). Both appear and
 * disappear with the pair; a secondary that a small window or the axes leave out of the window disappears, and
 * appears again when the pair has room for it.
 */
class ArrangementController internal constructor(
    activity: Activity,
    childRegistry: ChildControllersRegistry,
    id: String,
    presenter: Presenter,
    initialOptions: Options,
    private val definition: ArrangementDefinition,
    val primary: ChildController<*>,
    val secondary: ChildController<*>,
) : ParentController<ArrangementLayout>(activity, childRegistry, id, presenter, initialOptions),
    OptionsScreen, ScreensLeavingWithNode, KeepsStatusBarRoom {
    private val folds = WindowFolds.of(activity)
    // A change of the folds lays the pair out again: a device folded halfway moves the boundary onto the fold.
    private val onFoldsChanged = Runnable { peekView()?.requestLayout() }
    // The screens told they appeared, until they leave the window, and the moves told in the dispatcher's order; the
    // pair appeared (the push or the pop that shows it ended), until it disappears.
    private val shownChildren = ShownChildren(::visibleChildren)
    // How the pair lies in a window its size, until its first layout: which screens it will show.
    private val initialPlan: ArrangementPlan
    // The pair puts its secondary in the window, as the navigator was last told.
    private var showsSecondary: Boolean

    private val navigator
        get() = (activity as? NavigationActivity)?.navigator

    init {
        primary.parentController = this
        secondary.parentController = this
        // Each screen renders first with the state of a pair the size of the window; the pair's layout tells a change.
        initialPlan = firstPlan()
        showsSecondary = initialPlan.secondary != null
        publish(initialPlan, notify = false)
    }

    override fun getChildControllers(): Collection<ViewController<*>> = listOf(primary, secondary)

    override fun getCurrentChild(): ViewController<*> = primary

    /**
     * The screens the pair shows: both, or the primary alone while a small window or the axes leave the secondary out
     * of the window; before the pair's first layout as a pair the size of the window lies.
     */
    override fun visibleChildren(): List<ViewController<*>> {
        val plan = peekView()?.plan ?: initialPlan
        return if (plan.secondary != null) listOf(primary, secondary) else listOf(primary)
    }

    // A wait for the pair waits for the screens it puts in the window: a secondary out of it never renders.
    override fun waitedChildren(): List<ViewController<*>> = visibleChildren()

    // A pair laid out unseen (in a made tab that isn't selected, in a closed side of a side menu) asks its parent: its
    // screens measure their safe area there too, as a stack's top does.
    override fun measuresThoughNotShown(child: ViewController<*>): Boolean =
        parentController?.measuresThoughNotShown(this) == true

    // The capabilities the core asks for: the pair's options are the primary's, both screens leave a stack with it, and
    // the pair keeps the room of the status bar for its screens.
    override val optionsScreen: ViewController<*>
        get() = primary

    override val screensLeavingWithNode: List<ViewController<*>>
        get() = listOf(primary, secondary)

    override fun createView(): ArrangementLayout {
        folds?.addListener(onFoldsChanged)
        val primaryView = primary.getView()
        val secondaryView = secondary.getView()
        val folds = { folds?.folds.orEmpty() }
        return if (primaryView is ComponentLayout) {
            ReactArrangementLayout(
                activity, definition, primaryView, secondaryView, folds, ::isLargeWindow, ::onArranged,
            )
        } else {
            ArrangementLayout(activity, definition, primaryView, secondaryView, folds, ::isLargeWindow, ::onArranged)
        }
    }

    // Large enough for a pair: Material's window classes, neither compact in width nor in height. A phone is compact
    // both ways (in landscape in height), a tablet or an unfolded foldable is not. The activity's window, not the pair.
    private fun isLargeWindow(): Boolean {
        val window = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity)
        return window.widthDp >= LARGE_WIDTH_DP && window.heightDp >= LARGE_HEIGHT_DP
    }

    private fun firstPlan(): ArrangementPlan {
        val bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
        val window = Rect(0, 0, bounds.width(), bounds.height())
        val fold = folds?.folds.orEmpty().firstNotNullOfOrNull { it.across(window) }
        // The direction moves the frames only, not the state.
        return ArrangementPlanner.plan(definition, window.width(), window.height(), false, fold, isLargeWindow())
    }

    private fun onArranged(plan: ArrangementPlan) {
        publish(plan, notify = true)
        val withSecondary = plan.secondary != null
        val shownChanged = withSecondary != showsSecondary
        showsSecondary = withSecondary
        // After the window's layout, out of its pass: the screens that came into the window or left it, then a layer
        // or a stack that waits to show the pair waits for the screens it shows now.
        getView().post {
            updateAppeared()
            if (shownChanged && !isDestroyed) navigator?.shownScreensChanged(this)
        }
    }

    // The state of the React screens of the pair (useArrangement); a native screen has no hook to read it.
    private fun publish(plan: ArrangementPlan, notify: Boolean) {
        val layout = plan.layout.jsName
        val overlay = plan.layout == ArrangementPlan.Layout.OVERLAY
        val single = plan.layout == ArrangementPlan.Layout.SINGLE
        if (primary is ComponentViewController) {
            val state = ArrangementStates.State(PRIMARY, layout, front = overlay, hidden = false)
            ArrangementStates.set(primary.id, state, notify, EventEmitter.of(primary.jsGeneration))
        }
        if (secondary is ComponentViewController) {
            val state = ArrangementStates.State(SECONDARY, layout, front = false, hidden = single)
            ArrangementStates.set(secondary.id, state, notify, EventEmitter.of(secondary.jsGeneration))
        }
    }

    // Once the pair appeared, the screens that came into the window appear and those that left it disappear: no view
    // of the core tells a screen it left the window. The plan decides which are in it ([visibleChildren]), not the
    // views, which show nothing while the activity is stopped or before the pair's first layout.
    private fun updateAppeared() {
        if (!isDestroyed) shownChildren.moved()
    }

    // The screens its appearance told they appeared ([visibleChildren]): one its first layout leaves out of the
    // window disappears.
    override fun onViewWillAppear() {
        super.onViewWillAppear()
        shownChildren.containerWillAppear()
    }

    override fun onViewDidAppear() {
        shownChildren.containerDidAppear()
        super.onViewDidAppear()
        updateAppeared()
    }

    override fun onViewDisappear() {
        shownChildren.containerDisappeared()
        super.onViewDisappear()
    }

    override fun start() {
        primary.start()
        secondary.start()
    }

    override fun sendOnNavigationButtonPressed(buttonId: String) = primary.sendOnNavigationButtonPressed(buttonId)

    override fun getPushAnimations(appearingOptions: Options): Animator? = primary.getPushAnimations(appearingOptions)

    override fun getPopAnimations(appearingOptions: Options, disappearingOptions: Options): Animator? =
        primary.getPopAnimations(appearingOptions, disappearingOptions)

    // The pair's options are the primary's: what its stack merges into its entry (the back button, the options of a
    // pop) and a mergeOptions with the pair's id go to the primary, which tells the layouts around as a screen does.
    @SuppressLint("MissingSuperCall")
    override fun mergeOptions(options: Options) = primary.mergeOptions(options)

    // Only the primary's options reach the layouts around. The secondary that appears clears theirs first, as every
    // screen does (ViewController.onViewWillAppear): they are read again only as options are applied, which the
    // primary does.
    override fun applyChildOptions(options: Options, child: ViewController<*>) {
        if (child != primary) return
        super.applyChildOptions(options, child)
        performOnParentController { it.applyChildOptions(options, this) }
    }

    override fun mergeChildOptions(options: Options, child: ViewController<*>) {
        if (child != primary) return
        super.mergeChildOptions(options, child)
        performOnParentController { it.mergeChildOptions(options, this) }
    }

    // The pair keeps clear of the bar and the tabs as one, with the margins of its own view: on each screen, the bar's
    // room would cut the lower screen of a pair one above the other, and the tabs' room the upper one. Its screens
    // have no margins; they apply the pair's room as theirs would apply.
    override fun applyTopInset() {
        setMargins(top = getTopInset())
    }

    // The screens' rule, with the options the pair shows with (its primary's).
    override fun getTopInset(): Int {
        val options = resolveShownOptions(DefaultOptions.current)
        return ScreenFrame.topRoomOf(this, options, SystemUiUtils.getStatusBarHeight(activity))
    }

    override fun applyBottomInset() {
        setMargins(bottom = getBottomInset())
    }

    private fun setMargins(top: Int? = null, bottom: Int? = null) {
        val view = peekView() ?: return
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if ((top ?: params.topMargin) == params.topMargin && (bottom ?: params.bottomMargin) == params.bottomMargin) {
            return
        }
        top?.let { params.topMargin = it }
        bottom?.let { params.bottomMargin = it }
        view.requestLayout()
    }

    override fun destroy() {
        // The stack keeps the bar's title, buttons and search by the view of its screen on top: they go with the pair.
        if (!isDestroyed && peekView() != null) performOnParentController { it.onChildDestroyed(this) }
        folds?.removeListener(onFoldsChanged)
        ArrangementStates.remove(primary.id)
        ArrangementStates.remove(secondary.id)
        shownChildren.containerDisappeared()
        super.destroy()
    }

    private companion object {
        const val PRIMARY = "primary"
        const val SECONDARY = "secondary"
        const val LARGE_WIDTH_DP = 600f
        const val LARGE_HEIGHT_DP = 480f
    }
}

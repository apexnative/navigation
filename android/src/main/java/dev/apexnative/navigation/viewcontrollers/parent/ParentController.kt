package dev.apexnative.navigation.viewcontrollers.parent

import android.app.Activity
import android.view.ViewGroup
import dev.apexnative.navigation.options.BottomTabsEnterExitAnimationOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.viewcontrollers.bottomtabs.TabBarMotion
import dev.apexnative.navigation.viewcontrollers.child.ChildController
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.stack.StackController
import dev.apexnative.navigation.viewcontrollers.stack.fab.FabPlacement
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * A layout of nodes: a stack, tabs, a side menu, or a container module (the split view, the pair). It tells the core
 * its children and its current child, which the options resolve along and the visible screen is found through; applies
 * and merges the options of a child through itself; gives its children their room at the top and the bottom. Where the
 * core needs a container's own behavior, it asks the node's capabilities (`Capabilities.kt`), never its class.
 */
abstract class ParentController<V : ViewGroup>(
    activity: Activity,
    childRegistry: ChildControllersRegistry,
    id: String,
    presenter: Presenter,
    initialOptions: Options,
) : ChildController<V>(activity, childRegistry, id, presenter, initialOptions) {
    /** Every child of the layout, shown or not. */
    abstract fun getChildControllers(): Collection<ViewController<*>>

    /** The child the layout shows now, whose options are its own; none for a layout without children (a new stack). */
    abstract fun getCurrentChild(): ViewController<*>?

    /** Every child of the layout, shown or not. */
    val childControllers: Collection<ViewController<*>>
        @JvmName("childControllersProperty") get() = getChildControllers()

    /** The child the layout shows now; none for a layout without children. */
    val currentChild: ViewController<*>?
        @JvmName("currentChildProperty") get() = getCurrentChild()

    /**
     * The options a child takes from this layout: the layout's own over those of the layouts around it. A new object
     * at each call, which the child changes.
     */
    open fun resolveChildOptions(child: ViewController<*>): Options = resolveCurrentOptions()

    /**
     * What the layout shows with: the shown options of the screen its options come from (`OptionsScreen`, a pair's
     * primary), or else of its current child, down to the end of the chain; its own resolved options while it has no
     * child. A layout whose options come from elsewhere (a side menu's center) overrides it.
     */
    override fun resolveShownOptions(defaultOptions: Options): Options {
        val shown = (this as? OptionsScreen)?.optionsScreen?.takeIf { it !== this } ?: getCurrentChild()
        return shown?.resolveShownOptions(defaultOptions) ?: resolveCurrentOptions(defaultOptions)
    }

    /** A child shows with [options], resolved: what concerns this layout (its bar, its tabs) applies here. */
    open fun applyChildOptions(options: Options, child: ViewController<*>) = Unit

    /**
     * `mergeOptions` of a child (`mergeOptions` step 3): what concerns this layout applies here, when [child] is its
     * current child; then a container passes it on to its own parent (`performOnParentController`), as the pair does.
     * The default does nothing.
     */
    open fun mergeChildOptions(options: Options, child: ViewController<*>) = Unit

    /** A child was destroyed: what the layout kept for it goes. */
    open fun onChildDestroyed(child: ViewController<*>) = Unit

    /** The room [child] keeps at its top for this layout and the layouts around it. */
    open fun getTopInset(child: ViewController<*>): Int = parentController?.getTopInset(this) ?: 0

    /**
     * The room [child] keeps at its bottom for this layout and the layouts around it; [screen] is the screen that
     * asks, whose options say whether it is under the tabs: [child] itself, or a screen under it.
     */
    open fun getBottomInset(child: ViewController<*>, screen: ViewController<*> = child): Int =
        parentController?.getBottomInset(this, screen) ?: 0

    // The answers a screen's safe area and room take from the layouts around it. Each layout passes the question on to
    // the one around it; the stack, its bar and the tabs answer for their children. At the top of the tree the answer
    // is "none".

    /**
     * Whether a layout around [child] keeps the room of the status bar for it: its bar is drawn under the status bar
     * and [child] is under that bar, so the screen keeps no room of its own for the status bar.
     */
    open fun keepsStatusBarRoomOf(child: ViewController<*>): Boolean =
        parentController?.keepsStatusBarRoomOf(this) ?: false

    /**
     * Where the bottom of the header over [child] is in the window, in pixels, when that header covers its content:
     * shown and not hidden by the child's options, at the place it has in the layout; none without one.
     */
    open fun headerBottomOf(child: ViewController<*>): Int? = parentController?.headerBottomOf(this)

    /**
     * Where the top of the tabs under [child] is in the window, in pixels, when the tabs cover its content: shown and
     * not hidden by the options of [screen], the screen that asks; none without them.
     */
    open fun tabsTopOf(child: ViewController<*>, screen: ViewController<*> = child): Int? =
        parentController?.tabsTopOf(this, screen)

    /**
     * Whether [child] is the top of its stack: a screen under another doesn't compute its safe area again, once it has
     * one. Out of a stack, yes.
     */
    open fun isTopOfStack(child: ViewController<*>): Boolean = parentController?.isTopOfStack(this) ?: true

    /**
     * Whether [child] is in a side of a side menu (`ViewController.isInSideMenu`): as this layout is, by default; a
     * side menu answers yes for its left and right menus.
     */
    open fun isInSideMenu(child: ViewController<*>): Boolean = isInSideMenu

    /**
     * Whether [child] measures its safe area though its view doesn't show: a screen prepared for a push lays out
     * hidden in its stack as if it were the top, a made tab that isn't selected lays out invisible, so does a closed
     * menu of a side menu. No by default.
     */
    open fun measuresThoughNotShown(child: ViewController<*>): Boolean = false

    /**
     * Whether the safe area of [child] leaves its top edge free by default: its stack shows the header with
     * `topBar.hideOnScroll` or the search with `hideOnScroll`, or a large title shows.
     */
    open fun leavesTopEdgeFree(child: ViewController<*>): Boolean =
        parentController?.leavesTopEdgeFree(this) ?: false

    /** Whether a large title shows over [child]: its stack shows the bar with one. No by default. */
    open fun showsLargeTitleOver(child: ViewController<*>): Boolean =
        parentController?.showsLargeTitleOver(this) ?: false

    /**
     * The tab bar at rest under [child]: where a stack's FAB keeps clear of it, by the options of [screen], the stack's
     * screen on top; none out of tabs.
     */
    internal open fun tabsAtRestOf(
        child: ViewController<*>,
        screen: ViewController<*> = child,
    ): FabPlacement.Tabs? = parentController?.tabsAtRestOf(this, screen)

    /**
     * A stack under this layout ([child], or a layout on the way to it) changes its top with [move]: the tab bar of the
     * tabs around goes with the transition, right before the new top starts to appear; [side] is the transition's
     * `bottomTabs` with a slide of its own.
     */
    internal open fun tabBarTransition(
        child: ViewController<*>,
        move: TabBarMotion.Move,
        side: BottomTabsEnterExitAnimationOptions? = null,
    ) {
        parentController?.tabBarTransition(this, move, side)
    }

    /** The transition of a stack under this layout ([child]) brought its top to rest: the tab bar rests too. */
    internal open fun tabBarSettled(child: ViewController<*>) {
        parentController?.tabBarSettled(this)
    }

    /**
     * The children that show while the layout shows: they appear with it. The current child by default; a layout that
     * shows several at once (an open side menu over its center, columns side by side) gives them all.
     */
    open fun visibleChildren(): List<ViewController<*>> = listOfNotNull(getCurrentChild())

    /**
     * The children whose screens a wait for the layout to render waits for (the root's, a modal's): those it shows
     * when it shows ([visibleChildren]) by default. A layout that shows other children first, or more of them, gives
     * those (the tabs).
     */
    open fun waitedChildren(): List<ViewController<*>> = visibleChildren()

    /**
     * The children made with the layout that don't show when it shows, whose screens a wait counts only within its
     * first 0.5 s: the unselected tabs of `together`. None by default.
     */
    open fun waitedUnseenChildren(): List<ViewController<*>> = emptyList()

    override fun onViewWillAppear() {
        super.onViewWillAppear()
        visibleChildren().forEach { it.onViewWillAppear() }
    }

    override fun onViewDidAppear() {
        visibleChildren().forEach { it.onViewDidAppear() }
        super.onViewDidAppear()
    }

    // Every child hears it: those that didn't appear tell nothing.
    override fun onViewDisappear() {
        getChildControllers().forEach { it.onViewDisappear() }
        super.onViewDisappear()
    }

    // Every child starts to leave with the layout.
    override fun onLeaveStarted() {
        getChildControllers().forEach { it.onLeaveStarted() }
    }

    override fun sendOnNavigationButtonPressed(buttonId: String) {
        getCurrentChild()?.sendOnNavigationButtonPressed(buttonId)
    }

    override fun handleBack(listener: CommandListener): Boolean = getCurrentChild()?.handleBack(listener) == true

    override fun canHandleBack(): Boolean = getCurrentChild()?.canHandleBack() == true

    override fun stackPoppedByBack(): StackController? = getCurrentChild()?.stackPoppedByBack()

    override fun destroy() {
        if (isDestroyed) return
        getChildControllers().forEach { it.destroy() }
        super.destroy()
    }
}

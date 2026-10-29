package dev.apexnative.navigation.viewcontrollers.sidemenu

import android.app.Activity
import android.util.Log
import dev.apexnative.navigation.NavigationActivity
import dev.apexnative.navigation.options.BottomTabsEnterExitAnimationOptions
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.viewcontrollers.bottomtabs.TabBarMotion
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.navigator.BackCallbackState
import dev.apexnative.navigation.viewcontrollers.navigator.Navigator
import dev.apexnative.navigation.viewcontrollers.parent.ChildOffsetFromRest
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.sidemenu.SideMenuMotion.Side
import dev.apexnative.navigation.viewcontrollers.stack.StackController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.sidemenu.SideMenuView

/**
 * A side menu on Android: a center and up to one menu on each side, the Material drawer ([SideMenuView]) the menus
 * slide over the center in. The sides are places, not nodes: the nodes in them are this node's children, and only this
 * node is found by its id. It works as a root, in a tab and as a modal's content.
 *
 * - The current child is the open menu's node, else the center ([SideMenuMotion.openSide]: from the end of the
 *  opening to the end of the closing); the state, the options and back follow it.
 * - The center and the open menu show with the side menu ([visibleChildren]); a wait for the side menu to render
 *   waits for the center ([waitedChildren]); the menus' views join the drawer as `sideMenu.menusAttachMode` says
 *   ([attached]): with the side menu's view, once it first appeared, or each as it first starts to show.
 * - Its options ([SideMenuPresenter]): from the resolution of [resolveShownOptions] at creation, at every appearance
 *   of a screen inside, and for the fields a merge names; `sideMenu.<side>.visible` acts once, from a merge or from the
 *  layout at the first show. What goes on up goes without `sideMenu`: the nearest side menu acts.
 * - Back closes an open menu first, else goes to the center.
 *
 * Main thread only.
 */
internal class SideMenuController(
    activity: Activity,
    childRegistry: ChildControllersRegistry,
    id: String,
    presenter: Presenter,
    initialOptions: Options,
    private val center: ViewController<*>,
    private val left: ViewController<*>?,
    private val right: ViewController<*>?,
) : ParentController<SideMenuView>(activity, childRegistry, id, presenter, initialOptions), ChildOffsetFromRest {
    private val menus: SideMenuPresenter = presenter as? SideMenuPresenter ?: SideMenuPresenter(activity)

    /** The drawer the motion drives: the view's, once it is made; the unit tests give their own. */
    internal var drawer: SideMenuMotion.Drawer = ViewlessDrawer()

    /** The rules of the drawer's motion, its locks and its commands. */
    internal val motion = SideMenuMotion({ drawer }, MotionHost())

    /**
     * Whether the side menu shows: in the window and not in a layer that waits, so a menu moves with animation. The
     * unit tests give their own.
     */
    internal var shows: () -> Boolean = { isViewShown && navigator?.isInWaitingLayer(this) != true }

    // From the end of its appearance's start until it disappears: the drawer's motion tells the menu screens then.
    private var appeared = false
    private var firstShown = false
    private var firstAppeared = false

    private val attachMode = SideMenuRules.attachModeOf(initialOptions.sideMenu, DefaultOptions.current.sideMenu)

    /** The menus whose nodes' views are in the drawer, or join it as it is made. */
    internal val attached = HashSet<Side>(2)
    // Its children go first as it is destroyed: nothing reacts to them, nor to the drawer, from the start.
    private var destroying = false

    private val navigator: Navigator?
        get() = (activity as? NavigationActivity)?.navigator

    init {
        getChildControllers().forEach { it.parentController = this }
        SideMenuRules.attachedAtCreation(attachMode).filter { childOf(it) != null }.forEach { attached += it }
    }

    override fun getChildControllers(): List<ViewController<*>> = listOfNotNull(center, left, right)

    override fun getCurrentChild(): ViewController<*> = openChild() ?: center

    // The center stays visible under an open menu.
    override fun visibleChildren(): List<ViewController<*>> = listOfNotNull(center, openChild())

    // The menus render with the side menu; a wait waits for the center only.
    override fun waitedChildren(): List<ViewController<*>> = listOf(center)

    private fun openChild(): ViewController<*>? = motion.openSide?.let(::childOf)

    private fun childOf(side: Side): ViewController<*>? = if (side == Side.Left) left else right

    // The drawer and its parts, the view of each node attached so far in its part; the creation's options. An open menu
    // recorded before the view existed is open in it from the start; the drawer is silent before its first layout.
    override fun createView(): SideMenuView {
        val view = SideMenuView(activity)
        view.setCenter(center.view)
        for (side in Side.entries) {
            val child = childOf(side) ?: continue
            view.addPart(side)
            if (side in attached) view.fillPart(side, child.view)
        }
        menus.applyOnCreation(view, resolveShownOptions(DefaultOptions.current))
        motion.openSide?.let { view.open(it, false) }
        view.follow(motion)
        drawer = view
        motion.applyLocks()
        return view
    }

    // Options.

    /**
     * What the side menu shows with: the options of the open menu's screen with the `sideMenu` of the center's screen
     * over them — an open menu doesn't undo what the center's screen said —, else the center's. A new object while a
     * menu is open.
     */
    override fun resolveShownOptions(defaultOptions: Options): Options {
        val shownCenter = center.resolveShownOptions(defaultOptions)
        val open = openChild() ?: return shownCenter
        val shown = open.resolveShownOptions(defaultOptions).copy()
        shown.sideMenu.mergeWith(shownCenter.sideMenu)
        return shown
    }

    // A screen in the side menu appears: everything of the side menu applies again but the heights, as it resolves
    // now; what isn't the side menu's goes on up, only from the part that is current.
    override fun applyChildOptions(options: Options, child: ViewController<*>) {
        if (destroying || isDestroyed) return
        menus.applyOptions(this, resolveShownOptions(DefaultOptions.current))
        motion.applyLocks()
        if (child === currentChild) {
            performOnParentController { it.applyChildOptions(SideMenuRules.withoutSideMenu(options), this) }
        }
    }

    // `mergeOptions` with the side menu's id: its own options, then what a merge does, then the layouts around.
    override fun mergeOptions(options: Options) {
        mergeOwnOptions(options)
        applyMerge(options)
        performOnParentController { it.mergeChildOptions(SideMenuRules.withoutSideMenu(options), this) }
    }

    // `mergeOptions` of a screen inside, the center's or a menu's, shown or not.
    override fun mergeChildOptions(options: Options, child: ViewController<*>) {
        applyMerge(options)
        performOnParentController { it.mergeChildOptions(SideMenuRules.withoutSideMenu(options), this) }
    }

    // The fields the merge names while the side menu shows, the locks, then `visible` last, left before right; the
    // command acts also while the side menu doesn't show.
    private fun applyMerge(options: Options) {
        if (destroying || isDestroyed) return
        if (isViewShown) menus.mergeOptions(this, options)
        if (SideMenuRules.touchesEnabled(options.sideMenu)) motion.applyLocks()
        val commands = SideMenuRules.commands(options.sideMenu)
        if (commands.isEmpty()) return
        val shown = shows()
        commands.forEach { motion.command(it.side, it.visible, it.animate, shown) }
    }

    // Appearance: the center and the open menu appear with the side menu, every child disappears with it; the open menu
    // stays open.

    // The layout's own `visible` opens its menu once, as the side menu first starts to appear, without animation and
    // before its children hear it: the open menu appears with the center then.
    override fun onViewWillAppear() {
        if (destroying || isDestroyed) return
        if (!firstShown) {
            firstShown = true
            SideMenuRules.firstShow(initialOptions.sideMenu).forEach {
                motion.command(it, visible = true, animate = false, shown = false)
            }
        }
        super.onViewWillAppear()
        appeared = true
        motion.containerAppeared()
    }

    // `afterCenter`: the menus join once the side menu first appeared with its center.
    override fun onViewDidAppear() {
        super.onViewDidAppear()
        if (firstAppeared || destroying || isDestroyed) return
        firstAppeared = true
        SideMenuRules.attachedAfterAppearance(attachMode).forEach(::attach)
    }

    override fun onViewDisappear() {
        appeared = false
        motion.containerDisappeared()
        super.onViewDisappear()
    }

    // Back: an open menu closes; else the center.

    override fun handleBack(listener: CommandListener): Boolean = motion.back() || center.handleBack(listener)

    override fun canHandleBack(): Boolean = motion.canGoBack || center.canHandleBack()

    // An open menu closes first: no preview of a pop then.
    override fun stackPoppedByBack(): StackController? = if (motion.canGoBack) null else center.stackPoppedByBack()

    // A button's press addressed to the side menu goes to the center.
    override fun sendOnNavigationButtonPressed(buttonId: String) = center.sendOnNavigationButtonPressed(buttonId)

    // Room and place.

    // The menus go the whole height under the status bar; the center's screens are as anywhere else.
    override fun isInSideMenu(child: ViewController<*>): Boolean = child === left || child === right || isInSideMenu

    // A closed menu lies invisible, laid out: its screens measure their safe area where it opens.
    override fun measuresThoughNotShown(child: ViewController<*>): Boolean =
        child === left || child === right || parentController?.measuresThoughNotShown(this) == true

    override fun childOffsetFromRest(child: ViewController<*>): Int {
        val view = peekView() ?: return 0
        return when {
            left != null && child === left -> view.offsetFromRest(Side.Left)
            right != null && child === right -> view.offsetFromRest(Side.Right)
            else -> 0
        }
    }

    // The tab bar of the tabs around goes with the stack of the part that is current only.

    override fun tabBarTransition(
        child: ViewController<*>,
        move: TabBarMotion.Move,
        side: BottomTabsEnterExitAnimationOptions?,
    ) {
        if (child === currentChild) super.tabBarTransition(child, move, side)
    }

    override fun tabBarSettled(child: ViewController<*>) {
        if (child === currentChild) super.tabBarSettled(child)
    }

    // The view of the menu on [side] joins its part, once, before its screens hear that they appear: a stack lays out
    // its bar as it appears in the window. The drawer's first movement may come while it draws (a peek settles in
    // `computeScroll`, before the drawer draws its children): the view joins the part then, measured and laid out at the
    // next frame.
    private fun attach(side: Side) {
        val child = childOf(side) ?: return
        if (destroying || isDestroyed || !attached.add(side)) return
        peekView()?.fillPart(side, child.view)
    }

    /** Destroys the side menu once: the drawer's callbacks stop first, then the children go. */
    override fun destroy() {
        if (isDestroyed) return
        destroying = true
        motion.destroy()
        peekView()?.unfollow()
        super.destroy()
    }

    // What the motion asks of the side menu.
    private inner class MotionHost : SideMenuMotion.Host {
        override val appeared: Boolean
            get() = this@SideMenuController.appeared && !destroying

        override fun child(side: Side): ViewController<*>? = childOf(side)

        override fun starts(side: Side) = attach(side)

        override fun isEnabled(side: Side): Boolean =
            SideMenuRules.isEnabled(resolveShownOptions(DefaultOptions.current).sideMenu, side)

        override fun missingSide(side: Side) {
            Log.w(NavigationLog.TAG, "the side menu '$id' has no ${side.jsName} menu: its `visible` does nothing")
        }

        override fun stateChanged() = AppearanceDispatcher.state.setNeedsUpdate()

        override fun backChanged() {
            navigator?.let(BackCallbackState::update)
        }

        // The tabs around follow the center's screen again: they heard nothing of it while the menu was current.
        override fun centerCurrentAgain() {
            if (destroying) return
            val options = SideMenuRules.withoutSideMenu(center.resolveShownOptions(DefaultOptions.current))
            performOnParentController { it.applyChildOptions(options, this@SideMenuController) }
        }

        // The system UI follows the screen the user sees now, at rest: the open menu's, the center's once a close
        // ends. The owner reads it before the next frame, when a menu that closed is hidden already.
        override fun shownChanged() {
            navigator?.shownScreensChanged(this@SideMenuController)
            navigator?.systemUi?.invalidate()
        }
    }

    // The drawer before the view is made: it calls nothing back, so the motion records what is asked.
    private inner class ViewlessDrawer : SideMenuMotion.Drawer {
        override fun has(side: Side): Boolean = childOf(side) != null

        override val callsBack: Boolean = false

        override fun open(side: Side, animate: Boolean) = Unit

        override fun close(side: Side, animate: Boolean) = Unit

        override fun lock(side: Side, locked: Boolean) = Unit

        override fun isVisible(side: Side): Boolean = false

        override fun hide(side: Side) = Unit
    }
}

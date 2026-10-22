package dev.apexnative.navigation.viewcontrollers.viewcontroller

import android.animation.Animator
import android.app.Activity
import android.view.ViewGroup
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.OptionsCache
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.stack.StackController
import java.lang.ref.WeakReference

/**
 * A node of the layout tree on Android: a screen, or a layout of nodes (a stack, tabs, a split view, a pair), with its
 * id, its options and its view, made when it is first asked for. The native side owns the tree; commands find a node
 * by id (`ChildControllersRegistry`). Main thread only.
 *
 * Members a module overrides are open.
 */
abstract class ViewController<V : ViewGroup>(
    /** The activity the node's view lives in. */
    val activity: Activity,
    /** The node's id in the layout: the commands and the events name it. */
    val id: String,
    /** The options the layout was made with, one-shot options included. */
    var initialOptions: Options,
) {
    /**
     * The node's own options: those it was made with, with every `mergeOptions` and every user's choice over them.
     * Writing them is one of the two places the options change ([OptionsCache]): the setter takes away the one-shot
     * options, keeps the object (a stripped copy when it has a one-shot option: the caller's object never changes), and
     * makes every resolved result stale. Don't change the object in place: [changeOptions] or [mergeOptions] write a
     * changed copy.
     */
    var options: Options = initialOptions.copy().also(Options::clearOneShotOptions)
        set(value) {
            field = if (value.hasOneShotOptions()) value.copy().also(Options::clearOneShotOptions) else value
            OptionsCache.optionsChanged()
        }

    /**
     * The JavaScript the node was made for (`ReloadCoordinator`): the events of the node and its screens reach only
     * that one.
     */
    internal val jsGeneration: Int = ReloadCoordinator.generation

    private var createdView: V? = null
    private var parent: ParentController<*>? = null
    private val appearedListeners = mutableListOf<Runnable>()
    private var waitForRender: Bool = NullBool()

    // The last result of resolveCurrentOptions(DefaultOptions.current), the generation it was built at, and the nodes
    // it was built along: the layouts around this node, and the chain of current children under it.
    private var resolved: Options? = null
    private var resolvedGeneration = -1L
    private var resolvedAlong: List<WeakReference<ViewController<*>>> = emptyList()

    /** The view is made. */
    val isViewCreated: Boolean
        get() = createdView != null

    /** The node is destroyed: it shows nothing again. */
    var isDestroyed = false
        private set

    /** The node's view, made now if it isn't yet; a destroyed node makes none ([getView]). */
    val view: V
        @JvmName("requireView") get() = getView()

    /** The layout this node is in; none for a root, a modal's root, an overlay. */
    var parentController: ParentController<*>?
        get() = parent
        @JvmName("assignParentController") set(value) = setParentController(value)

    /** The node is the root of its window: no layout around it. */
    val isRoot: Boolean
        get() = parent == null

    /** The node's view shows: it is made, in the window and visible with all its parents. */
    open val isViewShown: Boolean
        get() = !isDestroyed && createdView?.isShown == true

    /**
     * The node is the root of a modal shown as a sheet: the modals set it before the node's view is made, so its
     * screens keep no room for the status bar from their first layout.
     */
    internal var shownAsSheet = false

    /** The node is in a modal shown as a sheet: it keeps no room for the status bar. */
    open val isInSheet: Boolean
        get() = shownAsSheet || parent?.isInSheet == true

    /**
     * The node is the root of an overlay: the overlays set it before the node's view is made, so its screens are drawn
     * under the status bar from their first layout (`StatusBarOptions.isHiddenOrDrawBehind`).
     */
    internal var shownAsOverlay = false

    /**
     * The node is in an overlay: it covers the whole window, and is drawn under the status bar by default. It holds from
     * the start of the show, while `OverlayCoordinator.isInOverlay` answers only for the overlays it lists.
     */
    open val isInOverlay: Boolean
        get() = shownAsOverlay || parent?.isInOverlay == true

    /**
     * The node is in a side of a side menu (its left or right menu, not its center): the side goes the whole height of
     * the window, and the node keeps no room for the status bar. The layout around answers for it
     * (`ParentController.isInSideMenu(child)`).
     */
    open val isInSideMenu: Boolean
        get() = parent?.isInSideMenu(this) == true

    /** The name of the screen the node shows, without `slot:`; none for a layout of nodes. */
    open val currentComponentName: String?
        get() = null

    /** Makes the node's view. */
    protected abstract fun createView(): V

    /**
     * The node's view, made now if it isn't yet. A destroyed node makes no view: a view made then would take the place
     * of a later node with the same id (a slot screen's container). Asking for it throws, which rejects the command
     * that asked; a destroyed node is asked through [peekView].
     */
    fun getView(): V {
        createdView?.let { return it }
        check(!isDestroyed) { "the node '$id' is destroyed" }
        return createView().also { createdView = it }
    }

    /** The node's view if it is made, without making it. */
    fun peekView(): V? = createdView

    open fun setParentController(parentController: ParentController<*>?) {
        parent = parentController
    }

    /** Runs [task] with the layout this node is in, if it is in one. */
    fun performOnParentController(task: (ParentController<*>) -> Unit) {
        parent?.let(task)
    }

    /** The stack this node is in, up through the layouts around it; none out of a stack. */
    fun findParentStack(): StackController? {
        var current = parent
        while (current != null) {
            if (current is StackController) return current
            current = current.parentController
        }
        return null
    }

    /**
     * The node's options over those of the layouts around it, without the default options: the parents' resolution,
     * then the node's own. A new object at each call, which the caller may change. For a layout of nodes this is not
     * what it shows with: that is [resolveShownOptions].
     */
    open fun resolveCurrentOptions(): Options {
        val resolved = parent?.resolveChildOptions(this) ?: Options()
        return resolved.mergeWith(options)
    }

    /**
     * The node's options over those of the layouts around it and [defaultOptions]: what it shows with. With the app's
     * default options ([DefaultOptions.current]) the result is kept and given again while no options changed
     * ([OptionsCache]) and the layouts around the node and its current children are the same: it is read-only, whoever
     * changes it copies it first. Main thread only.
     *
     * For a layout of nodes (a stack, tabs, a side menu) it is the layout's own options over those around it, without
     * any child's: not what the layout shows with, which [resolveShownOptions] gives.
     */
    open fun resolveCurrentOptions(defaultOptions: Options): Options {
        if (defaultOptions !== DefaultOptions.current) return resolveCurrentOptions().withDefaultOptions(defaultOptions)
        val generation = OptionsCache.generation
        val along = resolvedAlong()
        val kept = resolved
        if (kept != null && resolvedGeneration == generation && sameNodes(along, resolvedAlong)) return kept
        val result = resolveCurrentOptions().withDefaultOptions(defaultOptions)
        resolved = result
        resolvedGeneration = generation
        resolvedAlong = along.map(::WeakReference)
        return result
    }

    /**
     * The options the node shows with, also at a moment that is not an appearance (a repaint, a merge into a hidden
     * layout): for a screen its [resolveCurrentOptions]; for a layout of nodes those of the node at the end of its
     * current-child chain, which hold the layout's own and every layout's between. A pair answers with its options
     * screen (`OptionsScreen`). The result is [resolveCurrentOptions]'s of that node, kept and read-only in the same
     * way. Main thread only.
     */
    open fun resolveShownOptions(defaultOptions: Options): Options = resolveCurrentOptions(defaultOptions)

    // The layouts around this node, nearest first, then its current children down the visible chain.
    private fun resolvedAlong(): List<ViewController<*>> {
        val nodes = ArrayList<ViewController<*>>()
        var up = parent
        while (up != null) {
            nodes += up
            up = up.parentController
        }
        var down = (this as? ParentController<*>)?.currentChild
        while (down != null) {
            nodes += down
            down = (down as? ParentController<*>)?.currentChild
        }
        return nodes
    }

    private fun sameNodes(nodes: List<ViewController<*>>, kept: List<WeakReference<ViewController<*>>>): Boolean =
        nodes.size == kept.size && nodes.indices.all { nodes[it] === kept[it].get() }

    /**
     * Changes the node's own options: [change] gets a copy, which is stored ([options]). A presenter or a module that
     * writes a user's choice into the options (a menu's checkmark, a search scope, tokens, a toggled button) does it
     * this way; a button menu keeps its own shared `checked` (`ButtonMenuOptions`).
     */
    fun changeOptions(change: (Options) -> Unit) {
        val copy = options.copy()
        change(copy)
        options = copy
    }

    /**
     * `mergeOptions` with the node's id (`mergeOptions`): [options] go over its own and stay there (one-shot options
     * not kept); what shows applies them ([applyMergedOptions], when the view shows; otherwise as it appears); the
     * layouts around it apply their part (`ParentController.mergeChildOptions`, each passes it on). [options] still
     * carry the one-shot options to them. Containers that hand the merge to a child override it.
     */
    open fun mergeOptions(options: Options) {
        this.options = this.options.copy().mergeWith(options)
        if (isViewShown) applyMergedOptions(options)
        performOnParentController { it.mergeChildOptions(options, this) }
    }

    /** What [options] of a `mergeOptions` give applies over what shows; the node's view shows. */
    protected open fun applyMergedOptions(options: Options) = Unit

    /** The node shows with [options], resolved: they apply to its view and to the activity. */
    open fun applyOptions(options: Options) = Unit

    /** Calls [listener] each time the node has appeared, until it is removed. */
    fun addOnAppearedListener(listener: Runnable) {
        appearedListeners += listener
    }

    fun removeOnAppearedListener(listener: Runnable) {
        appearedListeners -= listener
    }

    /** `waitForRender` of the node as it shows now: true holds it hidden until its content is drawn. */
    fun setWaitForRender(waitForRender: Bool) {
        this.waitForRender = waitForRender
    }

    /** A button of the bar over the node was pressed: the screen it shows sends `ANN.NavigationButtonPressed`. */
    open fun sendOnNavigationButtonPressed(buttonId: String) = Unit

    /**
     * The animation of a push that brings this node in with [appearingOptions]; none for the default one. The stack
     * plays its screens' animations itself, from the options of the node's options screen (a pair's are its
     * primary's, the pair forwards these hooks to it), so nothing in the core asks here; the hook stays for the
     * containers that answer it.
     */
    open fun getPushAnimations(appearingOptions: Options): Animator? = null

    /** The animation of a pop that takes this node away; none for the default one. As [getPushAnimations]. */
    open fun getPopAnimations(appearingOptions: Options, disappearingOptions: Options): Animator? = null

    /** Back: the node goes back if it can, and tells [listener]; false when it has nothing to go back to. */
    open fun handleBack(listener: CommandListener): Boolean = false

    /** Whether [handleBack] would go back. */
    open fun canHandleBack(): Boolean = false

    /**
     * The stack whose top [handleBack] would take away with the default pop, which a back gesture previews; none when
     * back would do anything else (hold, close a menu or a pane, a pop of its own) or nothing.
     */
    internal open fun stackPoppedByBack(): StackController? = null

    /** Starts the node's content (a native screen's view, a React screen's surface). */
    open fun start() = Unit

    /**
     * The node starts to appear: the layout that shows it, or the transition that brings it in, says so; a screen tells
     * its appearance record (`AppearanceDispatcher`), a layout tells its visible children. A second call within one
     * appearance tells nothing new.
     */
    open fun onViewWillAppear() = Unit

    /**
     * The node has appeared, as for [onViewWillAppear] (a screen whose appearance didn't start starts it first); then
     * the listeners of [addOnAppearedListener] hear it.
     */
    open fun onViewDidAppear() {
        appearedListeners.toList().forEach { it.run() }
    }

    /**
     * The node has disappeared: it was covered or hidden, or it goes. A layout tells every child; a screen that didn't
     * appear tells nothing. Nothing calls it when the activity goes to the background.
     */
    open fun onViewDisappear() = Unit

    /** The node comes to the front again (a pop, a dismissed modal): what it set for the activity applies again. */
    open fun onViewBroughtToFront() = Unit

    /**
     * The transition that takes the node away starts (a new root, a pop, a dismissed modal), before it is destroyed: a
     * screen with `blurOnUnmount` lets go of the focus of the field it holds (`blurOnUnmount`). A layout tells every
     * child.
     */
    open fun onLeaveStarted() = Unit

    /** The room the node keeps at its top for what is over it: the status bar, a bar of the layouts around it. */
    open fun getTopInset(): Int = parent?.getTopInset(this) ?: 0

    /** Keeps the room of [getTopInset] at the node's top. */
    open fun applyTopInset() = Unit

    /** The room the node keeps at its bottom for what is under it: the tabs of the layouts around it. */
    open fun getBottomInset(): Int = parent?.getBottomInset(this) ?: 0

    /** Keeps the room of [getBottomInset] at the node's bottom. */
    open fun applyBottomInset() = Unit

    /** Destroys the node and its view; once. */
    open fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        appearedListeners.clear()
    }
}

package dev.apexnative.navigation.viewcontrollers.navigator

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import dev.apexnative.navigation.layout.LayoutChecks
import dev.apexnative.navigation.layout.LayoutNode
import dev.apexnative.navigation.layout.RootLayout
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.ScreenAnimationOptions
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.system.AppDirection
import dev.apexnative.navigation.system.SystemUiLayers
import dev.apexnative.navigation.system.SystemUiOwner
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.viewcontrollers.child.ChildController
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.component.ContentReadiness
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.externalcomponent.ExternalComponentViewController
import dev.apexnative.navigation.viewcontrollers.modal.ModalCoordinator
import dev.apexnative.navigation.viewcontrollers.modal.ModalViews
import dev.apexnative.navigation.viewcontrollers.overlay.OverlayCoordinator
import dev.apexnative.navigation.viewcontrollers.overlay.OverlayRecord
import dev.apexnative.navigation.viewcontrollers.overlay.OverlayViews
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.stack.PreparedPushes
import dev.apexnative.navigation.viewcontrollers.stack.StackCommands
import dev.apexnative.navigation.viewcontrollers.stack.StackController
import dev.apexnative.navigation.viewcontrollers.stack.topbar.BarComponents
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.FadeThrough
import dev.apexnative.navigation.views.animations.SideAnimator

/**
 * The navigation of one activity: its root, its modals, its overlays and the screens prepared for a push, in their
 * layers of the activity's content — the root layer, the modals layer and the overlays layer, bottom to top, full size,
 * the last two hidden while empty —, and the registry of their nodes. Each activity has its own; the layers exist from
 * its creation, so a `setRoot` before they join the content works on them. The root is [RootCoordinator]'s, the modals
 * [ModalCoordinator]'s, the overlays [OverlayCoordinator]'s. Main thread only.
 */
class Navigator(
    /** The activity whose content holds the layers. */
    val activity: Activity,
) {
    /** The nodes of the activity by id. */
    val registry = ChildControllersRegistry()

    // The layers of the activity's content, bottom to top.
    internal val rootLayer = FrameLayout(activity)
    internal val modalsLayer = FrameLayout(activity).apply { visibility = View.GONE }
    internal val overlaysLayer = FrameLayout(activity).apply { visibility = View.GONE }

    private val factory = LayoutFactory(activity, registry)

    /** The screens prepared for a push into the stacks of the activity (`preparePush`). */
    internal val preparedPushes = PreparedPushes()

    /** The commands of the stacks of the activity. */
    internal val stackCommands = StackCommands(registry, factory, preparedPushes, ::isWithOldRoot)

    // The clock of the main thread, as the dispatcher has it: the tests put their own there.
    private val scheduler = AppearanceDispatcher.Scheduler { delay, task ->
        AppearanceDispatcher.scheduler.schedule(delay, task)
    }

    // The content is drawn again, so the splash's hold asks once more.
    private val firstRootDraw = FirstRootDraw(scheduler) {
        activity.findViewById<View>(android.R.id.content)?.invalidate()
    }

    private val modalViews = ModalViews(activity, modalsLayer, rootLayer)

    /** The modals of the activity. */
    internal val modals = ModalCoordinator(
        views = modalViews,
        readiness = ContentReadiness,
        scheduler = scheduler,
        host = ModalHost(),
    )

    /** The overlays of the activity, which stay over a new root. */
    internal val overlays = OverlayCoordinator(
        views = OverlayViews(activity, overlaysLayer),
        readiness = ContentReadiness,
        scheduler = scheduler,
        host = OverlayHost(),
    )

    private val roots = RootCoordinator(
        layer = RootLayerViews(),
        readiness = ContentReadiness,
        scheduler = scheduler,
        firstRoot = firstRootDraw,
        onRootChanged = { root = it },
        onRootShown = {
            onRootShown?.invoke()
            BackCallbackState.update(this)
        },
        isSeen = ::isActivitySeen,
        modals = modals,
    )

    /**
     * The system UI of the activity: its one owner, which lives and dies with the activity (`NavigationActivity` starts
     * and releases it).
     */
    internal val systemUi = SystemUiOwner(::systemUiLayers)

    // The layers that may wait to show: the root's; the modals and the overlays that wait join it (addWaitingLayer).
    private val waitingLayers = mutableListOf<WaitingLayer>(roots)

    /** The root the user sees under the modals; none before the first `setRoot`. */
    var root: ViewController<*>? = null
        private set

    /**
     * The layout the user sees: the top modal that shows or comes in, or the root; overlays don't count.
     */
    val visibleLayout: ViewController<*>?
        get() = modals.visibleLayout ?: root

    /** The activity's first root has drawn: the splash's hold of the window ends (`SplashHold`). */
    val isFirstRootDrawn: Boolean
        get() = firstRootDraw.isDrawn

    /** A `setRoot` showed its root and answered: the activity takes the copy of the splash away. */
    internal var onRootShown: (() -> Unit)? = null

    /**
     * Whether back has something to do in the navigation: the mirror of [handleBack], without acting — the top modal
     * first, then the root. The back callback asks it before each frame (`BackCallbackState`).
     */
    fun canHandleBack(): Boolean = modals.canHandleBack() ?: (root?.canHandleBack() == true)

    /**
     * The navigation's step of back, after JavaScript's listeners didn't take it: the top modal first (its content,
     * else it closes or holds), else the root does the first thing it can (a stack's top leaves or holds, an open side
     * menu closes, a pane closes); [listener] hears the end. False when it has nothing to do: the activity does its own
     * back.
     */
    fun handleBack(listener: CommandListener): Boolean =
        modals.handleBack(listener) ?: (root?.handleBack(listener) == true)

    /**
     * The stack whose top [handleBack] would take away with the default pop: in the top modal, else in the root; none
     * when back would do anything else.
     */
    internal fun stackPoppedByBack(): StackController? {
        val modalRoot = modals.backTargetRoot()
        return if (modalRoot != null) modalRoot.stackPoppedByBack() else root?.stackPoppedByBack()
    }

    /**
     * [layer] may wait to show from now on ([WaitingLayer]): the nodes in it ask it through [isInWaitingLayer] and
     * [shownScreensChanged]. The root's layer is there from the start.
     */
    internal fun addWaitingLayer(layer: WaitingLayer) {
        if (layer !in waitingLayers) waitingLayers += layer
    }

    /** [layer] no longer waits to show, nor will. */
    internal fun removeWaitingLayer(layer: WaitingLayer) {
        waitingLayers -= layer
    }

    /**
     * [node] is in a layer that still waits to show (a pending root, a modal or an overlay that waits): it isn't on
     * screen yet.
     */
    internal fun isInWaitingLayer(node: ViewController<*>): Boolean = waitingLayerOf(node) != null

    /**
     * A layout of [node] (a stack, the tabs, a side menu, a split view, a pair) changed the screens it shows: when it
     * is in a layer that still waits, the layer waits for the screens it shows now, and no longer for one that went
     * ([WaitingLayer.shownScreensChanged]); so does the operation of its stack that waits for [node] as its entry (a
     * pushed pair); a modal's sheet takes the hold of the screen it shows now.
     */
    internal fun shownScreensChanged(node: ViewController<*>) {
        waitingLayerOf(node)?.shownScreensChanged()
        node.findParentStack()?.queue?.shownScreensChanged(node)
        modals.visibleScreenChanged(node)
    }

    // The waiting layer [node] is in, up through the layouts around it; none.
    private fun waitingLayerOf(node: ViewController<*>): WaitingLayer? {
        var current: ViewController<*>? = node
        while (current != null) {
            val here = current
            waitingLayers.firstOrNull { it.waitingNode === here }?.let { return it }
            current = current.parentController
        }
        return null
    }

    /**
     * The node a command names by [id]: the registry's, except a node of a modal that goes with the old root of a
     * `setRoot` on its way, which commands don't find (`setRoot` step 3).
     */
    internal fun findNode(id: String): ViewController<*>? = registry.find(id)?.takeUnless(::isWithOldRoot)

    /** [node] is in a modal that goes with the old root of a `setRoot` on its way. */
    internal fun isWithOldRoot(node: ViewController<*>): Boolean = modals.isWithOldRoot(node)

    /** Whether [node] is in an overlay: the system UI of the top layer asks. */
    internal fun isInOverlay(node: ViewController<*>): Boolean = overlays.isInOverlay(node)

    /**
     * A `mergeOptions` of [options] went into [node] (`mergeOptions` step 5): a modal's sheet and `testID`s, an
     * overlay's `interceptTouchOutside` and `testID` are read again; the screens a layout shows take their padding over
     * or under the navigation bar again when the merge names it (`navigationBar.drawBehind`; a screen merged into does
     * it itself, `ComponentPresenter`); the system UI follows (only what changed is written).
     */
    internal fun optionsMerged(node: ViewController<*>, options: Options = Options()) {
        modals.optionsMergedInto(node, options)
        overlays.optionsMergedInto(node)
        val navigationBar = options.navigationBar
        val padding = navigationBar.drawBehind.hasValue() || navigationBar.backgroundColor.hasValue()
        if (padding && node is ParentController<*>) applyInsetsAgain(node)
        systemUi.invalidate()
    }

    // The screens [node] shows take the window's insets again.
    private fun applyInsetsAgain(node: ViewController<*>) {
        when (node) {
            is ParentController<*> -> node.visibleChildren().forEach(::applyInsetsAgain)
            is ComponentViewController -> node.frame.applyInsetsAgain()
            is ExternalComponentViewController -> node.frame.applyInsetsAgain()
        }
    }

    // The layers that show, bottom to top, as the system UI reads them: the root, the modals that show or come in
    // (not one that waits or goes), the overlays that show.
    private fun systemUiLayers(): List<SystemUiLayers.Layer> {
        val layers = ArrayList<SystemUiLayers.Layer>()
        root?.takeUnless { it.isDestroyed }?.let {
            layers += SystemUiLayers.layerOf(it, SystemUiLayers.Kind.Root, isSheet = false)
        }
        for (record in modals.modals) {
            if (!SystemUiLayers.modalCounts(record.effective)) continue
            layers += SystemUiLayers.layerOf(record.root, SystemUiLayers.Kind.Modal, record.isSheet)
        }
        for (record in overlays.overlays) {
            if (record.state == OverlayRecord.State.Shown) layers += SystemUiLayers.overlayOf(record.root)
        }
        return layers
    }

    /**
     * `setRoot`, after the shared steps of the command: the ids of its modals and overlays that show now and stay
     * reject it (an overlay replaces one with its id, N5); a node of a type without a maker, in its root, its modals or
     * its overlays, would reject with `ErrorTexts.notDoneYet` before any node is made (every type has one now); then
     * [RootCoordinator] shows its root, then its modals and its overlays, one by one, before the answer.
     */
    internal fun setRoot(layout: RootLayout, answer: CommandAnswer) {
        // Step 1: the app's direction, the root's own over the defaults', before the root's view exists.
        systemUi.applyDirection(AppDirection.ofRoot(layout.root.options, DefaultOptions.current))
        val replaced = layout.overlays.mapTo(HashSet()) { it.id }
        val shown = LayoutChecks.firstShownId(layout.modals + layout.overlays, { isStaying(it, replaced) }) { false }
        if (shown != null) return answer.reject(ErrorTexts.alreadyShown(shown))
        val made = (listOf(layout.root) + layout.modals + layout.overlays).all(factory::canMake)
        if (!made) return answer.reject(ErrorTexts.notDoneYet("setRoot"))
        val steps = layout.modals.mapIndexed { index, node ->
            val last = index == layout.modals.lastIndex
            RootCoordinator.LayerStep { done ->
                modals.showInChain(node.id, { makeLayer(node, isOverlay = false) }, last, done)
            }
        } + layout.overlays.map { node ->
            RootCoordinator.LayerStep { done ->
                overlays.showInChain(node.id, { makeLayer(node, isOverlay = true) }, done)
            }
        }
        roots.setRoot({ factory.create(layout.root) }, answer, steps)
    }

    // A modal or an overlay of a `setRoot`'s layout, made as its turn comes: an id that shows by then fails it.
    private fun makeLayer(node: LayoutNode, isOverlay: Boolean): ViewController<*> {
        val shown = LayoutChecks.firstShownId(listOf(node), ::isShownNow) { isOverlay && isReplacedOverlay(node, it) }
        if (shown != null) error(ErrorTexts.alreadyShown(shown))
        return factory.create(node)
    }

    /**
     * `showModal` after the shared steps: a node of a type without a maker would reject with `ErrorTexts.notDoneYet`
     * (every type has one now), an id that shows already rejects with `ErrorTexts.alreadyShown`, before any node; then
     * [ModalCoordinator] shows it.
     */
    internal fun showModal(layout: LayoutNode, answer: CommandAnswer) {
        if (!factory.canMake(layout)) return answer.reject(ErrorTexts.notDoneYet("showModal"))
        val shown = LayoutChecks.firstShownId(listOf(layout), ::isShownNow) { false }
        if (shown != null) return answer.reject(ErrorTexts.alreadyShown(shown))
        modals.show({ factory.create(layout) }, answer)
    }

    /** `dismissModal` after the shared steps, with its [options] read (`dismissModal` step 1). */
    internal fun dismissModal(componentId: String, options: Options?, answer: CommandAnswer) =
        modals.dismiss(componentId, options, answer)

    /** `dismissAllModals` after the shared steps, with its [options] read and the path of a value it couldn't. */
    internal fun dismissAllModals(options: Options?, unreadablePath: String?, answer: CommandAnswer) =
        modals.dismissAll(options, unreadablePath, answer)

    /**
     * `showOverlay` after the shared steps: as [showModal], an overlay with the root id of one that shows replacing it:
     * its ids aren't another screen's.
     */
    internal fun showOverlay(layout: LayoutNode, answer: CommandAnswer) {
        if (!factory.canMake(layout)) return answer.reject(ErrorTexts.notDoneYet("showOverlay"))
        val shown = LayoutChecks.firstShownId(listOf(layout), ::isShownNow) { isReplacedOverlay(layout, it) }
        if (shown != null) return answer.reject(ErrorTexts.alreadyShown(shown))
        overlays.show({ factory.create(layout) }, answer)
    }

    /** `dismissOverlay` after the shared steps. */
    internal fun dismissOverlay(componentId: String, answer: CommandAnswer) = overlays.dismiss(componentId, answer)

    /** `dismissAllOverlays` after the shared steps. */
    internal fun dismissAllOverlays(answer: CommandAnswer) = overlays.dismissAll(answer)

    // Whether the activity can be seen, so the frames of an animation come.
    private fun isActivitySeen(): Boolean =
        (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true

    // An id a new layout can't take, the one query of `showModal`, `showOverlay` and `setRoot`'s layers (`push` keeps
    // its own): a node of the activity has it — a modal of the old root's too, still on screen.
    private fun isShownNow(id: String): Boolean = registry.find(id) != null

    // A node of [layout]'s id is in the overlay [layout] replaces: same root id.
    private fun isReplacedOverlay(layout: LayoutNode, node: LayoutNode): Boolean {
        val shown = registry.find(node.id) ?: return false
        return overlays.isReplacedBy(layout.id, shown)
    }

    // A node shown now that stays over a new root: one of the overlays, unless the layout replaces it (N5).
    private fun isStaying(id: String, replaced: Set<String>): Boolean {
        val node = registry.find(id) ?: return false
        val overlay = overlays.recordOf(node) ?: return false
        return overlay.rootId !in replaced
    }

    /** The layers join [content], over what is in it (the copy of the splash), once. Main thread. */
    internal fun addLayers(content: ViewGroup) {
        for (layer in listOf(rootLayer, modalsLayer, overlaysLayer)) {
            if (layer.parent === content) continue
            (layer.parent as? ViewGroup)?.removeView(layer)
            content.addView(layer, ViewGroup.LayoutParams(MATCH, MATCH))
        }
    }

    /**
     * The activity resumed: the root, the modals and the overlays lay out again — what shows has its place in the
     * navigation again —; no screen events.
     */
    fun onResume() {
        rootLayer.requestLayout()
        modals.onResume()
        overlays.onResume()
    }

    /**
     * The colors and looks of what shows apply again: after new default options, and after a light/dark switch. The
     * root, then the modals, then the overlays (the top layer sets the system bars last); in each layer the nodes that
     * show (each layout's visible children), children before their layouts, each through its presenter with the options
     * it shows with now (`ViewController.resolveShownOptions`). What doesn't show takes the new values as it appears.
     * The modals' sheets take their colors again, the overlays their options from the defaults; the system bars follow,
     * the orientation as it is. Main thread only.
     */
    fun repaint() {
        layers().forEach(::repaintLayer)
        modals.repaint()
        overlays.defaultOptionsChanged()
        systemUi.invalidateKeepingOrientation()
    }

    /**
     * New default options came (`setDefaultOptions`): a [direction] they changed applies to the activity when it has a
     * root (window and React Native), then what shows is repainted — the bars and the tabs take the direction at once.
     * Main thread only.
     */
    internal fun defaultOptionsChanged(direction: String?) {
        if (root != null) systemUi.applyDirection(direction)
        repaint()
    }

    // The layers of the activity, bottom to top: the root, the modals not gone, the overlays.
    private fun layers(): List<ViewController<*>> = listOfNotNull(root) + modals.layers() + overlays.layers()

    /**
     * The JavaScript went: every layer goes — the modals, the overlays, the prepared pushes, the root and the root of a
     * `setRoot` on its way —, silently, as the number of the JavaScript moved first; nothing gets an answer. The
     * registry is empty after it, and the navigation takes the root of the new JavaScript; no copy of the splash comes
     * back. Main thread only.
     */
    fun destroy() {
        destroyLayers(answerPending = false)
        // Nothing the old screens told of their readiness holds for the screens of the new JavaScript.
        ScreenReadiness.reset()
        BarComponents.reset()
    }

    /**
     * The activity is destroyed: every layer goes, with the events of a destroy; a `setRoot` on its way resolves with
     * `''` (JavaScript sets its root again in the next activity), a modal's or an overlay's show and the commands that
     * wait for a modal with their roots' ids. Main thread only.
     */
    internal fun destroyWithActivity() {
        destroyLayers(answerPending = true)
        firstRootDraw.cancel()
    }

    // Every layer goes before the registry empties: nothing forgotten keeps its views.
    private fun destroyLayers(answerPending: Boolean) {
        modals.destroyAll()
        overlays.destroyAll()
        preparedPushes.destroyAll()
        roots.destroyAll(answerPending)
        rootLayer.removeAllViews()
        modalsLayer.removeAllViews()
        modalsLayer.visibility = View.GONE
        overlaysLayer.removeAllViews()
        overlaysLayer.visibility = View.GONE
        registry.clear()
    }

    // What the modals ask of the navigation.
    private inner class ModalHost : ModalCoordinator.Host {
        override val root: ViewController<*>?
            get() = this@Navigator.root

        override fun find(id: String): ViewController<*>? = registry.find(id)

        override fun isSeen(): Boolean = isActivitySeen()

        override fun addWaitingLayer(layer: WaitingLayer) = this@Navigator.addWaitingLayer(layer)

        override fun removeWaitingLayer(layer: WaitingLayer) = this@Navigator.removeWaitingLayer(layer)

        override fun backChanged() = BackCallbackState.update(this@Navigator)

        override fun log(text: String) {
            Log.e(NavigationLog.TAG, text)
        }
    }

    // What the overlays ask of the navigation.
    private inner class OverlayHost : OverlayCoordinator.Host {
        override fun find(id: String): ViewController<*>? = findNode(id)

        override fun addWaitingLayer(layer: WaitingLayer) = this@Navigator.addWaitingLayer(layer)

        override fun removeWaitingLayer(layer: WaitingLayer) = this@Navigator.removeWaitingLayer(layer)

        override fun log(text: String) {
            Log.e(NavigationLog.TAG, text)
        }
    }

    // The views of the root layer.
    private inner class RootLayerViews : RootCoordinator.Layer {
        // Under the current root: React Native's root view takes every touch, which must reach the root the user
        // sees until the new one goes up.
        override fun place(root: ViewController<*>) {
            val view = root.view
            (view.parent as? ViewGroup)?.removeView(view)
            view.alpha = 0f
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            rootLayer.addView(view, 0, FrameLayout.LayoutParams(MATCH, MATCH))
        }

        override fun raise(root: ViewController<*>) {
            val view = root.peekView() ?: return
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            view.bringToFront()
        }

        override fun show(root: ViewController<*>) {
            root.peekView()?.alpha = 1f
        }

        override fun remove(root: ViewController<*>) {
            val view = root.peekView() ?: return
            (view.parent as? ViewGroup)?.removeView(view)
        }

        // The new root over the old one, its modals carried with it; every animator ends at its final values when
        // stopped (a newer `setRoot`). The new root keeps its `enter` end values.
        override fun animate(
            old: ViewController<*>?,
            new: ViewController<*>,
            enter: ScreenAnimationOptions?,
            exit: ScreenAnimationOptions?,
            onEnd: () -> Unit,
        ): () -> Unit {
            val view = new.view
            view.alpha = 1f
            val leaving = listOfNotNull(old?.peekView()) + modalViews.carriedViews
            val animators = listOfNotNull(enter?.let { SideAnimator.build(view, it, SideAnimator.Base(alpha = 1f)) }) +
                leaving.mapNotNull { exit?.let { side -> SideAnimator.build(it, side) } }
            if (animators.isEmpty()) {
                onEnd()
                return {}
            }
            val set = AnimatorSet()
            set.playTogether(animators)
            set.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            set.start()
            return {
                set.removeAllListeners()
                set.end()
            }
        }

        // The modals of the old root leave with it, as its part of the transition.
        override fun fadeThrough(old: ViewController<*>, new: ViewController<*>, onEnd: () -> Unit): () -> Unit {
            val leaving = listOfNotNull(old.peekView()) + modalViews.carriedViews
            val animators = leaving.map(FadeThrough::exit) + FadeThrough.enter(new.view)
            val set = AnimatorSet()
            set.playTogether(animators)
            set.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            set.start()
            return {
                set.removeAllListeners()
                set.end()
            }
        }
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        @Volatile
        private var reloading = false

        /**
         * The pass of [repaint] over one layer: the nodes that show, children first, those whose view is made; a
         * layout's presenter gets the options it shows with, those of the end of its current-child chain.
         */
        internal fun repaintLayer(node: ViewController<*>) {
            (node as? ParentController<*>)?.visibleChildren()?.forEach(::repaintLayer)
            if (node.isDestroyed || !node.isViewCreated || node !is ChildController<*>) return
            node.presenter.onConfigurationChanged(node, node.resolveShownOptions(DefaultOptions.current))
        }

        /**
         * JavaScript reloads: the layouts that go now go with it, and the calls they wait for are dropped without an
         * answer.
         */
        @JvmStatic
        fun isReloading(): Boolean = reloading

        @JvmStatic
        fun setReloading(value: Boolean) {
            reloading = value
        }
    }
}

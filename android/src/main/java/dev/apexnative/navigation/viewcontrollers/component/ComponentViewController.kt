package dev.apexnative.navigation.viewcontrollers.component

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.react.ArrangementStates
import dev.apexnative.navigation.react.ReactView
import dev.apexnative.navigation.react.ScreenInsets
import dev.apexnative.navigation.react.ScreenRegions
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.react.events.ComponentType
import dev.apexnative.navigation.slots.SlotScreenView
import dev.apexnative.navigation.system.SystemUiOwner
import dev.apexnative.navigation.viewcontrollers.child.ChildController
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.parent.ChildOffsetFromRest
import dev.apexnative.navigation.viewcontrollers.viewcontroller.IReactView
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.component.ComponentLayout

/**
 * A React screen (`Component` of the layout): its content is a React root of its own, or the container of a slot screen
 * for a name starting with `slot:` ([ScreenContentFactory]). It tells its appearance through its content, which owns
 * the appearance record (`AppearanceDispatcher`). Its view, and with it its content, is made when the layout that holds
 * it is made, before it appears: a root's surfaces start as its view joins the window (`setRoot` step 2).
 *
 * The content fills the screen's view; the view keeps the room for the bars as its margins and what the system
 * covers as its padding ([ScreenFrame]), and its presenter (`ComponentPresenter`) paints it. While the view shows, each
 * layout of the window measures the screen's safe area and regions into the stores the hooks of JavaScript read
 * ([updateSafeArea]).
 */
class ComponentViewController(
    activity: Activity,
    childRegistry: ChildControllersRegistry,
    id: String,
    /** The name the screen was registered with in JavaScript, `slot:` included. */
    val componentName: String,
    presenter: Presenter,
    initialOptions: Options,
) : ChildController<ComponentLayout>(activity, childRegistry, id, presenter, initialOptions) {
    /** The room of the screen's view for the bars and the system. */
    internal val frame = ScreenFrame(this, consumesInsets = false)

    init {
        // The screen owns its id from now on, before its content exists: what an earlier screen with the id told of
        // its readiness (a root made again with the same ids) isn't this one's.
        ScreenReadiness.remove(id)
    }

    override val currentComponentName: String
        get() = componentName.removePrefix(ScreenContentFactory.SLOT_PREFIX)

    override fun createView(): ComponentLayout {
        val layout = ComponentLayout(activity, ScreenContentFactory.create(activity, id, componentName))
        frame.attach(layout)
        frame.onInsetsApplied = ::applyBackgroundAgain
        layout.onAttached = { frame.onAttached(layout) }
        layout.onWindowLayout = ::updateSafeArea
        return layout
    }

    /** What the screen shows, once its view is made: what a command waits for before the screen comes in. */
    internal val content: IReactView?
        get() = peekView()?.reactView

    /**
     * A command's wait for the content ended at its limit: the content counts as mounted, and the events held for it go
     * at once.
     */
    internal fun markContentReady() {
        when (val content = content) {
            is ReactView -> content.appearance.markReady()
            is SlotScreenView -> content.appearance.markReady()
            else -> Unit
        }
    }

    // The bar's buttons send ANN.NavigationButtonPressed with the screen's own id and JavaScript, whatever its content
    // (a React root, a slot).
    override fun sendOnNavigationButtonPressed(buttonId: String) =
        AppearanceDispatcher.navigationButtonPressed(this, buttonId)

    // The screen shows with its options, and the layout around takes its part of them (its bar, its FAB, its tabs).
    override fun applyOptions(options: Options) {
        presenter.applyOptions(this, options)
        performOnParentController { it.applyChildOptions(options, this) }
    }

    override fun getTopInset(): Int = frame.topRoom()

    override fun applyTopInset() = frame.applyRoom()

    override fun getBottomInset(): Int = frame.bottomRoom()

    override fun applyBottomInset() = frame.applyRoom()

    // A screen that appears has its view: will and did appear go through it, made when needed; its options apply as
    // it starts to appear. A screen whose view isn't made has shown nothing, so disappear makes none. A destroyed
    // screen tells nothing.
    override fun onViewWillAppear() {
        if (isDestroyed) return
        super.onViewWillAppear()
        val layout = view
        applyOptions(resolveCurrentOptions(DefaultOptions.current))
        layout.reactView.sendComponentWillStart(ComponentType.Component)
        // The components of its bar appear with it.
        findParentStack()?.barScreenWillAppear(this)
    }

    override fun onViewDidAppear() {
        if (isDestroyed) return
        view.reactView.sendComponentStart(ComponentType.Component)
        findParentStack()?.barScreenDidAppear(this)
        super.onViewDidAppear()
    }

    // The system UI follows what is on top now (a modal or an overlay that goes).
    override fun onViewDisappear() {
        if (isDestroyed) return
        peekView()?.reactView?.sendComponentStop(ComponentType.Component)
        findParentStack()?.barScreenDisappeared(this)
        super.onViewDisappear()
        SystemUiOwner.of(activity)?.invalidate()
    }

    /**
     * The screen starts to leave (`blurOnUnmount`): with `blurOnUnmount` in its resolved options, the view that has the
     * activity's focus lets go of it, and a field of React Native hides the keyboard as it does.
     */
    override fun onLeaveStarted() {
        if (isDestroyed) return
        if (resolveCurrentOptions(DefaultOptions.current).blurOnUnmount.isTrue) ScreenFocus.blur(activity)
    }

    /**
     * Measures the screen's safe area and its regions into the stores: what the bars and the system cover of the React
     * content, in dp ([ScreenSafeArea.insets]), the part of it its safe area view keeps the content off
     * ([ScreenSafeArea.paddedEdges]), and the folds and the cutout over it. Only while the screen's view shows, never
     * while a layout is on its way: the next layout of the window measures then; a screen prepared for a push measures
     * as its stack laid it out. A screen under another in its stack keeps what it has. Main thread.
     */
    internal fun updateSafeArea() {
        if (isDestroyed) return
        val layout = peekView() ?: return
        val parent = parentController
        val content = layout.reactView.asView()
        // A screen prepared for a push measures too, though it doesn't show, as its stack laid it out.
        val prepared = parent?.measuresThoughNotShown(this) == true
        val measures = ScreenSafeArea.measuresNow(
            shown = layout.isShown || prepared,
            layoutPending = !prepared && isLayoutPending(content),
            topOfStack = parent?.isTopOfStack(this) ?: true,
            hasSafeArea = ScreenInsets.padding(id) != null,
        )
        if (!measures) return
        val windowInsets = ViewCompat.getRootWindowInsets(content) ?: return
        val window = content.rootView ?: return
        val system = windowInsets.getInsets(SYSTEM_EDGES)
        val place = ScreenSafeArea.placeInWindow(stepsToWindow(content), content.width, content.height)
        val insets = ScreenSafeArea.insets(
            windowWidth = window.width,
            windowHeight = window.height,
            system = ScreenSafeArea.Box(system.left, system.top, system.right, system.bottom),
            root = place,
            headerBottom = parent?.headerBottomOf(this),
            tabsTop = parent?.tabsTopOf(this),
            density = activity.resources.displayMetrics.density,
        )
        val edges = ScreenSafeArea.paddedEdges(
            safeAreaEdges = resolveCurrentOptions(DefaultOptions.current).layout.safeAreaEdges,
            topFree = parent?.leavesTopEdgeFree(this) == true,
            bottomFree = parent?.leavesBottomEdgeFree(this) == true,
        )
        ScreenInsets.update(activity, id, insets, ScreenSafeArea.padding(insets, edges))
        ScreenRegions.update(activity, id, Rect(place.left, place.top, place.right, place.bottom), windowInsets)
    }

    // A layout asked for on the content or on a view around it: it isn't in its place yet.
    private fun isLayoutPending(content: View): Boolean {
        var current: View? = content
        while (current != null) {
            if (current.isLayoutRequested) return true
            current = current.parent as? View
        }
        return false
    }

    // The views from the content up to the window, each with its place and its parent's scroll, at rest as [RestPlace]
    // counts them, the same rule as the header's bottom and the tabs' top. A child that moves counts where it rests
    // (`ChildOffsetFromRest`): a side menu's drawer where the open menu is, a sliding pane where it is open: the split
    // view gives the pane's distance from its open place (`ChildOffsetFromRest`), which takes the pane's move away.
    private fun stepsToWindow(content: View): List<ScreenSafeArea.Step> {
        val steps = ArrayList(RestPlace.stepsToWindow(content))
        val offset = offsetFromRest()
        if (offset != 0) steps += ScreenSafeArea.Step(offset, 0, 0, 0)
        return steps
    }

    // How far the layouts around move this screen from where it rests, along the width: the sum of what each layout
    // that moves a child gives for the child on the way to this screen.
    private fun offsetFromRest(): Int {
        var offset = 0
        var node: ViewController<*> = this
        var parent = parentController
        while (parent != null) {
            if (parent is ChildOffsetFromRest) offset += parent.childOffsetFromRest(node)
            node = parent
            parent = parent.parentController
        }
        return offset
    }

    // New window insets: the status bar's band of the background may have changed.
    private fun applyBackgroundAgain() {
        (presenter as? ComponentPresenter)?.applyBackground(this, resolveCurrentOptions(DefaultOptions.current))
    }

    /**
     * Destroys the screen once: it disappears when it showed (its `ANN.ScreenPopped`, when it left a stack, went
     * before), leaves the registry, its content stops — a view never made isn't made for it, and the screen counts as
     * destroyed all the same —, its view stops listening to the window and to the scroll of its lists and leaves its
     * parent, the link to its layout breaks, and what the synchronous methods kept for it goes, unless a later screen
     * with the same id is in the registry: that is its own then.
     */
    override fun destroy() {
        if (isDestroyed) return
        onViewDisappear()
        super.destroy()
        // The layout it is in lets go of what it kept for it while its view is still there: the components of its bar
        // stop before its own surface, its search goes, its stack forgets its FAB.
        performOnParentController { it.onChildDestroyed(this) }
        peekView()?.let { layout ->
            layout.destroy()
            (layout.parent as? ViewGroup)?.removeView(layout)
        }
        setParentController(null)
        if (isIdTakenByAnother) return
        ScreenInsets.remove(id)
        ScreenRegions.remove(id)
        ArrangementStates.remove(id)
        ScreenReadiness.remove(id)
    }

    private companion object {
        // What the system covers at the edges of the window: its bars and the cutout.
        val SYSTEM_EDGES = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }
}

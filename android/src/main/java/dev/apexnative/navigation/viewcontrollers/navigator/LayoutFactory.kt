package dev.apexnative.navigation.viewcontrollers.navigator

import android.app.Activity
import dev.apexnative.navigation.layout.ArrangementData
import dev.apexnative.navigation.layout.LayoutNode
import dev.apexnative.navigation.layout.LayoutType
import dev.apexnative.navigation.nativescreen.NativeScreenContext
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.react.ReadableJson
import dev.apexnative.navigation.react.events.EventEmitter
import dev.apexnative.navigation.viewcontrollers.arrangement.ArrangementController
import dev.apexnative.navigation.viewcontrollers.arrangement.ArrangementDefinition
import dev.apexnative.navigation.viewcontrollers.arrangement.SecondaryScreenPresenter
import dev.apexnative.navigation.viewcontrollers.bottomtabs.BottomTabsController
import dev.apexnative.navigation.viewcontrollers.bottomtabs.BottomTabsPresenter
import dev.apexnative.navigation.viewcontrollers.child.ChildController
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.ComponentPresenter
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.externalcomponent.ExternalComponentViewController
import dev.apexnative.navigation.viewcontrollers.sidemenu.SideMenuController
import dev.apexnative.navigation.viewcontrollers.sidemenu.SideMenuPresenter
import dev.apexnative.navigation.viewcontrollers.splitview.SplitViewController
import dev.apexnative.navigation.viewcontrollers.splitview.SplitViewPresenter
import dev.apexnative.navigation.viewcontrollers.stack.StackController
import dev.apexnative.navigation.viewcontrollers.stack.StackPresenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.json.JSONObject

/**
 * Makes the nodes of a layout that a command read and checked: one maker per type of node, each with the activity and
 * its registry, the node's options as its initial options (read on the module thread, one-shot options included) and
 * its id. A node registers itself as it is made (`ChildController`). Every type has its maker; a command still asks
 * [canMake] before it makes anything, so a type without one would reject before any view. The makers of the split view
 * and the pair are the only core code that names their module classes. Main thread only.
 */
internal class LayoutFactory(
    private val activity: Activity,
    private val registry: ChildControllersRegistry,
) {
    private val makers: Map<LayoutType, (LayoutNode) -> ViewController<*>> = mapOf(
        LayoutType.Component to { node -> component(node) },
        LayoutType.ExternalComponent to { node -> nativeScreen(node) },
        LayoutType.Stack to ::stack,
        LayoutType.BottomTabs to ::tabs,
        LayoutType.SideMenuRoot to ::sideMenu,
        LayoutType.SplitView to ::splitView,
        LayoutType.Arrangement to ::pair,
    )

    /**
     * Whether every node of [node]'s tree is of a type this factory makes. The sides of a side menu are places, made
     * by their side menu's maker: a side asked alone isn't made.
     */
    fun canMake(node: LayoutNode): Boolean = node.allNodes().all { it.isSideMenuSide || it.type in makers }

    /** The node of [node], with the nodes under it; [canMake] said yes. */
    fun create(node: LayoutNode): ViewController<*> {
        val maker = checkNotNull(makers[node.type]) {
            "the navigation doesn't make a node of type '${node.type.jsName}'"
        }
        return maker(node)
    }

    // A stack: its screens through their makers, bottom to top, with their keys; the last shows with it, the others
    // make their content after it appeared. The first gets no back arrow, the others do.
    private fun stack(node: LayoutNode): ViewController<*> {
        val stack = StackController(activity, registry, node.id, StackPresenter(activity), node.options)
        val children = node.children.map(::create)
        stack.setInitialChildren(children, node.children.map { it.key })
        return stack
    }

    // Bottom tabs: each child through its maker, in the order of the layout, before the tabs; the tabs read their first
    // tab from the layout's own options, one-shot options included. The reader checked the number of tabs before.
    private fun tabs(node: LayoutNode): ViewController<*> {
        val children = node.children.map(::create)
        return BottomTabsController(activity, registry, node.id, BottomTabsPresenter(activity), node.options, children)
    }

    // A side menu: the node of each side through its maker, then the side menu; the sides are no nodes of their own.
    // The reader checked one center and at most one menu on each side, each with one child; the side menu reads the
    // layout's own `visible` as it first shows.
    private fun sideMenu(node: LayoutNode): ViewController<*> {
        fun childOf(type: LayoutType): ViewController<*>? =
            node.children.firstOrNull { it.type == type }?.children?.firstOrNull()?.let(::create)
        val center = requireNotNull(childOf(LayoutType.SideMenuCenter)) { "the side menu '${node.id}' has no center" }
        val left = childOf(LayoutType.SideMenuLeft)
        val right = childOf(LayoutType.SideMenuRight)
        val presenter = SideMenuPresenter(activity)
        return SideMenuController(activity, registry, node.id, presenter, node.options, center, left, right)
    }

    // A split view: its primary, secondary and compact columns through their makers (Android has no other column, and
    // the reader registered nothing of the others), then the split view with its `splitView` over the defaults; later
    // options reach it through its merges and its presenter's repaint. The reader checked the primary and the
    // secondary.
    private fun splitView(node: LayoutNode): ViewController<*> {
        val columns = node.columnChildren()
        val made = ArrayList<ViewController<*>>(3)
        fun column(name: String): ViewController<*>? = columns[name]?.let(::create)?.also { made += it }
        try {
            val primary = requireNotNull(column(LayoutNode.PRIMARY)) { "the split view '${node.id}' has no primary" }
            val secondary =
                requireNotNull(column(LayoutNode.SECONDARY)) { "the split view '${node.id}' has no secondary" }
            val compact = column(LayoutNode.COMPACT)
            val split = node.options.splitView.copy()
                .apply { mergeWithDefault(DefaultOptions.current.splitView) }
                .toSplitViewOptions()
            val presenter = SplitViewPresenter(activity)
            val splitView = SplitViewController(
                activity,
                registry,
                node.id,
                presenter,
                node.options,
                split,
                primary,
                secondary,
                compact,
                EventEmitter.current(),
            )
            presenter.onSplitOptions = splitView::setSplitOptions
            return splitView
        } catch (e: Throwable) {
            made.forEach(ViewController<*>::destroy)
            throw e
        }
    }

    // A pair: the primary as any screen, the secondary with the pair's presenter of a secondary screen, then the pair,
    // whose definition the module reads from the node's data with The pair's defaults. The reader checked two screens.
    private fun pair(node: LayoutNode): ViewController<*> {
        fun child(index: Int): LayoutNode =
            requireNotNull(node.children.getOrNull(index)) { "the pair '${node.id}' has no screen $index" }
        val made = ArrayList<ViewController<*>>(2)
        try {
            val primary = screen(child(0)).also { made += it }
            val secondary = screen(child(1), SecondaryScreenPresenter(activity, DefaultOptions.current))
                .also { made += it }
            val definition = ArrangementDefinition.parse(definitionData(node.arrangement))
            val presenter = Presenter(activity, DefaultOptions.current)
            return ArrangementController(
                activity, registry, node.id, presenter, node.options, definition, primary, secondary,
            )
        } catch (e: Throwable) {
            made.forEach(ViewController<*>::destroy)
            throw e
        }
    }

    // A screen of a pair, React or native.
    private fun screen(node: LayoutNode, presenter: Presenter = ComponentPresenter(activity)): ChildController<*> =
        if (node.type == LayoutType.ExternalComponent) nativeScreen(node, presenter) else component(node, presenter)

    // A React screen: a React root of its own, or the container of a slot screen (`slot:` kept in its name).
    private fun component(
        node: LayoutNode,
        presenter: Presenter = ComponentPresenter(activity),
    ): ChildController<*> = ComponentViewController(
        activity,
        registry,
        node.id,
        requireNotNull(node.name) { "the screen '${node.id}' has no name" },
        presenter,
        node.options,
    )

    // A native screen: its context is made with the node, with the id, the props of the layout (none: empty), the names
    // of the functions among them and the sender of the events; the app's creator makes its view later, as the view is
    // made.
    private fun nativeScreen(
        node: LayoutNode,
        presenter: Presenter = ComponentPresenter(activity),
    ): ChildController<*> {
        val name = requireNotNull(node.name) { "the native screen '${node.id}' has no name" }
        val props = node.passProps?.let(ReadableJson::toJson) ?: JSONObject()
        val context = NativeScreenContext(node.id, props, node.functions, EventEmitter.current())
        return ExternalComponentViewController(
            activity,
            registry,
            node.id,
            name,
            context,
            presenter,
            node.options,
        )
    }

    private companion object {
        // The data of a pair as the module reads it: the fields JavaScript gave, the module's defaults for the rest
        // (`split`, `both`, 0.5, `trailing`, `primary`). A fraction that isn't a finite number is left out: the JSON
        // takes none, and the default applies.
        fun definitionData(data: ArrangementData?): JSONObject = JSONObject().apply {
            data?.style?.let { put("style", it) }
            data?.axes?.let { put("axes", it) }
            data?.fraction?.takeIf { it.isFinite() }?.let { put("fraction", it) }
            data?.primaryEdge?.let { put("primaryEdge", it) }
            data?.compact?.let { put("compact", it) }
        }
    }
}

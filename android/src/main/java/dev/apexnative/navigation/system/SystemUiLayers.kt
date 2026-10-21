package dev.apexnative.navigation.system

import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.viewcontrollers.bottomtabs.BottomTabsController
import dev.apexnative.navigation.viewcontrollers.modal.ModalRecord
import dev.apexnative.navigation.viewcontrollers.parent.OptionsScreen
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.parent.nodeWith
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * Which layer of an activity decides each group of the system UI. The layers that show, bottom to top: the root, the
 * modals that show or come in, the overlays that show. The top modal (any style) or else the root decides every group,
 * but a sheet leaves the status bar to the first layer under it that isn't a sheet. An overlay decides a group only
 * when its own and its parents' options name a key of it — never the app's defaults —, the topmost such overlay; the
 * groups it doesn't name stay with the layers under it. [pick] is pure; [layerOf] and [overlayOf] read a layer's nodes.
 * Main thread only.
 */
internal object SystemUiLayers {
    /**
     * The groups of the system UI a layer may decide on Android. [Keyboard] is whether the window resizes for the
     * keyboard without edge to edge; the top layer that isn't an overlay decides it (overlays don't name it).
     */
    enum class Group { StatusBar, NavigationBar, Orientation, Keyboard }

    /** What a layer is in the activity. */
    enum class Kind { Root, Modal, Overlay }

    /**
     * A layer that shows: its [kind], whether it is a sheet, the groups its options name (counted for overlays), and
     * the screen that decides for it, read only when asked.
     */
    class Layer(
        val kind: Kind,
        val isSheet: Boolean,
        val named: Set<Group>,
        read: () -> SystemUiRules.Screen,
    ) {
        /** The screen that decides for the layer: the end of its shown chain, with its resolved options. */
        val screen: SystemUiRules.Screen by lazy(LazyThreadSafetyMode.NONE, read)
    }

    /** The layer that decides each group; none for a group no layer decides: the window keeps it as it is. */
    class Picked(
        val statusBar: Layer?,
        val navigationBar: Layer?,
        val orientation: Layer?,
        val keyboard: Layer?,
    )

    /** Who decides each group among [layers], bottom to top; none when no layer shows. */
    fun pick(layers: List<Layer>): Picked? {
        if (layers.isEmpty()) return null
        val under = layers.filter { it.kind != Kind.Overlay }
        val overlays = layers.filter { it.kind == Kind.Overlay }
        fun overlayFor(group: Group): Layer? = overlays.lastOrNull { group in it.named }
        val top = under.lastOrNull()
        // A sheet keeps the status bar of the layer under it; with sheets only (no root yet) nothing decides it.
        val statusBar = under.lastOrNull { !it.isSheet }
        return Picked(
            statusBar = overlayFor(Group.StatusBar) ?: statusBar,
            navigationBar = overlayFor(Group.NavigationBar) ?: top,
            orientation = overlayFor(Group.Orientation) ?: top,
            keyboard = top,
        )
    }

    /**
     * Whether a modal in [state] counts as a layer: it shows or comes in; not one that waits, goes, or went with an
     * old root (its effective state, `ModalRecord.effective`).
     */
    fun modalCounts(state: ModalRecord.State): Boolean =
        state == ModalRecord.State.Presenting || state == ModalRecord.State.Shown

    /** The groups [options] name: a key of the status bar, of the navigation bar, `layout.orientation`. */
    fun namedGroups(options: Options): Set<Group> {
        val named = HashSet<Group>()
        val statusBar = options.statusBar
        // Only the keys Android reads: `animate`, `hideWithTopBar` and `blur` are iOS's.
        val androidStatusBar = statusBar.visible.hasValue() || statusBar.style.hasValue() ||
            statusBar.backgroundColor.hasValue() || statusBar.drawBehind.hasValue() || statusBar.translucent.hasValue()
        if (androidStatusBar) named += Group.StatusBar
        if (options.navigationBar.hasValue()) named += Group.NavigationBar
        if (options.layout.orientation != null) named += Group.Orientation
        return named
    }

    /** The root or a modal ([kind]) whose top node is [node]: its shown options with the app's defaults. */
    fun layerOf(node: ViewController<*>, kind: Kind, isSheet: Boolean): Layer =
        Layer(kind, isSheet, emptySet()) { screenOf(node, node.resolveShownOptions(DefaultOptions.current)) }

    /**
     * An overlay whose top node is [node]: what its own and its parents' options name, and its values from those
     * options only, without the app's defaults.
     */
    fun overlayOf(node: ViewController<*>): Layer {
        val options = node.resolveShownOptions(Options())
        return Layer(Kind.Overlay, isSheet = false, named = namedGroups(options)) { screenOf(node, options) }
    }

    // The screen at the end of the chain [node] shows with (as `ParentController.resolveShownOptions` walks: a pair's
    // options screen, else the current child), and where it is.
    private fun screenOf(node: ViewController<*>, options: Options): SystemUiRules.Screen {
        val screen = shownEnd(node)
        return SystemUiRules.Screen(
            options = options,
            inStack = screen.findParentStack() != null,
            inTabs = screen.parentController?.let { nodeWith<BottomTabsController>(it) } != null,
            inSheet = screen.isInSheet,
        )
    }

    private fun shownEnd(node: ViewController<*>): ViewController<*> {
        var current = node
        while (true) {
            val parent = current as? ParentController<*> ?: return current
            val optionsScreen = (parent as? OptionsScreen)?.optionsScreen
            current = if (optionsScreen != null && optionsScreen !== parent) {
                optionsScreen
            } else {
                parent.currentChild ?: return parent
            }
        }
    }
}

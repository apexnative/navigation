package dev.apexnative.navigation.layout

import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry

/**
 * The checks of a read layout against what already shows, which a command makes on the main thread before it makes
 * any view. Main thread only.
 */
internal object LayoutChecks {
    private val stackChildren = setOf(LayoutType.Component, LayoutType.ExternalComponent, LayoutType.Arrangement)

    /**
     * The first id of [tree] (sides of side menus left out: they have no id of their own to show under) that
     * [registry] already holds and [allowed] doesn't excuse; none when there is none. `push`, `showModal`, `setRoot`
     * (against the modals and overlays that stay) and `setStackRoot` (the ids of its own stack allowed) reject with
     * `ErrorTexts.alreadyShown` for it.
     */
    @JvmStatic
    @JvmOverloads
    fun firstShownId(
        tree: LayoutNode,
        registry: ChildControllersRegistry,
        allowed: (LayoutNode) -> Boolean = { false },
    ): String? = firstShownId(listOf(tree), { registry.find(it) != null }, allowed)

    /** [firstShownId] over several trees, in order: the screens of `setStackRoot`, the layers of `setRoot`. */
    @JvmStatic
    @JvmOverloads
    fun firstShownId(
        trees: List<LayoutNode>,
        registry: ChildControllersRegistry,
        allowed: (LayoutNode) -> Boolean = { false },
    ): String? = firstShownId(trees, { registry.find(it) != null }, allowed)

    /** [firstShownId] with the question "is this id shown" asked of [isShown]. */
    @JvmStatic
    fun firstShownId(trees: List<LayoutNode>, isShown: (String) -> Boolean, allowed: (LayoutNode) -> Boolean): String? =
        trees.asSequence()
            .flatMap { it.allNodes().asSequence() }
            .firstOrNull { !it.isSideMenuSide && isShown(it.id) && !allowed(it) }
            ?.id

    /**
     * Whether the stack [stackId] takes [node], which a `push` or a `setStackRoot` brings into it: the rejection when
     * it doesn't (a container in a stack), none when it does.
     */
    @JvmStatic
    fun checkStackChild(node: LayoutNode, stackId: String): String? =
        if (isStackChild(node)) null else ErrorTexts.stackTakesScreens(stackId, node.type.jsName)

    /** Whether a stack takes [node]: a screen, a native screen or a pair, not a container. */
    @JvmStatic
    fun isStackChild(node: LayoutNode): Boolean = node.type in stackChildren
}

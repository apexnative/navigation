package dev.apexnative.navigation.layout

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.Options

/**
 * A node of a layout as a command brings it, read and checked whole by [LayoutReader] on the module thread before
 * anything is queued, then read on the main thread. It doesn't change after it is read; its options are read into
 * [Options] on the module thread too, and `passProps` stays as React Native gave it.
 */
internal data class LayoutNode(
    /** The node's id, as JavaScript gave it. */
    val id: String,
    /** The node's type. */
    val type: LayoutType,
    /** `data.name`: the screen's module, `slot:<Name>`, the native screen's registration, the pair's name. */
    val name: String?,
    /**
     * `data.options`, read: the node's own options as the layout gives them, one-shot options included; empty when the
     * node has none.
     */
    val options: Options,
    /** `data.key` of a screen. */
    val key: String?,
    /** `data.passProps` of a native screen; a React screen's props stay in JavaScript. */
    val passProps: ReadableMap?,
    /** `data.functions` of a native screen: the names of the functions among its props. */
    val functions: List<String>,
    /** `data.columns` of a split view as given: the column of each child, in order; empty when missing. */
    val columns: List<String>,
    /** The data of a pair; none for other nodes. */
    val arrangement: ArrangementData?,
    /** The children; empty for a screen, whose children aren't read. */
    val children: List<LayoutNode>,
) {
    /** A screen: `Component` or `ExternalComponent`. */
    val isScreen: Boolean
        get() = type.isScreen

    /** A side of a side menu: commands don't find it by its id. */
    val isSideMenuSide: Boolean
        get() = type.isSideMenuSide

    /** The screen of a single React tree: its name is `slot:<Name>`. */
    val isSlot: Boolean
        get() = type == LayoutType.Component && name?.startsWith(SLOT_PREFIX) == true

    /** The name in events and in the navigation state: [name] without `slot:`. */
    val screenName: String?
        get() = if (isSlot) name?.removePrefix(SLOT_PREFIX) else name

    /** This node and every node under it, depth first. */
    fun allNodes(): List<LayoutNode> = mutableListOf<LayoutNode>().also(::collect)

    /**
     * The children of a split view by column: child i is the column `columns[i]`; without `columns` the first two
     * children are `primary` and `secondary`. Names that aren't columns, and children without a name, are left out; of
     * two children with one column the first counts.
     */
    fun columnChildren(): Map<String, LayoutNode> {
        val result = LinkedHashMap<String, LayoutNode>()
        children.forEachIndexed { index, child ->
            columnName(columns, index)?.let { result.putIfAbsent(it, child) }
        }
        return result
    }

    private fun collect(into: MutableList<LayoutNode>) {
        into += this
        children.forEach { it.collect(into) }
    }

    companion object {
        const val SLOT_PREFIX = "slot:"

        const val PRIMARY = "primary"
        const val SECONDARY = "secondary"
        const val SUPPLEMENTARY = "supplementary"
        const val COMPACT = "compact"
        const val INSPECTOR = "inspector"

        private val COLUMN_NAMES = setOf(PRIMARY, SECONDARY, SUPPLEMENTARY, COMPACT, INSPECTOR)
        private val COLUMNS_WITHOUT_NAMES = listOf(PRIMARY, SECONDARY)

        /** The column of the child at [index] of a split view with [columns]; none for a child without one. */
        internal fun columnName(columns: List<String>, index: Int): String? {
            val names = columns.ifEmpty { COLUMNS_WITHOUT_NAMES }
            return names.getOrNull(index)?.takeIf { it in COLUMN_NAMES }
        }
    }
}

/** The root, the modals and the overlays of `setRoot`. */
internal data class RootLayout(
    /** The new root of the app. */
    val root: LayoutNode,
    /** Shown after the root, in order, each over the one before. */
    val modals: List<LayoutNode>,
    /** Shown after the modals, one by one. */
    val overlays: List<LayoutNode>,
)

/**
 * The data of a pair as JavaScript gave it: a value of the wrong kind is none. The pair's defaults are applied where
 * the pair is made.
 */
internal data class ArrangementData(
    /** `split` or `overlay`. */
    val style: String?,
    /** `horizontal`, `vertical` or `both`. */
    val axes: String?,
    /** The share of the primary screen along the division, 0–1. */
    val fraction: Double?,
    /** `leading`, `trailing`, `top` or `bottom`: the primary's edge in `overlay`. */
    val primaryEdge: String?,
    /** `primary` or `pair`: what a window too small for the pair shows. */
    val compact: String?,
)

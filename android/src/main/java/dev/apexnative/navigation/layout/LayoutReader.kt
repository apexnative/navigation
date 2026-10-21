package dev.apexnative.navigation.layout

import android.content.Context
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import dev.apexnative.navigation.NavigationApplication
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.OptionsReader
import dev.apexnative.navigation.react.ErrorTexts

/**
 * Reads the layout of a command straight from React Native's data, once, into [LayoutNode]s, and checks it whole
 * before any view is made: the type and the id of every node, the name of every screen, the registration of every
 * native screen, the nesting of the layouts, and one id per node of the layout. The first error found throws a
 * [LayoutException] with the command's rejection. Fields the layout doesn't define are ignored. Once the whole layout
 * passed its checks, the options of every node are read ([OptionsReader]), depth first in layout order, so the first
 * error is the same on iOS: a value that can't be read rejects with `unreadableOption`. Runs on the module thread;
 * [isNativeScreen] reads the app's registry under its own lock; [Context] is the app's, which platform colors resolve
 * with (none in unit tests).
 */
internal object LayoutReader {
    /** One node: the layout of `push`, `preparePush`, `showModal`, `showOverlay`, `showInColumn`. */
    @JvmStatic
    fun node(map: ReadableMap?, isNativeScreen: (String) -> Boolean = ::isRegistered): LayoutNode =
        node(map, null, isNativeScreen)

    /** As [node], its options read with the app's [context]. */
    @JvmStatic
    fun node(map: ReadableMap?, context: Context?, isNativeScreen: (String) -> Boolean = ::isRegistered): LayoutNode {
        val reading = Reading(isNativeScreen, context)
        val node = reading.top(map)
        reading.readOptions()
        return reading.withOptions(node)
    }

    /** The layout of `setRoot`: a root, and the modals and overlays, none when missing. */
    @JvmStatic
    fun root(map: ReadableMap?, isNativeScreen: (String) -> Boolean = ::isRegistered): RootLayout =
        root(map, null, isNativeScreen)

    /** As [root], its options read with the app's [context]. */
    @JvmStatic
    fun root(map: ReadableMap?, context: Context?, isNativeScreen: (String) -> Boolean = ::isRegistered): RootLayout {
        val root = map?.mapOrNull("root")
        if (map == null || root == null) throw LayoutException(ErrorTexts.rootMissing())
        val reading = Reading(isNativeScreen, context)
        val layout = RootLayout(
            root = reading.top(root),
            modals = map.arrayOrNull("modals")?.let(reading::tops).orEmpty(),
            overlays = map.arrayOrNull("overlays")?.let(reading::tops).orEmpty(),
        )
        reading.readOptions()
        return RootLayout(
            root = reading.withOptions(layout.root),
            modals = layout.modals.map(reading::withOptions),
            overlays = layout.overlays.map(reading::withOptions),
        )
    }

    /** The screens of `setStackRoot`, bottom to top; ids count once across all of them. */
    @JvmStatic
    fun nodes(array: ReadableArray?, isNativeScreen: (String) -> Boolean = ::isRegistered): List<LayoutNode> =
        nodes(array, null, isNativeScreen)

    /** As [nodes], their options read with the app's [context]. */
    @JvmStatic
    fun nodes(
        array: ReadableArray?,
        context: Context?,
        isNativeScreen: (String) -> Boolean = ::isRegistered,
    ): List<LayoutNode> {
        val reading = Reading(isNativeScreen, context)
        val nodes = array?.let(reading::tops).orEmpty()
        reading.readOptions()
        return nodes.map(reading::withOptions)
    }

    /** The id of the node [map], read without failing: the texts of failures that come before the layout is read. */
    @JvmStatic
    fun idOf(map: ReadableMap?): String = try {
        map?.stringOrNull("id").orEmpty()
    } catch (e: RuntimeException) {
        ""
    }

    private fun isRegistered(name: String): Boolean =
        NavigationApplication.instanceOrNull?.isNativeScreenRegistered(name) == true

    // Where a node stands: the parent it is checked against, the menu of a side, the column of a split view's child.
    private class Place(
        val parent: LayoutType? = null,
        val parentId: String = "",
        val menuId: String = "",
        val column: String? = null,
    )

    // One reading of one command's layout: the ids it has met so far, and the options of each node, in layout order.
    private class Reading(private val isNativeScreen: (String) -> Boolean, private val context: Context?) {
        private val ids = HashSet<String>()
        private val rawOptions = LinkedHashMap<String, ReadableMap?>()
        private val options = HashMap<String, Options>()

        // The options of every node met, once the whole layout passed its checks; the first value that can't be
        // read rejects the command.
        fun readOptions() {
            rawOptions.forEach { (id, map) ->
                val read = OptionsReader.read(map, context)
                read.unreadablePath?.let { throw LayoutException(ErrorTexts.unreadableOption(it, id)) }
                options[id] = read.options
            }
        }

        // [node] and the nodes under it with the options read for them.
        fun withOptions(node: LayoutNode): LayoutNode = node.copy(
            options = options[node.id] ?: Options(),
            children = node.children.map(::withOptions),
        )

        fun top(map: ReadableMap?): LayoutNode = read(map, Place())

        fun tops(array: ReadableArray): List<LayoutNode> = (0 until array.size()).map { top(array.mapAt(it)) }

        private fun read(map: ReadableMap?, place: Place): LayoutNode {
            val typeName = map?.stringOrNull("type")
            val type = LayoutType.fromName(typeName)
            if (map == null || type == null) throw LayoutException(ErrorTexts.unknownNodeType(typeName.orEmpty()))
            val id = map.stringOrNull("id")?.takeIf { it.isNotEmpty() }
                ?: throw LayoutException(ErrorTexts.nodeWithoutId(type.jsName))
            checkPlace(type, id, place)
            if (!ids.add(id)) throw LayoutException(ErrorTexts.idGivenTwice(id))

            val data = map.mapOrNull("data")
            rawOptions[id] = data?.mapOrNull("options")
            val name = data?.stringOrNull("name")
            if (type.isScreen && name.isNullOrEmpty()) {
                throw LayoutException(ErrorTexts.nodeWithoutName(type.jsName, id))
            }
            if (type == LayoutType.ExternalComponent && name != null && !isNativeScreen(name)) {
                throw LayoutException(ErrorTexts.nativeScreenNotRegistered(name))
            }

            // A screen's children aren't read.
            val childMaps = if (type.isScreen) null else map.arrayOrNull("children")
            val count = childMaps?.size() ?: 0
            checkCount(type, id, count, place)
            val isNative = type == LayoutType.ExternalComponent
            val columns = if (type == LayoutType.SplitView) data?.arrayOrNull("columns").strings() else emptyList()
            val children = (0 until count).map { index ->
                val column = if (type == LayoutType.SplitView) LayoutNode.columnName(columns, index) else null
                read(childMaps?.mapAt(index), Place(type, id, place.parentId, column))
            }
            val node = LayoutNode(
                id = id,
                type = type,
                name = name,
                options = Options(),
                key = data?.stringOrNull("key"),
                passProps = if (isNative) data?.mapOrNull("passProps") else null,
                functions = if (isNative) data?.arrayOrNull("functions").strings() else emptyList(),
                columns = columns,
                arrangement = if (type == LayoutType.Arrangement) arrangementOf(data) else null,
                children = children,
            )
            checkChildren(node)
            return node
        }

        // The node where its parent put it, from the parent's point of view.
        private fun checkPlace(type: LayoutType, id: String, place: Place) {
            val text = when (place.parent) {
                LayoutType.Stack ->
                    if (type !in stackChildren) ErrorTexts.stackTakesScreens(place.parentId, type.jsName) else null
                LayoutType.BottomTabs ->
                    if (type == LayoutType.BottomTabs) ErrorTexts.tabsInTabs(place.parentId) else null
                LayoutType.SideMenuRoot ->
                    if (!type.isSideMenuSide) ErrorTexts.sideMenuChild(place.parentId, type.jsName) else null
                LayoutType.SideMenuLeft, LayoutType.SideMenuCenter, LayoutType.SideMenuRight ->
                    if (type == LayoutType.SideMenuRoot) ErrorTexts.sideMenuInSide(place.menuId) else null
                LayoutType.SplitView ->
                    if (place.column != null && type == LayoutType.SplitView) {
                        ErrorTexts.splitViewInColumn(place.parentId)
                    } else {
                        null
                    }
                LayoutType.Arrangement ->
                    if (!type.isScreen) ErrorTexts.pairNeedsTwoScreens(place.parentId) else null
                else -> null
            }
            text?.let { throw LayoutException(it) }
            if (type.isSideMenuSide && place.parent != LayoutType.SideMenuRoot) {
                throw LayoutException(ErrorTexts.sideOutsideMenu(id, type.jsName))
            }
        }

        // What the number of children decides, before the children are read.
        private fun checkCount(type: LayoutType, id: String, count: Int, place: Place) {
            val text = when (type) {
                LayoutType.Stack -> if (count == 0) ErrorTexts.emptyStack(id) else null
                LayoutType.BottomTabs -> when {
                    count == 0 -> ErrorTexts.emptyTabs(id)
                    count > MAX_TABS -> ErrorTexts.tooManyTabs(id, count)
                    else -> null
                }
                LayoutType.Arrangement -> if (count != 2) ErrorTexts.pairNeedsTwoScreens(id) else null
                LayoutType.SideMenuLeft, LayoutType.SideMenuCenter, LayoutType.SideMenuRight ->
                    if (count != 1) ErrorTexts.sideChildCount(id, place.parentId) else null
                else -> null
            }
            text?.let { throw LayoutException(it) }
        }

        // What the types of the children decide, once they are read.
        private fun checkChildren(node: LayoutNode) {
            when (node.type) {
                LayoutType.SideMenuRoot -> {
                    val sides = node.children.groupingBy { it.type }.eachCount()
                    val center = sides[LayoutType.SideMenuCenter] ?: 0
                    val left = sides[LayoutType.SideMenuLeft] ?: 0
                    val right = sides[LayoutType.SideMenuRight] ?: 0
                    if (center != 1 || left > 1 || right > 1) {
                        throw LayoutException(ErrorTexts.sideMenuSides(node.id))
                    }
                }
                LayoutType.SplitView -> {
                    val columns = node.columnChildren()
                    if (LayoutNode.PRIMARY !in columns || LayoutNode.SECONDARY !in columns) {
                        throw LayoutException(ErrorTexts.splitViewColumns(node.id))
                    }
                }
                else -> Unit
            }
        }

        private fun arrangementOf(data: ReadableMap?) = ArrangementData(
            style = data?.stringOrNull("style"),
            axes = data?.stringOrNull("axes"),
            fraction = data?.doubleOrNull("fraction"),
            primaryEdge = data?.stringOrNull("primaryEdge"),
            compact = data?.stringOrNull("compact"),
        )
    }
}

// The most tabs Android shows.
private const val MAX_TABS = 5

// What a stack takes.
private val stackChildren = setOf(LayoutType.Component, LayoutType.ExternalComponent, LayoutType.Arrangement)

// A field of the wrong JSON kind counts as missing. `getType` is asked only for a key the map has: a native map
// throws for one it hasn't.
private fun ReadableMap.typeOf(key: String): ReadableType? = if (hasKey(key)) getType(key) else null

private fun ReadableMap.stringOrNull(key: String): String? =
    if (typeOf(key) == ReadableType.String) getString(key) else null

private fun ReadableMap.mapOrNull(key: String): ReadableMap? =
    if (typeOf(key) == ReadableType.Map) getMap(key) else null

private fun ReadableMap.arrayOrNull(key: String): ReadableArray? =
    if (typeOf(key) == ReadableType.Array) getArray(key) else null

private fun ReadableMap.doubleOrNull(key: String): Double? =
    if (typeOf(key) == ReadableType.Number) getDouble(key) else null

private fun ReadableArray.mapAt(index: Int): ReadableMap? =
    if (getType(index) == ReadableType.Map) getMap(index) else null

// The strings of an array; an entry of another kind is an empty name, which names nothing.
private fun ReadableArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until size()).map { if (getType(it) == ReadableType.String) getString(it).orEmpty() else "" }
}

package dev.apexnative.navigation.layout

import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.ReadableMap

/** Layout JSON as React Native gives it, built the way JavaScript builds it (`src/engine/layout.ts`). */
object Layouts {
    fun node(type: String, id: String, data: JavaOnlyMap = JavaOnlyMap(), vararg children: ReadableMap?): JavaOnlyMap =
        JavaOnlyMap.of("id", id, "type", type, "data", data, "children", JavaOnlyArray.from(children.toList()))

    fun screen(id: String, name: String = "Home", options: JavaOnlyMap? = null, key: String? = null): JavaOnlyMap {
        val data = JavaOnlyMap.of("name", name)
        options?.let { data.putMap("options", it) }
        key?.let { data.putString("key", it) }
        return node("Component", id, data)
    }

    fun native(id: String, name: String, passProps: JavaOnlyMap? = null, functions: List<String>? = null): JavaOnlyMap {
        val data = JavaOnlyMap.of("name", name)
        passProps?.let { data.putMap("passProps", it) }
        functions?.let { data.putArray("functions", JavaOnlyArray.from(it)) }
        return node("ExternalComponent", id, data)
    }

    fun stack(id: String, vararg children: ReadableMap?) = node("Stack", id, JavaOnlyMap(), *children)

    fun tabs(id: String, vararg children: ReadableMap?) = node("BottomTabs", id, JavaOnlyMap(), *children)

    fun menu(id: String, vararg sides: ReadableMap?) = node("SideMenuRoot", id, JavaOnlyMap(), *sides)

    fun left(id: String, vararg children: ReadableMap?) = node("SideMenuLeft", id, JavaOnlyMap(), *children)

    fun center(id: String, vararg children: ReadableMap?) = node("SideMenuCenter", id, JavaOnlyMap(), *children)

    fun right(id: String, vararg children: ReadableMap?) = node("SideMenuRight", id, JavaOnlyMap(), *children)

    fun split(id: String, columns: List<String>?, vararg children: ReadableMap?): JavaOnlyMap {
        val data = JavaOnlyMap()
        columns?.let { data.putArray("columns", JavaOnlyArray.from(it)) }
        return node("SplitView", id, data, *children)
    }

    fun pair(id: String, vararg children: ReadableMap?) = node(
        "Arrangement",
        id,
        JavaOnlyMap.of(
            "name",
            "Details",
            "style",
            "overlay",
            "axes",
            "vertical",
            "fraction",
            0.3,
            "primaryEdge",
            "top",
            "compact",
            "pair",
        ),
        *children,
    )

    fun root(root: ReadableMap?, modals: List<ReadableMap>? = null, overlays: List<ReadableMap>? = null): JavaOnlyMap {
        val map = JavaOnlyMap()
        root?.let { map.putMap("root", it) }
        modals?.let { map.putArray("modals", JavaOnlyArray.from(it)) }
        overlays?.let { map.putArray("overlays", JavaOnlyArray.from(it)) }
        return map
    }
}

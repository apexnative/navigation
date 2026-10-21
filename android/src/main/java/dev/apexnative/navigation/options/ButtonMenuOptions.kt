package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.parsers.IconParser

/**
 * A menu of the bar: `topBar.overflowMenu`, or the `menu` of a button. Its items, and submenus among them; with
 * `singleSelection` one item of a level has a checkmark at a time.
 *
 * Copied options share it, so the checkmark a pick moves stays with the screen when it appears again, until new
 * options give the menu again.
 */
class ButtonMenuOptions(
    @JvmField val singleSelection: Boolean,
    @JvmField val items: List<Element>,
    /** The identifier tests find the menu's button by (`topBar.overflowMenu.testID`). */
    @JvmField val testID: String? = null,
) {
    sealed class Element(val title: String, val icon: String?, val testID: String?)

    class Item(
        val id: String,
        title: String,
        icon: String?,
        val destructive: Boolean,
        val disabled: Boolean,
        var checked: Boolean,
        testID: String? = null,
    ) : Element(title, icon, testID)

    class Submenu(
        title: String,
        icon: String?,
        val singleSelection: Boolean,
        val items: List<Element>,
        testID: String? = null,
    ) : Element(title, icon, testID)

    companion object {
        /** The menu, null when the options don't give it; `items: []` gives an empty one, which takes it away. */
        @JvmStatic
        fun parse(json: ReadableMap?): ButtonMenuOptions? {
            val items = json?.optArray("items") ?: return null
            return ButtonMenuOptions(json.optBoolean("singleSelection"), parseItems(items), testIDOf(json))
        }

        private fun testIDOf(json: ReadableMap): String? = json.optString("testID").takeIf { it.isNotEmpty() }

        private fun parseItems(array: ReadableArray): List<Element> = (0 until array.size()).mapNotNull { index ->
            val json = array.optMap(index) ?: return@mapNotNull null
            val title = json.optString("title")
            val icon = IconParser.parse(json, "icon").get(null)
            val items = json.optArray("items")
            if (items != null) {
                Submenu(title, icon, json.optBoolean("singleSelection"), parseItems(items), testIDOf(json))
            } else {
                Item(
                    json.optString("id"),
                    title,
                    icon,
                    json.optBoolean("destructive"),
                    json.optBoolean("disabled"),
                    json.optBoolean("checked"),
                    testIDOf(json),
                )
            }
        }
    }
}

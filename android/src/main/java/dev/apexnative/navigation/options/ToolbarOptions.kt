package dev.apexnative.navigation.options

import android.content.Context
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.TextParser

/**
 * `toolbar` (`OptionsToolbar`): the bar of buttons at the bottom of a screen. An item of `buttons` is a button
 * (`OptionsTopBarButton`), a space (`OptionsToolbarSpace`: `systemItem` `flexibleSpace` or `fixedSpace`) or the search
 * field (`OptionsToolbarSearchField`: `systemItem` `searchField`); all three are read as a [ButtonOptions], the last
 * two with nothing but their `systemItem`. A list replaces the one below whole.
 */
class ToolbarOptions {
    @JvmField var visible: Bool = NullBool()
    /** `toolbar.buttons`; null when the options don't give them. */
    @JvmField var buttons: List<ButtonOptions>? = null
    @JvmField var testID: Text = NullText()

    fun hasValue(): Boolean = visible.hasValue() || buttons != null || testID.hasValue()

    fun mergeWith(other: ToolbarOptions) {
        if (other.visible.hasValue()) visible = other.visible
        other.buttons?.let { buttons = it.map(ButtonOptions::copy) }
        if (other.testID.hasValue()) testID = other.testID
    }

    fun mergeWithDefault(defaults: ToolbarOptions) {
        if (!visible.hasValue()) visible = defaults.visible
        if (buttons == null) buttons = defaults.buttons?.map(ButtonOptions::copyWithMenu)
        if (!testID.hasValue()) testID = defaults.testID
    }

    fun copy() = ToolbarOptions().also { it.mergeWith(this) }

    companion object {
        // The system items of a button, and the one that stands for the search field.
        private val SYSTEM_ITEMS = ButtonOptions.SYSTEM_ITEMS + "searchField"

        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): ToolbarOptions {
            val options = ToolbarOptions()
            if (map == null) return options
            options.visible = BoolParser.parse(map, "visible")
            options.buttons = map.groupList("buttons") { parseItem(context, it) }
            options.testID = TextParser.parse(map, "testID")
            return options
        }

        private fun parseItem(context: Context?, map: ReadableMap?): ButtonOptions {
            val item = ButtonOptions.parse(context, map)
            item.systemItem = TextParser.parse(map, "systemItem", SYSTEM_ITEMS)
            return item
        }
    }
}

package dev.apexnative.navigation.options

import android.content.Context
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullThemeColour
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.options.parsers.BoolParser

/** `navigationBar`: Android's navigation bar under a screen. */
class NavigationBarOptions {
    @JvmField var backgroundColor: ThemeColour = NullThemeColour()
    @JvmField var visible: Bool = NullBool()
    @JvmField var drawBehind: Bool = NullBool()

    fun hasValue(): Boolean = backgroundColor.hasValue() || visible.hasValue() || drawBehind.hasValue()

    fun mergeWith(other: NavigationBarOptions) {
        backgroundColor = backgroundColor.mergeWith(other.backgroundColor)
        if (other.visible.hasValue()) visible = other.visible
        if (other.drawBehind.hasValue()) drawBehind = other.drawBehind
    }

    fun mergeWithDefault(defaults: NavigationBarOptions) {
        backgroundColor = backgroundColor.withDefault(defaults.backgroundColor)
        if (!visible.hasValue()) visible = defaults.visible
        if (!drawBehind.hasValue()) drawBehind = defaults.drawBehind
    }

    fun copy() = NavigationBarOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): NavigationBarOptions {
            val options = NavigationBarOptions()
            if (map == null) return options
            options.backgroundColor = ThemeColour.parse(context, map, "backgroundColor")
            options.visible = BoolParser.parse(map, "visible")
            options.drawBehind = BoolParser.parse(map, "drawBehind")
            return options
        }
    }
}

package dev.apexnative.navigation.options

import android.content.Context
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.NullThemeColour
import dev.apexnative.navigation.options.params.ThemeColour

/**
 * `window`: the app's window, only in the default options. iOS colors its main window with `backgroundColor`; Android
 * reads it and leaves its windows as they are.
 */
class WindowOptions {
    @JvmField var backgroundColor: ThemeColour = NullThemeColour()

    fun hasValue(): Boolean = backgroundColor.hasValue()

    fun mergeWith(other: WindowOptions) {
        backgroundColor = backgroundColor.mergeWith(other.backgroundColor)
    }

    fun mergeWithDefault(defaults: WindowOptions) {
        backgroundColor = backgroundColor.withDefault(defaults.backgroundColor)
    }

    fun copy() = WindowOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): WindowOptions {
            val options = WindowOptions()
            if (map == null) return options
            options.backgroundColor = ThemeColour.parse(context, map, "backgroundColor")
            return options
        }
    }
}

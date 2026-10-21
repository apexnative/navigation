package dev.apexnative.navigation.options.parsers

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.has
import dev.apexnative.navigation.options.opt
import dev.apexnative.navigation.options.params.NoneText
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.Text

/** Reads a text of the options, or one of the values of an enum. A value of another kind is unset. */
object TextParser {
    /** A text: a string; `null` is an explicit "no text" ([NoneText]). */
    @JvmStatic
    fun parse(map: ReadableMap?, key: String): Text {
        if (map == null || !map.has(key)) return NullText()
        return when (val value = map.opt(key)) {
            null -> NoneText()
            is String -> Text(value)
            else -> NullText()
        }
    }

    /**
     * One of the [allowed] values of an enum; an unknown value, and `null`, are unset: the option's default behavior
     * holds.
     */
    @JvmStatic
    fun parse(map: ReadableMap?, key: String, allowed: Set<String>): Text {
        val value = map?.opt(key) as? String ?: return NullText()
        return if (value in allowed) Text(value) else NullText()
    }
}

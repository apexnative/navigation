package dev.apexnative.navigation.options.parsers

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.opt
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.NullBool

/** Reads a yes or no of the options: `true` or `false` only; anything else, numbers and `null` too, is unset. */
object BoolParser {
    @JvmStatic
    fun parse(map: ReadableMap?, key: String): Bool = when (val value = map?.opt(key)) {
        is Boolean -> Bool(value)
        else -> NullBool()
    }
}

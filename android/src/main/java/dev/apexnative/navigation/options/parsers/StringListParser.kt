package dev.apexnative.navigation.options.parsers

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.UnreadableValueException
import dev.apexnative.navigation.options.opt
import dev.apexnative.navigation.options.optArray

/**
 * Reads an array of texts (`safeAreaEdges`, `orientation`, `modifiers`): null when it is missing or isn't an array;
 * items that aren't strings are dropped, and with [allowed] the unknown values too. `[]` is an empty list, which
 * replaces what the layers below give.
 */
object StringListParser {
    @JvmStatic
    @JvmOverloads
    fun parse(map: ReadableMap?, key: String, allowed: Set<String>? = null): List<String>? {
        val array = map?.optArray(key) ?: return null
        return (0 until array.size()).mapNotNull { index ->
            val item = try {
                array.opt(index)
            } catch (e: UnreadableValueException) {
                throw UnreadableValueException("$key[$index]", e)
            }
            (item as? String)?.takeIf { allowed == null || it in allowed }
        }
    }
}

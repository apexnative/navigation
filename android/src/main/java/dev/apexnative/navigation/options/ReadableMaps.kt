package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType

// Reading the options straight from React Native's data: a value of another kind than the one asked for is none, never
// converted (`"12"` is no number, `1` no yes or no). A failure of React Native's own reading throws an
// [UnreadableValueException] with the key, which the group around it catches (`OptionsReader`).

/**
 * A value of the options that React Native's data could not give: [segment] is its key, or `key[index]` in an array.
 * The group it is in reads as empty, and the path of the first such value is kept (`OptionsReader`).
 */
internal class UnreadableValueException(val segment: String, cause: Throwable) : RuntimeException(cause)

/** The map has [key], `null` included. */
internal fun ReadableMap.has(key: String): Boolean = hasKey(key)

/**
 * The value of [key] as Kotlin has it: a `String`, a `Double`, a `Boolean`, a [ReadableMap] or a [ReadableArray];
 * null when the key is missing or `null`.
 */
internal fun ReadableMap.opt(key: String): Any? {
    if (!hasKey(key)) return null
    return try {
        when (getType(key)) {
            ReadableType.Null -> null
            ReadableType.Boolean -> getBoolean(key)
            ReadableType.Number -> getDouble(key)
            ReadableType.String -> getString(key)
            ReadableType.Map -> getMap(key)
            ReadableType.Array -> getArray(key)
        }
    } catch (e: RuntimeException) {
        throw UnreadableValueException(key, e)
    }
}

/** The object of [key]; null when it is missing or of another kind. */
internal fun ReadableMap.optMap(key: String): ReadableMap? = opt(key) as? ReadableMap

/** The array of [key]; null when it is missing or of another kind. */
internal fun ReadableMap.optArray(key: String): ReadableArray? = opt(key) as? ReadableArray

/** The string of [key]; [fallback] when it is missing or of another kind. */
internal fun ReadableMap.optString(key: String, fallback: String = ""): String = opt(key) as? String ?: fallback

/** The yes or no of [key]; [fallback] when it is missing or of another kind. */
internal fun ReadableMap.optBoolean(key: String, fallback: Boolean = false): Boolean = opt(key) as? Boolean ?: fallback

/** The item at [index], as [ReadableMap.opt] gives a value; null out of the array. */
internal fun ReadableArray.opt(index: Int): Any? {
    if (index < 0 || index >= size()) return null
    return try {
        when (getType(index)) {
            ReadableType.Null -> null
            ReadableType.Boolean -> getBoolean(index)
            ReadableType.Number -> getDouble(index)
            ReadableType.String -> getString(index)
            ReadableType.Map -> getMap(index)
            ReadableType.Array -> getArray(index)
        }
    } catch (e: RuntimeException) {
        throw UnreadableValueException("[$index]", e)
    }
}

/** The object at [index]; null when it is of another kind or out of the array. */
internal fun ReadableArray.optMap(index: Int): ReadableMap? = opt(index) as? ReadableMap

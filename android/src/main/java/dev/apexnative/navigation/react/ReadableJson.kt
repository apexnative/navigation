package dev.apexnative.navigation.react

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import org.json.JSONArray
import org.json.JSONObject

/**
 * What JavaScript sends a native screen (its props, an update of them, the answer of a call) as the JSON the native
 * screen's context keeps (`NativeScreenContext`): maps, lists, strings, numbers, booleans, and `null` kept as a value
 * (`JSONObject.NULL`, `updateNativeScreenProps`). Read on the thread the command comes on.
 */
internal object ReadableJson {
    /** [map] as JSON. */
    fun toJson(map: ReadableMap): JSONObject = jsonObject(plain(map))

    /** [map] as plain values: nested maps and lists too, a `null` kept as an entry with no value. */
    fun plain(map: ReadableMap): Map<String, Any?> {
        val values = LinkedHashMap<String, Any?>()
        val keys = map.keySetIterator()
        while (keys.hasNextKey()) {
            val key = keys.nextKey()
            values[key] = when (map.getType(key)) {
                ReadableType.Null -> null
                ReadableType.Boolean -> map.getBoolean(key)
                ReadableType.Number -> map.getDouble(key)
                ReadableType.String -> map.getString(key)
                ReadableType.Map -> map.getMap(key)?.let(::plain)
                ReadableType.Array -> map.getArray(key)?.let(::plain)
            }
        }
        return values
    }

    /** [array] as plain values, as [plain] of a map. */
    fun plain(array: ReadableArray): List<Any?> = (0 until array.size()).map { index ->
        when (array.getType(index)) {
            ReadableType.Null -> null
            ReadableType.Boolean -> array.getBoolean(index)
            ReadableType.Number -> array.getDouble(index)
            ReadableType.String -> array.getString(index)
            ReadableType.Map -> array.getMap(index)?.let(::plain)
            ReadableType.Array -> array.getArray(index)?.let(::plain)
        }
    }

    private fun jsonObject(values: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        for ((key, value) in values) json.put(key, jsonValue(value))
        return json
    }

    private fun jsonValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> jsonObject(value.entries.associate { (key, item) -> key.toString() to item })
        is List<*> -> JSONArray().also { array -> value.forEach { array.put(jsonValue(it)) } }
        else -> value
    }
}

package dev.apexnative.navigation.options

import android.content.Context
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap

/**
 * Reads an options tree from React Native's data into typed [Options], on the thread the data came on (the
 * native-modules thread). Reading is tolerant: a value of the wrong kind is unset, a group that isn't an object is
 * empty, unknown keys are not read, and nothing stops the rest of the tree. An exception while reading a value is
 * caught around the group it happened in: that group reads as empty, and the dotted path of the first such value is
 * kept ([Result.unreadablePath]), for the text `unreadableOption` of the command.
 *
 * Groups read their children through [group] and [groupList], which keep the path; their values through the parsers
 * of `parsers/` and `ThemeColour.parse`.
 */
object OptionsReader {
    /** The options read, and the path of the first value that could not be read; none when all could. */
    class Result(
        /** The options, every value that could be read. */
        @JvmField val options: Options,
        /** `topBar.rightButtons[1].icon`; none when every value could be read. */
        @JvmField val unreadablePath: String?,
    )

    /** Reads [map] whole; [context] (the app's) is the one colors resolve platform colors with, none in unit tests. */
    @JvmStatic
    fun read(map: ReadableMap?, context: Context?): Result {
        val state = State()
        val previous = current.get()
        current.set(state)
        try {
            val options = try {
                Options.parse(context, map)
            } catch (e: Exception) {
                state.failed(e)
                Options()
            }
            return Result(options, state.unreadable)
        } finally {
            current.set(previous)
        }
    }

    /** The dotted path of [key] in the group being read now; [key] alone outside a reading. */
    internal fun pathOf(key: String): String = current.get()?.pathWith(key) ?: key

    // The path of the group being read, and the first value that could not be read. One per reading thread.
    private class State {
        val path = ArrayList<String>()
        var unreadable: String? = null

        fun pathWith(segment: String): String {
            if (path.isEmpty()) return segment
            val base = path.joinToString(".")
            return if (segment.startsWith("[")) base + segment else "$base.$segment"
        }

        fun failed(e: Exception) {
            if (unreadable != null) return
            unreadable = when {
                e is UnreadableValueException -> pathWith(e.segment)
                path.isEmpty() -> ""
                else -> path.joinToString(".")
            }
        }
    }

    private val current = ThreadLocal<State?>()

    internal fun enter(segment: String) {
        current.get()?.path?.add(segment)
    }

    internal fun leave() {
        current.get()?.path?.let { if (it.isNotEmpty()) it.removeAt(it.size - 1) }
    }

    internal fun failed(e: Exception) {
        current.get()?.failed(e)
    }
}

/**
 * The group of [key], read by [parse] from its object; [parse] gets null for a missing key, `null`, or a value that
 * isn't an object, and gives the empty group then. An exception while reading it gives the empty group too, and keeps
 * the path of the failure.
 */
internal inline fun <G> ReadableMap.group(key: String, parse: (ReadableMap?) -> G): G {
    // The map is read before the key is entered: a failure of the read itself is the key's own, not a child's.
    val map = try {
        optMap(key)
    } catch (e: Exception) {
        OptionsReader.failed(e)
        return parse(null)
    }
    OptionsReader.enter(key)
    try {
        return parse(map)
    } catch (e: Exception) {
        OptionsReader.failed(e)
        return parse(null)
    } finally {
        OptionsReader.leave()
    }
}

/**
 * The array of groups of [key], each item read by [parse] as [group] reads one (an item that isn't an object is an
 * empty group); null when the key is missing or isn't an array. `[]` is an empty list: it replaces what the layers
 * below give.
 */
internal inline fun <G> ReadableMap.groupList(key: String, parse: (ReadableMap?) -> G): List<G>? {
    val array = optArray(key) ?: return null
    return (0 until array.size()).map { index ->
        OptionsReader.enter("$key[$index]")
        try {
            parse(itemAt(array, index))
        } catch (e: Exception) {
            OptionsReader.failed(e)
            parse(null)
        } finally {
            OptionsReader.leave()
        }
    }
}

// The object at [index]; an item React Native can't give fails as the item itself, whose path the reading holds.
internal fun itemAt(array: ReadableArray, index: Int): ReadableMap? = try {
    array.optMap(index)
} catch (e: UnreadableValueException) {
    throw IllegalStateException("the item can't be read", e)
}

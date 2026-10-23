package dev.apexnative.navigation.viewcontrollers.bottomtabs

import dev.apexnative.navigation.utils.ImageKey

/**
 * The last call of each kind for each item of the bar, by which [BottomTabsPresenter.run] skips a call equal to it: an
 * icon isn't loaded and set again as a screen appears. The badge and the font always go (their defaults are read at
 * each set). An icon whose load gave no image goes again ([failed]); a `file:` icon compares by its [ImageKey] (the
 * file's modification time and size, read by [fileKey]), so a file written again at the same path shows. Main thread.
 */
internal class AppliedTabCalls(private val fileKey: (String) -> ImageKey) {
    /** A call that runs: [isChange], the item had a call of its kind before; [image], the key of a `file:` icon. */
    class Next(val isChange: Boolean, val image: ImageKey?)

    private class Applied(val call: TabItems.Call, val image: ImageKey?, var failed: Boolean = false)

    private val last = HashMap<Pair<Int, Class<*>>, Applied>()

    /** How [call] runs, and it is the last of its kind for its item from now on; none when it is skipped. */
    fun next(call: TabItems.Call): Next? {
        val key = call.index to call.javaClass
        val before = last[key]
        val image = uriOf(call)?.takeIf { it.startsWith(FILE_SCHEME) }?.let(fileKey)
        val always = call is TabItems.Call.Badge || call is TabItems.Call.Font
        if (!always && before != null && before.call == call && !before.failed && before.image == image) return null
        last[key] = Applied(call, image)
        return Next(isChange = before != null, image = image)
    }

    /** The icon of [call] didn't load: the same call goes again. */
    fun failed(call: TabItems.Call) {
        last[call.index to call.javaClass]?.takeIf { it.call == call }?.failed = true
    }

    /** The bar goes: every call goes again. */
    fun clear() {
        last.clear()
    }

    private fun uriOf(call: TabItems.Call): String? = when (call) {
        is TabItems.Call.Icon -> call.uri
        is TabItems.Call.SelectedIcon -> call.uri
        else -> null
    }

    private companion object {
        const val FILE_SCHEME = "file:"
    }
}

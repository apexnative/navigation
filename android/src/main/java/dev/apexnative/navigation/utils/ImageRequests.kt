package dev.apexnative.navigation.utils

import android.graphics.drawable.Drawable

/** Where the images of the options come from, for one context: [OptionImages.source], or a fake in the tests. */
internal interface ImageSource {
    /** A copy of the image of [uri] when the cache has it, none otherwise; never loads. */
    fun cached(uri: String): Drawable?

    /** A copy of the image of [uri], loaded on the caller's thread when the cache doesn't have it; none on a failure. */
    fun now(uri: String): Drawable?

    /** Whether [uri] can load off the main thread: a file or a drawable of the app. */
    fun loadsLater(uri: String): Boolean

    /** Loads [uri] off the main thread and gives a copy of it, or none on a failure, to [done] on the main thread. */
    fun later(uri: String, done: (Drawable?) -> Unit)
}

/**
 * The images that the places of a view asked for (an item's icon, a button's icon), by place [P]: what the cache has
 * comes at once; what it doesn't, when the place can show it later, loads off the main thread and comes when ready;
 * everything else loads at once. The last request of a place wins: a load that arrives after the place asked again,
 * or after [forget] or [clear], is dropped. A load that fails gives the place no image, as an option without one:
 * the image it showed before doesn't stay. Main thread only.
 */
internal class ImageRequests<P : Any>(private val source: ImageSource) {
    // The open load of each place: its number, which a later request of the place replaces.
    private val waiting = HashMap<P, Long>()
    private var last = 0L

    /**
     * Gives [show] the image of [uri] for [place]: now when the cache has it, or when the place can't wait for it
     * ([mayWait] false: the first frame needs it) or the uri can't load off the main thread (Metro's address, a content
     * uri); otherwise later, unless [place] asks again first. Null when it doesn't load: the place shows none.
     */
    fun request(place: P, uri: String, mayWait: Boolean, show: (Drawable?) -> Unit) {
        waiting.remove(place)
        source.cached(uri)?.let {
            show(it)
            return
        }
        if (!mayWait || !source.loadsLater(uri)) {
            show(source.now(uri))
            return
        }
        val number = ++last
        waiting[place] = number
        source.later(uri) { image ->
            if (waiting[place] != number) return@later
            waiting.remove(place)
            show(image)
        }
    }

    /** The place shows no image of the options now: a load it asked for earlier is dropped. */
    fun forget(place: P) {
        waiting.remove(place)
    }

    /** The view goes: every open load is dropped. */
    fun clear() {
        waiting.clear()
    }
}

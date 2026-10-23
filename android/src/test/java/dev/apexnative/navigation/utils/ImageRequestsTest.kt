package dev.apexnative.navigation.utils

import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The images the places of a view ask for: the cache at once, a file or a drawable of the app later when the place can
 * wait, the rest at once; the last uri a place asked for wins over a load that arrives late; a load that fails gives
 * the place no image; a request builds the key of its image once (a file's version is read once).
 */
class ImageRequestsTest {
    private val source = FakeSource()
    private val requests = ImageRequests<String>(source)
    private val shown = mutableListOf<Pair<String, String>>()

    @Test
    fun anImageOfTheCacheShowsAtOnce() {
        source.cache += "file:///home.png"
        ask("icon", "file:///home.png")
        assertEquals(listOf("icon" to "file:///home.png"), shown)
        assertEquals(0, source.pending.size)
    }

    @Test
    fun aFileOutsideTheCacheShowsWhenItLoads() {
        ask("icon", "file:///home.png")
        assertEquals(emptyList<Pair<String, String>>(), shown)
        source.finish("file:///home.png")
        assertEquals(listOf("icon" to "file:///home.png"), shown)
    }

    @Test
    fun aPlaceThatCantWaitLoadsAtOnce() {
        ask("icon", "file:///home.png", mayWait = false)
        assertEquals(listOf("icon" to "file:///home.png"), shown)
        assertEquals(listOf("file:///home.png"), source.loadedNow)
    }

    @Test
    fun anAddressThatCantLoadOffTheMainThreadLoadsAtOnce() {
        ask("icon", METRO)
        assertEquals(listOf("icon" to METRO), shown)
        assertEquals(0, source.pending.size)
    }

    @Test
    fun theLastAskedUriWinsOverALoadThatArrivesLate() {
        ask("icon", "file:///home.png")
        ask("icon", "file:///profile.png")
        source.finish("file:///profile.png")
        source.finish("file:///home.png")
        assertEquals(listOf("icon" to "file:///profile.png"), shown)
    }

    @Test
    fun anImageOfTheCacheAskedLastWinsOverAnEarlierLoad() {
        ask("icon", "file:///home.png")
        source.cache += "file:///profile.png"
        ask("icon", "file:///profile.png")
        source.finish("file:///home.png")
        assertEquals(listOf("icon" to "file:///profile.png"), shown)
    }

    @Test
    fun anImageLoadedAtOnceAskedLastWinsOverAnEarlierLoad() {
        ask("icon", "file:///home.png")
        ask("icon", METRO)
        source.finish("file:///home.png")
        assertEquals(listOf("icon" to METRO), shown)
    }

    @Test
    fun theSameUriAskedAgainShowsOnce() {
        ask("icon", "file:///home.png")
        ask("icon", "file:///home.png")
        source.finishAll()
        assertEquals(listOf("icon" to "file:///home.png"), shown)
    }

    @Test
    fun placesDontReplaceEachOther() {
        ask("first", "file:///home.png")
        ask("second", "file:///profile.png")
        source.finishAll()
        assertEquals(listOf("first" to "file:///home.png", "second" to "file:///profile.png"), shown)
    }

    @Test
    fun aForgottenPlaceDropsItsLoad() {
        ask("icon", "file:///home.png")
        requests.forget("icon")
        source.finishAll()
        assertEquals(emptyList<Pair<String, String>>(), shown)
    }

    @Test
    fun aClearDropsEveryLoad() {
        ask("first", "file:///home.png")
        ask("second", "file:///profile.png")
        requests.clear()
        source.finishAll()
        assertEquals(emptyList<Pair<String, String>>(), shown)
    }

    @Test
    fun aLoadThatFailsLaterGivesThePlaceNoImage() {
        source.cache += "file:///home.png"
        ask("icon", "file:///home.png")
        source.broken += "file:///missing.png"
        ask("icon", "file:///missing.png")
        source.finishAll()
        assertEquals(listOf("icon" to "file:///home.png", "icon" to NONE), shown)
    }

    @Test
    fun aLoadThatFailsAtOnceGivesThePlaceNoImage() {
        source.cache += "file:///home.png"
        ask("icon", "file:///home.png")
        source.broken += "file:///missing.png"
        ask("icon", "file:///missing.png", mayWait = false)
        assertEquals(listOf("icon" to "file:///home.png", "icon" to NONE), shown)
    }

    @Test
    fun aFailedLoadThatArrivesLateDoesntClearANewerImage() {
        source.broken += "file:///missing.png"
        ask("icon", "file:///missing.png")
        source.cache += "file:///home.png"
        ask("icon", "file:///home.png")
        source.finishAll()
        assertEquals(listOf("icon" to "file:///home.png"), shown)
    }

    @Test
    fun aRequestBuildsTheKeyOnceWhenTheImageLoadsAtOnce() {
        ask("icon", "file:///home.png", mayWait = false)
        assertEquals(listOf("file:///home.png"), source.keys)
    }

    @Test
    fun aRequestBuildsTheKeyOnceWhenTheImageLoadsLater() {
        ask("icon", "file:///home.png")
        source.finishAll()
        assertEquals(listOf("file:///home.png"), source.keys)
    }

    @Test
    fun aRequestWithAKeyBuildsNone() {
        val key = requests.key("file:///home.png")
        requests.request("icon", key, mayWait = true) { shown += "icon" to ((it as FakeImage?)?.uri ?: NONE) }
        source.finishAll()
        assertEquals(listOf("file:///home.png"), source.keys)
        assertEquals(listOf("icon" to "file:///home.png"), shown)
    }

    private fun ask(place: String, uri: String, mayWait: Boolean = true) {
        requests.request(place, uri, mayWait) { shown += place to ((it as FakeImage?)?.uri ?: NONE) }
    }

    private class FakeSource : ImageSource {
        val cache = mutableSetOf<String>()
        val broken = mutableSetOf<String>()
        val pending = mutableListOf<Pair<String, (Drawable?) -> Unit>>()
        val loadedNow = mutableListOf<String>()
        // The uris whose key was built, in order: a file's version is read each time.
        val keys = mutableListOf<String>()

        override fun key(uri: String): ImageKey {
            keys += uri
            return ImageKey(uri, densityDpi = 420, night = false, rightToLeft = false)
        }

        override fun cached(key: ImageKey) = if (key.uri in cache) FakeImage(key.uri) else null

        override fun now(key: ImageKey): Drawable? {
            loadedNow += key.uri
            return imageOf(key.uri)
        }

        override fun loadsLater(uri: String) = uri.startsWith("file:")

        override fun later(key: ImageKey, done: (Drawable?) -> Unit) {
            pending += key.uri to done
        }

        // The loads of [uri] arrive on main, in the order they were asked.
        fun finish(uri: String) {
            val arrived = pending.filter { it.first == uri }
            pending.removeAll(arrived)
            arrived.forEach { (_, done) -> done(imageOf(uri)) }
        }

        fun finishAll() {
            pending.map { it.first }.distinct().forEach(::finish)
        }

        private fun imageOf(uri: String) = if (uri in broken) null else FakeImage(uri)
    }

    private class FakeImage(val uri: String) : Drawable() {
        override fun draw(canvas: android.graphics.Canvas) = Unit

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Drawable.getOpacity is deprecated")
        override fun getOpacity() = PixelFormat.OPAQUE
    }

    private companion object {
        // What a place shows when its image doesn't load.
        const val NONE = "none"
        const val METRO = "http://10.0.2.2:8081/assets/src/images/home.png?platform=android&hash=1"
    }
}

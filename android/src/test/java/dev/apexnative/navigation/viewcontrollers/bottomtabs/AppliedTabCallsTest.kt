package dev.apexnative.navigation.viewcontrollers.bottomtabs

import dev.apexnative.navigation.options.FontOptions
import dev.apexnative.navigation.utils.ImageKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which calls of the bar's items run again: a call equal to the last of its kind is skipped, except the badge and the
 * font, an icon that didn't load, and a `file:` icon whose file changed at the same path.
 */
class AppliedTabCallsTest {
    // The version of each file, as the key reads it: its modification time.
    private val versions = HashMap<String, Long>()
    private val keysRead = mutableListOf<String>()
    private val applied = AppliedTabCalls { uri ->
        keysRead += uri
        ImageKey(uri, densityDpi = 420, night = false, rightToLeft = false, modified = versions[uri] ?: 0, size = 10)
    }

    @Test
    fun theSameCallAgainIsSkipped() {
        assertNotNull(applied.next(TabItems.Call.Title(0, "Home")))
        assertNull(applied.next(TabItems.Call.Title(0, "Home")))
        assertNotNull(applied.next(TabItems.Call.Icon(0, "ic_home")))
        assertNull(applied.next(TabItems.Call.Icon(0, "ic_home")))
    }

    @Test
    fun theFirstCallIsNoChangeTheNextIs() {
        assertFalse(applied.next(TabItems.Call.Icon(0, "ic_home"))!!.isChange)
        assertTrue(applied.next(TabItems.Call.Icon(0, "ic_settings"))!!.isChange)
        // Another item, or the selected icon of the same item, is its own.
        assertFalse(applied.next(TabItems.Call.Icon(1, "ic_home"))!!.isChange)
        assertFalse(applied.next(TabItems.Call.SelectedIcon(0, "ic_home"))!!.isChange)
    }

    @Test
    fun theBadgeAndTheFontAlwaysGo() {
        val badge = TabItems.Call.Badge(0, "1", null, false, null)
        assertNotNull(applied.next(badge))
        assertNotNull(applied.next(badge))
        val font = TabItems.Call.Font(0, FontOptions())
        assertNotNull(applied.next(font))
        assertNotNull(applied.next(font))
    }

    @Test
    fun anIconThatDidntLoadGoesAgain() {
        val icon = TabItems.Call.Icon(0, "file:///data/home.png")
        assertNotNull(applied.next(icon))
        applied.failed(icon)
        val again = applied.next(icon)
        assertNotNull(again)
        // The item had an icon call: the retry may wait for its load.
        assertTrue(again!!.isChange)
        // Loaded this time: the next is skipped.
        assertNull(applied.next(icon))
    }

    @Test
    fun aFailureOfAnEarlierIconDoesntRepeatTheNewer() {
        val home = TabItems.Call.SelectedIcon(0, "ic_home")
        val settings = TabItems.Call.SelectedIcon(0, "ic_settings")
        applied.next(home)
        applied.next(settings)
        applied.failed(home)
        assertNull(applied.next(settings))
    }

    @Test
    fun aFileWrittenAgainAtTheSamePathGoesAgain() {
        val icon = TabItems.Call.Icon(0, "file:///data/home.png")
        versions["file:///data/home.png"] = 1_000
        assertNotNull(applied.next(icon))
        assertNull(applied.next(icon))
        versions["file:///data/home.png"] = 2_000
        val again = applied.next(icon)
        assertEquals(2_000L, again!!.image!!.modified)
        assertNull(applied.next(icon))
    }

    @Test
    fun onlyAFileIconReadsItsKey() {
        assertNull(applied.next(TabItems.Call.Icon(0, "ic_home"))!!.image)
        applied.next(TabItems.Call.Title(0, "Home"))
        assertNotNull(applied.next(TabItems.Call.Icon(1, "file:///data/home.png"))!!.image)
        assertEquals(listOf("file:///data/home.png"), keysRead)
    }

    @Test
    fun aClearRunsEveryCallAgain() {
        val title = TabItems.Call.Title(0, "Home")
        applied.next(title)
        applied.clear()
        assertFalse(applied.next(title)!!.isChange)
    }
}

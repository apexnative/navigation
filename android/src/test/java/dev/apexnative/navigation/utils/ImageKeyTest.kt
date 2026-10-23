package dev.apexnative.navigation.utils

import android.content.res.Configuration
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The key of the shared cache of option images: the uri and what changes the decoded result for it, and for a file the
 * version of the file at that path.
 */
class ImageKeyTest {
    private val day = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO
    private val night = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun theSameUriInTheSameConfigurationIsOneKey() {
        assertEquals(key("file:///data/home.png"), key("file:///data/home.png"))
    }

    @Test
    fun anotherUriIsAnotherKey() {
        assertNotEquals(key("ic_home"), key("ic_settings"))
    }

    @Test
    fun anotherDensityIsAnotherKey() {
        assertNotEquals(key("ic_home", densityDpi = 420), key("ic_home", densityDpi = 560))
    }

    @Test
    fun lightAndDarkAreTwoKeys() {
        assertNotEquals(key("ic_home", uiMode = day), key("ic_home", uiMode = night))
    }

    @Test
    fun onlyTheNightBitsOfTheUiModeCount() {
        val car = Configuration.UI_MODE_TYPE_CAR or Configuration.UI_MODE_NIGHT_YES
        assertEquals(key("ic_home", uiMode = night), key("ic_home", uiMode = car))
        // A mode the system didn't name is light.
        assertEquals(key("ic_home", uiMode = day), key("ic_home", uiMode = Configuration.UI_MODE_NIGHT_UNDEFINED))
    }

    @Test
    fun leftToRightAndRightToLeftAreTwoKeys() {
        assertNotEquals(
            key("ic_back", direction = View.LAYOUT_DIRECTION_LTR),
            key("ic_back", direction = View.LAYOUT_DIRECTION_RTL),
        )
    }

    @Test
    fun aFileWrittenAgainWithOtherContentIsAnotherKey() {
        val file = folder.newFile("home.png")
        file.writeBytes(ByteArray(10))
        file.setLastModified(1_000_000L)
        val uri = file.toURI().toString()
        val before = key(uri)
        file.writeBytes(ByteArray(20))
        file.setLastModified(1_000_000L)
        assertNotEquals(before, key(uri))
    }

    @Test
    fun aFileWrittenAgainLaterWithTheSameSizeIsAnotherKey() {
        val file = folder.newFile("home.png")
        file.writeBytes(ByteArray(10))
        file.setLastModified(1_000_000L)
        val uri = "file://${file.path}"
        val before = key(uri)
        file.writeBytes(ByteArray(10))
        file.setLastModified(2_000_000L)
        assertNotEquals(before, key(uri))
    }

    @Test
    fun aFileLeftAsItWasIsTheSameKey() {
        val file = folder.newFile("home.png")
        file.writeBytes(ByteArray(10))
        val uri = file.toURI().toString()
        val first = key(uri)
        assertEquals(10L, first.size)
        assertEquals(first, key(uri))
    }

    @Test
    fun anEscapedFileUriReadsItsFile() {
        val file = folder.newFile("home icon.png")
        file.writeBytes(ByteArray(10))
        assertEquals(10L, key(file.toURI().toString()).size)
        // Unescaped, as an app may write it: the path after the scheme.
        assertEquals(10L, key("file://${file.path}").size)
    }

    @Test
    fun aMissingFileAndAnotherUriHaveNoVersion() {
        val missing = key("file:///no/such/home.png")
        assertEquals(0L, missing.modified)
        assertEquals(0L, missing.size)
        val drawable = key("ic_home")
        assertEquals(0L, drawable.modified)
        assertEquals(0L, drawable.size)
    }

    private fun key(
        uri: String,
        densityDpi: Int = 420,
        uiMode: Int = day,
        direction: Int = View.LAYOUT_DIRECTION_LTR,
    ) = ImageKey.of(uri, densityDpi, uiMode, direction)
}

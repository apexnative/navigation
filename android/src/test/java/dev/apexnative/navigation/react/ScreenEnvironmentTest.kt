package dev.apexnative.navigation.react

import dev.apexnative.navigation.react.ScreenEnvironment.Arrangement
import dev.apexnative.navigation.react.ScreenEnvironment.Insets
import dev.apexnative.navigation.react.ScreenEnvironment.Region
import dev.apexnative.navigation.react.ScreenEnvironment.Regions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The store of each screen's environment: each fact set on its own without touching the others, one remove that clears
 * all of them, an event only on a change (regions: none for no regions at first), and padding listeners called only
 * for their screen and only when its padding changes.
 */
class ScreenEnvironmentTest {
    private class RecordedChanges : ScreenEnvironment.Changes {
        val entries = mutableListOf<String>()

        override fun insetsChanged(screenId: String, insets: Insets) {
            entries += "insets:$screenId"
        }

        override fun regionsChanged(screenId: String, regions: Regions) {
            entries += "regions:$screenId"
        }

        override fun arrangementChanged(screenId: String, arrangement: Arrangement) {
            entries += "arrangement:$screenId"
        }
    }

    private val changes = RecordedChanges()
    private val insets = Insets(0.0, 0.0, 34.0, 0.0)
    private val padding = Insets(47.0, 0.0, 0.0, 0.0)
    private val regions = Regions(listOf(Region(0.0, 400.0, 360.0, 0.0)), emptyList())
    private val arrangement = Arrangement("primary", "split", front = false, hidden = false)

    @After
    fun tearDown() {
        listOf(HOME, PROFILE).forEach(ScreenEnvironment::remove)
    }

    @Test
    fun eachFactIsSetWithoutTouchingTheOthers() {
        ScreenEnvironment.setInsets(HOME, insets, padding, changes)
        ScreenEnvironment.setRegions(HOME, regions, changes)
        ScreenEnvironment.setArrangement(HOME, arrangement, notify = true, changes)

        assertEquals(insets, ScreenEnvironment.insets(HOME))
        assertEquals(padding, ScreenEnvironment.padding(HOME))
        assertEquals(regions, ScreenEnvironment.regions(HOME))
        assertEquals(arrangement, ScreenEnvironment.arrangement(HOME))
        assertNull(ScreenEnvironment.padding(PROFILE))
    }

    @Test
    fun oneRemoveClearsTheWholeEnvironmentOfThatScreenOnly() {
        ScreenEnvironment.setInsets(HOME, insets, padding, changes)
        ScreenEnvironment.setRegions(HOME, regions, changes)
        ScreenEnvironment.setArrangement(HOME, arrangement, notify = true, changes)
        ScreenEnvironment.setInsets(PROFILE, insets, padding, changes)

        ScreenEnvironment.remove(HOME)

        assertNull(ScreenEnvironment.insets(HOME))
        assertNull(ScreenEnvironment.padding(HOME))
        assertNull(ScreenEnvironment.regions(HOME))
        assertNull(ScreenEnvironment.arrangement(HOME))
        assertEquals(padding, ScreenEnvironment.padding(PROFILE))
    }

    @Test
    fun insetsAreToldOnlyWhenTheyChange() {
        ScreenEnvironment.setInsets(HOME, insets, padding, changes)
        ScreenEnvironment.setInsets(HOME, insets, padding, changes)
        // A new padding with the same insets for JavaScript isn't an event.
        ScreenEnvironment.setInsets(HOME, insets, Insets(0.0, 0.0, 0.0, 0.0), changes)
        ScreenEnvironment.setInsets(HOME, insets.copy(top = 20.0), Insets(0.0, 0.0, 0.0, 0.0), changes)

        assertEquals(listOf("insets:$HOME", "insets:$HOME"), changes.entries)
    }

    @Test
    fun regionsAreToldOnlyWhenTheyChangeAndNotForNoneAtFirst() {
        val none = Regions(emptyList(), emptyList())
        ScreenEnvironment.setRegions(HOME, none, changes)
        ScreenEnvironment.setRegions(HOME, regions, changes)
        ScreenEnvironment.setRegions(HOME, regions, changes)
        ScreenEnvironment.setRegions(HOME, none, changes)

        assertEquals(listOf("regions:$HOME", "regions:$HOME"), changes.entries)
    }

    @Test
    fun theArrangementIsToldOnlyWhenItChangesAndNotifyHolds() {
        ScreenEnvironment.setArrangement(HOME, arrangement, notify = false, changes)
        ScreenEnvironment.setArrangement(HOME, arrangement, notify = true, changes)
        val overlay = arrangement.copy(layout = "overlay", front = true)
        ScreenEnvironment.setArrangement(HOME, overlay, notify = true, changes)

        assertEquals(listOf("arrangement:$HOME"), changes.entries)
        assertEquals(overlay, ScreenEnvironment.arrangement(HOME))
    }

    @Test
    fun aRemovedScreenStartsAnew() {
        ScreenEnvironment.setInsets(HOME, insets, padding, changes)
        ScreenEnvironment.remove(HOME)
        ScreenEnvironment.setInsets(HOME, insets, padding, changes)

        assertEquals(listOf("insets:$HOME", "insets:$HOME"), changes.entries)
    }

    @Test
    fun aPaddingListenerHearsOnlyItsScreenAndOnlyAPaddingChange() {
        var heard = 0
        val stop = ScreenEnvironment.listenToPadding(HOME) { heard++ }

        ScreenEnvironment.setInsets(HOME, insets, padding, changes)
        ScreenEnvironment.setInsets(HOME, insets.copy(top = 20.0), padding, changes)
        ScreenEnvironment.setInsets(PROFILE, insets, padding.copy(top = 10.0), changes)
        ScreenEnvironment.setInsets(HOME, insets, padding.copy(top = 10.0), changes)
        assertEquals(2, heard)

        stop()
        ScreenEnvironment.setInsets(HOME, insets, padding, changes)
        assertEquals(2, heard)
    }

    @Test
    fun aListenerKeepsListeningAcrossARemove() {
        var heard = 0
        val stop = ScreenEnvironment.listenToPadding(HOME) { heard++ }

        ScreenEnvironment.setInsets(HOME, insets, padding, changes)
        ScreenEnvironment.remove(HOME)
        // The screen measured again after a remove: its padding is new to the store, and the view hears it.
        ScreenEnvironment.setInsets(HOME, insets, padding, changes)
        stop()

        assertEquals(2, heard)
    }

    @Test
    fun aListenerSeesTheNewPaddingWhenCalled() {
        var seen: Insets? = null
        val stop = ScreenEnvironment.listenToPadding(HOME) { seen = ScreenEnvironment.padding(HOME) }

        ScreenEnvironment.setInsets(HOME, insets, padding, changes)
        stop()

        assertEquals(padding, seen)
    }

    private companion object {
        const val HOME = "Home1"
        const val PROFILE = "Profile2"
    }
}

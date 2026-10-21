package dev.apexnative.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether the activity draws edge to edge. */
class EdgeToEdgeDecisionTest {
    private fun decide(app: Boolean = false, sdk: Int, target: Int, optOut: Boolean? = null) =
        EdgeToEdgeDecision.isEnabled(app, sdk, target, optOut)

    @Test
    fun theAppAsksForItOnEveryVersion() {
        assertTrue(decide(app = true, sdk = 24, target = 24, optOut = true))
    }

    @Test
    fun android16WithTarget36AlwaysDrawsEdgeToEdge() {
        assertTrue(decide(sdk = 36, target = 36, optOut = true))
    }

    @Test
    fun android15WithTarget35UnlessTheThemeOptsOut() {
        assertTrue(decide(sdk = 35, target = 35))
        assertTrue(decide(sdk = 36, target = 35, optOut = false))
        assertFalse(decide(sdk = 35, target = 35, optOut = true))
        assertFalse(decide(sdk = 36, target = 35, optOut = true))
    }

    @Test
    fun android15WithALowerTargetOnlyWhenTheThemeSetsTheAttributeToFalse() {
        assertFalse(decide(sdk = 35, target = 34))
        assertFalse(decide(sdk = 35, target = 34, optOut = true))
        assertTrue(decide(sdk = 35, target = 34, optOut = false))
    }

    // Before Android 15 only the app's flag.
    @Test
    fun belowAndroid15NeverWithoutTheAppsFlag() {
        assertFalse(decide(sdk = 34, target = 36))
        assertFalse(decide(sdk = 34, target = 34, optOut = true))
        assertFalse(decide(sdk = 34, target = 34, optOut = false))
        assertTrue(decide(app = true, sdk = 34, target = 34))
    }
}

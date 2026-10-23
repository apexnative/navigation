package dev.apexnative.navigation

import dev.apexnative.navigation.react.ReactGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The application's React gateway: only the first activity makes it ([NavigationApplication.reactGateway]); the log,
 * the events and the React roots read it ([NavigationApplication.reactGatewayOrNull]) and never make it, as they may
 * run first, off the main thread, before the app's `reactHost` can be made.
 */
class NavigationApplicationTest {
    private class App : NavigationApplication() {
        var made = 0

        override fun createReactGateway(): ReactGateway {
            made++
            error("no React host in unit tests")
        }
    }

    @Test
    fun aReaderDoesntMakeTheGateway() {
        val app = App()
        assertNull(app.reactGatewayOrNull)
        assertNull(app.reactGatewayOrNull)
        assertEquals(0, app.made)
    }

    @Test
    fun theGatewayAskedForBeforeReactNativeLoadsFailsAndTheReaderStillHasNone() {
        val app = App()
        val failure = assertThrows(IllegalStateException::class.java) { app.reactGateway }
        assertEquals(
            "NavigationApplication needs React Native loaded first: call loadReactNative(this) in onCreate, " +
                "as the React Native template does",
            failure.message,
        )
        assertEquals(0, app.made)
        assertNull(app.reactGatewayOrNull)
    }
}

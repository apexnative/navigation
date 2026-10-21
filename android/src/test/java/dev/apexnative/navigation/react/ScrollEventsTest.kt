package dev.apexnative.navigation.react

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The fields of React Native's scroll event, looked up under both spellings and read as floats. */
class ScrollEventsTest {
    // As React Native 0.87 has them: no prefix, floats.
    @Suppress("unused")
    private class Current {
        private val scrollY: Float = 120.5f
        private val yVelocity: Float = -3.25f
    }

    // As earlier versions have them: the `m` prefix, an int offset and a double speed.
    @Suppress("unused")
    private class Earlier {
        private val mScrollY: Int = 240
        private val mYVelocity: Double = 1.5
    }

    // A field with the name but not a number, and one boxed number.
    @Suppress("unused")
    private class Odd {
        private val scrollY: String = "120"
        private val mScrollY: Long? = 7L
    }

    private fun offsetOf(target: Any): Float? {
        val field = ScrollEvents.declaredField(target.javaClass, listOf("scrollY", "mScrollY"), ScrollEvents::isNumeric)
            ?: return null
        return ScrollEvents.readFloat(field, target)
    }

    private fun velocityOf(target: Any): Float? {
        val names = listOf("yVelocity", "mYVelocity")
        val field = ScrollEvents.declaredField(target.javaClass, names, ScrollEvents::isNumeric) ?: return null
        return ScrollEvents.readFloat(field, target)
    }

    @Test
    fun theCurrentFloatFieldsAreRead() {
        val target = Current()
        assertEquals(120.5f, offsetOf(target))
        assertEquals(-3.25f, velocityOf(target))
    }

    @Test
    fun theEarlierNamesAndAnyNumericTypeAreRead() {
        val target = Earlier()
        assertEquals(240f, offsetOf(target))
        assertEquals(1.5f, velocityOf(target))
    }

    @Test
    fun aFieldThatIsNoNumberIsPassedOver() {
        // `scrollY` is text: the next spelling, a boxed number, is taken.
        assertEquals(7f, offsetOf(Odd()))
    }

    @Test
    fun noFieldUnderEitherName() {
        assertNull(ScrollEvents.declaredField(Any::class.java, listOf("scrollY", "mScrollY"), ScrollEvents::isNumeric))
    }
}

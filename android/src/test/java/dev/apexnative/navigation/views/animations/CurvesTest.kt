package dev.apexnative.navigation.views.animations

import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import dev.apexnative.navigation.options.InterpolationOptions
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * The curves of `interpolation`: each type at p = 0, 0.25, 0.5, 0.75, 1 against its formula — the vectors iOS checks
 * too —, the `fastOutSlowIn` table against AndroidX's, the spring's three ways, its defaults and its real time, and the
 * ends exact whatever the curve gives.
 */
class CurvesTest {
    private val points = listOf(0.0, 0.25, 0.5, 0.75, 1.0)

    private fun curve(type: String?, duration: Long = 300L, change: InterpolationOptions.() -> Unit = {}): Curve =
        Curves.of(InterpolationOptions().apply {
            type?.let { this.type = Text(it) }
            change()
        }, duration)

    private fun assertVector(curve: Curve, vararg expected: Double) {
        points.forEachIndexed { index, p -> assertEquals("f($p)", expected[index], curve.value(p), 1e-12) }
    }

    @Test
    fun theFormulasAtFivePoints() {
        assertVector(curve(null), 0.0, 0.25, 0.5, 0.75, 1.0)
        assertVector(curve("linear"), 0.0, 0.25, 0.5, 0.75, 1.0)
        assertVector(curve("accelerate"), 0.0, 0.0625, 0.25, 0.5625, 1.0)
        assertVector(curve("accelerate") { factor = Fraction(2.0) }, 0.0, 0.00390625, 0.0625, 0.31640625, 1.0)
        assertVector(curve("decelerate"), 0.0, 0.4375, 0.75, 0.9375, 1.0)
        assertVector(curve("decelerate") { factor = Fraction(2.0) }, 0.0, 0.68359375, 0.9375, 0.99609375, 1.0)
        // Slow at the ends, the mirror of decelerateAccelerate.
        assertVector(curve("accelerateDecelerate"), 0.0, 0.0625, 0.5, 0.9375, 1.0)
        // Fast at the ends, f(0) = 0 and f(1) = 1.
        assertVector(curve("decelerateAccelerate"), 0.0, 0.4375, 0.5, 0.5625, 1.0)
        assertVector(curve("overshoot"), 0.0, 0.71875, 1.0, 1.03125, 1.0)
        assertVector(curve("overshoot") { tension = Fraction(2.0) }, 0.0, 0.859375, 1.125, 1.078125, 1.0)
    }

    @Test
    fun aMissingOrUnknownTypeIsLinear() {
        assertVector(Curves.of(null, 300L), 0.0, 0.25, 0.5, 0.75, 1.0)
        assertVector(curve(null) { factor = Fraction(3.0) }, 0.0, 0.25, 0.5, 0.75, 1.0)
    }

    @Test
    fun theEndsAreExactBeforeAndAfter() {
        for (type in listOf("linear", "overshoot", "spring", "fastOutSlowIn", "decelerateAccelerate")) {
            val curve = curve(type)
            assertEquals(type, 0.0, curve.value(-0.5), 0.0)
            assertEquals(type, 0.0, curve.value(0.0), 0.0)
            assertEquals(type, 1.0, curve.value(1.0), 0.0)
            assertEquals(type, 1.0, curve.value(1.5), 0.0)
            assertEquals(type, 1f, curve.getInterpolation(1f), 0f)
            assertEquals(type, 0f, curve.getInterpolation(0f), 0f)
        }
    }

    // The Bézier (0.4, 0, 0.2, 1) at x = i / 200, to 4 decimals: AndroidX's own values.
    @Test
    fun fastOutSlowInIsAndroidxsTable() {
        val androidx = FastOutSlowInInterpolator()
        val table = Curves.FastOutSlowIn.table
        assertEquals(201, table.size)
        for (i in 0..200) {
            assertEquals("value $i", androidx.getInterpolation(i / 200f).toDouble(), table[i], 1e-6)
        }
        for (step in 0..997) {
            val p = step / 997.0
            val expected = androidx.getInterpolation(p.toFloat()).toDouble()
            assertEquals("p = $p", expected, Curves.FastOutSlowIn.value(p), 2e-6)
            // The table is the curve, near enough: rounding and the straight lines between its values.
            assertEquals("p = $p", Curves.FastOutSlowIn.bezierAt(p), Curves.FastOutSlowIn.value(p), 1e-4)
        }
    }

    @Test
    fun fastOutSlowInVectors() {
        val curve = curve("fastOutSlowIn")
        for (p in points) assertEquals(Curves.FastOutSlowIn.bezierAt(p), curve.value(p), 1e-4)
        assertEquals(0.0, curve.value(0.0), 0.0)
        assertEquals(1.0, curve.value(1.0), 0.0)
    }

    // The spring by its own formulas, written again here.
    private fun spring(m: Double, c: Double, k: Double, v0: Double, overdamping: Boolean, t: Double): Double {
        val omega = sqrt(k / m)
        val zeta = (c / (2 * m)).let { if (!overdamping && it > omega) omega else it }
        val decay = exp(-zeta * t)
        val gap = zeta * zeta - omega * omega
        val x = when {
            abs(gap) < 1e-9 -> decay * (-1 + (v0 - zeta) * t)
            gap < 0 -> sqrt(-gap).let { decay * (-cos(it * t) + (v0 - zeta) / it * sin(it * t)) }
            else -> sqrt(gap).let { decay * (-cosh(it * t) + (v0 - zeta) / it * sinh(it * t)) }
        }
        return 1 + x
    }

    @Test
    fun theSpringsThreeWays() {
        // Oscillating: ζ = 1 < ω₀ = 10.
        val oscillating = curve("spring", 1000L) {
            mass = Fraction(1.0)
            damping = Fraction(2.0)
            stiffness = Fraction(100.0)
        }
        for (p in points.dropLast(1)) {
            assertEquals("p = $p", spring(1.0, 2.0, 100.0, 0.0, false, p), oscillating.value(p), 1e-12)
        }
        assertTrue(oscillating.value(0.25) > 1.0)
        // Over the critical damping: critical without `allowsOverdamping`, slower with it.
        val critical = curve("spring", 1000L) {
            mass = Fraction(1.0)
            damping = Fraction(40.0)
            stiffness = Fraction(100.0)
        }
        val over = curve("spring", 1000L) {
            mass = Fraction(1.0)
            damping = Fraction(40.0)
            stiffness = Fraction(100.0)
            allowsOverdamping = Bool(true)
        }
        for (p in listOf(0.25, 0.5, 0.75)) {
            assertEquals(spring(1.0, 40.0, 100.0, 0.0, false, p), critical.value(p), 1e-12)
            assertEquals(spring(1.0, 40.0, 100.0, 0.0, true, p), over.value(p), 1e-12)
            assertTrue(over.value(p) < critical.value(p))
        }
        assertEquals(1 + exp(-2.5) * (-1 - 2.5), critical.value(0.25), 1e-12)
    }

    // The defaults are critical (ζ ≈ 8.16 per second): about 70 % of the way at 300 ms, in real time.
    @Test
    fun theDefaultSpringInRealTime() {
        val short = curve("spring", 300L)
        assertEquals(spring(3.0, 500.0, 200.0, 0.0, false, 0.15), short.value(0.5), 1e-12)
        assertEquals(0.702, short.value(0.9999), 0.002)
        assertEquals(1.0, short.value(1.0), 0.0)
        // The same moment of real time is the same value, whatever the duration.
        val long = curve("spring", 600L)
        assertEquals(short.value(0.5), long.value(0.25), 1e-12)
        // The initial velocity starts it faster.
        val pushed = curve("spring", 300L) { initialVelocity = Fraction(10.0) }
        assertTrue(pushed.value(0.2) > short.value(0.2))
    }

    // A mass, a damping or a stiffness of 0 or less takes its default.
    @Test
    fun springParametersAtOrBelowZeroTakeTheDefaults() {
        val defaults = curve("spring")
        val broken = curve("spring") {
            mass = Fraction(0.0)
            damping = Fraction(-1.0)
            stiffness = Fraction(0.0)
        }
        for (p in points) assertEquals(defaults.value(p), broken.value(p), 1e-12)
        for (p in points) assertTrue(!broken.value(p).isNaN())
    }

    @Test
    fun theVectorsSharedWithIos() {
        // p = 0.3 of 300 ms, the default spring; overshoot at 0.9; fastOutSlowIn at 0.3 (by the table).
        val zeta = sqrt(200 / 3.0)
        assertEquals(1 + exp(-0.09 * zeta) * (-1 - 0.09 * zeta), curve("spring").value(0.3), 1e-12)
        assertEquals(1 + 0.01 * (2 * -0.1 + 1), curve("overshoot").value(0.9), 1e-12)
        val table = Curves.FastOutSlowIn.table
        assertEquals(table[60], curve("fastOutSlowIn").value(0.3), 1e-12)
        assertEquals(0.5 + 4 * (0.1 - 0.5).pow(3), curve("decelerateAccelerate").value(0.1), 1e-12)
    }
}

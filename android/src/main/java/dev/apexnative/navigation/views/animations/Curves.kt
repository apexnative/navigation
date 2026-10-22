package dev.apexnative.navigation.views.animations

import android.animation.TimeInterpolator
import dev.apexnative.navigation.options.InterpolationOptions
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * A curve of `interpolation`: the part of the way f(p) at the part of the time p. Before the start it is exactly 0 and
 * from the end on exactly 1, whatever the formula gives there: an animator's last frame is its target. Pure.
 */
internal abstract class Curve : TimeInterpolator {
    /** f(p) for p in [0, 1]. */
    protected abstract fun at(p: Double): Double

    /** f(p): 0 at and before the start, 1 at and after the end. */
    fun value(p: Double): Double = when {
        p.isNaN() || p <= 0.0 -> 0.0
        p >= 1.0 -> 1.0
        else -> at(p)
    }

    override fun getInterpolation(input: Float): Float = value(input.toDouble()).toFloat()
}

/**
 * The curves of `interpolation` by `type`, written from their formulas: the same values on iOS. A missing or unknown
 * `type` is `linear`. The framework's curves are never used for the option (its `AccelerateDecelerateInterpolator` is a
 * cosine, its `OvershootInterpolator` has another tension). Pure.
 */
internal object Curves {
    /** The curve of [options] for a property that lasts [durationMillis]: a spring runs in real time. */
    fun of(options: InterpolationOptions?, durationMillis: Long): Curve {
        options ?: return Linear
        return when (options.type.get(null)) {
            "accelerate" -> Accelerate(factorOf(options))
            "decelerate" -> Decelerate(factorOf(options))
            "accelerateDecelerate" -> AccelerateDecelerate
            "decelerateAccelerate" -> DecelerateAccelerate
            "fastOutSlowIn" -> FastOutSlowIn
            "overshoot" -> Overshoot(finite(options.tension.get(null)) ?: 1.0)
            "spring" -> Spring(
                mass = positive(options.mass.get(null)) ?: Spring.MASS,
                damping = positive(options.damping.get(null)) ?: Spring.DAMPING,
                stiffness = positive(options.stiffness.get(null)) ?: Spring.STIFFNESS,
                allowsOverdamping = options.allowsOverdamping.isTrue,
                initialVelocity = finite(options.initialVelocity.get(null)) ?: 0.0,
                durationSeconds = durationMillis.coerceAtLeast(0L) / MILLIS,
            )
            else -> Linear
        }
    }

    /** `linear`: f = p. */
    object Linear : Curve() {
        override fun at(p: Double): Double = p
    }

    /** `accelerate`: f = p^(2·factor). */
    class Accelerate(private val factor: Double) : Curve() {
        override fun at(p: Double): Double = p.pow(2 * factor)
    }

    /** `decelerate`: f = 1 − (1 − p)^(2·factor). */
    class Decelerate(private val factor: Double) : Curve() {
        override fun at(p: Double): Double = 1 - (1 - p).pow(2 * factor)
    }

    /** `accelerateDecelerate`: 4p³ before the middle, 1 − 4(1 − p)³ after it — slow at the ends. */
    object AccelerateDecelerate : Curve() {
        override fun at(p: Double): Double = if (p < HALF) 4 * p * p * p else 1 - 4 * (1 - p).pow(3)
    }

    /** `decelerateAccelerate`: 0.5 + 4(p − 0.5)³ — fast at the ends, still in the middle. */
    object DecelerateAccelerate : Curve() {
        override fun at(p: Double): Double = HALF + 4 * (p - HALF).pow(3)
    }

    /** `overshoot` with r = p − 1: 1 + r²((tension + 1)·r + tension) — past the target and back. */
    class Overshoot(private val tension: Double) : Curve() {
        override fun at(p: Double): Double {
            val r = p - 1
            return 1 + r * r * ((tension + 1) * r + tension)
        }
    }

    /**
     * `fastOutSlowIn`: the cubic Bézier (0.4, 0, 0.2, 1) sampled at x = i / 200 and rounded to 4 decimals, 201 values,
     * linear between them — the values of AndroidX's `FastOutSlowInInterpolator`, computed here.
     */
    object FastOutSlowIn : Curve() {
        // Before the table, which is made from it.
        private val curve = CubicBezier(0.4, 0.0, 0.2, 1.0)

        /** The 201 values. */
        val table: DoubleArray = DoubleArray(TABLE_STEPS + 1) {
            round(bezierAt(it.toDouble() / TABLE_STEPS) * DECIMALS) / DECIMALS
        }

        override fun at(p: Double): Double {
            val position = p * TABLE_STEPS
            val index = position.toInt().coerceIn(0, TABLE_STEPS - 1)
            val within = position - index
            return table[index] + (table[index + 1] - table[index]) * within
        }

        /** y of the Bézier at its x = [x], exactly (to 1e-12). */
        fun bezierAt(x: Double): Double = curve.yAt(x)

        private const val DECIMALS = 1e4
    }

    /**
     * A cubic Bézier from (0, 0) to (1, 1) with the control points ([x1], [y1]) and ([x2], [y2]), as the framework's
     * `PathInterpolator` draws it, computed exactly: the system's curves without a framework class.
     */
    class CubicBezier(
        private val x1: Double,
        private val y1: Double,
        private val x2: Double,
        private val y2: Double,
    ) : Curve() {
        override fun at(p: Double): Double = yAt(p)

        /** y of the curve at its x = [x], exactly (to 1e-12). */
        fun yAt(x: Double): Double {
            if (x <= 0.0) return 0.0
            if (x >= 1.0) return 1.0
            // x(t) only grows with t: a bisection finds t, then y(t).
            var low = 0.0
            var high = 1.0
            var t = x
            repeat(BISECTION_STEPS) {
                t = (low + high) / 2
                if (coordinate(t, x1, x2) < x) low = t else high = t
            }
            return coordinate(t, y1, y2)
        }

        // One coordinate of the curve with the control points' coordinates [c1], [c2].
        private fun coordinate(t: Double, c1: Double, c2: Double): Double {
            val u = 1 - t
            return 3 * u * u * t * c1 + 3 * u * t * t * c2 + t * t * t
        }

        private companion object {
            const val BISECTION_STEPS = 60
        }
    }

    /**
     * `spring`: a mass on a damped spring, m·x″ + c·x′ + k·x = 0 with x(0) = −1 and x′(0) = [initialVelocity] (parts of
     * the way per second), at the real time t = p × the property's duration; f = 1 + x(t). ζ = c / 2m, ω₀ = √(k/m);
     * without [allowsOverdamping] a damping over the critical one counts as the critical one.
     */
    class Spring(
        mass: Double,
        damping: Double,
        stiffness: Double,
        allowsOverdamping: Boolean,
        private val initialVelocity: Double,
        private val durationSeconds: Double,
    ) : Curve() {
        private val omega = sqrt(stiffness / mass)
        private val zeta = (damping / (2 * mass)).let { if (!allowsOverdamping && it > omega) omega else it }

        override fun at(p: Double): Double = 1 + distance(p * durationSeconds)

        /** x(t): where the mass is at [t] seconds, −1 at the start and 0 at the target. */
        fun distance(t: Double): Double {
            val decay = exp(-zeta * t)
            val gap = zeta * zeta - omega * omega
            return when {
                abs(gap) < CRITICAL_EPSILON -> decay * (-1 + (initialVelocity - zeta) * t)
                gap < 0 -> {
                    val damped = sqrt(-gap)
                    decay * (-cos(damped * t) + (initialVelocity - zeta) / damped * sin(damped * t))
                }
                else -> {
                    val over = sqrt(gap)
                    decay * (-cosh(over * t) + (initialVelocity - zeta) / over * sinh(over * t))
                }
            }
        }

        companion object {
            const val MASS = 3.0
            const val DAMPING = 500.0
            const val STIFFNESS = 200.0
            private const val CRITICAL_EPSILON = 1e-9
        }
    }

    private fun factorOf(options: InterpolationOptions): Double = finite(options.factor.get(null)) ?: 1.0

    private fun finite(value: Double?): Double? = value?.takeIf { it.isFinite() }

    // `mass`, `damping`, `stiffness` of 0 or less take their defaults.
    private fun positive(value: Double?): Double? = finite(value)?.takeIf { it > 0 }

    private const val HALF = 0.5
    private const val MILLIS = 1000.0
    private const val TABLE_STEPS = 200
}

package dev.apexnative.navigation.options.parsers

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.opt
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.NullFraction

/**
 * Reads a number of the options: a finite number only; `NaN`, infinity, strings, booleans and `null` are unset.
 */
object FractionParser {
    @JvmStatic
    fun parse(map: ReadableMap?, key: String): Fraction {
        val value = map?.opt(key) as? Double ?: return NullFraction()
        return if (value.isFinite()) Fraction(value) else NullFraction()
    }

    /** A duration in milliseconds (`duration`, `startDelay`): a number as [parse] reads one, and not below 0. */
    @JvmStatic
    fun parseDuration(map: ReadableMap?, key: String): Fraction {
        val value = parse(map, key)
        return if (value.get(-1.0) >= 0) value else NullFraction()
    }
}

package dev.apexnative.navigation.options.params

/**
 * A number of the options: a size in dp, a radius, an alpha, an index, a duration in milliseconds. Area code
 * truncates it where its option is a whole number.
 */
open class Fraction(value: Double?) : Param<Double>(value)

/** A number the options don't give. */
class NullFraction : Fraction(null) {
    override fun hasValue(): Boolean = false
}

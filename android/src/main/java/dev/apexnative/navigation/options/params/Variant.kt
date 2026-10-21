package dev.apexnative.navigation.options.params

/**
 * A value whose option takes several forms (`popGesture: boolean | 'edge'`, a button's `badge: number | string |
 * boolean | null`): a `Boolean`, a `Double` or a `String`, as the option allows (`VariantParser`).
 */
open class Variant(value: Any?) : Param<Any>(value) {
    /** The options give `null` for it, where its option takes `null` as "none" (a badge taken away). */
    open val isNone: Boolean
        get() = false

    /** The value when it is a yes or no. */
    fun asBoolean(): Boolean? = value as? Boolean

    /** The value when it is a number. */
    fun asNumber(): Double? = value as? Double

    /** The value when it is a text. */
    fun asText(): String? = value as? String
}

/** A value the options don't give. */
class NullVariant : Variant(null) {
    override fun hasValue(): Boolean = false
}

/** `null` of a value whose option takes it as "none": set, and neither a yes or no, a number nor a text. */
class NoneVariant : Variant(NONE) {
    override val isNone: Boolean
        get() = true

    private companion object {
        // What a none holds: nothing area code reads as a value.
        val NONE = Any()
    }
}

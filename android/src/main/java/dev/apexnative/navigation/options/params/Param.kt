package dev.apexnative.navigation.options.params

/**
 * A value of the options that may be unset: the options give it, or they don't, and the layouts around or the default
 * options give it then.
 */
abstract class Param<T : Any>(@JvmField val value: T?) {
    /** The options give the value. */
    open fun hasValue(): Boolean = value != null

    /** The value; only for a value that is set. */
    fun get(): T = requireNotNull(value) { "the option has no value: check hasValue() first" }

    /** The value, or [defaultValue] when the options don't give it. */
    fun get(defaultValue: T): T = value ?: defaultValue

    /** The value, or null when the options don't give it. */
    fun get(@Suppress("UNUSED_PARAMETER") defaultValue: Nothing?): T? = value

    override fun equals(other: Any?): Boolean =
        other is Param<*> && other.javaClass == javaClass && other.value == value

    override fun hashCode(): Int = value?.hashCode() ?: 0
}

package dev.apexnative.navigation.options.params

/** A yes or no of the options; [isTrue] and [isFalse] are both false while it is unset. */
open class Bool(value: Boolean?) : Param<Boolean>(value) {
    /** Set, and true. */
    val isTrue: Boolean
        @JvmName("isTrueValue") get() = value == true

    /** Set, and false. */
    val isFalse: Boolean
        @JvmName("isFalseValue") get() = value == false

    /** Set, and true; as [isTrue]. */
    fun isTrue(): Boolean = isTrue

    /** Set, and false; as [isFalse]. */
    fun isFalse(): Boolean = isFalse
}

/** A yes or no the options don't give. */
class NullBool : Bool(null) {
    override fun hasValue(): Boolean = false
}

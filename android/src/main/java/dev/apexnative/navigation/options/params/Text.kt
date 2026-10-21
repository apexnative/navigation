package dev.apexnative.navigation.options.params

/** A text of the options: a title, an id, a name, an icon's path, one of the values of an enum. */
open class Text(value: String?) : Param<String>(value) {
    /**
     * The options give `null` for this text: an explicit "no text" that replaces what the layers below give; its value
     * is the empty text.
     */
    open val isNone: Boolean
        get() = false

    override fun toString(): String = value ?: ""
}

/** A text the options don't give. */
class NullText : Text(null) {
    override fun hasValue(): Boolean = false
}

/** `null` of a text: set, and no text. */
class NoneText : Text("") {
    override val isNone: Boolean
        get() = true
}

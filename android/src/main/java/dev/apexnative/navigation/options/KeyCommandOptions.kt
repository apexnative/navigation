package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.parsers.StringListParser
import dev.apexnative.navigation.options.parsers.TextParser

/** An item of `keyCommands`: a keyboard shortcut of a screen. */
class KeyCommandOptions {
    @JvmField var id: Text = NullText()
    @JvmField var input: Text = NullText()
    /** Unknown modifiers are dropped. */
    @JvmField var modifiers: List<String>? = null
    @JvmField var title: Text = NullText()

    fun hasValue(): Boolean = id.hasValue() || input.hasValue() || modifiers != null || title.hasValue()

    fun mergeWith(other: KeyCommandOptions) {
        if (other.id.hasValue()) id = other.id
        if (other.input.hasValue()) input = other.input
        other.modifiers?.let { modifiers = it }
        if (other.title.hasValue()) title = other.title
    }

    fun mergeWithDefault(defaults: KeyCommandOptions) {
        if (!id.hasValue()) id = defaults.id
        if (!input.hasValue()) input = defaults.input
        if (modifiers == null) modifiers = defaults.modifiers
        if (!title.hasValue()) title = defaults.title
    }

    fun copy() = KeyCommandOptions().also { it.mergeWith(this) }

    companion object {
        private val MODIFIERS = setOf("command", "shift", "option", "alternate", "control")

        @JvmStatic
        fun parse(map: ReadableMap?): KeyCommandOptions {
            val options = KeyCommandOptions()
            if (map == null) return options
            options.id = TextParser.parse(map, "id")
            options.input = TextParser.parse(map, "input")
            options.modifiers = StringListParser.parse(map, "modifiers", MODIFIERS)
            options.title = TextParser.parse(map, "title")
            return options
        }
    }
}

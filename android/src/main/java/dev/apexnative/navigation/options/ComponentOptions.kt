package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullFraction
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.FractionParser
import dev.apexnative.navigation.options.parsers.TextParser

/**
 * A React component in the options (`topBar.searchBar.resultsComponent`): its registered `name`, the `id` the app gave
 * it, and the `componentId` JavaScript made it with (the app's `id`, else a new one); its props stay in JavaScript. The
 * components with more fields have groups of their own ([TitleComponentOptions], [ButtonComponentOptions]).
 */
class ComponentOptions(
    @JvmField var name: Text = NullText(),
    @JvmField var componentId: Text = NullText(),
    @JvmField var id: Text = NullText(),
) {
    /** The options give a component. */
    fun hasValue(): Boolean = name.hasValue()

    fun mergeWith(other: ComponentOptions) {
        if (other.name.hasValue()) name = other.name
        if (other.componentId.hasValue()) componentId = other.componentId
        if (other.id.hasValue()) id = other.id
    }

    fun mergeWithDefault(defaultOptions: ComponentOptions) {
        if (!name.hasValue()) name = defaultOptions.name
        if (!componentId.hasValue()) componentId = defaultOptions.componentId
        if (!id.hasValue()) id = defaultOptions.id
    }

    fun copy(): ComponentOptions = ComponentOptions(name, componentId, id)

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): ComponentOptions {
            if (map == null) return ComponentOptions()
            return ComponentOptions(
                name = TextParser.parse(map, "name"),
                componentId = TextParser.parse(map, "componentId"),
                id = TextParser.parse(map, "id"),
            )
        }
    }
}

/** `topBar.title.component`: a React component in place of the title, and how it lays out. */
class TitleComponentOptions {
    @JvmField var name: Text = NullText()
    @JvmField var componentId: Text = NullText()
    @JvmField var id: Text = NullText()
    @JvmField var alignment: Text = NullText()
    @JvmField var waitForRender: Bool = NullBool()

    fun hasValue(): Boolean = name.hasValue()

    fun mergeWith(other: TitleComponentOptions) {
        if (other.name.hasValue()) name = other.name
        if (other.componentId.hasValue()) componentId = other.componentId
        if (other.id.hasValue()) id = other.id
        if (other.alignment.hasValue()) alignment = other.alignment
        if (other.waitForRender.hasValue()) waitForRender = other.waitForRender
    }

    fun mergeWithDefault(defaults: TitleComponentOptions) {
        if (!name.hasValue()) name = defaults.name
        if (!componentId.hasValue()) componentId = defaults.componentId
        if (!id.hasValue()) id = defaults.id
        if (!alignment.hasValue()) alignment = defaults.alignment
        if (!waitForRender.hasValue()) waitForRender = defaults.waitForRender
    }

    fun copy() = TitleComponentOptions().also { it.mergeWith(this) }

    companion object {
        /** The values of `alignment`. */
        val ALIGNMENTS = setOf("center", "fill")

        @JvmStatic
        fun parse(map: ReadableMap?): TitleComponentOptions {
            val options = TitleComponentOptions()
            if (map == null) return options
            options.name = TextParser.parse(map, "name")
            options.componentId = TextParser.parse(map, "componentId")
            options.id = TextParser.parse(map, "id")
            options.alignment = TextParser.parse(map, "alignment", ALIGNMENTS)
            options.waitForRender = BoolParser.parse(map, "waitForRender")
            return options
        }
    }
}

/** The `component` of a button of the bar: a React component in place of its icon or text, and its size in dp. */
class ButtonComponentOptions {
    @JvmField var name: Text = NullText()
    @JvmField var componentId: Text = NullText()
    @JvmField var id: Text = NullText()
    @JvmField var width: Fraction = NullFraction()
    @JvmField var height: Fraction = NullFraction()

    fun hasValue(): Boolean = name.hasValue()

    fun mergeWith(other: ButtonComponentOptions) {
        if (other.name.hasValue()) name = other.name
        if (other.componentId.hasValue()) componentId = other.componentId
        if (other.id.hasValue()) id = other.id
        if (other.width.hasValue()) width = other.width
        if (other.height.hasValue()) height = other.height
    }

    fun mergeWithDefault(defaults: ButtonComponentOptions) {
        if (!name.hasValue()) name = defaults.name
        if (!componentId.hasValue()) componentId = defaults.componentId
        if (!id.hasValue()) id = defaults.id
        if (!width.hasValue()) width = defaults.width
        if (!height.hasValue()) height = defaults.height
    }

    fun copy() = ButtonComponentOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): ButtonComponentOptions {
            val options = ButtonComponentOptions()
            if (map == null) return options
            options.name = TextParser.parse(map, "name")
            options.componentId = TextParser.parse(map, "componentId")
            options.id = TextParser.parse(map, "id")
            options.width = FractionParser.parse(map, "width")
            options.height = FractionParser.parse(map, "height")
            return options
        }
    }
}

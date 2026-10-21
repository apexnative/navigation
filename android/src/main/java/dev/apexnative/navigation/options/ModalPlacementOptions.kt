package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullFraction
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.NullVariant
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.Variant
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.FractionParser
import dev.apexnative.navigation.options.parsers.StringListParser
import dev.apexnative.navigation.options.parsers.TextParser
import dev.apexnative.navigation.options.parsers.VariantParser

/** `modal.contentSize` (`OptionsModalContentSize`): the size of a modal, in dp. `height` is a number or `'content'`. */
class ModalContentSizeOptions {
    @JvmField var width: Fraction = NullFraction()
    /** `number | 'content'`. */
    @JvmField var height: Variant = NullVariant()

    fun hasValue(): Boolean = width.hasValue() || height.hasValue()

    fun mergeWith(other: ModalContentSizeOptions) {
        if (other.width.hasValue()) width = other.width
        if (other.height.hasValue()) height = other.height
    }

    fun mergeWithDefault(defaults: ModalContentSizeOptions) {
        if (!width.hasValue()) width = defaults.width
        if (!height.hasValue()) height = defaults.height
    }

    fun copy() = ModalContentSizeOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): ModalContentSizeOptions {
            val options = ModalContentSizeOptions()
            if (map == null) return options
            options.width = FractionParser.parse(map, "width")
            options.height = VariantParser.parse(map, "height", numbers = true, texts = setOf("content"))
            return options
        }
    }
}

/** `modal.popover` (`OptionsModalPopover`): where a modal shown as a popover points to. */
class ModalPopoverOptions {
    @JvmField var sourceButtonId: Text = NullText()
    @JvmField var sourceNativeID: Text = NullText()
    @JvmField var arrowDirections: List<String>? = null
    @JvmField var adaptive: Bool = NullBool()

    fun hasValue(): Boolean = sourceButtonId.hasValue() || sourceNativeID.hasValue() || arrowDirections != null ||
        adaptive.hasValue()

    fun mergeWith(other: ModalPopoverOptions) {
        if (other.sourceButtonId.hasValue()) sourceButtonId = other.sourceButtonId
        if (other.sourceNativeID.hasValue()) sourceNativeID = other.sourceNativeID
        other.arrowDirections?.let { arrowDirections = it }
        if (other.adaptive.hasValue()) adaptive = other.adaptive
    }

    fun mergeWithDefault(defaults: ModalPopoverOptions) {
        if (!sourceButtonId.hasValue()) sourceButtonId = defaults.sourceButtonId
        if (!sourceNativeID.hasValue()) sourceNativeID = defaults.sourceNativeID
        if (arrowDirections == null) arrowDirections = defaults.arrowDirections
        if (!adaptive.hasValue()) adaptive = defaults.adaptive
    }

    fun copy() = ModalPopoverOptions().also { it.mergeWith(this) }

    companion object {
        private val ARROW_DIRECTIONS = setOf("up", "down", "left", "right")

        @JvmStatic
        fun parse(map: ReadableMap?): ModalPopoverOptions {
            val options = ModalPopoverOptions()
            if (map == null) return options
            options.sourceButtonId = TextParser.parse(map, "sourceButtonId")
            options.sourceNativeID = TextParser.parse(map, "sourceNativeID")
            options.arrowDirections = StringListParser.parse(map, "arrowDirections", ARROW_DIRECTIONS)
            options.adaptive = BoolParser.parse(map, "adaptive")
            return options
        }
    }
}

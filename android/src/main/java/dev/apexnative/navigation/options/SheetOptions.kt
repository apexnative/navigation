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
 * A height a sheet stops at: about half the screen, the full height, the height of the screen's content, or a height in
 * dp.
 */
sealed class SheetDetent {
    object Medium : SheetDetent()
    object Large : SheetDetent()
    object Content : SheetDetent()
    data class Height(val dp: Double) : SheetDetent()

    /** The detent as the app names it, in `sheetDetentChanged`. */
    fun toValue(): Any = when (this) {
        Medium -> "medium"
        Large -> "large"
        Content -> "content"
        is Height -> dp
    }

    companion object {
        fun parse(value: Any?): SheetDetent? = when (value) {
            "medium" -> Medium
            "large" -> Large
            "content" -> Content
            // A number is a height only when it is finite and positive.
            is Number -> value.toDouble().takeIf { it.isFinite() && it > 0 }?.let { Height(it) }
            else -> null
        }
    }
}

/**
 * `modal.sheet`: a modal shown as a Material bottom sheet, its detents and its look. In mergeOptions only the given
 * fields change; `largestUndimmedDetent: null` dims at every detent again.
 */
class SheetOptions {
    @JvmField var detents: List<SheetDetent>? = null
    @JvmField var selectedDetent: SheetDetent? = null
    @JvmField var largestUndimmedDetent: SheetDetent? = null
    /** The options give `largestUndimmedDetent`, `null` included. */
    @OptionKey("largestUndimmedDetent") @JvmField var hasLargestUndimmedDetent = false
    @JvmField var grabber: Bool = NullBool()
    @JvmField var expandsOnScroll: Bool = NullBool()
    @JvmField var cornerRadius: Fraction = NullFraction()
    @JvmField var testID: Text = NullText()

    /** The options give the sheet: the modal shows as one. */
    val hasValue: Boolean
        get() = detents != null || selectedDetent != null || hasLargestUndimmedDetent || grabber.hasValue() ||
            expandsOnScroll.hasValue() || cornerRadius.hasValue() ||
            testID.hasValue()

    fun mergeWith(other: SheetOptions) {
        other.detents?.let { detents = it }
        other.selectedDetent?.let { selectedDetent = it }
        if (other.hasLargestUndimmedDetent) {
            largestUndimmedDetent = other.largestUndimmedDetent
            hasLargestUndimmedDetent = true
        }
        if (other.grabber.hasValue()) grabber = other.grabber
        if (other.expandsOnScroll.hasValue()) expandsOnScroll = other.expandsOnScroll
        if (other.cornerRadius.hasValue()) cornerRadius = other.cornerRadius
        if (other.testID.hasValue()) testID = other.testID
    }

    fun mergeWithDefault(defaults: SheetOptions) {
        if (detents == null) detents = defaults.detents
        if (selectedDetent == null) selectedDetent = defaults.selectedDetent
        if (!hasLargestUndimmedDetent && defaults.hasLargestUndimmedDetent) {
            largestUndimmedDetent = defaults.largestUndimmedDetent
            hasLargestUndimmedDetent = true
        }
        if (!grabber.hasValue()) grabber = defaults.grabber
        if (!expandsOnScroll.hasValue()) expandsOnScroll = defaults.expandsOnScroll
        if (!cornerRadius.hasValue()) cornerRadius = defaults.cornerRadius
        if (!testID.hasValue()) testID = defaults.testID
    }

    companion object {
        @JvmStatic
        fun parse(json: ReadableMap?): SheetOptions {
            val options = SheetOptions()
            if (json == null) return options
            json.optArray("detents")?.let { array ->
                options.detents = (0 until array.size()).mapNotNull { SheetDetent.parse(array.opt(it)) }.distinct()
            }
            options.selectedDetent = SheetDetent.parse(json.opt("selectedDetent"))
            if (json.has("largestUndimmedDetent")) {
                options.hasLargestUndimmedDetent = true
                options.largestUndimmedDetent = SheetDetent.parse(json.opt("largestUndimmedDetent"))
            }
            options.grabber = BoolParser.parse(json, "grabber")
            options.expandsOnScroll = BoolParser.parse(json, "expandsOnScroll")
            options.cornerRadius = FractionParser.parse(json, "cornerRadius")
            options.testID = TextParser.parse(json, "testID")
            return options
        }
    }
}

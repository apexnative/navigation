package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.NullFraction
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.parsers.FractionParser
import dev.apexnative.navigation.options.parsers.TextParser

/**
 * A shared element of a push or a pop: the view with `nativeID` [fromId] on the screen that goes moves to the place of
 * [toId] on the screen that comes, in `duration` ms after `startDelay` ms, along `interpolation`.
 */
class SharedElementTransitionOptions(
    @JvmField var fromId: Text = NullText(),
    @JvmField var toId: Text = NullText(),
) {
    @JvmField var duration: Fraction = NullFraction()
    @JvmField var startDelay: Fraction = NullFraction()
    @JvmField var interpolation = InterpolationOptions()

    fun hasValue(): Boolean = fromId.hasValue() || toId.hasValue() || duration.hasValue() || startDelay.hasValue() ||
        interpolation.hasValue()

    fun mergeWith(other: SharedElementTransitionOptions) {
        if (other.fromId.hasValue()) fromId = other.fromId
        if (other.toId.hasValue()) toId = other.toId
        if (other.duration.hasValue()) duration = other.duration
        if (other.startDelay.hasValue()) startDelay = other.startDelay
        interpolation.mergeWith(other.interpolation)
    }

    fun mergeWithDefault(defaults: SharedElementTransitionOptions) {
        if (!fromId.hasValue()) fromId = defaults.fromId
        if (!toId.hasValue()) toId = defaults.toId
        if (!duration.hasValue()) duration = defaults.duration
        if (!startDelay.hasValue()) startDelay = defaults.startDelay
        interpolation.mergeWithDefault(defaults.interpolation)
    }

    fun copy() = SharedElementTransitionOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): SharedElementTransitionOptions {
            val options = SharedElementTransitionOptions()
            if (map == null) return options
            options.fromId = TextParser.parse(map, "fromId")
            options.toId = TextParser.parse(map, "toId")
            options.duration = FractionParser.parseDuration(map, "duration")
            options.startDelay = FractionParser.parseDuration(map, "startDelay")
            options.interpolation = map.group("interpolation") { InterpolationOptions.parse(it) }
            return options
        }
    }
}

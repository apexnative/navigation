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
 * An `Interpolation`: the curve of an animation. The fields of every `type` are read; a field the type doesn't use is
 * not looked at. Without a type, or with an unknown one, the value moves linearly.
 */
class InterpolationOptions {
    @JvmField var type: Text = NullText()
    @JvmField var factor: Fraction = NullFraction()
    @JvmField var tension: Fraction = NullFraction()
    @JvmField var mass: Fraction = NullFraction()
    @JvmField var damping: Fraction = NullFraction()
    @JvmField var stiffness: Fraction = NullFraction()
    @JvmField var allowsOverdamping: Bool = NullBool()
    @JvmField var initialVelocity: Fraction = NullFraction()

    fun hasValue(): Boolean = type.hasValue() || factor.hasValue() || tension.hasValue() || mass.hasValue() ||
        damping.hasValue() || stiffness.hasValue() || allowsOverdamping.hasValue() || initialVelocity.hasValue()

    fun mergeWith(other: InterpolationOptions) {
        if (other.type.hasValue()) type = other.type
        if (other.factor.hasValue()) factor = other.factor
        if (other.tension.hasValue()) tension = other.tension
        if (other.mass.hasValue()) mass = other.mass
        if (other.damping.hasValue()) damping = other.damping
        if (other.stiffness.hasValue()) stiffness = other.stiffness
        if (other.allowsOverdamping.hasValue()) allowsOverdamping = other.allowsOverdamping
        if (other.initialVelocity.hasValue()) initialVelocity = other.initialVelocity
    }

    fun mergeWithDefault(defaults: InterpolationOptions) {
        if (!type.hasValue()) type = defaults.type
        if (!factor.hasValue()) factor = defaults.factor
        if (!tension.hasValue()) tension = defaults.tension
        if (!mass.hasValue()) mass = defaults.mass
        if (!damping.hasValue()) damping = defaults.damping
        if (!stiffness.hasValue()) stiffness = defaults.stiffness
        if (!allowsOverdamping.hasValue()) allowsOverdamping = defaults.allowsOverdamping
        if (!initialVelocity.hasValue()) initialVelocity = defaults.initialVelocity
    }

    fun copy() = InterpolationOptions().also { it.mergeWith(this) }

    companion object {
        private val TYPES = setOf(
            "linear", "accelerate", "decelerate", "accelerateDecelerate", "decelerateAccelerate", "fastOutSlowIn",
            "overshoot", "spring",
        )

        @JvmStatic
        fun parse(map: ReadableMap?): InterpolationOptions {
            val options = InterpolationOptions()
            if (map == null) return options
            options.type = TextParser.parse(map, "type", TYPES)
            options.factor = FractionParser.parse(map, "factor")
            options.tension = FractionParser.parse(map, "tension")
            options.mass = FractionParser.parse(map, "mass")
            options.damping = FractionParser.parse(map, "damping")
            options.stiffness = FractionParser.parse(map, "stiffness")
            options.allowsOverdamping = BoolParser.parse(map, "allowsOverdamping")
            options.initialVelocity = FractionParser.parse(map, "initialVelocity")
            return options
        }
    }
}

/**
 * `OptionsAnimationPropertyConfig`: the animation of one property of a view. `duration` and `startDelay` are in
 * milliseconds and not below 0.
 */
class PropertyAnimationOptions {
    @JvmField var from: Fraction = NullFraction()
    @JvmField var to: Fraction = NullFraction()
    @JvmField var duration: Fraction = NullFraction()
    @JvmField var startDelay: Fraction = NullFraction()
    @JvmField var interpolation = InterpolationOptions()

    fun hasValue(): Boolean = from.hasValue() || to.hasValue() || duration.hasValue() || startDelay.hasValue() ||
        interpolation.hasValue()

    fun mergeWith(other: PropertyAnimationOptions) {
        if (other.from.hasValue()) from = other.from
        if (other.to.hasValue()) to = other.to
        if (other.duration.hasValue()) duration = other.duration
        if (other.startDelay.hasValue()) startDelay = other.startDelay
        interpolation.mergeWith(other.interpolation)
    }

    fun mergeWithDefault(defaults: PropertyAnimationOptions) {
        if (!from.hasValue()) from = defaults.from
        if (!to.hasValue()) to = defaults.to
        if (!duration.hasValue()) duration = defaults.duration
        if (!startDelay.hasValue()) startDelay = defaults.startDelay
        interpolation.mergeWithDefault(defaults.interpolation)
    }

    fun copy() = PropertyAnimationOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): PropertyAnimationOptions {
            val options = PropertyAnimationOptions()
            if (map == null) return options
            options.from = FractionParser.parse(map, "from")
            options.to = FractionParser.parse(map, "to")
            options.duration = FractionParser.parseDuration(map, "duration")
            options.startDelay = FractionParser.parseDuration(map, "startDelay")
            options.interpolation = map.group("interpolation") { InterpolationOptions.parse(it) }
            return options
        }
    }
}

/**
 * `ElementAnimation` (`AppearingElementAnimation`, `DisappearingElementAnimation`): the animation of one property of an
 * element transition. `duration` and `startDelay` are in milliseconds and not below 0.
 */
class ElementAnimationOptions {
    @JvmField var from: Fraction = NullFraction()
    @JvmField var to: Fraction = NullFraction()
    @JvmField var duration: Fraction = NullFraction()
    @JvmField var startDelay: Fraction = NullFraction()
    @JvmField var interpolation = InterpolationOptions()

    fun hasValue(): Boolean = from.hasValue() || to.hasValue() || duration.hasValue() || startDelay.hasValue() ||
        interpolation.hasValue()

    fun mergeWith(other: ElementAnimationOptions) {
        if (other.from.hasValue()) from = other.from
        if (other.to.hasValue()) to = other.to
        if (other.duration.hasValue()) duration = other.duration
        if (other.startDelay.hasValue()) startDelay = other.startDelay
        interpolation.mergeWith(other.interpolation)
    }

    fun mergeWithDefault(defaults: ElementAnimationOptions) {
        if (!from.hasValue()) from = defaults.from
        if (!to.hasValue()) to = defaults.to
        if (!duration.hasValue()) duration = defaults.duration
        if (!startDelay.hasValue()) startDelay = defaults.startDelay
        interpolation.mergeWithDefault(defaults.interpolation)
    }

    fun copy() = ElementAnimationOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): ElementAnimationOptions {
            val options = ElementAnimationOptions()
            if (map == null) return options
            options.from = FractionParser.parse(map, "from")
            options.to = FractionParser.parse(map, "to")
            options.duration = FractionParser.parseDuration(map, "duration")
            options.startDelay = FractionParser.parseDuration(map, "startDelay")
            options.interpolation = map.group("interpolation") { InterpolationOptions.parse(it) }
            return options
        }
    }
}

/**
 * `ZoomTransitionOptions`: the zoom of a screen out of a view. `fromId: null` is an explicit "no view" ([Text.isNone]).
 */
class ZoomTransitionOptions {
    @JvmField var fromId: Text = NullText()
    @JvmField var enabled: Bool = NullBool()

    fun hasValue(): Boolean = fromId.hasValue() || enabled.hasValue()

    fun mergeWith(other: ZoomTransitionOptions) {
        if (other.fromId.hasValue()) fromId = other.fromId
        if (other.enabled.hasValue()) enabled = other.enabled
    }

    fun mergeWithDefault(defaults: ZoomTransitionOptions) {
        if (!fromId.hasValue()) fromId = defaults.fromId
        if (!enabled.hasValue()) enabled = defaults.enabled
    }

    fun copy() = ZoomTransitionOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): ZoomTransitionOptions {
            val options = ZoomTransitionOptions()
            if (map == null) return options
            options.fromId = TextParser.parse(map, "fromId")
            options.enabled = BoolParser.parse(map, "enabled")
            return options
        }
    }
}

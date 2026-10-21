package dev.apexnative.navigation.customrow

import android.content.Context
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.NullFraction
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.NullThemeColour
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.options.parsers.FractionParser
import dev.apexnative.navigation.options.parsers.TextParser

/**
 * `bottomTabs.customRow`: the look of the row that hosts the tabs' React components. Every value
 * is optional; the row picks its defaults. Parsed, merged and resolved with defaults as the other
 * bottom tabs options, so each bottom tabs layout has its own.
 */
class BottomTabsCustomRowOptions(
    var height: Fraction = NullFraction(),
    var backgroundColor: ThemeColour = NullThemeColour(),
    var backgroundEffect: Text = NullText(),
    var cornerRadius: Fraction = NullFraction(),
    var horizontalMargin: Fraction = NullFraction(),
    var bottomMargin: Fraction = NullFraction(),
    var testID: Text = NullText(),
) {
    enum class BackgroundEffect { GLASS, BLUR, NONE }

    /** `null` when not set or unknown. */
    val effect: BackgroundEffect?
        get() = when (backgroundEffect.get(null)?.lowercase()) {
            "glass" -> BackgroundEffect.GLASS
            "blur" -> BackgroundEffect.BLUR
            "none" -> BackgroundEffect.NONE
            else -> null
        }

    fun copy() = BottomTabsCustomRowOptions(
        height, backgroundColor, backgroundEffect, cornerRadius, horizontalMargin, bottomMargin, testID,
    )

    fun mergeWith(other: BottomTabsCustomRowOptions): BottomTabsCustomRowOptions {
        if (other.height.hasValue()) height = other.height
        backgroundColor = backgroundColor.mergeWith(other.backgroundColor)
        if (other.backgroundEffect.hasValue()) backgroundEffect = other.backgroundEffect
        if (other.cornerRadius.hasValue()) cornerRadius = other.cornerRadius
        if (other.horizontalMargin.hasValue()) horizontalMargin = other.horizontalMargin
        if (other.bottomMargin.hasValue()) bottomMargin = other.bottomMargin
        if (other.testID.hasValue()) testID = other.testID
        return this
    }

    fun mergeWithDefault(defaults: BottomTabsCustomRowOptions): BottomTabsCustomRowOptions {
        if (!height.hasValue()) height = defaults.height
        backgroundColor = backgroundColor.withDefault(defaults.backgroundColor)
        if (!backgroundEffect.hasValue()) backgroundEffect = defaults.backgroundEffect
        if (!cornerRadius.hasValue()) cornerRadius = defaults.cornerRadius
        if (!horizontalMargin.hasValue()) horizontalMargin = defaults.horizontalMargin
        if (!bottomMargin.hasValue()) bottomMargin = defaults.bottomMargin
        if (!testID.hasValue()) testID = defaults.testID
        return this
    }

    fun hasValue() = height.hasValue() || backgroundColor.hasValue() || backgroundEffect.hasValue() ||
        cornerRadius.hasValue() || horizontalMargin.hasValue() || bottomMargin.hasValue() || testID.hasValue()

    companion object {
        private val BACKGROUND_EFFECTS = setOf("glass", "blur", "none")

        @JvmStatic
        fun parse(context: Context?, json: ReadableMap?): BottomTabsCustomRowOptions {
            if (json == null) return BottomTabsCustomRowOptions()
            return BottomTabsCustomRowOptions(
                height = FractionParser.parse(json, "height"),
                backgroundColor = ThemeColour.parse(context, json, "backgroundColor"),
                backgroundEffect = TextParser.parse(json, "backgroundEffect", BACKGROUND_EFFECTS),
                cornerRadius = FractionParser.parse(json, "cornerRadius"),
                horizontalMargin = FractionParser.parse(json, "horizontalMargin"),
                bottomMargin = FractionParser.parse(json, "bottomMargin"),
                testID = TextParser.parse(json, "testID"),
            )
        }
    }
}

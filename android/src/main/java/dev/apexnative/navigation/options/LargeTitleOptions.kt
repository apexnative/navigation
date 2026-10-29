package dev.apexnative.navigation.options

import android.content.Context
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullFraction
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.NullThemeColour
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.FontParser
import dev.apexnative.navigation.options.parsers.FractionParser
import dev.apexnative.navigation.options.parsers.TextParser

/** `topBar.largeTitle`: the title big under the row of the bar, as Material 3's large top app bar. */
class LargeTitleOptions {
    @JvmField var visible: Bool = NullBool()
    @JvmField var displayMode: Text = NullText()
    @JvmField var collapseOnScroll: Bool = NullBool()
    @JvmField var fontSize: Fraction = NullFraction()
    @JvmField var color: ThemeColour = NullThemeColour()
    @ReadFromParent @JvmField var font = FontOptions()

    /**
     * When the large title shows: [ALWAYS], [NEVER] or [AUTOMATIC], as on the screen below. `displayMode` over
     * `visible`; `inline` puts the title in the row of the bar, as without a large title.
     */
    fun mode(): String = when (val mode = displayMode.get(null)) {
        ALWAYS, AUTOMATIC -> mode
        null -> if (visible.isTrue) ALWAYS else NEVER
        else -> NEVER
    }

    fun mergeWith(other: LargeTitleOptions) {
        if (other.visible.hasValue()) visible = other.visible
        if (other.displayMode.hasValue()) displayMode = other.displayMode
        if (other.collapseOnScroll.hasValue()) collapseOnScroll = other.collapseOnScroll
        if (other.fontSize.hasValue()) fontSize = other.fontSize
        color = color.mergeWith(other.color)
        font.mergeWith(other.font)
    }

    fun mergeWithDefault(defaultOptions: LargeTitleOptions) {
        if (!visible.hasValue()) visible = defaultOptions.visible
        if (!displayMode.hasValue()) displayMode = defaultOptions.displayMode
        if (!collapseOnScroll.hasValue()) collapseOnScroll = defaultOptions.collapseOnScroll
        if (!fontSize.hasValue()) fontSize = defaultOptions.fontSize
        color = color.withDefault(defaultOptions.color)
        font.mergeWithDefault(defaultOptions.font)
    }

    /** Any of its options set: they change what shows. */
    fun hasValue() = visible.hasValue() || displayMode.hasValue() || collapseOnScroll.hasValue() ||
            fontSize.hasValue() || color.hasValue() || font.hasValue()

    companion object {
        const val ALWAYS = "always"
        const val NEVER = "never"
        const val AUTOMATIC = "automatic"
        private val DISPLAY_MODES = setOf(ALWAYS, NEVER, AUTOMATIC, "inline")

        @JvmStatic
        fun parse(context: Context?, json: ReadableMap?): LargeTitleOptions {
            val options = LargeTitleOptions()
            if (json == null) return options
            options.visible = BoolParser.parse(json, "visible")
            options.displayMode = TextParser.parse(json, "displayMode", DISPLAY_MODES)
            options.collapseOnScroll = BoolParser.parse(json, "collapseOnScroll")
            options.fontSize = FractionParser.parse(json, "fontSize")
            options.color = ThemeColour.parse(context, json, "color")
            options.font = FontParser.parse(json)
            return options
        }
    }
}

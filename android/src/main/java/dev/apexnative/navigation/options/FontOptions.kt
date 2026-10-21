package dev.apexnative.navigation.options

import android.graphics.Typeface
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.parsers.TypefaceLoader

/**
 * The font of a text of the options, read from the text's own object: `fontFamily`, `fontStyle` (`normal`, `italic`),
 * `fontWeight` (`bold`, `100`…). The text's `fontSize` is a field of the text, in sp.
 */
class FontOptions(
    @JvmField var fontFamily: Text = NullText(),
    @JvmField var fontStyle: Text = NullText(),
    @JvmField var fontWeight: Text = NullText(),
) {
    /** Any of its values is set. */
    fun hasValue(): Boolean = fontFamily.hasValue() || fontStyle.hasValue() || fontWeight.hasValue()

    /** The values [overrides] sets replace these; the ones it leaves unset stay. */
    fun mergeWith(overrides: FontOptions) {
        fontFamily = firstSet(overrides.fontFamily, fontFamily)
        fontStyle = firstSet(overrides.fontStyle, fontStyle)
        fontWeight = firstSet(overrides.fontWeight, fontWeight)
    }

    /** The values not set here come from [fallback]. */
    fun mergeWithDefault(fallback: FontOptions) {
        fontFamily = firstSet(fontFamily, fallback.fontFamily)
        fontStyle = firstSet(fontStyle, fallback.fontStyle)
        fontWeight = firstSet(fontWeight, fallback.fontWeight)
    }

    private fun firstSet(preferred: Text, otherwise: Text): Text = if (preferred.hasValue()) preferred else otherwise

    fun copy(): FontOptions = FontOptions(fontFamily, fontStyle, fontWeight)

    /**
     * The typeface of these values, made as React Native's text makes it, over [defaultTypeface] (the place's own);
     * [defaultTypeface] itself when none is set: the place keeps its system font.
     */
    fun getTypeface(typefaceLoader: TypefaceLoader, defaultTypeface: Typeface?): Typeface? {
        if (!hasValue()) return defaultTypeface
        val family = fontFamily.get(null)?.takeIf { it.isNotEmpty() }
        return typefaceLoader.getTypeface(family, fontWeight.get(null), fontStyle.get(null), defaultTypeface)
    }
}

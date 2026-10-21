package dev.apexnative.navigation.options.parsers

import android.content.Context
import android.graphics.Typeface
import com.facebook.react.views.text.ReactTypefaceUtils

/**
 * Makes the typeface of a font of the options as React Native's text makes it: the app's own family from
 * `assets/fonts/` or React Native's `res/font` registry, else the system family by that name; the weight and the slant
 * as React Native reads them, also over an own family. A typeface is made once for each family, weight, slant and place
 * font. Main thread only.
 */
class TypefaceLoader(private val context: Context) {
    /**
     * The typeface of [fontFamily] with [fontWeight] (`'bold'`, `'100'`…`'900'`) and [fontStyle] (`'italic'`), over
     * [defaultTypeface] (the place's own) where no family is given.
     */
    fun getTypeface(
        fontFamily: String?,
        fontWeight: String?,
        fontStyle: String?,
        defaultTypeface: Typeface?,
    ): Typeface {
        val key = Key(fontFamily, fontWeight, fontStyle, defaultTypeface)
        return cache.getOrPut(key) {
            ReactTypefaceUtils.applyStyles(
                defaultTypeface,
                ReactTypefaceUtils.parseFontStyle(fontStyle),
                ReactTypefaceUtils.parseFontWeight(fontWeight),
                fontFamily,
                context.assets,
            )
        }
    }

    // A typeface made: its family, weight, slant and the place font it was made over.
    private data class Key(val family: String?, val weight: String?, val style: String?, val base: Typeface?)

    private companion object {
        val cache = HashMap<Key, Typeface>()
    }
}

package dev.apexnative.navigation.options.parsers

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.FontOptions

/**
 * Reads the font of a text of the options from the text's own object: `fontFamily`, `fontStyle`, `fontWeight`. Its
 * `fontSize` is a field of the text itself, in sp.
 */
object FontParser {
    /** The values of `fontStyle`. */
    @JvmField
    val FONT_STYLES = setOf("normal", "italic")

    /** The values of `fontWeight` (the names of UIKit's weights are iOS's; Android draws them normal). */
    @JvmField
    val FONT_WEIGHTS = setOf(
        "normal", "bold", "100", "200", "300", "400", "500", "600", "700", "800", "900",
        "ultralight", "thin", "light", "regular", "medium", "semibold", "heavy", "black",
    )

    @JvmStatic
    fun parse(map: ReadableMap?): FontOptions = FontOptions(
        fontFamily = TextParser.parse(map, "fontFamily"),
        fontStyle = TextParser.parse(map, "fontStyle", FONT_STYLES),
        fontWeight = TextParser.parse(map, "fontWeight", FONT_WEIGHTS),
    )
}

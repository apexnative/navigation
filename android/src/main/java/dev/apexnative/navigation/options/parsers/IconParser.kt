package dev.apexnative.navigation.options.parsers

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.OptionsReader
import dev.apexnative.navigation.options.has
import dev.apexnative.navigation.options.opt
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.utils.DevelopmentLog

/**
 * Reads an image of the options as the image loader takes it: the string itself (a drawable of the app by name, a file,
 * Metro's address in development), the `uri` of an image source, or the Android side of an `{ ios, android }` icon. The
 * other fields of a source are not read. An icon without an Android side, and `null`, are unset; an array of sources is
 * unset with an error in development.
 */
object IconParser {
    @JvmStatic
    fun parse(map: ReadableMap?, key: String): Text = when (val value = map?.opt(key)) {
        is String -> Text(value)
        is ReadableMap -> sourceOf(value)
        is ReadableArray -> {
            DevelopmentLog.error(
                "the option '${OptionsReader.pathOf(key)}' gives several image sources: the navigation takes one " +
                    "source, an object or a string",
            )
            NullText()
        }
        else -> NullText()
    }

    private fun sourceOf(icon: ReadableMap): Text {
        if (icon.has("ios") || icon.has("android")) {
            return when (val android = icon.opt("android")) {
                is String -> Text(android)
                is ReadableMap -> uriOf(android)
                else -> NullText()
            }
        }
        return uriOf(icon)
    }

    private fun uriOf(source: ReadableMap): Text {
        val uri = source.opt("uri") as? String
        return if (uri.isNullOrEmpty()) NullText() else Text(uri)
    }
}

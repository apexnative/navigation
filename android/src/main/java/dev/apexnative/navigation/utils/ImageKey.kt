package dev.apexnative.navigation.utils

import android.content.Context
import android.content.res.Configuration
import android.view.View
import java.io.File
import java.net.URI
import java.net.URISyntaxException

/**
 * What an image of the options is cached by: its [uri] and what changes the decoded result for the same uri, the
 * screen's density (a drawable of the app picks its density's file, a decoded file is scaled to it), light or dark
 * (`drawable-night`) and the layout direction (`drawable-ldrtl`, auto-mirrored drawables). For a `file:` uri also the
 * file's [modified] time and [size]: a file the app writes again at the same path is another image. The loader bakes
 * no tint and no size into an image, so neither is part of it.
 */
internal data class ImageKey(
    val uri: String,
    val densityDpi: Int,
    val night: Boolean,
    val rightToLeft: Boolean,
    /** When the file of a `file:` uri last changed, in milliseconds; 0 for any other uri or a file that isn't there. */
    val modified: Long = 0,
    /** The bytes of the file of a `file:` uri; 0 for any other uri or a file that isn't there. */
    val size: Long = 0,
) {
    companion object {
        private const val FILE_SCHEME = "file:"
        private const val FILE_PREFIX = "file://"

        /** The key of [uri] in the current configuration of [context]. Any thread. */
        fun of(context: Context, uri: String): ImageKey {
            val configuration = context.resources.configuration
            return of(uri, configuration.densityDpi, configuration.uiMode, configuration.layoutDirection)
        }

        /**
         * The key of [uri] for a configuration's density, `uiMode` (only its night bits count) and layout direction;
         * a `file:` uri reads its file's modification time and size. Any thread.
         */
        fun of(uri: String, densityDpi: Int, uiMode: Int, layoutDirection: Int): ImageKey {
            val file = filePath(uri)?.let(::File)
            return ImageKey(
                uri = uri,
                densityDpi = densityDpi,
                night = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
                rightToLeft = layoutDirection == View.LAYOUT_DIRECTION_RTL,
                // Both 0 when the file isn't there or can't be read.
                modified = file?.lastModified() ?: 0,
                size = file?.length() ?: 0,
            )
        }

        // The path of a `file:` uri, decoded as the loader's `Uri.getPath` decodes it; none for any other uri. A uri
        // that `URI` can't parse (an unescaped space) is taken as it is after its scheme.
        private fun filePath(uri: String): String? {
            if (!uri.startsWith(FILE_SCHEME)) return null
            val path = try {
                URI(uri).path
            } catch (e: URISyntaxException) {
                null
            }
            return path ?: uri.removePrefix(FILE_PREFIX).removePrefix(FILE_SCHEME).ifEmpty { null }
        }
    }
}

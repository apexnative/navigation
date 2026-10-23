package dev.apexnative.navigation.utils

import android.content.Context
import android.graphics.drawable.Drawable
import java.io.IOException

/** What an image loads into: [onComplete] with the image, or [onError] when it can't load. Main thread. */
open class ImageLoadingListenerAdapter {
    open fun onComplete(drawable: Drawable) = Unit

    open fun onError(error: Throwable) = Unit
}

/**
 * Loads an image or an icon of the options at once, through the shared cache of [OptionImages]: each image its own
 * mutable copy. For the places whose first frame needs the image (the top bar's buttons, the back button, menus, search
 * suggestions); a place that can show it when ready asks through [ImageRequests]. A failure never ends the app: the
 * listener's [ImageLoadingListenerAdapter.onError], and one warning per address.
 */
class ImageLoader {
    /** Loads [uri] and gives it to [listener] before returning. Main thread. */
    fun loadIcon(context: Context, uri: String, listener: ImageLoadingListenerAdapter) {
        val drawable = OptionImages.load(context, uri)
        if (drawable == null) {
            listener.onError(IOException("Could not load image $uri"))
            return
        }
        listener.onComplete(drawable)
    }
}

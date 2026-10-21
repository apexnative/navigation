package dev.apexnative.navigation.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.StrictMode
import androidx.core.content.ContextCompat
import com.facebook.react.views.imagehelper.ResourceDrawableIdHelper
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URL

/** What an image loads into: [onComplete] with the image, or [onError] when it can't load. Main thread. */
open class ImageLoadingListenerAdapter {
    open fun onComplete(drawable: Drawable) = Unit

    open fun onError(error: Throwable) = Unit
}

/**
 * Loads the images and icons of the options when they apply: synchronously on the main thread, without a cache, each
 * image its own mutable copy. A `file:` address is a file; anything else first a drawable of the app by name (as React
 * Native's images by resource name: release builds' assets, `{ android }` icons). Not found while the app is in
 * development: the address is read as a stream, Metro's asset over the network (network on the main thread allowed for
 * that read only) or through the content resolver. A remote address that is not Metro's asset is never loaded. A
 * failure never ends the app: the listener's [ImageLoadingListenerAdapter.onError], the log, and React Native's red
 * screen in development.
 */
class ImageLoader {
    /** Loads [uri] and gives it to [listener] before returning. Main thread. */
    fun loadIcon(context: Context, uri: String, listener: ImageLoadingListenerAdapter) {
        val drawable = try {
            load(context, uri)
        } catch (e: Exception) {
            val reason = if (e is LoadFailure) "" else ": ${e.message ?: e.javaClass.simpleName}"
            DevelopmentLog.error("Could not load image $uri$reason${(e as? LoadFailure)?.why.orEmpty()}", e)
            listener.onError(e)
            return
        }
        listener.onComplete(drawable)
    }

    private fun load(context: Context, uri: String): Drawable {
        if (uri.startsWith(FILE_SCHEME)) return decode(context) { FileInputStream(Uri.parse(uri).path) }
        resourceDrawable(context, uri)?.let { return it }
        if (!DevelopmentLog.isDevelopment) throw LoadFailure("")
        val address = Uri.parse(uri)
        return when (address.scheme) {
            "http", "https" -> {
                if (!isMetroAsset(address)) throw LoadFailure(": remote images are not supported in options")
                withNetworkOnMainThread { decode(context) { URL(uri).openStream() } }
            }
            else -> decode(context) {
                context.contentResolver.openInputStream(address) ?: throw FileNotFoundException(uri)
            }
        }
    }

    // A drawable of the app by name, its own copy; none when the app has none by that name.
    private fun resourceDrawable(context: Context, name: String): Drawable? {
        val id = ResourceDrawableIdHelper.getResourceDrawableId(context, name)
        if (id <= 0) return null
        return try {
            ContextCompat.getDrawable(context, id)?.mutate()
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    // The image of the stream [open] gives, closed after the read, also on an error; one it can't decode fails, and so
    // does one too large for the memory: that is a load error too, never the end of the app.
    private fun decode(context: Context, open: () -> InputStream): Drawable {
        val decoded = try {
            open().use { BitmapFactory.decodeStream(it) }
        } catch (e: OutOfMemoryError) {
            throw LoadFailure(": it is too large to decode")
        }
        val bitmap = decoded ?: throw LoadFailure(": it can't be decoded")
        return BitmapDrawable(context.resources, bitmap)
    }

    // A failure the loader names itself: `Could not load image <source>`, then [why].
    private class LoadFailure(val why: String) : IOException("Could not load image$why")

    // Metro's asset: an address of the server the JavaScript bundle came from, under `/assets/`.
    private fun isMetroAsset(address: Uri): Boolean {
        val bundle = DevelopmentLog.bundleUrl?.let(Uri::parse) ?: return false
        return address.host == bundle.host && address.port == bundle.port &&
            address.path.orEmpty().startsWith(ASSETS_PATH)
    }

    // Reading from the network on the main thread for [read] only; the thread's policy comes back, also on an error.
    private fun <T> withNetworkOnMainThread(read: () -> T): T {
        val policy = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder(policy).permitNetwork().build())
        try {
            return read()
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
    }

    private companion object {
        const val FILE_SCHEME = "file:"
        const val ASSETS_PATH = "/assets/"
    }
}

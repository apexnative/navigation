package dev.apexnative.navigation.utils

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.StrictMode
import android.util.LruCache
import androidx.core.content.ContextCompat
import com.facebook.react.views.imagehelper.ResourceDrawableIdHelper
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The images and icons of the options, every one of them (bar buttons and their menus, the back button, search
 * suggestions, tab icons, the FAB): one cache for the app, by [ImageKey], sized by the bytes of its images and let go
 * whenever the system trims the app's memory; each caller gets its own mutable copy. The images don't depend on the
 * JavaScript, so a reload keeps them.
 *
 * A `file:` address is a file; anything else first a drawable of the app by name (as React Native's images by resource
 * name: release builds' assets, `{ android }` icons). Those two load on the caller's thread ([load]) or on a
 * background thread of their own ([loadLater]). Not found while the app is in development: the address is read as a
 * stream on the main thread, Metro's asset over the network (network on the main thread allowed for that read only)
 * or through the content resolver. A remote address that is not Metro's asset is never loaded. A failure (a missing
 * file or drawable, data that doesn't decode, an image too large for the memory) never ends the app: no image, and one
 * warning per address in the log (and LogBox in development).
 *
 * A theme attribute inside a drawable of the app is resolved against the theme of the context that loaded it first.
 * Main thread, unless a member says otherwise.
 */
internal object OptionImages {
    private val cache = object : LruCache<ImageKey, Entry>(maxBytes()) {
        override fun sizeOf(key: ImageKey, value: Entry) = value.bytes
    }

    // The addresses already named in a warning: one each.
    private val warned: MutableSet<String> = Collections.synchronizedSet(HashSet())

    // The loads off the main thread under way, with who waits for each.
    private val loading = HashMap<ImageKey, MutableList<Waiter>>()

    private val background: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { task -> Thread(task, THREAD_NAME).apply { isDaemon = true } }
    }

    @Volatile
    private var listening = false

    /** The cache and the loads for [context]: the source of an [ImageRequests]. */
    fun source(context: Context): ImageSource = object : ImageSource {
        override fun cached(uri: String) = cached(context, uri)

        override fun now(uri: String) = load(context, uri)

        override fun loadsLater(uri: String) = loadsLater(context, uri)

        override fun later(uri: String, done: (Drawable?) -> Unit) = loadLater(context, uri, done)
    }

    /** A copy of the image of [uri] when the cache has it. Any thread. */
    fun cached(context: Context, uri: String): Drawable? = cache.get(ImageKey.of(context, uri))?.copy(context)

    /** A copy of the image of [uri], from the cache or loaded on the caller's thread; none on a failure. */
    fun load(context: Context, uri: String): Drawable? {
        listen(context)
        val key = ImageKey.of(context, uri)
        cache.get(key)?.let { return it.copy(context) }
        val loaded = decodeOrWarn(context, uri) ?: return null
        return keep(key, loaded)?.copy(context) ?: loaded
    }

    /** Whether [uri] loads off the main thread: a file, or a drawable the app has by that name. Any thread. */
    fun loadsLater(context: Context, uri: String): Boolean =
        uri.startsWith(FILE_SCHEME) || ResourceDrawableIdHelper.getResourceDrawableId(context, uri) > 0

    /**
     * Loads [uri] on the background thread and gives [done] a copy of it, or none on a failure, on the main thread;
     * one load for all who ask for the same image meanwhile. Only for what [loadsLater].
     */
    fun loadLater(context: Context, uri: String, done: (Drawable?) -> Unit) {
        listen(context)
        val key = ImageKey.of(context, uri)
        val waiters = loading[key]
        if (waiters != null) {
            waiters.add(Waiter(context, done))
            return
        }
        loading[key] = mutableListOf(Waiter(context, done))
        background.execute {
            val loaded = decodeOrWarn(context, uri)
            val entry = loaded?.let { keep(key, it) }
            UiThread.post { deliver(key, loaded, entry) }
        }
    }

    /** Lets every image of the cache go. Any thread. */
    fun clear() {
        cache.evictAll()
    }

    // Each waiter gets a copy of its own; an image the cache can't keep (no constant state) goes to the first only,
    // the others load it again.
    private fun deliver(key: ImageKey, loaded: Drawable?, entry: Entry?) {
        val waiters = loading.remove(key) ?: return
        waiters.forEachIndexed { index, waiter ->
            val image = when {
                loaded == null -> null
                entry != null -> entry.copy(waiter.context)
                index == 0 -> loaded
                else -> load(waiter.context, key.uri)
            }
            waiter.done(image)
        }
    }

    // Into the cache, when the image can be copied; the entry it keeps. Any thread.
    private fun keep(key: ImageKey, image: Drawable): Entry? {
        val state = image.constantState ?: return null
        return Entry(state, bytesOf(image)).also { cache.put(key, it) }
    }

    private fun bytesOf(image: Drawable): Int {
        if (image is BitmapDrawable) image.bitmap?.let { return it.allocationByteCount }
        // A drawable that draws itself (a vector) keeps a picture of its own size when it is drawn.
        return image.intrinsicWidth.coerceAtLeast(1) * image.intrinsicHeight.coerceAtLeast(1) * BYTES_PER_PIXEL
    }

    // The image of [uri], or none and one warning per address. Any thread.
    private fun decodeOrWarn(context: Context, uri: String): Drawable? = try {
        decode(context, uri)
    } catch (e: Exception) {
        warnOnce(uri, (e as? LoadFailure)?.why ?: ": ${e.message ?: e.javaClass.simpleName}")
        null
    } catch (e: OutOfMemoryError) {
        warnOnce(uri, ": it is too large to decode")
        null
    }

    private fun warnOnce(uri: String, why: String) {
        if (warned.add(uri)) DevelopmentLog.warn("Could not load image $uri$why")
    }

    private fun decode(context: Context, uri: String): Drawable {
        if (uri.startsWith(FILE_SCHEME)) return decodeStream(context) { FileInputStream(Uri.parse(uri).path) }
        resourceDrawable(context, uri)?.let { return it }
        if (!DevelopmentLog.isDevelopment) throw LoadFailure("")
        val address = Uri.parse(uri)
        return when (address.scheme) {
            "http", "https" -> {
                if (!isMetroAsset(address)) throw LoadFailure(": remote images are not supported in options")
                withNetworkOnMainThread { decodeStream(context) { URL(uri).openStream() } }
            }
            else -> decodeStream(context) {
                context.contentResolver.openInputStream(address) ?: throw FileNotFoundException(uri)
            }
        }
    }

    // A drawable of the app by name; none when the app has none by that name.
    private fun resourceDrawable(context: Context, name: String): Drawable? {
        val id = ResourceDrawableIdHelper.getResourceDrawableId(context, name)
        if (id <= 0) return null
        return try {
            ContextCompat.getDrawable(context, id)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    // The image of the stream [open] gives, closed after the read, also on an error; one it can't decode fails, and so
    // does one too large for the memory.
    private fun decodeStream(context: Context, open: () -> InputStream): Drawable {
        val decoded = try {
            open().use { BitmapFactory.decodeStream(it) }
        } catch (e: OutOfMemoryError) {
            throw LoadFailure(": it is too large to decode")
        }
        val bitmap = decoded ?: throw LoadFailure(": it can't be decoded")
        return BitmapDrawable(context.resources, bitmap)
    }

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

    // The cache lets its images go whenever the system trims the app's memory: they load again when asked.
    private fun listen(context: Context) {
        if (listening) return
        synchronized(this) {
            if (listening) return
            listening = true
        }
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) = clear()

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Android 14 deprecates it; onTrimMemory comes with it")
            override fun onLowMemory() = clear()
        })
    }

    // A sixteenth of the app's heap, at most 16 MB: the options' icons are small.
    private fun maxBytes(): Int = (Runtime.getRuntime().maxMemory() / HEAP_PART).coerceAtMost(MAX_BYTES).toInt()

    // An image the cache keeps: what makes its copies, and its size.
    private class Entry(val state: Drawable.ConstantState, val bytes: Int) {
        fun copy(context: Context): Drawable = state.newDrawable(context.resources).mutate()
    }

    private class Waiter(val context: Context, val done: (Drawable?) -> Unit)

    // A failure the loader names itself: `Could not load image <source>`, then [why].
    private class LoadFailure(val why: String) : IOException("Could not load image$why")

    private const val FILE_SCHEME = "file:"
    private const val ASSETS_PATH = "/assets/"
    private const val THREAD_NAME = "ApexNavigation images"
    private const val BYTES_PER_PIXEL = 4
    private const val HEAP_PART = 16L
    private const val MAX_BYTES = 16L * 1024 * 1024
}

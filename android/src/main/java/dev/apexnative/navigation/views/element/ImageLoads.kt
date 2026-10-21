package dev.apexnative.navigation.views.element

import android.view.View
import android.widget.ImageView
import com.facebook.react.views.image.ReactImageView
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import java.util.Collections
import java.util.WeakHashMap

/**
 * Whether the image of a shared element is there to fly: loaded, with a size, its content fitted. One that doesn't load
 * in [LIMIT_MILLIS] drops out of its transition with a line in the log, and the transition goes on. Main thread only.
 */
internal object ImageLoads {
    /** How long a shared element's image is waited for, at most, ms. */
    const val LIMIT_MILLIS = 1000L

    // The images whose wait ended without them: their pairs drop out quietly, the log has them already.
    private val failed: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())

    /** [view] is an image (React Native's or another `ImageView`). */
    fun isImage(view: View): Boolean = view is ImageView

    /** [view] is not an image, or its image is loaded and it has a size. */
    fun isLoaded(view: View): Boolean {
        if (view !is ImageView) return true
        if (view.width <= 0 || view.height <= 0) return false
        if (view is ReactImageView) return view.hasHierarchy() && view.hierarchy.hasImage()
        val drawable = view.drawable ?: return false
        return drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0
    }

    /** The image of [view] (its `nativeID` [id]) didn't load in time: logged once. */
    fun failed(view: View, id: String, log: (String) -> Unit) {
        if (failed.add(view)) log(textOf(id))
    }

    /** The line of the log for an image that didn't load. */
    fun textOf(id: String): String = "The image '$id' did not load in $LIMIT_MILLIS ms: no transition for it"
}

/**
 * The wait for the images of a transition's shared elements: each until it is loaded, [ImageLoads.LIMIT_MILLIS] at most
 * from the start, checked on every frame; one that isn't loaded by its limit [Image.failed]. [done] once, when none is
 * left to wait for. The clock and the frames are given, so it runs in unit tests. Main thread only.
 */
internal class ImageWait(
    private val images: List<Image>,
    private val scheduler: AppearanceDispatcher.Scheduler,
    /** Calls its argument on the next frame; what it returns cancels that. */
    private val nextFrame: (() -> Unit) -> () -> Unit,
    private val done: () -> Unit,
) {
    /** One image: [isLoaded] asks it, [failed] tells it didn't load in time. */
    class Image(val isLoaded: () -> Boolean, val failed: () -> Unit)

    private val pending = LinkedHashSet<Image>()
    private val limits = ArrayList<() -> Unit>()
    private var cancelFrame: () -> Unit = {}
    private var ended = false

    /** Starts the wait; the function returned stops it without [done]. */
    fun start(): () -> Unit {
        images.filterTo(pending) { !it.isLoaded() }
        if (pending.isEmpty()) {
            end()
            return {}
        }
        for (image in pending.toList()) {
            limits += scheduler.schedule(ImageLoads.LIMIT_MILLIS) {
                if (!ended && pending.remove(image)) {
                    image.failed()
                    if (pending.isEmpty()) end()
                }
            }
        }
        cancelFrame = nextFrame(::frame)
        return {
            ended = true
            stop()
        }
    }

    private fun frame() {
        if (ended) return
        pending.removeAll { it.isLoaded() }
        if (pending.isEmpty()) return end()
        cancelFrame = nextFrame(::frame)
    }

    private fun end() {
        if (ended) return
        ended = true
        stop()
        done()
    }

    private fun stop() {
        limits.forEach { it() }
        limits.clear()
        cancelFrame()
    }
}

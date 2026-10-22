package dev.apexnative.navigation.views.element

import android.util.Log
import android.view.Choreographer
import android.view.View
import dev.apexnative.navigation.options.LayoutAnimation
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.utils.DevelopmentLog
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The wait of a forward transition with elements for its targets: the module's frames (`awaitTransitionTargets`, 0.5 s
 * at most, then one frame more), then the images of the shared elements found ([ImageWait], 1 s at most each). It ends
 * early when the screen that goes is destroyed. A failure in it is logged and the transition starts. Main thread only.
 */
internal object TargetsWait {
    // The waits run on the main thread's frames; each is cancelled on its own.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Starts the wait for [animation]'s targets in [to] (and [from]); [done] once; the function returned stops it. */
    fun start(
        animation: LayoutAnimation,
        from: ViewController<*>?,
        to: ViewController<*>,
        waiting: () -> Boolean,
        done: () -> Unit,
    ): () -> Unit {
        var stopped = false
        var stopImages: () -> Unit = NOTHING
        val still = { !stopped && waiting() && from?.isDestroyed != true }
        // Its end, once: a failure of what it starts is logged, never left to the coroutine (it would end the app).
        var ended = false
        val end = {
            if (!stopped && !ended) {
                ended = true
                guarded { done() }
            }
        }
        val job = scope.launch {
            try {
                awaitTransitionTargets(animation, from ?: to, to, still)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failed(e)
            }
            if (!still()) return@launch end()
            guarded { stopImages = awaitImages(animation, from, to, end) }
            // A failure before the image wait could start: the transition starts without it.
            if (stopImages === NOTHING) end()
        }
        return {
            stopped = true
            job.cancel()
            stopImages()
        }
    }

    // The images among the shared elements found now: both views of each pair.
    private fun awaitImages(
        animation: LayoutAnimation,
        from: ViewController<*>?,
        to: ViewController<*>,
        done: () -> Unit,
    ): () -> Unit {
        val fromView = from?.let { AppearanceDispatcher.visibleNode(it).peekView() }
        val toView = AppearanceDispatcher.visibleNode(to).peekView()
        val pairs = animation.sharedElements.get()
        val views = TransitionViews(pairs, emptyList(), fromView, toView)
        val images = ArrayList<ImageWait.Image?>()
        for (pair in pairs) {
            val fromId = pair.fromId.get(null) ?: continue
            val toId = pair.toId.get(null) ?: continue
            val source = views.leaving(fromId) ?: continue
            val target = views.arriving(toId) ?: continue
            images += imageOf(source, fromId)
            images += imageOf(target, toId)
        }
        return ImageWait(images.filterNotNull(), AppearanceDispatcher.scheduler, ::onNextFrame, done).start()
    }

    private val NOTHING: () -> Unit = {}

    private inline fun guarded(work: () -> Unit) {
        try {
            work()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            failed(e)
        }
    }

    private fun failed(e: Throwable) =
        Log.w(NavigationLog.TAG, "[ApexNavigation] The wait for the transition's elements failed", e)

    private fun imageOf(view: View, id: String): ImageWait.Image? {
        if (!ImageLoads.isImage(view)) return null
        return ImageWait.Image({ ImageLoads.isLoaded(view) }, { ImageLoads.failed(view, id, DevelopmentLog::warn) })
    }

    private fun onNextFrame(frame: () -> Unit): () -> Unit {
        val callback = Choreographer.FrameCallback { frame() }
        Choreographer.getInstance().postFrameCallback(callback)
        return { Choreographer.getInstance().removeFrameCallback(callback) }
    }
}

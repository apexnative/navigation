package dev.apexnative.navigation.views.element.animators

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.views.element.elementStep

/**
 * The view's own drawing with its children, as drawing commands: sharp at any scale. Its opacity and place belong to
 * the parent. Recording can fail (a hardware bitmap in an image before Android 9): the pair then doesn't fly.
 */
internal fun recordPicture(view: View): Picture? = try {
    Picture().also {
        val canvas = it.beginRecording(view.width, view.height)
        try {
            view.draw(canvas)
        } finally {
            it.endRecording()
        }
    }
} catch (e: RuntimeException) {
    Log.w(NavigationLog.TAG, "[ApexNavigation] Element transition: no picture of the element", e)
    null
}

/**
 * What a shared pair's flight draws in its transition's layer, in the layer's coordinates: the copy where the flight
 * has brought it ([update]). The layer draws it while it is [shown]: from the start of the flight, and after a landing
 * until the layer goes.
 */
internal abstract class FlightPicture {
    /** Whether the layer draws it now. */
    var shown = false

    /** Whether what it draws changes by itself (an animated image): the layer draws it on every frame while shown. */
    open val live: Boolean get() = false

    /** What runs after its drawing failed: it is drawn no more, and the views of its pair show again. */
    var onFailed: (Exception) -> Unit = {}

    /** The copy at [time] (the part of the time), [progress] the curve's value. */
    abstract fun update(time: Float, progress: Float)

    /** The copy, on the layer's canvas; the layer saves and restores the canvas around it. */
    abstract fun draw(canvas: Canvas)
}

/**
 * The cut of a copy whose views' parents hide part of them ([FlightMask]): it keeps, of the mask the copy draws on its
 * layer, only the part that shows. A layer of that part's rounded rect laid over the mask (`DST_IN`), not a clip path:
 * its edges stay smooth.
 */
internal class MaskCut(private val mask: FlightMask) {
    private val values = FloatArray(FlightMask.SIZE)
    private val rect = RectF()
    private val shape = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cut = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    /** The cut at [time] (the part of the time), [progress] the curve's value. */
    fun update(time: Float, progress: Float) = mask.at(progress, time, values)

    /** Cuts the mask drawn so far on the copy's layer of [bounds], in the copy's coordinates (0, 0 its center). */
    fun draw(canvas: Canvas, bounds: RectF) {
        rect.set(values[0], values[1], values[2], values[3])
        canvas.saveLayer(bounds, cut)
        if (!rect.isEmpty) canvas.drawRoundRect(rect, values[RADIUS], values[RADIUS], shape)
        canvas.restore()
    }

    private companion object {
        const val RADIUS = 4
    }
}

// Runs in linear time and keeps the curve it is given for the move: an element moves along the transition's curve,
// while what has to follow the clock (the pictures of a text changing over) can. Its start and its frames each run in
// [elementStep]: one that fails stops the element — it is cancelled, so its ends put back what it changed and the
// transition plays on without it.
internal class MoveAnimator : ValueAnimator() {
    var curve: TimeInterpolator = LinearInterpolator()
        private set

    /** The element's name in the log ([pairName]). */
    var element = "''"

    private val starts = ArrayList<() -> Unit>()
    private val frames = ArrayList<(Float) -> Unit>()
    private val ends = ArrayList<() -> Unit>()
    private var failed = false
    private var cancelled = false

    /**
     * Whether the flight ran to its end: it wasn't cancelled and nothing in it failed. Read at its end; a flight that
     * landed may keep showing its last frame until the transition gives its views back.
     */
    val landed: Boolean get() = !cancelled

    init {
        setFloatValues(0f, 1f)
        super.setInterpolator(LinearInterpolator())
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) = guarded { for (start in starts) start() }

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                for (end in ends) elementStep(element) { end() }
            }
        })
        addUpdateListener { animation ->
            if (!failed) guarded { for (frame in frames) frame(animation.animatedFraction) }
        }
    }

    /** At the start of the flight, after its delay. */
    fun onStart(action: () -> Unit) {
        starts += action
    }

    /** Each frame, with the fraction of the time, linear. */
    fun onFrame(action: (fraction: Float) -> Unit) {
        frames += action
    }

    /** At its end, also when it is cancelled or its start or a frame failed. */
    fun onEnd(action: () -> Unit) {
        ends += action
    }

    private fun guarded(work: () -> Unit) {
        if (failed || elementStep(element, work = work)) return
        failed = true
        cancel()
    }

    override fun setInterpolator(value: TimeInterpolator?) {
        curve = value ?: LinearInterpolator()
    }

    override fun getInterpolator(): TimeInterpolator = curve
}

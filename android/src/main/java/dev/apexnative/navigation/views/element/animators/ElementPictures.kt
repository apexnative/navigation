package dev.apexnative.navigation.views.element.animators

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Picture
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.views.element.elementStep

/**
 * The view's own drawing with its children, as drawing commands: sharp at any scale. Its opacity and place belong to
 * the parent. Recording can fail (a hardware bitmap in an image on an older Android): the element then moves without
 * pictures.
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
    Log.w(NavigationLog.TAG, "Element transition: no picture of the element, it moves without one", e)
    null
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

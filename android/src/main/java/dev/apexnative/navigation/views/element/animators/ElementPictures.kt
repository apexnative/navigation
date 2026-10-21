package dev.apexnative.navigation.views.element.animators

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Picture
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import dev.apexnative.navigation.utils.NavigationLog

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
// while what has to follow the clock (the pictures of a text changing over) can.
internal class MoveAnimator : ValueAnimator() {
    var curve: TimeInterpolator = LinearInterpolator()
        private set

    init {
        setFloatValues(0f, 1f)
        super.setInterpolator(LinearInterpolator())
    }

    override fun setInterpolator(value: TimeInterpolator?) {
        curve = value ?: LinearInterpolator()
    }

    override fun getInterpolator(): TimeInterpolator = curve
}

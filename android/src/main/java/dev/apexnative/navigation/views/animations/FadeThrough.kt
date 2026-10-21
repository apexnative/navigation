package dev.apexnative.navigation.views.animations

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.util.Property
import android.view.View
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator

/**
 * Material's fade through, the default of setStackRoot and of a new root (setRoot): the screens become others, not a step
 * forward. What goes fades out in the first 35%, what comes fades in after it and grows from 92%.
 */
object FadeThrough {
    const val DURATION = 300L
    const val OUT = 0.35f
    private const val START_SCALE = 0.92f

    @JvmField
    val CURVE: Interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    @JvmStatic
    fun enter(view: View): Animator {
        view.scaleX = START_SCALE
        view.scaleY = START_SCALE
        val scale = ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, START_SCALE, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, START_SCALE, 1f)).apply {
            duration = DURATION
            interpolator = CURVE
        }
        return AnimatorSet().apply { playTogether(fadeIn(view), scale) }
    }

    /** Transparent until what goes has faded out, from the start: a start delay doesn't apply the first value. */
    @JvmStatic
    @JvmOverloads
    fun fadeIn(view: View, alpha: Property<View, Float> = View.ALPHA): Animator {
        view.alpha = 0f
        return ObjectAnimator.ofPropertyValuesHolder(view, PropertyValuesHolder.ofKeyframe(alpha,
                Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(OUT, 0f), Keyframe.ofFloat(1f, 1f))).apply {
            duration = DURATION
            interpolator = LinearInterpolator()
        }
    }

    /** From where it is: one still coming in, or hidden while it waited to be drawn, showed whole first. */
    @JvmStatic
    fun exit(view: View): Animator = ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f).apply {
        duration = (DURATION * OUT).toLong()
        interpolator = LinearInterpolator()
    }
}

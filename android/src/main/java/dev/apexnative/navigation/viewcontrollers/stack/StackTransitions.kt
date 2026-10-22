package dev.apexnative.navigation.viewcontrollers.stack

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.Fade
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.Motion
import dev.apexnative.navigation.views.animations.AnimationRules
import dev.apexnative.navigation.views.animations.FadeThrough
import dev.apexnative.navigation.views.animations.SystemTransition

/**
 * The animators of a stack's default transitions, one per screen, built as the transition starts: push and pop are the
 * system's transition between activities (`SystemTransition`: 96 dp along the axis in 450 ms on
 * `fast_out_extra_slow_in`, mirrored right to left), `setStackRoot` is Material's fade through (`FadeThrough`). What
 * leaves starts from where it is; what comes in on a push is hidden explicitly until it fades in, as a delay doesn't
 * apply an animator's first value. The system's windows also extend their edge by the distance they move
 * ([extendsStart], `StackLayout.extendEdge`). Main thread only.
 */
internal object StackTransitions {
    /**
     * The animator of [view] for [motion]; [fromCurrent] for a screen taken over from a transition still running.
     * [onSlide] runs at each frame of its slide.
     */
    fun animator(view: View, motion: Motion, fromCurrent: Boolean, onSlide: () -> Unit = {}): Animator {
        val distance = SystemTransition.DISTANCE_DP * view.resources.displayMetrics.density
        // Forward is to the end of the line: right to left, the screens move the other way. The stack's view inherits
        // the app's direction, which `layout.direction` sets on the activity's content (`AppDirection`).
        val forward = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) -distance else distance
        return when (motion) {
            Motion.PushIn -> {
                view.translationX = forward
                view.alpha = 0f
                together(
                    slide(view, 0f, onSlide),
                    fade(view, 1f, SystemTransition.PUSH_FADE_DELAY),
                )
            }
            Motion.PushCover -> slide(view, -forward, onSlide)
            Motion.PopOut -> together(
                slide(view, forward, onSlide),
                fade(view, 0f, SystemTransition.POP_FADE_DELAY),
            )
            Motion.PopReveal -> {
                if (!fromCurrent) view.translationX = -forward
                if (view.alpha < 1f) together(slide(view, 0f, onSlide), fade(view, 1f, 0L)) else slide(view, 0f, onSlide)
            }
            Motion.FadeIn -> FadeThrough.enter(view)
            Motion.FadeOut -> FadeThrough.exit(view)
        }
    }

    /**
     * Whether a screen moving by [motion] extends its start edge (the side it moves away from on a push in and a pop
     * out), its end edge (a push cover, a pop reveal), or none (a fade through: null), as the system's windows do.
     */
    fun extendsStart(motion: Motion): Boolean? = when (motion) {
        Motion.PushIn, Motion.PopOut -> true
        Motion.PushCover, Motion.PopReveal -> false
        Motion.FadeIn, Motion.FadeOut -> null
    }

    /** Whether [motion] animates the screen's alpha: it draws through a hardware layer meanwhile. */
    fun fades(motion: Motion): Boolean = motion != Motion.PushCover && motion != Motion.PopReveal

    /**
     * The fade of a screen around elements: in from 0 to 1, out from where it is to 0, linearly in
     * [AnimationRules.SCREEN_FADE]; none for [Fade.None].
     */
    fun fade(view: View, fade: Fade): Animator? {
        val (from, to) = when (fade) {
            Fade.None -> return null
            Fade.In -> 0f to 1f
            Fade.Out -> view.alpha to 0f
        }
        view.alpha = from
        return ObjectAnimator.ofFloat(view, View.ALPHA, from, to).apply {
            duration = AnimationRules.SCREEN_FADE
            interpolator = LinearInterpolator()
        }
    }

    private fun slide(view: View, to: Float, onFrame: () -> Unit): Animator =
        ObjectAnimator.ofFloat(view, View.TRANSLATION_X, view.translationX, to).apply {
            duration = SystemTransition.DURATION
            interpolator = SystemTransition.CURVE
            addUpdateListener { onFrame() }
        }

    private fun fade(view: View, to: Float, delay: Long): Animator =
        ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, to).apply {
            startDelay = delay
            duration = SystemTransition.FADE_DURATION
            interpolator = LinearInterpolator()
        }

    private fun together(vararg animators: Animator): Animator = AnimatorSet().apply { playTogether(*animators) }
}

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
 * apply an animator's first value. Main thread only.
 */
internal object StackTransitions {
    /** The animator of [view] for [motion]; [fromCurrent] for a screen taken over from a transition still running. */
    fun animator(view: View, motion: Motion, fromCurrent: Boolean): Animator {
        val distance = SystemTransition.DISTANCE_DP * view.resources.displayMetrics.density
        // Forward is to the end of the line: right to left, the screens move the other way. The stack's view inherits
        // the app's direction, which `layout.direction` sets on the activity's content (`AppDirection`).
        val forward = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) -distance else distance
        return when (motion) {
            Motion.PushIn -> {
                view.translationX = forward
                view.alpha = 0f
                together(
                    slide(view, 0f),
                    fade(view, 1f, SystemTransition.PUSH_FADE_DELAY),
                )
            }
            Motion.PushCover -> slide(view, -forward)
            Motion.PopOut -> together(
                slide(view, forward),
                fade(view, 0f, SystemTransition.POP_FADE_DELAY),
            )
            Motion.PopReveal -> {
                if (!fromCurrent) view.translationX = -forward
                if (view.alpha < 1f) together(slide(view, 0f), fade(view, 1f, 0L)) else slide(view, 0f)
            }
            Motion.FadeIn -> FadeThrough.enter(view)
            Motion.FadeOut -> FadeThrough.exit(view)
        }
    }

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

    private fun slide(view: View, to: Float): Animator =
        ObjectAnimator.ofFloat(view, View.TRANSLATION_X, view.translationX, to).apply {
            duration = SystemTransition.DURATION
            interpolator = SystemTransition.CURVE
        }

    private fun fade(view: View, to: Float, delay: Long): Animator =
        ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, to).apply {
            startDelay = delay
            duration = SystemTransition.FADE_DURATION
            interpolator = LinearInterpolator()
        }

    private fun together(vararg animators: Animator): Animator = AnimatorSet().apply { playTogether(*animators) }
}

package dev.apexnative.navigation.views.animations

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.util.Property
import android.view.View
import dev.apexnative.navigation.options.ElementAnimationOptions
import dev.apexnative.navigation.options.ElementTransitionOptions
import dev.apexnative.navigation.options.InterpolationOptions
import dev.apexnative.navigation.options.PropertyAnimationOptions
import dev.apexnative.navigation.options.ViewAnimationOptions

/**
 * The animators of one side of an animation on one view: one `ObjectAnimator` per property that plays, each with its
 * own duration, delay and curve ([Curves]), together in one `AnimatorSet` whose own duration and curve are never set. A
 * given end is in dp for the places and shifts (pixels here), the missing end is the view's current value in pixels;
 * `x` and `y` are the place in the view's parent. With `translationX` and `x` on one side the translation plays and `x`
 * doesn't (they write the same field), the same for `y`. Every property's start value is written as the set starts,
 * also of one that waits for its delay. Scale and rotations turn about the view's pivot (its center unless set), the
 * tilts with the view's camera. Main thread only.
 */
internal object SideAnimator {
    /** What a side's missing ends come from besides the view, and where the parent's origin is now. */
    class Base(
        /** The missing `alpha` end: the view's alpha before the core hid it; none: the view's own now. */
        val alpha: Float? = null,
        /** Pixels added to a given `x` end: where the parent's origin is in the view's parent now. */
        val xOffset: Float = 0f,
        /** Pixels added to a given `y` end. */
        val yOffset: Float = 0f,
    )

    /**
     * [view] at rest: every property a side or a default transition writes — no shift, opaque, full size, not
     * turned. The one place for the stack, the modals and the bars.
     */
    fun rest(view: View) {
        view.translationX = 0f
        view.translationY = 0f
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.rotation = 0f
        view.rotationX = 0f
        view.rotationY = 0f
    }

    /** The animator of [side] on [view]; none when the side doesn't play (AnimationRules.sidePlays). */
    fun build(view: View, side: ViewAnimationOptions, base: Base = Base()): Animator? {
        if (!AnimationRules.sidePlays(side)) return null
        return build(view, specsOf(side), base)
    }

    /** The animator of the properties of an element transition on its view; none when no property plays. */
    fun build(view: View, element: ElementTransitionOptions, base: Base): Animator? =
        build(view, specsOf(element), base)

    private fun build(view: View, specs: List<Spec>, base: Base): Animator? {
        val playing = specs.filter { it.from != null || it.to != null }
        val skipX = playing.any { it.target == Target.TranslationX }
        val skipY = playing.any { it.target == Target.TranslationY }
        val used = playing.filterNot {
            (it.target == Target.X && skipX) || (it.target == Target.Y && skipY)
        }
        if (used.isEmpty()) return null
        val density = view.resources?.displayMetrics?.density ?: 1f
        val starts = ArrayList<Pair<Property<View, Float>, Float>>(used.size)
        val animators = used.map { spec ->
            val property = spec.target.property
            val current = when (spec.target) {
                Target.Alpha -> base.alpha ?: view.alpha
                else -> property.get(view)
            }
            val from = spec.from?.let { spec.target.toPixels(it, density, base) } ?: current
            val to = spec.to?.let { spec.target.toPixels(it, density, base) } ?: current
            starts += property to from
            ObjectAnimator.ofFloat(view, property, from, to).apply {
                duration = spec.duration
                startDelay = spec.delay
                interpolator = Curves.of(spec.interpolation, spec.duration)
            }
        }
        return AnimatorSet().apply {
            playTogether(animators)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    for ((property, value) in starts) property.set(view, value)
                }
            })
        }
    }

    // One property to animate: which, its ends as the options give them (unconverted), its time and its curve.
    private class Spec(
        val target: Target,
        val from: Double?,
        val to: Double?,
        val duration: Long,
        val delay: Long,
        val interpolation: InterpolationOptions,
    )

    // In the order of the options' properties (AnimationRules.properties).
    private enum class Target(val property: Property<View, Float>) {
        X(View.X), Y(View.Y), TranslationX(View.TRANSLATION_X), TranslationY(View.TRANSLATION_Y), Alpha(View.ALPHA),
        ScaleX(View.SCALE_X), ScaleY(View.SCALE_Y), RotationX(View.ROTATION_X), RotationY(View.ROTATION_Y),
        Rotation(View.ROTATION);

        // A value of the options as the view takes it: places and shifts from dp, `x` / `y` in the parent's origin.
        fun toPixels(value: Double, density: Float, base: Base): Float = when (this) {
            X -> value.toFloat() * density + base.xOffset
            Y -> value.toFloat() * density + base.yOffset
            TranslationX, TranslationY -> value.toFloat() * density
            else -> value.toFloat()
        }
    }

    private fun specsOf(side: ViewAnimationOptions): List<Spec> =
        AnimationRules.properties(side).mapIndexed { index, property -> spec(Target.entries[index], property) }

    private fun specsOf(element: ElementTransitionOptions): List<Spec> =
        AnimationRules.elementProperties(element).mapIndexed { i, property -> spec(Target.entries[i], property) }

    private fun spec(target: Target, property: PropertyAnimationOptions) = Spec(
        target,
        property.from.get(null)?.takeIf { it.isFinite() },
        property.to.get(null)?.takeIf { it.isFinite() },
        AnimationRules.durationOf(property.duration.get(null), AnimationRules.DEFAULT_DURATION),
        AnimationRules.delayOf(property.startDelay.get(null)),
        property.interpolation,
    )

    private fun spec(target: Target, property: ElementAnimationOptions) = Spec(
        target,
        property.from.get(null)?.takeIf { it.isFinite() },
        property.to.get(null)?.takeIf { it.isFinite() },
        AnimationRules.durationOf(property.duration.get(null), AnimationRules.DEFAULT_DURATION),
        AnimationRules.delayOf(property.startDelay.get(null)),
        property.interpolation,
    )
}

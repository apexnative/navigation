package dev.apexnative.navigation.views.animations

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View

/**
 * A hardware layer on a view for the time of an animator that fades it. A view whose `alpha` is below 1 and whose
 * rendering overlaps (any `ViewGroup` by default) is drawn into an offscreen buffer at every frame; through a layer it
 * is drawn once and the GPU only composites it. The layer is taken as the animator starts and only from
 * `LAYER_TYPE_NONE` (a layer someone else set stays theirs), and given back as it ends or is cancelled. A pure
 * translation or scale needs none: RenderThread draws it. The stack's default transition has its own
 * (`StackViews.dress`). Main thread only.
 */
internal object AnimationLayer {
    private const val ALPHA = "alpha"

    /**
     * [view] draws through a hardware layer while [animator] plays, when [animator] changes the `alpha` of [view] and
     * the view's rendering overlaps; [animator] itself, for chaining.
     */
    fun during(view: View, animator: Animator): Animator {
        if (!changesAlpha(animator, view)) return animator
        val hold = Hold({ view.layerType }, { view.setLayerType(it, null) })
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                hold.take(changesAlpha = true, overlapping = view.hasOverlappingRendering())
            }

            override fun onAnimationCancel(animation: Animator) = hold.release()

            override fun onAnimationEnd(animation: Animator) = hold.release()
        })
        return animator
    }

    /** Whether an animation takes a layer: it fades, its view's rendering overlaps, and the view has no layer. */
    fun takes(changesAlpha: Boolean, overlapping: Boolean, layerType: Int): Boolean =
        changesAlpha && overlapping && layerType == View.LAYER_TYPE_NONE

    /**
     * The layer one animation took on one view, through [layerType] and [setLayerType]: taken at most once at a time,
     * given back once, and only while it is still the hardware layer it set.
     */
    class Hold(private val layerType: () -> Int, private val setLayerType: (Int) -> Unit) {
        /** Whether the animation holds the view's layer now. */
        var isTaken = false
            private set

        /** The animation starts: the layer, when [takes] says so. */
        fun take(changesAlpha: Boolean, overlapping: Boolean) {
            if (isTaken || !takes(changesAlpha, overlapping, layerType())) return
            setLayerType(View.LAYER_TYPE_HARDWARE)
            isTaken = true
        }

        /** The animation ended or stopped: the layer goes, unless someone changed it meanwhile. */
        fun release() {
            if (!isTaken) return
            isTaken = false
            if (layerType() == View.LAYER_TYPE_HARDWARE) setLayerType(View.LAYER_TYPE_NONE)
        }
    }

    // Whether [animator], or an animator in it, writes the alpha of [view].
    private fun changesAlpha(animator: Animator, view: View): Boolean = when (animator) {
        is AnimatorSet -> animator.childAnimations.any { changesAlpha(it, view) }
        is ObjectAnimator -> animator.target === view && animator.values.orEmpty().any { it.propertyName == ALPHA }
        else -> false
    }
}

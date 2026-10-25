package dev.apexnative.navigation.views.element.animators

import android.animation.Animator
import android.view.View
import android.widget.ImageView
import com.facebook.react.views.image.ReactImageView
import com.facebook.react.views.text.ReactTextView
import com.facebook.react.views.view.ReactViewGroup
import dev.apexnative.navigation.options.SharedElementTransitionOptions
import dev.apexnative.navigation.views.animations.AnimationRules
import dev.apexnative.navigation.views.animations.Curves
import dev.apexnative.navigation.views.element.TransitionLayer
import dev.apexnative.navigation.views.element.pairName

/**
 * The animator of a shared element by the kinds of its two views, chosen by explicit classes, never by reflection:
 * texts (`TextChangeAnimator`, the module), React views with children (`ViewChangeAnimator`, the module; a failed
 * picture flies as a view without children), React images ([ReactImageAnimator]), other images ([ImageMatrixAnimator]),
 * any other view ([PlainViewAnimator]). The animator gets the pair's duration (else the fallback, the screen's fade),
 * its delay and its curve here, each of its own (a spring in real time); an `AnimatorSet` around it never sets them.
 * Main thread only.
 */
internal object SharedAnimatorFactory {
    /**
     * A pair's animator, and how it shows the pair: by pictures in the layer's overlay while `to` is hidden (the
     * modules), or by moving `to` itself, which shows from the start of the flight.
     */
    class Flight(val animator: Animator, val drawsPictures: Boolean)

    /**
     * The flight of the pair [from] → [to]; [to] is in [layer] at its margins, at its real alpha (the pictures are
     * taken now). [toParent] is the parent [to] had in its screen, [toStandIn] the view in its place there. None: the
     * pair doesn't fly (a text that didn't change, a picture that can't be taken, no place).
     */
    fun create(
        from: View,
        to: View,
        toParent: View?,
        toStandIn: View?,
        layer: TransitionLayer,
        options: SharedElementTransitionOptions,
        fallbackDuration: Long,
        log: (String) -> Unit,
    ): Flight? {
        val flight = when {
            from is ReactTextView && to is ReactTextView -> pictures(
                TextChangeAnimator(from, to, toStandIn).takeIf { it.shouldAnimate() }?.create(options),
            )
            hasChildren(from) || hasChildren(to) -> pictures(
                ViewChangeAnimator(from, to, toStandIn).takeIf { it.shouldAnimate() }?.create(options),
            ) ?: moves(PlainViewAnimator(from, to, toStandIn, layer).create())
            from is ReactImageView && to is ReactImageView ->
                moves(ReactImageAnimator(from, to, toStandIn, layer).create())
            from is ImageView && to is ImageView -> moves(
                ImageMatrixAnimator(
                    from, to, toStandIn, layer, from.parent as? View, toParent, options.toId.get(""), log,
                ).create(),
            )
            else -> moves(PlainViewAnimator(from, to, toStandIn, layer).create())
        } ?: return null
        val animator = flight.animator
        (animator as? MoveAnimator)?.element = pairName(options.fromId.get(""), options.toId.get(""))
        val duration = AnimationRules.durationOf(options.duration.get(null), fallbackDuration)
        animator.duration = duration
        animator.startDelay = AnimationRules.delayOf(options.startDelay.get(null))
        animator.interpolator = Curves.of(options.interpolation, duration)
        return flight
    }

    private fun pictures(animator: Animator?): Flight? = animator?.let { Flight(it, drawsPictures = true) }

    private fun moves(animator: Animator?): Flight? = animator?.let { Flight(it, drawsPictures = false) }

    private fun hasChildren(view: View): Boolean = view is ReactViewGroup && view.childCount > 0
}

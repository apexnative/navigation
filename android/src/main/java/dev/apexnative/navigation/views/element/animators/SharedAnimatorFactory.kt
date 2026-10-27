package dev.apexnative.navigation.views.element.animators

import android.view.View
import android.widget.ImageView
import com.facebook.react.views.text.ReactTextView
import dev.apexnative.navigation.options.SharedElementTransitionOptions
import dev.apexnative.navigation.views.animations.AnimationRules
import dev.apexnative.navigation.views.animations.Curves
import dev.apexnative.navigation.views.element.elementSkipped
import dev.apexnative.navigation.views.element.pairName

/**
 * The flight of a shared pair, as on iOS: a copy drawn in the transition's layer, chosen by the kinds of the two views,
 * by explicit classes, never by reflection — texts ([TextMorph]), images ([ImageMorph]; one without an image to fit
 * flies as a view), any other views ([ViewMorph]). The views themselves are neither moved nor changed. The animator
 * gets the pair's duration (else the fallback, the screen's fade), its delay and its curve here, each of its own (a
 * spring in real time); an `AnimatorSet` around it never sets them. Main thread only.
 */
internal object SharedAnimatorFactory {
    /** A pair's animator and the copy it moves. */
    class Flight(val animator: MoveAnimator, val picture: FlightPicture)

    /**
     * The flight of the pair [from] → [to] in [layer], its pictures taken now. None: the pair doesn't fly — a text that
     * doesn't change, or, with a line in [log], a view without a size or a picture that can't be taken.
     */
    fun create(
        from: View,
        to: View,
        layer: View,
        options: SharedElementTransitionOptions,
        fallbackDuration: Long,
        log: (String) -> Unit,
    ): Flight? {
        val name = pairName(options.fromId.get(""), options.toId.get(""))
        if (from.width <= 0 || from.height <= 0 || to.width <= 0 || to.height <= 0) {
            elementSkipped(name, "a view of the pair has no size, it isn't laid out", log)
            return null
        }
        val picture = if (from is ReactTextView && to is ReactTextView) {
            val text = TextMorph.of(from, to, layer) ?: return noPicture(name, log)
            if (!text.changes) return null
            text
        } else {
            val image = if (from is ImageView && to is ImageView) ImageMorph.of(from, to, layer) else null
            image ?: ViewMorph.of(from, to, layer) ?: return noPicture(name, log)
        }
        val animator = MoveAnimator().apply {
            element = name
            onStart {
                picture.shown = true
                picture.update(0f, curve.getInterpolation(0f))
                layer.invalidate()
            }
            onFrame {
                picture.update(it, curve.getInterpolation(it))
                layer.invalidate()
            }
            // A flight that landed keeps its last frame until the transition takes the layer away: the screens may
            // still move. A stopped one draws nothing more.
            onEnd {
                if (!landed) picture.shown = false
                layer.invalidate()
            }
        }
        val duration = AnimationRules.durationOf(options.duration.get(null), fallbackDuration)
        animator.duration = duration
        animator.startDelay = AnimationRules.delayOf(options.startDelay.get(null))
        animator.interpolator = Curves.of(options.interpolation, duration)
        return Flight(animator, picture)
    }

    private fun noPicture(name: String, log: (String) -> Unit): Flight? {
        elementSkipped(name, "no picture of a view of the pair can be taken", log)
        return null
    }
}

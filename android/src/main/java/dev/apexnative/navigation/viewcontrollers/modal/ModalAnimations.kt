package dev.apexnative.navigation.viewcontrollers.modal

import kotlin.math.PI
import kotlin.math.cos

/**
 * The default animations of a modal on Android, as values: it comes up from the bottom of the window by the window's
 * height (in a split screen or a window, the window's, not the display's) and fades in, [DURATION] with a deceleration
 * at the end; it goes down and fades out in the same time, accelerating and decelerating (the system's cosine, not the
 * option's `accelerateDecelerate`). A sheet moves only by the Material sheet's own motion. Pure arithmetic.
 */
internal object ModalAnimations {
    /** How long the default show and the default dismissal last (ms). */
    const val DURATION = 300L

    /** How far a modal comes up and goes down: the window's height ([windowHeight]), or the display's while it is 0. */
    fun distance(windowHeight: Int, displayHeight: Int): Float =
        (if (windowHeight > 0) windowHeight else displayHeight).toFloat()

    /** The curve of the show: a deceleration, 1 − (1 − p)². */
    fun decelerate(progress: Float): Float = 1f - (1f - progress) * (1f - progress)

    /** The curve of the dismissal: an acceleration then a deceleration, cos((p + 1)π) / 2 + 0.5. */
    fun accelerateDecelerate(progress: Float): Float = (cos((progress + 1) * PI) / 2 + 0.5).toFloat()

    /** Where a modal is at [progress] (0 … 1, in time) of its show over [distance]: its translation and its alpha. */
    fun show(progress: Float, distance: Float): Frame {
        val value = decelerate(progress)
        return Frame(translationY = distance * (1f - value), alpha = value)
    }

    /** Where a modal is at [progress] of its dismissal over [distance], from where it rests. */
    fun dismiss(progress: Float, distance: Float): Frame {
        val value = accelerateDecelerate(progress)
        return Frame(translationY = distance * value, alpha = 1f - value)
    }

    /** A modal's place in its animation. */
    data class Frame(val translationY: Float, val alpha: Float)
}

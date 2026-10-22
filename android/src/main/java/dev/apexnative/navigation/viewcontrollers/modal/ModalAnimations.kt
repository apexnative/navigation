package dev.apexnative.navigation.viewcontrollers.modal

import dev.apexnative.navigation.views.animations.Curves

/**
 * The default animations of a modal over the whole screen on Android, as values: the system's motion of a window that
 * opens over the one below it (`activity_translucent_open_enter` and `activity_translucent_close_exit` of the
 * platform, which Android 16 plays for a translucent activity). The modal comes up from the bottom of the window by the
 * window's height (in a split screen or a window, the window's, not the display's) in [SHOW_DURATION], slowing down at
 * the end; it goes down in [DISMISS_DURATION], speeding up. It doesn't fade: the screen below is seen only around it,
 * as under a translucent activity. A sheet moves only by the Material sheet's own motion. Pure arithmetic.
 */
internal object ModalAnimations {
    /** How long the default show lasts (ms): `activity_translucent_open_enter`. */
    const val SHOW_DURATION = 300L

    /** How long the default dismissal lasts (ms): `activity_translucent_close_exit`. */
    const val DISMISS_DURATION = 250L

    // `linear_out_slow_in` of the platform: the path (0, 0) (0, 0) (0.2, 1) (1, 1).
    private val linearOutSlowIn = Curves.CubicBezier(0.0, 0.0, 0.2, 1.0)

    /** How far a modal comes up and goes down: the window's height ([windowHeight]), or the display's while it is 0. */
    fun distance(windowHeight: Int, displayHeight: Int): Float =
        (if (windowHeight > 0) windowHeight else displayHeight).toFloat()

    /** The curve of the show: the platform's `linear_out_slow_in`, fast at first and slow at the end. */
    fun linearOutSlowIn(progress: Float): Float = linearOutSlowIn.getInterpolation(progress)

    /** The curve of the dismissal: the platform's `accelerate_quad`, p². */
    fun accelerate(progress: Float): Float = progress.coerceIn(0f, 1f).let { it * it }

    /** Where a modal is at [progress] (0 … 1, in time) of its show over [distance]: its translation. */
    fun show(progress: Float, distance: Float): Float = distance * (1f - linearOutSlowIn(progress))

    /** Where a modal is at [progress] of its dismissal over [distance], from where it rests. */
    fun dismiss(progress: Float, distance: Float): Float = distance * accelerate(progress)
}

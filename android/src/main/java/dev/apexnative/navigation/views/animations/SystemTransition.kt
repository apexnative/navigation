package dev.apexnative.navigation.views.animations

import android.graphics.Path
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * The system's transition between activities (activity_open_* and activity_close_* of Android 17, with their right to
 * left variants), the default of a push and a pop: what moves goes 96 dp along the axis on the system's curve, what
 * comes in on a push or goes out on a pop fades at once.
 */
object SystemTransition {
    const val DISTANCE_DP = 96f
    const val DURATION = 450L
    const val FADE_DURATION = 83L
    const val PUSH_FADE_DELAY = 50L
    const val POP_FADE_DELAY = 35L

    /** fast_out_extra_slow_in. */
    @JvmField
    val CURVE: Interpolator = PathInterpolator(Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    })
}

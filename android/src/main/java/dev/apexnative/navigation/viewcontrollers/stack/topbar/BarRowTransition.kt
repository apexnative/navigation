package dev.apexnative.navigation.viewcontrollers.stack.topbar

import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.BarMotion

/**
 * The bar's row in a default transition of its stack: as the bar takes the incoming screen's options, a picture of the
 * row and the large title lies over them and the real row turns transparent; then the picture goes with the old screen
 * and the row comes with the new one ([RowTimeline]). Each change of the stack's current child owns the bar: a newer
 * one takes it over (the older picture goes, its animator stops), and only the end of the transition that owns it puts
 * the row back at rest. Without a picture (a `HARDWARE` bitmap in the bar) the old row just hides. Main thread only.
 */
internal class BarRowTransition(private val target: Target) {
    /** What the transition moves: the bar's views, or the unit tests' fake. */
    interface Target {
        /**
         * Lays a picture of the row and the large title over them, laid out at once and taking the touches; false when
         * the bar can't be drawn in software (a `HARDWARE` bitmap): no picture then.
         */
        fun takeSnapshot(): Boolean

        /** The picture goes. */
        fun dropSnapshot()

        /** The row and the large title as [frame] says; the picture too while it is there. Pixels from dp here. */
        fun show(frame: RowTimeline.Frame, rtl: Boolean)

        /** The row and the large title at rest: opaque, in their place, without the picture. */
        fun rest()

        /** The bar's row reads right to left. */
        val isRtl: Boolean

        /** The slide's curve (`fast_out_extra_slow_in`); the unit tests give a straight line. */
        val curve: (Float) -> Float

        /** Plays [onFrame] with the time since the start, in milliseconds, for [duration]; the function stops it. */
        fun play(duration: Long, onFrame: (elapsed: Long) -> Unit): () -> Unit
    }

    private class Run(val motion: BarMotion)

    private var owner: Run? = null
    private var stop: (() -> Unit)? = null

    /** A transition owns the bar now. */
    val isRunning: Boolean
        get() = owner != null

    /**
     * The stack's current child changes ([BarMotion], or none for a change at once): what a running transition did to
     * the row goes, and a default transition takes the picture before the bar takes the new screen's options.
     */
    fun begin(motion: BarMotion?) {
        release()
        if (motion == null) return
        val run = Run(motion)
        owner = run
        if (!target.takeSnapshot()) target.dropSnapshot()
        val rtl = target.isRtl
        val curve = target.curve
        target.show(RowTimeline.frame(motion, 0L, curve), rtl)
        stop = target.play(RowTimeline.duration(motion)) { elapsed ->
            if (owner === run) target.show(RowTimeline.frame(motion, elapsed, curve), rtl)
        }
    }

    /**
     * The transition that brought the current child ended (`operationEnded` with the top settled), or the stack's
     * animations were all cancelled: the row at rest, the picture gone.
     */
    fun end() {
        if (owner == null) return
        release()
    }

    private fun release() {
        stop?.invoke()
        stop = null
        val had = owner != null
        owner = null
        if (had) target.rest()
    }
}

/**
 * The timeline of the row in the default transitions: push and pop slide both 96 dp in 450 ms on
 * `fast_out_extra_slow_in` (the system's transition), the picture fades out linearly in 100 ms and the new row fades in
 * linearly in 150 ms after 50 ms (push) or 35 ms (pop); fade through (`setStackRoot`): the picture fades out in 105 ms,
 * the new row is transparent for the first 35 % of 300 ms, then fades in. No scale.
 */
internal object RowTimeline {
    /** The values at one moment: alphas, and shifts along the line in dp (forward is to the end of the line). */
    class Frame(val snapshotAlpha: Float, val snapshotShift: Float, val rowAlpha: Float, val rowShift: Float)

    const val DISTANCE_DP = 96f
    private const val SLIDE = 450L
    private const val SNAPSHOT_FADE = 100L
    private const val ROW_FADE = 150L
    private const val PUSH_DELAY = 50L
    private const val POP_DELAY = 35L
    private const val FADE_THROUGH = 300L
    private const val FADE_THROUGH_OUT = 105L

    fun duration(motion: BarMotion): Long = if (motion == BarMotion.FadeThrough) FADE_THROUGH else SLIDE

    fun frame(motion: BarMotion, elapsed: Long, curve: (Float) -> Float): Frame {
        val slide = curve((elapsed.toFloat() / SLIDE).coerceIn(0f, 1f))
        val out = 1f - fraction(elapsed, 0L, SNAPSHOT_FADE)
        return when (motion) {
            BarMotion.Push -> Frame(out, -DISTANCE_DP * slide, fraction(elapsed, PUSH_DELAY, ROW_FADE),
                DISTANCE_DP * (1f - slide))
            BarMotion.Pop -> Frame(out, DISTANCE_DP * slide, fraction(elapsed, POP_DELAY, ROW_FADE),
                -DISTANCE_DP * (1f - slide))
            BarMotion.FadeThrough -> Frame(1f - fraction(elapsed, 0L, FADE_THROUGH_OUT), 0f,
                fraction(elapsed, FADE_THROUGH_OUT, FADE_THROUGH - FADE_THROUGH_OUT), 0f)
        }
    }

    // 0 until [delay], then linear to 1 over [duration].
    private fun fraction(elapsed: Long, delay: Long, duration: Long): Float =
        ((elapsed - delay).toFloat() / duration).coerceIn(0f, 1f)
}

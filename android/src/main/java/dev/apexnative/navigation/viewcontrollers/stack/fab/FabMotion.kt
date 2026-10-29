package dev.apexnative.navigation.viewcontrollers.stack.fab

import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.BarMotion
import dev.apexnative.navigation.views.animations.FadeThrough
import dev.apexnative.navigation.views.animations.SystemTransition

/**
 * How a stack's FAB goes with a change of its screen on top, as the values alone: the new top's FAB arrives with its
 * screen and a copy of the old one leaves with the screen it belonged to, from the start of the transition. A FAB with
 * the same id stays where it is. The stack plays them on its views (`FabPresenter`).
 */
internal object FabMotion {
    /** How the stack's transition goes, as its queue tells it (`StackQueue.Host.fabTransition`). */
    enum class Move {
        /** The system's push: each FAB rides with its screen along the line, as the tab bar does. */
        WithPush,

        /** The system's pop: the same, the other way. */
        WithPop,

        /** The fade through of a `setStackRoot`: the old one fades out first, the new one fades in and grows. */
        FadeThrough,

        /**
         * A transition of the screens' own (`content`, elements): Material's swap at its start, the old one shrinking
         * away, the new one growing from 60 %.
         */
        Swap,

        /** No animation: the new top's FAB is there at once. */
        AtOnce,
    }

    /** The curve a ride's move and scale take; its alpha changes on a straight line. */
    enum class Curve { System, FadeThrough, Material }

    /**
     * A FAB's ride: along the x axis in dp, mirrored right to left; its scale on [curve] over [duration]; its alpha after
     * a hold of [alphaDelay] over [alphaDuration], in ms.
     */
    data class Ride(
        val fromX: Float,
        val toX: Float,
        val fromAlpha: Float,
        val toAlpha: Float,
        val fromScale: Float,
        val toScale: Float,
        val alphaDelay: Long,
        val alphaDuration: Long,
        val duration: Long,
        val curve: Curve,
    )

    /** How long Material's swap takes, as the FAB shrinks or grows. */
    const val SWAP_MILLIS = 200L

    /** Where Material's swap grows the new FAB from. */
    const val GROW_FROM = 0.6f

    private const val FADE_THROUGH_SCALE = 0.92f

    /**
     * The move of the FAB with a change of the current child: none when the change comes at once or the new top shows
     * already ([shown]: a back gesture's preview showed it); Material's swap with an animation of the screens' own
     * ([own]); else the default transition's [row], as the bar's row goes.
     */
    fun moveOf(animated: Boolean, own: Boolean, row: BarMotion, shown: Boolean): Move = when {
        !animated || shown -> Move.AtOnce
        own -> Move.Swap
        row == BarMotion.Push -> Move.WithPush
        row == BarMotion.Pop -> Move.WithPop
        else -> Move.FadeThrough
    }

    /**
     * The new top's FAB comes with its screen: a push brings it from +96 dp (−96 dp on a pop), transparent for the hold
     * of the screen's fade, then fading in; the fade through fades it in after the old one went and grows it from 92 %;
     * the swap grows it from 60 %. None at once.
     */
    fun arrival(move: Move, rtl: Boolean): Ride? {
        val distance = SystemTransition.DISTANCE_DP * if (rtl) -1 else 1
        return when (move) {
            Move.WithPush, Move.WithPop -> Ride(
                fromX = if (move == Move.WithPush) distance else -distance,
                toX = 0f,
                fromAlpha = 0f,
                toAlpha = 1f,
                fromScale = 1f,
                toScale = 1f,
                alphaDelay = fadeDelay(move),
                alphaDuration = SystemTransition.FADE_DURATION,
                duration = SystemTransition.DURATION,
                curve = Curve.System,
            )
            Move.FadeThrough -> Ride(
                fromX = 0f,
                toX = 0f,
                fromAlpha = 0f,
                toAlpha = 1f,
                fromScale = FADE_THROUGH_SCALE,
                toScale = 1f,
                alphaDelay = fadeThroughOut(),
                alphaDuration = FadeThrough.DURATION - fadeThroughOut(),
                duration = FadeThrough.DURATION,
                curve = Curve.FadeThrough,
            )
            Move.Swap -> Ride(0f, 0f, 1f, 1f, GROW_FROM, 1f, 0L, 0L, SWAP_MILLIS, Curve.Material)
            Move.AtOnce -> null
        }
    }

    /**
     * The old top's FAB leaves with its screen, from where it is: a push takes it to −96 dp with the covered screen and
     * fades it out as the new screen fades in over it; a pop takes it to +96 dp, fading with the leaving screen; the
     * fade through fades it out in the first 35 %; the swap shrinks it to nothing. None at once.
     */
    fun departure(move: Move, rtl: Boolean): Ride? {
        val distance = SystemTransition.DISTANCE_DP * if (rtl) -1 else 1
        return when (move) {
            Move.WithPush, Move.WithPop -> Ride(
                fromX = 0f,
                toX = if (move == Move.WithPush) -distance else distance,
                fromAlpha = 1f,
                toAlpha = 0f,
                fromScale = 1f,
                toScale = 1f,
                alphaDelay = fadeDelay(move),
                alphaDuration = SystemTransition.FADE_DURATION,
                duration = SystemTransition.DURATION,
                curve = Curve.System,
            )
            Move.FadeThrough -> Ride(0f, 0f, 1f, 0f, 1f, 1f, 0L, fadeThroughOut(), fadeThroughOut(), Curve.FadeThrough)
            Move.Swap -> Ride(0f, 0f, 1f, 1f, 1f, 0f, 0L, 0L, SWAP_MILLIS, Curve.Material)
            Move.AtOnce -> null
        }
    }

    private fun fadeDelay(move: Move): Long =
        if (move == Move.WithPush) SystemTransition.PUSH_FADE_DELAY else SystemTransition.POP_FADE_DELAY

    private fun fadeThroughOut(): Long = (FadeThrough.DURATION * FadeThrough.OUT).toLong()
}

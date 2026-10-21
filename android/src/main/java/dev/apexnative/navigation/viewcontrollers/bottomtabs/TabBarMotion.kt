package dev.apexnative.navigation.viewcontrollers.bottomtabs

import dev.apexnative.navigation.views.animations.SystemTransition

/**
 * How the tab bar moves when a stack in the selected tab changes its top and the new top shows or hides the bar: with
 * the screens of the stack's system transition, by a slide of its own, or at once. The values alone, without views; the
 * tabs play them on the bar's container.
 */
internal object TabBarMotion {
    /** How the stack's transition goes, as its queue tells it (`StackQueue.Host.tabBarTransition`). */
    enum class Move {
        /** The system's push: the bar rides with the screen it belongs to. */
        WithPush,

        /** The system's pop: the same, the other way. */
        WithPop,

        /**
         * A transition of its own (a `content` animation, `animations.*.bottomTabs`) or a `setStackRoot` that fades:
         * the bar shows or hides by the transition's `bottomTabs` side, else slides by its height, 300 ms.
         */
        Slide,

        /** No animation: the bar is where the new top wants it at once. */
        AtOnce,
    }

    /** The bar rides with a screen: along the x axis in dp, its alpha changing after a hold. */
    data class Ride(
        val fromX: Float,
        val toX: Float,
        val fromAlpha: Float,
        val toAlpha: Float,
        /** How long the alpha holds before it changes, ms. */
        val alphaDelay: Long,
        /** How long the alpha takes to change after its hold, ms. */
        val alphaDuration: Long,
        /** How long the ride takes, ms, on [SystemTransition.CURVE]. */
        val duration: Long,
    )

    /** A slide by the bar's height: [fromY] and [toY] as parts of the height (0 at rest, 1 under the bottom). */
    data class Slide(val fromY: Float, val toY: Float, val duration: Long)

    /** How long the slide of a merge of `visible` or of a transition of its own takes. */
    const val SLIDE_MILLIS = 300L

    /**
     * The ride for [move] (only [Move.WithPush] and [Move.WithPop]) when the bar ends [showing] or hidden, mirrored
     * right to left ([rtl]): a push takes it away with the covered screen (0 → −96 dp, alpha 1 held 50 ms then to 0
     * at 133 ms) or brings it in with the new one (+96 dp → 0, alpha 0 held 50 ms then to 1); a pop brings it back with
     * the revealed screen (−96 dp → 0, alpha 0 held 35 ms then to 1 at 118 ms) or takes it away with the leaving one.
     */
    fun ride(move: Move, showing: Boolean, rtl: Boolean): Ride? {
        val distance = SystemTransition.DISTANCE_DP * if (rtl) -1 else 1
        val delay = when (move) {
            Move.WithPush -> SystemTransition.PUSH_FADE_DELAY
            Move.WithPop -> SystemTransition.POP_FADE_DELAY
            else -> return null
        }
        // Where the bar is when hidden: with the covered screen on a push, with the leaving one on a pop.
        val away = when {
            move == Move.WithPush && !showing -> -distance
            move == Move.WithPush -> distance
            showing -> -distance
            else -> distance
        }
        return Ride(
            fromX = if (showing) away else 0f,
            toX = if (showing) 0f else away,
            fromAlpha = if (showing) 0f else 1f,
            toAlpha = if (showing) 1f else 0f,
            alphaDelay = delay,
            alphaDuration = SystemTransition.FADE_DURATION,
            duration = SystemTransition.DURATION,
        )
    }

    /** The slide of the bar to [showing] or hidden. */
    fun slide(showing: Boolean): Slide =
        if (showing) Slide(1f, 0f, SLIDE_MILLIS) else Slide(0f, 1f, SLIDE_MILLIS)
}

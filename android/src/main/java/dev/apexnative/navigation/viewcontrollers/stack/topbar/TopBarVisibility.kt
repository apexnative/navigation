package dev.apexnative.navigation.viewcontrollers.stack.topbar

import dev.apexnative.navigation.options.TopBarAnimationOptions
import dev.apexnative.navigation.options.TopBarEnterExitAnimationOptions
import dev.apexnative.navigation.views.animations.AnimationRules

/**
 * Showing and hiding the bar (`topBar.visible`, `.animate`): a hide moves the bar up by its height and the room over it
 * in 300 ms (`FastOutSlowInInterpolator`) and makes it `GONE` at the end; a show moves it back. In a transition of the
 * stack its side (`animations.*.topBar`) plays instead — its `exit` hides, its `enter` shows; `enabled: false` shows or
 * hides at once. A show cancels a running hide and a hide a running show, each with its own token, so `visible: false`
 * then `true` leaves the bar shown; a motion cancelled leaves the bar at rest; a bar shown again stands in its place
 * with its row seen (the hide on scroll's shift and fade reset). [onHidden] runs once a hide has made the bar `GONE`.
 * Main thread only.
 */
internal class TopBarVisibility(private val target: Target, private val onHidden: () -> Unit = {}) {
    /** What moves: the bar's view, or the unit tests' fake. */
    interface Target {
        /** How far up the bar goes to hide: its height and the room over it, in pixels. */
        val hiddenOffset: Float

        var translationY: Float

        /** The bar takes no room and draws nothing (`GONE`). */
        var isGone: Boolean

        /** Moves the bar to [to] in [duration] ms; [onEnd] at its end, not when the function returned stops it. */
        fun slide(to: Float, duration: Long, onEnd: () -> Unit): () -> Unit

        /**
         * Plays [side] on the bar; [onEnd] at its end, not when the function returned stops it. Either way the bar is
         * left at rest (no shift, opaque, full size, not turned).
         */
        fun play(side: TopBarAnimationOptions, onEnd: () -> Unit): () -> Unit

        /** The hide on scroll lets go of the bar: where the rows took it no longer counts. */
        fun resetCollapse()
    }

    private var stop: (() -> Unit)? = null
    // The motion running now: a motion's end does something only while it is still this one.
    private var sliding: Any? = null
    private var hidden = false

    /** The bar is hidden, or hides. */
    val isHidden: Boolean
        get() = hidden

    /** The bar is on its way out: a hide moves it and it still shows. */
    val isHiding: Boolean
        get() = hidden && sliding != null

    /** Shows the bar: by [side] when it plays, sliding down when [animated], else at once. */
    fun show(animated: Boolean, side: TopBarAnimationOptions? = null) {
        val wasHidden = hidden
        hidden = false
        val wasSliding = sliding != null
        cancel()
        if (!wasHidden && !wasSliding) return
        target.resetCollapse()
        val moves = animated && !AnimationRules.isTurnedOff(side)
        val own = side?.takeIf { moves && AnimationRules.sidePlays(it) }
        if (target.isGone) {
            target.isGone = false
            if (moves && own == null) target.translationY = -target.hiddenOffset
        }
        if (!moves) {
            target.translationY = 0f
            return
        }
        val token = Any()
        sliding = token
        val end = if (own != null) {
            target.play(own) { if (sliding === token) settle() }
        } else {
            target.slide(0f, DURATION) { if (sliding === token) settle() }
        }
        if (sliding === token) stop = end
    }

    /** Hides the bar: by [side] when it plays, sliding up when [animated], else at once. */
    fun hide(animated: Boolean, side: TopBarAnimationOptions? = null) {
        val wasHidden = hidden
        hidden = true
        val wasSliding = sliding != null
        cancel()
        if (wasHidden && !wasSliding) return
        val moves = animated && !AnimationRules.isTurnedOff(side)
        if (!moves || target.isGone) {
            target.translationY = 0f
            target.isGone = true
            onHidden()
            return
        }
        val token = Any()
        sliding = token
        val gone = {
            if (sliding === token) {
                settle()
                target.isGone = true
                target.translationY = 0f
                onHidden()
            }
        }
        val own = side?.takeIf { AnimationRules.sidePlays(it) }
        val end = if (own != null) target.play(own, gone) else target.slide(-target.hiddenOffset, DURATION, gone)
        if (sliding === token) stop = end
    }

    /** As the options say: shown unless `visible: false`; in a transition by its [side] (`enter` or `exit`). */
    fun apply(visible: Boolean, animated: Boolean, side: TopBarEnterExitAnimationOptions? = null) =
        if (visible) show(animated, side?.enter) else hide(animated, side?.exit)

    // A running motion stops where it is, without its end.
    private fun cancel() {
        val running = stop
        settle()
        running?.invoke()
    }

    private fun settle() {
        sliding = null
        stop = null
    }

    private companion object {
        const val DURATION = 300L
    }
}

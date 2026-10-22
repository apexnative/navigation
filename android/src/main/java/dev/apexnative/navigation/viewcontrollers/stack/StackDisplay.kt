package dev.apexnative.navigation.viewcontrollers.stack

import dev.apexnative.navigation.options.LayoutAnimation
import dev.apexnative.navigation.options.TopBarEnterExitAnimationOptions
import dev.apexnative.navigation.options.ViewAnimationOptions
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * What the queue of a stack ([StackQueue]) asks of what shows the stack: where the views of its screens are in the
 * stack's container, and the animators that move them. The stack gives its views (`StackViews`); the unit tests give
 * a fake that records the calls. Main thread only.
 */
internal interface StackDisplay {
    /** How a screen moves in a transition of the stack (`SystemTransition`, `FadeThrough`). */
    enum class Motion {
        /** A push brings it in: from +96 dp, hidden until it fades in. */
        PushIn,

        /** A push covers it: to −96 dp, not fading. */
        PushCover,

        /** A pop takes it away: to +96 dp from where it is, fading from its current alpha. */
        PopOut,

        /** A pop reveals it: from −96 dp (or from where it is, when it was moving) to its place. */
        PopReveal,

        /** A `setStackRoot` brings it in: transparent first, then it fades in and grows from 92 %. */
        FadeIn,

        /** A `setStackRoot` takes it away: it fades out from its current alpha. */
        FadeOut,
    }

    /**
     * How the bar's row goes with a default transition of the stack: with the screens of a push (also `setStackRoot`
     * with `asPush`), of a pop, or through a fade (`setStackRoot`).
     */
    enum class BarMotion { Push, Pop, FadeThrough }

    /**
     * How the bar shows or hides when the new top's `topBar.visible` differs: at once (no animation, or a transition
     * with elements), or [animated] by the transition's [side] (`animations.push.topBar`, the leaving screen's
     * `animations.pop.topBar`, the new top's `animations.setStackRoot.topBar`): its `exit` hides, its `enter` shows,
     * the default slide without one.
     */
    class BarVisibility(val animated: Boolean, val side: TopBarEnterExitAnimationOptions?) {
        companion object {
            /** The bar shows or hides at once. */
            @JvmField
            val AT_ONCE = BarVisibility(false, null)
        }
    }

    /** How a screen fades around elements without an animation of its own: 300 ms. */
    enum class Fade {
        None,

        /** The arriving screen: `alpha` 0 → 1. */
        In,

        /** The leaving screen: `alpha` 1 → 0. */
        Out,
    }

    /**
     * The elements of a transition: [animation]'s shared elements and element transitions, from the screen that goes
     * ([from], none: no screen leaves) to the one that comes ([to]).
     */
    class Elements(val animation: LayoutAnimation, val from: ViewController<*>?, val to: ViewController<*>)

    /**
     * A screen's part in a transition of its own: [side] plays on it, or it fades ([fade]); with neither it doesn't
     * move. [alpha]: its alpha before the core hid it, what a missing `alpha` end is and what it shows with; none: its
     * own now. [elements]: this screen's animator carries the transition's elements, which go back to their places when
     * it ends or stops.
     */
    class Custom(
        val side: ViewAnimationOptions?,
        val fade: Fade = Fade.None,
        val alpha: Float? = null,
        val elements: Elements? = null,
    )

    /** The stack's view is made. */
    val hasView: Boolean

    /**
     * The stack shows now: its view shows, and it isn't in a root that still waits to show. Elsewhere the commands run
     * at once, without a wait and without a transition, and the new top appears when the stack shows.
     */
    val isOnScreen: Boolean

    /**
     * The frames of an animation come: the activity can be seen. Without them an operation runs at once, without its
     * animation, and its screens appear as the dispatcher's rules say (as the root's).
     */
    val canAnimate: Boolean

    /** [screen]'s view is in the stack's container. */
    fun isPlaced(screen: ViewController<*>): Boolean

    /**
     * Puts [screen]'s view in the container under the current top, transparent, without touches and hidden from
     * accessibility: it renders there at its size while its push or its `setStackRoot` waits for it.
     */
    fun placeWaiting(screen: ViewController<*>)

    /**
     * Puts [screen]'s view at the bottom of the container, transparent, without touches and hidden from
     * accessibility: a lower screen makes its content there in the window.
     */
    fun placeLower(screen: ViewController<*>)

    /**
     * [screen]'s view goes right under the bar, over the screens, seen again (touches, accessibility): a view in the
     * container moves without leaving it, a view out of it joins it with the stack's last window insets.
     */
    fun raise(screen: ViewController<*>)

    /** [screen]'s view goes right under [over]'s, seen again, as a pop reveals it under the screen that leaves. */
    fun reveal(screen: ViewController<*>, over: ViewController<*>)

    /** [screen]'s view hides at once: a screen a cut takes away from between the leaving top and its target. */
    fun hide(screen: ViewController<*>)

    /** [screen]'s view leaves the container at rest, alive: a covered screen. */
    fun detach(screen: ViewController<*>)

    /** [screen]'s view leaves the container: a screen that never showed goes. */
    fun remove(screen: ViewController<*>)

    /** [screen]'s view at rest: opaque, in its place, full size, not turned. */
    fun rest(screen: ViewController<*>)

    /** Moves [screen] as [motion] says, from its current values where [fromCurrent]; [done] once, at its end. */
    fun animate(screen: ViewController<*>, motion: Motion, fromCurrent: Boolean, done: () -> Unit)

    /**
     * Moves [screen] as [custom] says, from its current values where a side misses an end; [done] once, at its end
     * (at once when nothing moves it). One animator per screen, as [animate]'s: [stop] stops it too.
     */
    fun animateCustom(screen: ViewController<*>, custom: Custom, done: () -> Unit)

    /**
     * The wait for the targets of [animation]: a frame at a time, 0.5 s at most, while [waiting] holds, until [to]'s
     * content is there and every `toId` and element is found (in [to] or [from]); then one frame more, and the images
     * of the shared elements load (1 s at most each). [done] once at its end, not after the function returned stopped
     * it. [to]'s view is in the container, transparent.
     */
    fun awaitTargets(
        animation: LayoutAnimation,
        from: ViewController<*>?,
        to: ViewController<*>,
        waiting: () -> Boolean,
        done: () -> Unit,
    ): () -> Unit

    /** The preview of a back gesture over [top], [target] under it, shows ([finishBackGesture] can end it). */
    fun hasBackPreview(top: ViewController<*>, target: ViewController<*>): Boolean = false

    /**
     * The pop of a back gesture whose preview shows ([hasBackPreview]), released: the preview plays the end of [top]
     * going and [target] coming. [topDone] and [targetDone] once each, as [animate]'s; [stop] of one screen ends the
     * preview where it is and its other screen's part.
     */
    fun finishBackGesture(
        top: ViewController<*>,
        target: ViewController<*>,
        topDone: () -> Unit,
        targetDone: () -> Unit,
    ) {
        topDone()
        targetDone()
    }

    /** Stops the animator of [screen] where it is, without its end: a later transition takes the screen over. */
    fun stop(screen: ViewController<*>)

    /**
     * The push of a prepared slot screen: [mounted] as the slot's content mounts into the screen's container (or at the
     * limit of 0.5 s), then [start] on the pre-draw of that frame, while [waiting] holds.
     */
    fun awaitPrepared(screen: ViewController<*>, waiting: () -> Boolean, mounted: () -> Unit, start: () -> Unit)
}

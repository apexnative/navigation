package dev.apexnative.navigation.views.animations

import dev.apexnative.navigation.options.BottomTabsAnimationOptions
import dev.apexnative.navigation.options.BottomTabsEnterExitAnimationOptions
import dev.apexnative.navigation.options.ElementAnimationOptions
import dev.apexnative.navigation.options.ElementTransitionOptions
import dev.apexnative.navigation.options.EnterExitAnimationOptions
import dev.apexnative.navigation.options.LayoutAnimation
import dev.apexnative.navigation.options.ModalAnimationOptions
import dev.apexnative.navigation.options.PropertyAnimationOptions
import dev.apexnative.navigation.options.ScreenAnimationOptions
import dev.apexnative.navigation.options.StackAnimationOptions
import dev.apexnative.navigation.options.TopBarAnimationOptions
import dev.apexnative.navigation.options.ViewAnimationOptions
import dev.apexnative.navigation.options.params.Bool

/**
 * The rules of the `animations` options: which property plays, which side plays, when a command's animation is its own
 * (the core plays it, not the platform's default), and how long a side lasts. The one place of these rules: the stack,
 * the modals, the root and the bars ask here. Pure.
 */
internal object AnimationRules {
    /** The duration of a property, an element or a shared element without its own, ms. */
    const val DEFAULT_DURATION = 300L

    /** The fade of a screen around elements without an animation of its own, ms. */
    const val SCREEN_FADE = 300L

    /** A property plays when it has `from` or `to`. */
    fun propertyPlays(property: PropertyAnimationOptions): Boolean = property.from.hasValue() || property.to.hasValue()

    /** An element's property plays when it has `from` or `to`. */
    fun propertyPlays(property: ElementAnimationOptions): Boolean = property.from.hasValue() || property.to.hasValue()

    /** The properties of [side] that play, in the order of the options. */
    fun playingProperties(side: ViewAnimationOptions): List<PropertyAnimationOptions> =
        properties(side).filter(::propertyPlays)

    /** The ten properties of [side], in the order of the options. */
    fun properties(side: ViewAnimationOptions): List<PropertyAnimationOptions> = listOf(
        side.x, side.y, side.translationX, side.translationY, side.alpha, side.scaleX, side.scaleY, side.rotationX,
        side.rotationY, side.rotation,
    )

    /**
     * A side plays: it isn't turned off by `enabled: false` and at least one of its properties plays. An `enabled:
     * false` side counts as no side.
     */
    fun sidePlays(side: ViewAnimationOptions?): Boolean {
        side ?: return false
        if (enabledOf(side)?.isFalse == true) return false
        return properties(side).any(::propertyPlays)
    }

    /** A side is turned off (`enabled: false`): what it shows or hides goes at once. */
    fun isTurnedOff(side: ViewAnimationOptions?): Boolean = side?.let(::enabledOf)?.isFalse == true

    /**
     * The side of a bar is given: it plays, or it is turned off. Either way the bar no longer goes with the screens of
     * the system's transition (`animations.setStackRoot.bottomTabs.enter`).
     */
    fun barSideGiven(side: ViewAnimationOptions?): Boolean = sidePlays(side) || isTurnedOff(side)

    /** The shared elements or the element transitions of [animation] are not empty. */
    fun hasElements(animation: LayoutAnimation): Boolean =
        animation.sharedElements.get().isNotEmpty() || animation.elementTransitions.transitions.isNotEmpty()

    /**
     * A transition of the stack is its own (push, pop, `setStackRoot`): `content.enter` or `content.exit` plays, or
     * it has elements. `topBar`, `bottomTabs` and `zoom` never make it own.
     */
    fun stackIsOwn(animation: StackAnimationOptions): Boolean = contentIsOwn(animation) || hasElements(animation)

    /** `content.enter` or `content.exit` of [animation] plays: what a push with `asPush` looks at. */
    fun contentIsOwn(animation: StackAnimationOptions): Boolean =
        sidePlays(animation.content.enter) || sidePlays(animation.content.exit)

    /** A modal's show or dismissal is its own: `enter` or `exit` plays, or it has elements. */
    fun modalIsOwn(animation: ModalAnimationOptions): Boolean =
        sidePlays(animation.enter) || sidePlays(animation.exit) || hasElements(animation)

    /** A new root's animation is its own: one of its sides plays. */
    fun rootPlays(animation: EnterExitAnimationOptions): Boolean =
        sidePlays(animation.enter) || sidePlays(animation.exit)

    /**
     * `showModal.enter.enabled: false` turns the whole show off; `waitForRender` alone keeps it.
     */
    fun showAnimated(animation: ModalAnimationOptions): Boolean = !animation.enter.enabled.isFalse

    /** `dismissModal.exit.enabled: false` turns the whole dismissal off. */
    fun dismissAnimated(animation: ModalAnimationOptions): Boolean = !animation.exit.enabled.isFalse

    /** `setRoot.enter.enabled: false`: the root comes at once, whatever else is given. */
    fun rootAtOnce(animation: EnterExitAnimationOptions): Boolean = animation.enter.enabled.isFalse

    /**
     * Android with elements takes the screen's own side only when it is a single `alpha`; else the screen fades by
     * default.
     */
    fun isSingleAlpha(side: ViewAnimationOptions?): Boolean {
        if (!sidePlays(side)) return false
        val playing = playingProperties(requireNotNull(side) { "a side that plays is given" })
        return playing.size == 1 && playing[0] === side.alpha
    }

    /** The side of a bar for its way: `enter` when it shows, `exit` when it hides. */
    fun barSide(sides: BottomTabsEnterExitAnimationOptions?, showing: Boolean): BottomTabsAnimationOptions? =
        sides?.let { if (showing) it.enter else it.exit }

    // Length: what a shared element without a duration takes from a screen's single `alpha`.

    /** Where a property ends after the start, ms: its delay and its duration, 300 without one. */
    fun endOf(property: PropertyAnimationOptions): Long =
        delayOf(property.startDelay.get(null)) + durationOf(property.duration.get(null), DEFAULT_DURATION)

    /** Where [side] ends: the latest end of its playing properties; 0 when it doesn't play. */
    fun lengthOf(side: ViewAnimationOptions?): Long =
        if (!sidePlays(side)) 0L else playingProperties(requireNotNull(side) { "a side that plays is given" })
            .maxOf(::endOf)

    /** The ten properties of an element transition, in the order of the options. */
    fun elementProperties(element: ElementTransitionOptions) = listOf(
        element.x, element.y, element.translationX, element.translationY, element.alpha, element.scaleX,
        element.scaleY, element.rotationX, element.rotationY, element.rotation,
    )

    /** A duration in ms from the options: unset, not finite or below 0 is [fallback]. */
    fun durationOf(value: Double?, fallback: Long): Long =
        if (value == null || !value.isFinite() || value < 0) fallback else value.toLong()

    /** A delay in ms from the options: unset, not finite or below 0 is 0. */
    fun delayOf(value: Double?): Long = durationOf(value, 0L)

    private fun enabledOf(side: ViewAnimationOptions): Bool? = when (side) {
        is ScreenAnimationOptions -> side.enabled
        is TopBarAnimationOptions -> side.enabled
        is BottomTabsAnimationOptions -> side.enabled
        else -> null
    }
}

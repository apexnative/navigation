package dev.apexnative.navigation.views.element.animators

import android.animation.Animator
import android.view.View
import dev.apexnative.navigation.options.SharedElementTransitionOptions

/**
 * One animator of a shared element: how [from], on the screen that goes, becomes [to], on the screen that comes, when
 * both are of [type]. The core picks one by the views' classes (`SharedAnimatorFactory`) and runs it in the
 * transition's layer.
 */
abstract class PropertyAnimatorCreator<T : View>(
    protected val from: View,
    protected val to: View,
    private val type: Class<T>,
) {
    /** Whether this animator takes part: both views are of its type and differ in what it animates. */
    fun shouldAnimate(): Boolean {
        if (!type.isInstance(from) || !type.isInstance(to)) return false
        val fromChild = type.cast(from) ?: return false
        val toChild = type.cast(to) ?: return false
        return shouldAnimateProperty(fromChild, toChild)
    }

    /** Whether [fromChild] and [toChild] differ in what this animator animates. */
    abstract fun shouldAnimateProperty(fromChild: T, toChild: T): Boolean

    /**
     * The animator of the pair for [options]; the core gives it the pair's duration, delay and curve (each animator
     * its own, never through a set around it).
     */
    abstract fun create(options: SharedElementTransitionOptions): Animator
}

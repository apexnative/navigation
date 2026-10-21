package dev.apexnative.navigation.utils

import android.view.View
import com.facebook.react.uimanager.BackgroundStyleApplicator
import com.facebook.react.uimanager.LengthPercentage
import com.facebook.react.uimanager.style.BorderRadiusProp

/**
 * The corner radius of the view's background, in pixels, as an element transition takes it from one view to another;
 * 0 without rounded corners. React Native keeps it in its own background (`BackgroundStyleApplicator`), in dp or as a
 * part of the view's width: one radius for all corners.
 */
val View.borderRadius: Float
    get() {
        val radius = try {
            BackgroundStyleApplicator.getBorderRadius(this, BorderRadiusProp.BORDER_RADIUS)
        } catch (e: RuntimeException) {
            null
        } ?: return 0f
        return resolveBorderRadius(radius, width, resources?.displayMetrics?.density ?: 1f)
    }

/**
 * A radius of React Native's background in pixels: in dp, or in percent of the view's [width] (pixels); [density] is
 * the pixels of a dp.
 */
internal fun resolveBorderRadius(radius: LengthPercentage, width: Int, density: Float): Float =
    (radius.resolve(width / density) * density).coerceAtLeast(0f)

/** The scale the parents of [view] give it, along x and along y: a view in a scaled parent shows smaller. */
fun computeInheritedScale(view: View): Pair<Float, Float> {
    var scaleX = 1f
    var scaleY = 1f
    var parent = view.parent as? View
    while (parent != null) {
        scaleX *= parent.scaleX
        scaleY *= parent.scaleY
        parent = parent.parent as? View
    }
    return scaleX to scaleY
}

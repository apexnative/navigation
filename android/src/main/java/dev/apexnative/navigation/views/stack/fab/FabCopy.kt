package dev.apexnative.navigation.views.stack.fab

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Picture
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.coordinatorlayout.widget.CoordinatorLayout
import dev.apexnative.navigation.views.element.animators.recordPicture

/**
 * The picture of a FAB that leaves with its screen while the new top's FAB arrives: drawn as the button was, with the
 * shadow of its shape, taking no touches and hidden from accessibility. The live button goes from its parent at once.
 */
@SuppressLint("ViewConstructor")
internal class FabCopy private constructor(
    context: Context,
    private val picture: Picture,
    shape: Outline,
) : View(context) {
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.set(shape)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPicture(picture)
    }

    companion object {
        /**
         * A copy of [button] in [container], a child of [parent] (the button itself, or its menu), as it shows now: in
         * its place in [parent], moved, scaled and faded as it is. None when it doesn't show or can't be recorded (a
         * hardware bitmap in its icon before Android 9).
         */
        fun of(button: View, container: View, parent: ViewGroup): FabCopy? {
            if (container.parent !== parent || !button.isAttachedToWindow) return null
            if (button.width <= 0 || button.height <= 0) return null
            if (container.visibility != VISIBLE || button.visibility != VISIBLE) return null
            val inner = button !== container
            val alpha = container.alpha * if (inner) button.alpha else 1f
            val scale = container.scaleX * if (inner) button.scaleX else 1f
            if (alpha <= 0f || scale <= 0f) return null
            val picture = recordPicture(button) ?: return null
            val shape = Outline().apply {
                // As the view counts its own outline: opaque unless the provider says otherwise.
                this.alpha = 1f
                button.outlineProvider?.getOutline(button, this)
            }
            val copy = FabCopy(button.context, picture, shape)
            val left = container.left + if (inner) button.left else 0
            val top = container.top + if (inner) button.top else 0
            copy.translationX = container.translationX + if (inner) button.translationX else 0f
            copy.translationY = container.translationY + if (inner) button.translationY else 0f
            copy.scaleX = scale
            copy.scaleY = scale
            copy.alpha = alpha
            copy.elevation = button.elevation
            copy.translationZ = button.translationZ
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                copy.outlineAmbientShadowColor = button.outlineAmbientShadowColor
                copy.outlineSpotShadowColor = button.outlineSpotShadowColor
            }
            parent.addView(copy, CoordinatorLayout.LayoutParams(button.width, button.height).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                leftMargin = left
                topMargin = top
            })
            // In its place before the next layout, so its first frame draws.
            copy.measure(
                MeasureSpec.makeMeasureSpec(button.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(button.height, MeasureSpec.EXACTLY),
            )
            copy.layout(left, top, left + button.width, top + button.height)
            return copy
        }
    }
}

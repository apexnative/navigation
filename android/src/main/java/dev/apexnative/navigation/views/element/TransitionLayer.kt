package dev.apexnative.navigation.views.element

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import dev.apexnative.navigation.views.element.animators.FlightPicture

/**
 * The layer the copies of a transition's shared elements fly in: full size over the screens' common parent (a stack's
 * view; for a modal, between the modals layer and the overlays layer), there only while the transition runs. It holds
 * no views: it draws the pictures of the flights ([FlightPicture]) in the order they are added, each while it is shown.
 * A picture whose drawing fails draws no more, and its [FlightPicture.onFailed] runs after the frame. It takes no
 * touches, accessibility doesn't see it. Main thread only.
 */
@SuppressLint("ViewConstructor")
internal class TransitionLayer(context: Context) : View(context) {
    private val pictures = ArrayList<FlightPicture>()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    /**
     * Joins [parent] at [index] (its top without one), laid out at once at the parent's size: the places of the views
     * are measured from it on the frame the transition starts.
     */
    fun attachTo(parent: ViewGroup, index: Int = parent.childCount) {
        // The parent turns these into its own kind of params.
        parent.addView(this, index.coerceIn(0, parent.childCount), ViewGroup.LayoutParams(MATCH, MATCH))
        measure(
            MeasureSpec.makeMeasureSpec(parent.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(parent.height, MeasureSpec.EXACTLY),
        )
        layout(0, 0, parent.width, parent.height)
    }

    /** [picture] is drawn over those added before it. */
    fun add(picture: FlightPicture) {
        pictures += picture
        invalidate()
    }

    /** The layer leaves its parent. */
    fun detach() {
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun onDraw(canvas: Canvas) {
        for (picture in pictures) {
            if (!picture.shown) continue
            val count = canvas.save()
            try {
                picture.draw(canvas)
            } catch (e: RuntimeException) {
                picture.shown = false
                post { picture.onFailed(e) }
            } finally {
                canvas.restoreToCount(count)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean = false

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}

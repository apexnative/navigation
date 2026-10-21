package dev.apexnative.navigation.views.element

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View

/**
 * The empty view that stands in an element's place in its parent while the element is in the transition layer: the same
 * id at the same index with the same layout, so Fabric finds the parent's children as it left them; React removing it
 * removes the element for good. It has no `nativeID`, so no search finds it as the element. It draws nothing and takes
 * no touches. Main thread only.
 */
@SuppressLint("ViewConstructor")
internal class ElementStub(context: Context, element: View) : View(context) {
    init {
        id = element.id
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))

    override fun draw(canvas: Canvas) = Unit

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean = false
}

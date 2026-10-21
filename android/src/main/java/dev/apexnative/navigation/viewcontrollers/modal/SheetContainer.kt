package dev.apexnative.navigation.viewcontrollers.modal

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors

/**
 * The container of a modal shown as a sheet (`ModalSheet`), which its bottom sheet behavior moves. Its background is
 * Material 3's "surface container low" of its context (the app's Material 3 theme, else the navigation's neutral one),
 * read again after a light/dark switch. A view in it that measures where it is in the window counts the sheet where it
 * rests, not where a drag took it ([shift]).
 */
@SuppressLint("ViewConstructor")
class SheetContainer(context: Context) : FrameLayout(context) {
    /**
     * How far the sheet is below where it rests, in pixels: fully raised, its bottom at the bottom of its layout. The
     * behavior moves the sheet only by `offsetTopAndBottom` and lays it out again in `onLayout`; both keep it up to
     * date, so a drag moves the sheet and the place of rest stays.
     */
    var shift = 0
        private set

    init {
        applyBackground()
    }

    /** The background of the sheet in the colors of its theme now: after a light/dark switch too. */
    fun applyBackground() {
        setBackgroundColor(MaterialColors.getColor(context, MaterialR.attr.colorSurfaceContainerLow, Color.WHITE))
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        applyBackground()
    }

    override fun offsetTopAndBottom(offset: Int) {
        super.offsetTopAndBottom(offset)
        updateShift()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateShift()
    }

    private fun updateShift() {
        val parent = parent as? View ?: return
        shift = shiftOf(top, height, parent.height)
    }

    companion object {
        /**
         * How far a sheet whose top is at [top], [height] tall, is below its place of rest in a layout [parentHeight]
         * tall: its rest is fully raised, its bottom at the layout's bottom.
         */
        fun shiftOf(top: Int, height: Int, parentHeight: Int): Int = top - (parentHeight - height)

        /** Where a sheet whose top is at [top] and that is [shift] below its rest has its top at rest. */
        fun restTop(top: Int, shift: Int): Int = top - shift
    }
}

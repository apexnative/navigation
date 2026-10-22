package dev.apexnative.navigation.viewcontrollers.parent

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.utils.SystemUiUtils
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The background of a stack's and of the tabs' view (`layout.backgroundColor`): `layout.backgroundColor` of the options
 * the layout shows with (its visible screen's over its own over the defaults), kept out of the status bar's height as a
 * screen's is (`ScreenSafeArea.backgroundBand`); it shows around the screens during transitions. Without the option no
 * background (the window shows); `'NoColor'` transparent. The layers of the navigation never paint; the side menu
 * paints its own. Main thread only.
 */
internal object ContainerBackground {
    /** Whether a merge of [options] changes the background: its color, or the status bar's room. */
    fun isTouchedBy(options: Options): Boolean = options.layout.backgroundColor.hasValue() ||
        options.statusBar.visible.hasValue() || options.statusBar.drawBehind.hasValue()

    /** [node]'s view takes the background of [options], resolved; nothing before the view is made. */
    fun apply(node: ViewController<*>, options: Options) {
        val view = node.peekView() ?: return
        view.background = backgroundOf(node, options)
    }

    private fun backgroundOf(node: ViewController<*>, options: Options): Drawable? {
        val colour = options.layout.backgroundColor
        if (!colour.hasValue()) return null
        val color = colour.get(node.activity, Color.TRANSPARENT) ?: Color.TRANSPARENT
        // The screens' band rule (`ScreenSafeArea.backgroundBand`) for a layout, which keeps no room at its top: the
        // status bar's height, but none when it is hidden or the layout is drawn under it (in an overlay by default),
        // in a side menu, a sheet or a pair.
        val full = options.statusBar.isHiddenOrDrawBehind(node.isInOverlay) || node.isInSideMenu || node.isInSheet ||
            node.parentController is KeepsStatusBarRoom
        val band = if (full) 0 else SystemUiUtils.getStatusBarHeight(node.activity)
        if (band <= 0) return ColorDrawable(color)
        // A layer inset reports no padding, so the view keeps its own.
        return LayerDrawable(arrayOf<Drawable>(ColorDrawable(color))).apply { setLayerInset(0, 0, band, 0, 0) }
    }
}

package dev.apexnative.navigation.utils

import android.app.Activity
import android.content.res.Resources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

/** The system bars of an activity's window. */
object SystemUiUtils {
    private const val FALLBACK_STATUS_BAR_DP = 24f

    /**
     * The height of the status bar over the window of [activity], in pixels: the window's status bar inset, shown or
     * not, so it follows a rotation, a cutout, a split screen and a window of any size; before the window has insets
     * the system's `status_bar_height`, without it 24 dp.
     */
    @JvmStatic
    fun getStatusBarHeight(activity: Activity): Int {
        val decor = activity.window?.peekDecorView()
        val insets = decor?.let(ViewCompat::getRootWindowInsets)
        val inset = insets?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())?.top
        if (inset != null) return inset
        val density = activity.resources?.displayMetrics?.density ?: 1f
        return statusBarHeight(null, systemStatusBarHeight(), density)
    }

    /**
     * As a rule: the window's [inset] when it has one; else the system's dimension ([systemDimension], 0 when it has
     * none); else 24 dp at [density].
     */
    internal fun statusBarHeight(inset: Int?, systemDimension: Int, density: Float): Int =
        inset ?: systemDimension.takeIf { it > 0 } ?: (FALLBACK_STATUS_BAR_DP * density).roundToInt()

    // The `android` package's `status_bar_height`: the platform's default, without a cutout; 0 without it.
    private fun systemStatusBarHeight(): Int {
        val resources: Resources = Resources.getSystem() ?: return 0
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else 0
    }
}

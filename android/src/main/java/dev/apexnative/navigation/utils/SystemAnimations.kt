package dev.apexnative.navigation.utils

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import dev.apexnative.navigation.NavigationApplication

/**
 * Whether the system plays no animations: "Remove animations" in the accessibility settings, or the animator duration
 * scale at 0 in the developer options. Then a transition doesn't wait for its elements' targets and images and moves no
 * element, and the side menu opens and closes at once. Any thread.
 */
internal object SystemAnimations {
    /**
     * The animations are off now; [context] reads the scale below Android 8, else the application's. Lint can't see
     * that the decision calls `areAnimatorsEnabled` (API 26) only from Android 8.
     */
    @SuppressLint("NewApi")
    fun areOff(context: Context? = null): Boolean =
        areOff(Build.VERSION.SDK_INT, { ValueAnimator.areAnimatorsEnabled() }) { durationScaleOf(context) }

    /**
     * The decision: from Android 8 the animator's own answer ([animatorsEnabled]), which follows both settings;
     * below it the duration scale ([durationScale]) at 0.
     */
    fun areOff(sdk: Int, animatorsEnabled: () -> Boolean, durationScale: () -> Float): Boolean =
        if (sdk >= Build.VERSION_CODES.O) !animatorsEnabled() else durationScale() == 0f

    // Without a context and before the application started (the unit tests), the animations play.
    private fun durationScaleOf(context: Context?): Float {
        val resolver = (context ?: NavigationApplication.instanceOrNull)?.contentResolver ?: return 1f
        return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
}

package dev.apexnative.navigation.react

import android.content.res.Resources
import androidx.annotation.VisibleForTesting
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import dev.apexnative.navigation.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * What JavaScript asks as it starts (`getStartInfo`): whether its runtime is the first of the process, a cold start,
 * when the navigation's splash shows; the size of the native splash's logo; and whether the native side shows a pair
 * of screens (`defineArrangement`), which Android always does.
 */
internal object StartInfo {
    // The JavaScript runtimes of the process so far, each counted as it makes its navigation module.
    private val runtimes = AtomicInteger()

    /** Counts a new runtime, once for each module: true for the first of the process. Any thread. */
    fun countRuntime(): Boolean = runtimes.getAndIncrement() == 0

    /**
     * `{ coldStart, splashLogo, arrangements }`. The logo is `{ width, height }` in dp from the app's resources, which
     * `npx apex-navigation splash` writes, and null with the library's 0dp. Any thread.
     */
    fun toMap(coldStart: Boolean, resources: Resources): WritableMap = Arguments.createMap().apply {
        putBoolean("coldStart", coldStart)
        putBoolean("arrangements", true)
        val density = resources.displayMetrics.density
        val width = resources.getDimension(R.dimen.apex_navigation_splash_logo_width) / density
        val height = resources.getDimension(R.dimen.apex_navigation_splash_logo_height) / density
        if (width > 0f && height > 0f) {
            putMap("splashLogo", Arguments.createMap().apply {
                putDouble("width", width.toDouble())
                putDouble("height", height.toDouble())
            })
        } else {
            putNull("splashLogo")
        }
    }

    @VisibleForTesting
    fun reset() = runtimes.set(0)
}

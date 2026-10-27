package dev.apexnative.navigation.react

import android.content.Context
import android.util.Log
import com.facebook.react.interfaces.fabric.ReactSurface
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlags
import com.facebook.react.modules.i18nmanager.I18nUtil
import com.facebook.react.runtime.ReactSurfaceImpl
import com.facebook.soloader.SoLoader
import dev.apexnative.navigation.utils.NavigationLog

/**
 * The height of React content laid out at a width without a height limit, as iOS measures a sheet's `'content'`
 * detent: Fabric lays out a copy of the tree, so the shown layout stays as it is. A React root's surface measures
 * through `SurfaceHandler::measure`. The native half is the library's own C++ (`android/src/main/jni`), in the codegen
 * library of its components; when that library doesn't load, nothing is measured. Main thread.
 */
internal object ContentHeights {
    private const val LIBRARY = "react_codegen_ApexNavigationSpec"

    private val loaded: Boolean by lazy {
        try {
            SoLoader.loadLibrary(LIBRARY)
            true
        } catch (e: Throwable) {
            Log.w(NavigationLog.TAG, "[ApexNavigation] $LIBRARY doesn't load: no content is measured", e)
            false
        }
    }

    /**
     * The height of [surface]'s content at [width] pixels, in pixels, with the layout context the surface lays out
     * with (`ReactSurfaceImpl.updateLayoutSpecs`); null before the surface runs or renders, or when it can't measure.
     */
    fun surfaceHeight(context: Context, surface: ReactSurface, width: Int): Int? {
        if (width <= 0 || surface !is ReactSurfaceImpl || !loaded) return null
        val density = context.resources.displayMetrics.density
        val scalesFont = ReactNativeFeatureFlags.enableFontScaleChangesUpdatingLayout()
        val fontScale = if (scalesFont) context.resources.configuration.fontScale else 1f
        val height = try {
            nativeSurfaceHeight(
                surface,
                width / density,
                density,
                fontScale,
                I18nUtil.instance.isRTL(context),
                I18nUtil.instance.doLeftAndRightSwapInRTL(context),
            )
        } catch (e: Throwable) {
            Log.w(NavigationLog.TAG, "[ApexNavigation] the content of a surface can't be measured", e)
            return null
        }
        return if (height > 0f) Math.round(height * density) else null
    }

    @JvmStatic
    private external fun nativeSurfaceHeight(
        surface: ReactSurfaceImpl,
        width: Float,
        pointScale: Float,
        fontScale: Float,
        rtl: Boolean,
        swapLeftAndRight: Boolean,
    ): Float
}

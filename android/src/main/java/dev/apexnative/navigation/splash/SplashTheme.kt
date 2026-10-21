package dev.apexnative.navigation.splash

import android.app.Activity
import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import dev.apexnative.navigation.R

/**
 * What a splash theme sets: the one `npx apex-navigation splash` writes, the main activity's theme in the manifest.
 * The app's theme, which the activity takes in onCreate, and the splash's background and logo, which the copy of the
 * splash draws. They are kept by resource id, so each read gives the current light or dark mode.
 */
internal class SplashTheme private constructor(
    /** The app's theme (a style resource); 0 when the theme names none. */
    val postSplashTheme: Int,
    // The background as a color resource, or as a color value; the logo as a drawable resource. 0 or null: none.
    private val backgroundRes: Int,
    private val backgroundValue: Int?,
    private val logoRes: Int,
) {
    /** Whether there is a picture to copy: a background or a logo. */
    val hasPicture: Boolean
        get() = backgroundRes != 0 || backgroundValue != null || logoRes != 0

    /** The background color in the current mode; null without one. */
    @ColorInt
    fun background(context: Context): Int? =
        if (backgroundRes != 0) ContextCompat.getColor(context, backgroundRes) else backgroundValue

    /** The logo on its 288 dp canvas in the current mode; null without one. */
    fun logo(context: Context): Drawable? = if (logoRes != 0) ContextCompat.getDrawable(context, logoRes) else null

    companion object {
        /**
         * What the activity's theme sets for the splash; null when it sets none of it. Read before the activity takes
         * the app's theme, which doesn't set them.
         */
        @JvmStatic
        fun read(activity: Activity): SplashTheme? {
            val attributes = activity.theme.obtainStyledAttributes(R.styleable.ApexNavigationSplash)
            return try {
                from(attributes)
            } finally {
                attributes.recycle()
            }
        }

        /**
         * The theme an activity runs with when the manifest gives it [manifestTheme]: the app's theme it names when it
         * is a splash theme, else [manifestTheme] itself.
         */
        @JvmStatic
        fun appTheme(context: Context, @StyleRes manifestTheme: Int): Int {
            if (manifestTheme == 0) return 0
            val names = intArrayOf(R.attr.apexNavigationPostSplashTheme)
            val attributes = context.obtainStyledAttributes(manifestTheme, names)
            return try {
                attributes.getResourceId(0, 0).takeIf { it != 0 } ?: manifestTheme
            } finally {
                attributes.recycle()
            }
        }

        @VisibleForTesting
        fun from(attributes: TypedArray): SplashTheme? {
            val theme = attributes.getResourceId(R.styleable.ApexNavigationSplash_apexNavigationPostSplashTheme, 0)
            val background = R.styleable.ApexNavigationSplash_apexNavigationSplashBackground
            val backgroundRes = attributes.getResourceId(background, 0)
            val backgroundValue =
                if (backgroundRes == 0 && attributes.hasValue(background)) attributes.getColor(background, 0) else null
            val logo = attributes.getResourceId(R.styleable.ApexNavigationSplash_apexNavigationSplashLogo, 0)
            val splash = SplashTheme(theme, backgroundRes, backgroundValue, logo)
            return splash.takeIf { it.postSplashTheme != 0 || it.hasPicture }
        }
    }
}

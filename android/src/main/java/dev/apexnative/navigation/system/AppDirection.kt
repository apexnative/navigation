package dev.apexnative.navigation.system

import android.content.Context
import android.text.TextUtils
import android.view.View
import com.facebook.react.modules.i18nmanager.I18nUtil
import dev.apexnative.navigation.options.Options
import java.util.Locale

/**
 * The direction of the app on Android: `layout.direction` of the default options — and of the root's own options at
 * `setRoot` — set on the activity's views and on React Native. Each activity's owner applies it
 * ([SystemUiOwner.applyDirection]) and remembers what it applied; this object keeps only the direction the last default
 * options brought, a value with no views, so that `setDefaultOptions` acts only when that direction changed. Without
 * the key nothing changes, and the bars and the tabs follow React Native's direction ([isReactNativeRtl]). Main thread
 * only.
 */
internal object AppDirection {
    private const val LTR = "ltr"
    private const val RTL = "rtl"
    private const val LOCALE = "locale"

    /** The direction of the last default options, to tell a change from the same value sent again. */
    class Defaults {
        private var last: String? = null

        /** New default options with [direction]: it when it differs from the previous defaults' and is set. */
        fun changed(direction: String?): String? {
            val changed = direction != last
            last = direction
            return direction.takeIf { changed }
        }
    }

    private val defaults = Defaults()

    /**
     * New default options came (`setDefaultOptions`): their `layout.direction` when it changed from the one the
     * previous defaults brought; none when it is the same or unset (a root's own direction stays then).
     */
    fun defaultsChanged(options: Options): String? = defaults.changed(options.layout.direction.get(null))

    /** The direction `setRoot` applies: the root's own `layout.direction` over the defaults'. */
    fun ofRoot(root: Options, defaults: Options): String? =
        root.layout.direction.get(null) ?: defaults.layout.direction.get(null)

    /**
     * Whether [direction] is right to left now: `rtl` yes, `ltr` no, `locale` as the device's language now
     * ([localeIsRtl]) — React Native's own check doesn't read the current language.
     */
    fun rtlNow(direction: String, localeIsRtl: Boolean): Boolean = when (direction) {
        RTL -> true
        LOCALE -> localeIsRtl
        else -> false
    }

    /** [direction] as a view takes it; none for a value that is no direction. */
    fun viewDirectionOf(direction: String): Int? = when (direction) {
        LTR -> View.LAYOUT_DIRECTION_LTR
        RTL -> View.LAYOUT_DIRECTION_RTL
        LOCALE -> View.LAYOUT_DIRECTION_LOCALE
        else -> null
    }

    /** The device's language now is written right to left. */
    fun isLocaleRtl(): Boolean =
        TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL

    /**
     * [viewDirection] (`View.LAYOUT_DIRECTION_*`) as a view resolves it, for a drawable, which resolves nothing itself:
     * right to left only where the app supports it ([rtlSupported], `android:supportsRtl`), `LOCALE` as the device's
     * language now ([localeIsRtl]), anything else left to right.
     */
    fun resolvedViewDirection(viewDirection: Int, localeIsRtl: Boolean, rtlSupported: Boolean): Int {
        val rtl = when (viewDirection) {
            View.LAYOUT_DIRECTION_RTL -> true
            View.LAYOUT_DIRECTION_LOCALE -> localeIsRtl
            else -> false
        }
        return if (rtl && rtlSupported) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    }

    /**
     * React Native's direction: right to left when forced, or allowed and the device's language is, with `supportsRtl`.
     * The bars and the tabs follow it without `layout.direction`.
     */
    fun isReactNativeRtl(context: Context): Boolean {
        // A context without an application (the unit tests' stubs) has no React Native settings to read.
        if (context.applicationInfo == null) return false
        return I18nUtil.instance.isRTL(context)
    }
}

package dev.apexnative.navigation.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.view.ContextThemeWrapper as AppCompatThemeWrapper
import com.google.android.material.R as MaterialR
import dev.apexnative.navigation.R
import dev.apexnative.navigation.splash.SplashTheme
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Where the navigation's Material 3 views and default colors come from: the app's theme when it is a Material 3 one
 * (`Theme.ApexNavigation` or the app's own), otherwise the navigation's neutral `Theme.ApexNavigation` over it, light or
 * dark with the app. Main thread only.
 */
internal object MaterialTheme {
    // A theme is costly to build and the top bar asks for its colors several times an update. The wrapper is held
    // weakly: it holds its base context, which a strong value would keep from ever going.
    private val wrappers = WeakHashMap<Context, WeakReference<Context>>()
    // Themes of the navigation's own views, each made once over the activity's with a style of its own.
    private val followers = WeakHashMap<Context, Int>()
    // The popup theme of the bar's "⋮" per activity: the app's theme doesn't change while the activity lives.
    private val popupThemes = WeakHashMap<Activity, Int>()

    fun context(context: Context): Context {
        if (isMaterial3(context)) return context
        wrappers[context]?.get()?.let { return it }
        return ContextThemeWrapper(context, R.style.Theme_ApexNavigation).also { wrappers[context] = WeakReference(it) }
    }

    /**
     * The popup theme of the top bar's "⋮", over the bar's theme at each opening of its popup, in the mode of that
     * moment. None when the app's theme follows light and dark: the menu takes the bar's own theme, which [rebase]
     * keeps in the current mode, with the app's colors. With a Material 3 theme fixed to light or to dark, a day/night
     * overlay that gives the menu the current mode's colors where the theme shows the other mode.
     */
    fun popupTheme(context: Context): Int {
        val activity = activityOf(context) ?: return 0
        popupThemes[activity]?.let { return it }
        return fixedPopupTheme(activity).also { popupThemes[activity] = it }
    }

    /**
     * A theme made once over the activity's, with [style] over it: [rebase] applies it again too. With no style (0), as
     * for Material's own theme overlays, only from Android 10 on, where a theme can be rebased as it is.
     */
    @JvmStatic
    fun follow(wrapper: Context, style: Int) {
        followers[wrapper] = style
    }

    /**
     * A Material view's own copy of the theme ([wrapper], the view's context), made with the overlay its style names
     * (`materialThemeOverlay`): followed with that overlay, so before Android 10 it is applied again too and the view
     * takes the new mode's colors; from Android 10 the copy is rebased as it is. A context without an overlay of its
     * own is followed as it is.
     */
    fun followOverlay(wrapper: Context) = follow(wrapper, (wrapper as? AppCompatThemeWrapper)?.themeResId ?: 0)

    /**
     * A light/dark switch the activity takes itself (`configChanges` with `uiMode`, the activity isn't recreated):
     * Android updates the resources in place but not the themes made from them, and every color read from those stays
     * the old mode's. The activity's theme and the navigation's own are applied again, before the controllers and the
     * views read their colors again.
     */
    @JvmStatic
    fun rebase(activity: Activity) {
        val activityTheme = activityTheme(activity)
        rebase(activity.theme, activityTheme)
        // A follower made over the navigation's own theme (the app's isn't Material 3) takes that theme again too, or
        // before Android 10 it keeps the old mode's colors of it.
        val neutral = if (isMaterial3(activity)) 0 else R.style.Theme_ApexNavigation
        followers.forEach { (wrapper, style) ->
            if (style != 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                rebase(wrapper.theme, activityTheme, neutral, style)
            }
        }
        wrappers.values.forEach { wrapper ->
            wrapper.get()?.let { rebase(it.theme, activityTheme, R.style.Theme_ApexNavigation) }
        }
    }

    // Before Android 10, which has Theme.rebase, the styles are applied again over the theme, as AppCompat does.
    private fun rebase(theme: Resources.Theme, vararg styles: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            theme.rebase()
        } else {
            styles.filter { it != 0 }.forEach { theme.applyStyle(it, true) }
        }
    }

    // The theme the activity runs with: the manifest's, or the app's theme that a splash theme there names, which the
    // activity took in onCreate.
    private fun activityTheme(activity: Activity): Int = try {
        SplashTheme.appTheme(activity, activity.packageManager.getActivityInfo(activity.componentName, 0).themeResource)
    } catch (e: PackageManager.NameNotFoundException) {
        0
    }

    // The overlay for the menu of an app's Material 3 theme that shows one mode in both; none for a theme that follows
    // the mode, as the navigation's own does, which an app's theme that isn't Material 3 gets.
    private fun fixedPopupTheme(activity: Activity): Int {
        val style = activityTheme(activity)
        if (style == 0 || !isMaterial3(activity)) return 0
        val light = isLight(activity.theme) ?: return 0
        val config = Configuration(activity.resources.configuration)
        val night = config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val other = if (night) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or other
        val otherTheme = activity.createConfigurationContext(config).resources.newTheme()
        otherTheme.applyStyle(style, true)
        if (isLight(otherTheme) != light) return 0
        return if (light) {
            R.style.ThemeOverlay_ApexNavigation_TopBar_PopupOverLight
        } else {
            R.style.ThemeOverlay_ApexNavigation_TopBar_PopupOverDark
        }
    }

    private fun isLight(theme: Resources.Theme): Boolean? {
        val value = TypedValue()
        if (!theme.resolveAttribute(AppCompatR.attr.isLightTheme, value, true)) return null
        return value.data != 0
    }

    private fun activityOf(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    // A role only Material 3 themes have.
    private fun isMaterial3(context: Context) =
        context.theme.resolveAttribute(MaterialR.attr.colorSurfaceContainerHigh, TypedValue(), true)
}

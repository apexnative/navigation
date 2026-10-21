package dev.apexnative.navigation.system

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.facebook.react.modules.i18nmanager.I18nUtil
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import dev.apexnative.navigation.NavigationActivity
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.utils.MaterialTheme
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.views.stack.topbar.TopBarDefaults

/**
 * The window of one activity as its system UI owner sees it ([SystemUiOwner]): what it reads (the theme's colors and
 * the manifest's orientation at creation, the night mode, the insets), what it writes (the bars' visibility, colors
 * and icons, the orientation, the direction), and the owner's frame post. An interface, so the owner runs in unit
 * tests with a fake; [of] gives the real one. Main thread only.
 */
internal interface SystemUiWindow {
    /** What the activity's creation gives, read once by [start]. */
    class Remembered(
        /** The window's status bar color before anything changes it: the theme's, transparent with edge to edge. */
        val statusBarColor: Int,
        /** The app theme's own `android:navigationBarColor`; none when the theme doesn't set it. */
        val navigationBarColor: Int?,
        /** The activity's orientation of the manifest. */
        val orientation: Int,
    )

    /** The Android version. */
    val sdk: Int

    /** The activity draws edge to edge. */
    val isEdgeToEdge: Boolean

    /**
     * Remembers what the theme and the manifest give, before the owner changes anything, and lets the bars a swipe
     * shows while they are hidden come back for a while.
     */
    fun start(): Remembered

    /** The bars' views join [content] over every layer; [barsChanged] runs at each change of the window's insets. */
    fun attach(content: ViewGroup, barsChanged: () -> Unit)

    /** The dark mode shows. */
    fun isNight(): Boolean

    /** The window's background when it is an opaque color. */
    fun windowBackground(): Int?

    /** The top bar's color without options. */
    fun topBarDefaultColor(): Int

    /** The tab bar's color without options. */
    fun tabsDefaultColor(): Int

    /** The color [colour] gives now, platform colors with the activity's theme; none for unset and `null`. */
    fun colourOf(colour: ThemeColour): Int?

    /** What the window's insets say of the navigation bar now; none before the window has insets. */
    fun bars(): SystemUiRules.Bars?

    /** Runs [task] once before the next frame draws. */
    fun post(task: Runnable)

    /** Takes [task] back if it hasn't run. */
    fun cancel(task: Runnable)

    fun setStatusBarShown(shown: Boolean)

    fun setStatusBarColor(color: Int)

    fun setStatusBarLightIcons(light: Boolean)

    fun setNavigationBarShown(shown: Boolean)

    fun setNavigationBarColor(color: Int)

    fun setNavigationBarLightButtons(light: Boolean)

    fun setOrientation(orientation: Int)

    /**
     * The window doesn't resize for the keyboard ([covers]); or, after it was made so, it takes back exactly the soft
     * input adjust it had then (the manifest's, or what the app set itself). Without edge to edge only; with it the
     * window never resizes.
     */
    fun setKeyboardCovers(covers: Boolean)

    /** The device's language is right to left now. */
    fun isLocaleRtl(): Boolean

    /** The activity's views take [layoutDirection] (`View.LAYOUT_DIRECTION_*`), React Native [rtl]. */
    fun setDirection(layoutDirection: Int, rtl: Boolean)

    /** The views and the listeners go; the window keeps what it shows. */
    fun release()

    companion object {
        /** The window of [activity]. */
        fun of(activity: NavigationActivity): SystemUiWindow = ActivityWindow(activity)
    }
}

// The real window: the activity's window, its insets controller, and the two views of the bars' backgrounds in the
// content (`statusBar.backgroundColor`). The views are plain, decorative and take no touch; they lie over every layer
// as the last children of the content, sized by the visible insets (0 while a bar hides).
private class ActivityWindow(private val activity: NavigationActivity) : SystemUiWindow {
    override val sdk: Int = Build.VERSION.SDK_INT
    override val isEdgeToEdge: Boolean = activity.isEdgeToEdge

    private var content: ViewGroup? = null
    private var barsChanged: () -> Unit = {}
    private var statusBarView: View? = null
    private var navigationBarView: View? = null
    // The visible insets the views take.
    private var statusBarTop = 0
    private var navigationBar = SystemUiRules.Edges(0, 0, 0, 0)
    // The window's adjust for the keyboard before it was made not to resize; none while it resizes as it had it.
    private var adjustBeforeCovering: Int? = null

    private val controller: WindowInsetsControllerCompat? by lazy {
        activity.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    // Without edge to edge the content may not get the window's insets: a change of the bars moves or resizes the
    // content the window fits between them. Other layouts of the window read nothing.
    private val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight,
            oldBottom ->
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) barsChanged()
    }

    // The status bar's color has its own view on Android 15 and later with edge to edge, where the window's does
    // nothing.
    private val statusBarHasView: Boolean
        get() = isEdgeToEdge && sdk >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    override fun start(): SystemUiWindow.Remembered {
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        return SystemUiWindow.Remembered(
            statusBarColor = windowStatusBarColor(),
            navigationBarColor = themeNavigationBarColor(),
            orientation = manifestOrientation(),
        )
    }

    override fun attach(content: ViewGroup, barsChanged: () -> Unit) {
        this.content = content
        this.barsChanged = barsChanged
        if (isEdgeToEdge) navigationBarView = barView(content)
        if (statusBarHasView) setWindowStatusBarColor(Color.TRANSPARENT)
        // The content gets the insets before any of its children, on every version; they go on as they are.
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            takeInsets(insets)
            barsChanged()
            insets
        }
        if (!isEdgeToEdge) content.addOnLayoutChangeListener(layoutListener)
    }

    override fun isNight(): Boolean = when (AppCompatDelegate.getDefaultNightMode()) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    override fun windowBackground(): Int? {
        val color = (activity.window?.peekDecorView()?.background as? ColorDrawable)?.color ?: return null
        return color.takeIf { Color.alpha(it) == OPAQUE }
    }

    override fun topBarDefaultColor(): Int = TopBarDefaults.backgroundColor(activity)

    // As the tab bar reads it: through the Material 3 theme the bar's container is made with.
    override fun tabsDefaultColor(): Int =
        MaterialColors.getColor(MaterialTheme.context(activity), MaterialR.attr.colorSurfaceContainer, Color.WHITE)

    override fun colourOf(colour: ThemeColour): Int? = colour.get(activity, null)

    override fun bars(): SystemUiRules.Bars? {
        val decor = activity.window?.peekDecorView() ?: return null
        val insets = ViewCompat.getRootWindowInsets(decor) ?: return null
        val navigation = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
        val tappable = insets.getInsets(WindowInsetsCompat.Type.tappableElement())
        return SystemUiRules.Bars(
            navigationBar = SystemUiRules.Edges(navigation.left, navigation.top, navigation.right, navigation.bottom),
            tappable = SystemUiRules.Edges(tappable.left, tappable.top, tappable.right, tappable.bottom),
            navigationBarShown = insets.isVisible(WindowInsetsCompat.Type.navigationBars()),
        )
    }

    override fun post(task: Runnable) {
        activity.window?.decorView?.postOnAnimation(task)
    }

    override fun cancel(task: Runnable) {
        activity.window?.peekDecorView()?.removeCallbacks(task)
    }

    override fun setStatusBarShown(shown: Boolean) {
        val controller = controller ?: return
        if (shown) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun setStatusBarColor(color: Int) {
        if (!statusBarHasView) return setWindowStatusBarColor(color)
        val content = content ?: return
        // The view is made at the first color that isn't transparent.
        val view = statusBarView ?: if (color == Color.TRANSPARENT) return else barView(content).also {
            statusBarView = it
            sizeViews()
        }
        view.setBackgroundColor(color)
    }

    override fun setStatusBarLightIcons(light: Boolean) {
        controller?.isAppearanceLightStatusBars = !light
    }

    override fun setNavigationBarShown(shown: Boolean) {
        val controller = controller ?: return
        if (shown) {
            controller.show(WindowInsetsCompat.Type.navigationBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    override fun setNavigationBarColor(color: Int) {
        val view = navigationBarView
        if (view == null) setWindowNavigationBarColor(color) else view.setBackgroundColor(color)
    }

    // A no-op on Android 7: the buttons stay light there.
    override fun setNavigationBarLightButtons(light: Boolean) {
        controller?.isAppearanceLightNavigationBars = !light
    }

    // Android 8.0 throws for a translucent activity that asks for an orientation; the app goes on without it.
    override fun setOrientation(orientation: Int) {
        try {
            activity.requestedOrientation = orientation
        } catch (e: IllegalStateException) {
            Log.w(NavigationLog.TAG, "[ApexNavigation] the activity can't take the orientation $orientation", e)
        }
    }

    // The adjust bits only: the state bits (whether the keyboard shows at start) stay as the window has them.
    override fun setKeyboardCovers(covers: Boolean) {
        if (isEdgeToEdge) return
        val window = activity.window ?: return
        val mode = window.attributes.softInputMode
        val adjust = if (covers) {
            if (adjustBeforeCovering == null) adjustBeforeCovering = mode and ADJUST_MASK
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        } else {
            // Nothing to give back when the window was never made not to resize.
            val before = adjustBeforeCovering ?: return
            adjustBeforeCovering = null
            before
        }
        if (mode and ADJUST_MASK == adjust) return
        window.setSoftInputMode((mode and ADJUST_MASK.inv()) or adjust)
    }

    override fun isLocaleRtl(): Boolean = AppDirection.isLocaleRtl()

    // On the content, which every view of the navigation is in: the window sets the decor view's own direction back
    // to the configuration's when it wasn't set before the window joined.
    override fun setDirection(layoutDirection: Int, rtl: Boolean) {
        activity.findViewById<View>(android.R.id.content)?.layoutDirection = layoutDirection
        I18nUtil.instance.allowRTL(activity, rtl)
        I18nUtil.instance.forceRTL(activity, rtl)
    }

    override fun release() {
        content?.let { content ->
            ViewCompat.setOnApplyWindowInsetsListener(content, null)
            content.removeOnLayoutChangeListener(layoutListener)
            statusBarView?.let(content::removeView)
            navigationBarView?.let(content::removeView)
        }
        content = null
        statusBarView = null
        navigationBarView = null
        barsChanged = {}
    }

    // A transparent view of no size at the end of the content; only an apply paints it, the insets size it.
    private fun barView(content: ViewGroup): View = View(activity).apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        content.addView(this, FrameLayout.LayoutParams(0, 0))
    }

    private fun takeInsets(insets: WindowInsetsCompat) {
        statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        navigationBar = SystemUiRules.Edges(navigation.left, navigation.top, navigation.right, navigation.bottom)
        sizeViews()
    }

    // The status bar's view at the top, the status bar's height; the navigation bar's at the bottom, or at the side
    // the bar is on in landscape.
    private fun sizeViews() {
        statusBarView?.let { size(it, MATCH, statusBarTop, Gravity.TOP) }
        val view = navigationBarView ?: return
        val bar = navigationBar
        when {
            bar.bottom > 0 -> size(view, MATCH, bar.bottom, Gravity.BOTTOM)
            bar.right > 0 -> size(view, bar.right, MATCH, Gravity.RIGHT)
            bar.left > 0 -> size(view, bar.left, MATCH, Gravity.LEFT)
            else -> size(view, 0, 0, Gravity.BOTTOM)
        }
    }

    private fun size(view: View, width: Int, height: Int, gravity: Int) {
        val params = view.layoutParams as? FrameLayout.LayoutParams
        if (params != null && params.width == width && params.height == height && params.gravity == gravity) return
        view.layoutParams = FrameLayout.LayoutParams(width, height, gravity)
    }

    // The window's status bar color is deprecated from Android 15, where edge to edge makes it do nothing; below it
    // (and without edge to edge) it is the only way to color the bar. It goes when the minimum SDK is 35.
    @Suppress("DEPRECATION")
    private fun windowStatusBarColor(): Int = activity.window?.statusBarColor ?: Color.TRANSPARENT

    // As windowStatusBarColor.
    @Suppress("DEPRECATION")
    private fun setWindowStatusBarColor(color: Int) {
        activity.window?.statusBarColor = color
    }

    // The window's navigation bar color is deprecated from Android 15 for the same reason; it colors the bar without
    // edge to edge. It goes when the minimum SDK is 35.
    @Suppress("DEPRECATION")
    private fun setWindowNavigationBarColor(color: Int) {
        activity.window?.navigationBarColor = color
    }

    // The theme's `android:navigationBarColor` when the app's theme sets it (a color of its own or a resource of the
    // app's package, the libraries' merged in); the themes of Android set it only with resources of the `android`
    // package. The attribute is deprecated from Android 15; it is read for the bar over the screens and goes when the
    // minimum SDK is 35.
    @Suppress("DEPRECATION")
    private fun themeNavigationBarColor(): Int? {
        val value = TypedValue()
        if (!activity.theme.resolveAttribute(android.R.attr.navigationBarColor, value, true)) return null
        if (value.type < TypedValue.TYPE_FIRST_COLOR_INT || value.type > TypedValue.TYPE_LAST_COLOR_INT) return null
        if (value.resourceId == 0) return value.data
        val packageName = try {
            activity.resources.getResourcePackageName(value.resourceId)
        } catch (e: android.content.res.Resources.NotFoundException) {
            return null
        }
        return value.data.takeIf { packageName != ANDROID_PACKAGE }
    }

    // The orientation the manifest declares for the activity; `requestedOrientation` gives the last request after a
    // recreation, so it is only the fallback.
    private fun manifestOrientation(): Int = try {
        activityInfo().screenOrientation
    } catch (e: PackageManager.NameNotFoundException) {
        activity.requestedOrientation
    }

    private fun activityInfo() = if (sdk >= Build.VERSION_CODES.TIRAMISU) {
        activity.packageManager.getActivityInfo(activity.componentName, PackageManager.ComponentInfoFlags.of(0))
    } else {
        legacyActivityInfo()
    }

    // Below Android 13 the flags are an int; that overload is deprecated from 13.
    @Suppress("DEPRECATION")
    private fun legacyActivityInfo() = activity.packageManager.getActivityInfo(activity.componentName, 0)

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val OPAQUE = 0xFF
        const val ANDROID_PACKAGE = "android"
        const val ADJUST_MASK = WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
    }
}

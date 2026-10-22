package dev.apexnative.navigation.viewcontrollers.component

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.system.SystemUiOwner
import dev.apexnative.navigation.viewcontrollers.externalcomponent.ExternalComponentViewController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The presenter of a screen, React or native: its background — the screen's view takes `layout.backgroundColor`, kept
 * out of the status bar's height where the status bar shows over it, and the React content takes
 * `layout.componentBackgroundColor` over it —, its room for the bars, and its safe area again on `mergeOptions`
 * (`mergeOptions` step 4). It applies as the screen appears, on `mergeOptions` of what it covers, and again with new
 * default options or after a light/dark switch from the resolved options it is given, which it never writes. Each
 * appearance tells the activity's system UI owner, which follows the screen on top. Main thread only.
 */
open class ComponentPresenter(activity: Activity) : Presenter(activity, DefaultOptions.current) {
    override fun applyOptions(view: ViewController<*>, options: Options) {
        frameOf(view)?.applyRoom()
        applyBackground(view, options)
        SystemUiOwner.of(activity)?.invalidate()
    }

    override fun mergeOptions(viewController: ViewController<*>, options: Options) {
        val statusBar = options.statusBar.visible.hasValue() || options.statusBar.drawBehind.hasValue()
        if (statusBar) frameOf(viewController)?.applyRoom()
        if (statusBar || options.layout.backgroundColor.hasValue() ||
            options.layout.componentBackgroundColor.hasValue()
        ) {
            applyBackground(viewController, viewController.resolveCurrentOptions(defaultOptions))
        }
        // The keyboard's room again, with the keyboard open too; the padding over or under the navigation bar again
        // (`navigationBar.drawBehind`).
        val navigationBar = options.navigationBar.let { it.drawBehind.hasValue() || it.backgroundColor.hasValue() }
        if (options.layout.adjustResize.hasValue() || navigationBar) frameOf(viewController)?.applyInsetsAgain()
        // The edges, the large title and the bars' hiding may have changed.
        (viewController as? ComponentViewController)?.updateSafeArea()
    }

    override fun onConfigurationChanged(controller: ViewController<*>, options: Options) =
        applyBackground(controller, options)

    /**
     * The colors of [screen] from its resolved [options]: `layout.backgroundColor` on the screen's view, none without
     * it (the window shows); `layout.componentBackgroundColor` on its React content.
     */
    fun applyBackground(screen: ViewController<*>, options: Options) {
        val view = screen.peekView() ?: return
        view.background = screenBackground(screen, options)
        contentOf(screen)?.background = colorOf(options.layout.componentBackgroundColor)?.let(::ColorDrawable)
    }

    private fun screenBackground(screen: ViewController<*>, options: Options): Drawable? {
        val color = colorOf(options.layout.backgroundColor) ?: return null
        val frame = frameOf(screen) ?: return ColorDrawable(color)
        val band = ScreenSafeArea.backgroundBand(
            statusBarHeight = frame.statusBarHeight,
            hiddenOrDrawBehind = options.statusBar.isHiddenOrDrawBehind(screen.isInOverlay),
            inSideMenu = screen.isInSideMenu,
            inSheet = screen.isInSheet,
            inPair = frame.isInPair,
            topRoom = frame.topRoom(),
        )
        if (band <= 0) return ColorDrawable(color)
        // A layer inset reports no padding (an InsetDrawable does, and the view would take it in place of its own:
        // the side bars, the navigation bar and the keyboard).
        return LayerDrawable(arrayOf<Drawable>(ColorDrawable(color))).apply { setLayerInset(0, 0, band, 0, 0) }
    }

    // A color the options give, with the activity's theme for platform colors; none without one.
    private fun colorOf(colour: ThemeColour): Int? =
        if (colour.hasValue()) colour.get(activity, Color.TRANSPARENT) ?: Color.TRANSPARENT else null

    private fun frameOf(screen: ViewController<*>): ScreenFrame? = when (screen) {
        is ComponentViewController -> screen.frame
        is ExternalComponentViewController -> screen.frame
        else -> null
    }

    // The view under the React content: the React root, or the container of a slot screen. A native screen has none.
    private fun contentOf(screen: ViewController<*>): View? = (screen as? ComponentViewController)?.content?.asView()
}

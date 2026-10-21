package dev.apexnative.navigation.viewcontrollers.viewcontroller

import android.app.Activity
import android.graphics.Color
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options

/**
 * Applies a layout's resolved options to the layout's own view (its background, its bars). Each layout has one. The
 * system UI of the activity (the status bar, the navigation bar, the orientation) is its owner's (`SystemUiOwner`),
 * which a presenter only tells that something changed. The options it gets are read-only (the node keeps them,
 * `ViewController.resolveCurrentOptions`): code that changes them copies them first.
 */
open class Presenter(
    @JvmField protected val activity: Activity,
    // Kept for the modules that pass it: the defaults are the app's one tree, read from DefaultOptions.
    @Suppress("UNUSED_PARAMETER") defaultOptions: Options,
) {
    /**
     * The default options of the app (`setDefaultOptions`), which every layout's options take what they lack from:
     * always the current ones ([DefaultOptions.current]).
     */
    val defaultOptions: Options
        get() = DefaultOptions.current

    /** The layout shows with [options], resolved: everything they give applies. */
    open fun applyOptions(view: ViewController<*>, options: Options) = Unit

    /** `mergeOptions` of the layout: what [options] give applies over what shows. */
    open fun mergeOptions(viewController: ViewController<*>, options: Options) = Unit

    /** The layout comes to the front again (a pop, a dismissed modal): what it set for the activity applies again. */
    open fun onViewBroughtToFront(viewController: ViewController<*>, options: Options) = Unit

    /**
     * Light or dark changed (the activity takes `uiMode` itself), or new default options came (`setDefaultOptions`):
     * what depends on colors and looks applies again with [options], resolved with the new defaults
     * (`Navigator.repaint`).
     */
    open fun onConfigurationChanged(controller: ViewController<*>, options: Options) = Unit

    /**
     * The background of the layout's view: `layout.backgroundColor` of [options], platform colors with the activity's
     * theme; `'NoColor'` (or a side the options don't give) is no background.
     */
    fun applyBackgroundColor(view: ViewController<*>, options: Options) {
        val color = options.layout.backgroundColor
        if (!color.hasValue()) return
        view.peekView()?.setBackgroundColor(color.get(activity, Color.TRANSPARENT) ?: Color.TRANSPARENT)
    }
}

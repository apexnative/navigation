package dev.apexnative.navigation.viewcontrollers.arrangement

import android.app.Activity
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.viewcontrollers.component.ComponentPresenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The presenter of the secondary screen of a pair ([ArrangementController]). The status bar, the navigation bar and
 * the orientation are the primary's, as the top bar is: the secondary applies only its background (the theme's
 * without `layout.backgroundColor`, as any React screen), and brought to the front it gives them back to the primary.
 */
internal class SecondaryScreenPresenter(activity: Activity, defaultOptions: Options) :
    Presenter(activity, defaultOptions) {

    override fun applyOptions(view: ViewController<*>, options: Options) =
        applyBackground(view, options.copy().withDefaultOptions(defaultOptions))

    override fun mergeOptions(viewController: ViewController<*>, options: Options) {
        if (!options.layout.backgroundColor.hasValue()) return
        val merged = viewController.resolveCurrentOptions().copy().mergeWith(options)
        applyBackground(viewController, merged.withDefaultOptions(defaultOptions))
    }

    override fun onViewBroughtToFront(viewController: ViewController<*>, options: Options) {
        (viewController.parentController as? ArrangementController)?.primary?.onViewBroughtToFront()
    }

    override fun onConfigurationChanged(controller: ViewController<*>, options: Options) =
        applyBackground(controller, options.copy().withDefaultOptions(defaultOptions))

    // `layout.backgroundColor`, else the theme's background (`ComponentPresenter.themeBackgroundOf`).
    private fun applyBackground(view: ViewController<*>, options: Options) {
        if (options.layout.backgroundColor.hasValue()) return applyBackgroundColor(view, options)
        val color = ComponentPresenter.themeBackgroundOf(activity) ?: return
        view.peekView()?.setBackgroundColor(color)
    }
}

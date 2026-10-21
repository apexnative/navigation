package dev.apexnative.navigation.viewcontrollers.arrangement

import android.app.Activity
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The presenter of the secondary screen of a pair ([ArrangementController]). The status bar, the navigation bar and
 * the orientation are the primary's, as the top bar is: the secondary applies only its background, and brought to the
 * front it gives them back to the primary.
 */
internal class SecondaryScreenPresenter(activity: Activity, defaultOptions: Options) :
    Presenter(activity, defaultOptions) {

    override fun applyOptions(view: ViewController<*>, options: Options) =
        applyBackgroundColor(view, options.copy().withDefaultOptions(defaultOptions))

    override fun mergeOptions(viewController: ViewController<*>, options: Options) {
        if (!options.layout.backgroundColor.hasValue()) return
        val merged = viewController.resolveCurrentOptions().copy().mergeWith(options)
        applyBackgroundColor(viewController, merged.withDefaultOptions(defaultOptions))
    }

    override fun onViewBroughtToFront(viewController: ViewController<*>, options: Options) {
        (viewController.parentController as? ArrangementController)?.primary?.onViewBroughtToFront()
    }

    override fun onConfigurationChanged(controller: ViewController<*>, options: Options) =
        applyBackgroundColor(controller, options.copy().withDefaultOptions(defaultOptions))
}

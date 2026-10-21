package dev.apexnative.navigation.viewcontrollers.stack

import android.app.Activity
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.viewcontrollers.parent.ContainerBackground
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The presenter of a stack: what the stack shows of its screen on top — the bar, the FAB, the room of the bar — takes a
 * `mergeOptions` with the stack's id, and the colors of a light/dark switch or new default options. The stack keeps the
 * views; the presenter tells it what changed. Main thread only.
 */
internal class StackPresenter(activity: Activity) : Presenter(activity, Options()) {
    override fun mergeOptions(viewController: ViewController<*>, options: Options) {
        (viewController as? StackController)?.applyTopMerge(options)
    }

    // The bar (its colors, title, buttons, search) and the FAB take the new mode, the stack's view its background.
    override fun onConfigurationChanged(controller: ViewController<*>, options: Options) {
        ContainerBackground.apply(controller, options)
        (controller as? StackController)?.repaintTop()
    }
}

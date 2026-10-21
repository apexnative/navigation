package dev.apexnative.navigation.viewcontrollers.splitview

import android.app.Activity
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.SplitViewOptions
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The presenter of a split view. The split view module applies its own options; what the core gives it here is the
 * repaint after `setDefaultOptions` (`Navigator.repaint`), which reaches a node only through its presenter: the split
 * view's own `splitView` with the new defaults goes to [onSplitOptions], which the layout factory binds to the module
 * as it makes the split view. The split view's own options, not those of its current child: they don't change as the
 * panes move. Main thread only.
 */
internal class SplitViewPresenter(activity: Activity) : Presenter(activity, DefaultOptions.current) {
    /** Takes the split view's resolved `splitView` as Android reads it. */
    var onSplitOptions: ((SplitViewOptions) -> Unit)? = null

    override fun onConfigurationChanged(controller: ViewController<*>, options: Options) {
        val split = controller.resolveCurrentOptions(defaultOptions).splitView.toSplitViewOptions()
        onSplitOptions?.invoke(split)
    }
}

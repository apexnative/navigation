package dev.apexnative.navigation.viewcontrollers.child

import android.app.Activity
import android.view.ViewGroup
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.viewcontrollers.navigator.Navigators
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * A node that can be a child of a layout and is found by its id: it is in the activity's registry from its creation
 * to its destruction, and applies its resolved options through its [presenter].
 */
abstract class ChildController<V : ViewGroup>(
    activity: Activity,
    private val childRegistry: ChildControllersRegistry,
    id: String,
    /** Applies the node's resolved options to the activity and to its view. */
    val presenter: Presenter,
    initialOptions: Options,
) : ViewController<V>(activity, id, initialOptions) {
    init {
        childRegistry.onChildCreated(this)
    }

    /**
     * Another node has this node's id now, in this activity's registry or in another navigation activity's: a later
     * layout made with the same ids (a `setRoot` builds the new root before the old one goes; a new activity sets the
     * last root again). What is kept by id belongs to that node then.
     */
    protected val isIdTakenByAnother: Boolean
        get() = isOther(childRegistry.find(id)) ||
            Navigators.all().any { isOther(it.navigator?.registry?.find(id)) }

    private fun isOther(node: ViewController<*>?): Boolean = node != null && node !== this

    /** `mergeOptions` while the node's view shows: its presenter applies what [options] give. */
    override fun applyMergedOptions(options: Options) = presenter.mergeOptions(this, options)

    override fun destroy() {
        if (isDestroyed) return
        super.destroy()
        childRegistry.onChildDestroyed(this)
    }
}

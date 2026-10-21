package dev.apexnative.navigation.viewcontrollers.viewcontroller

import android.app.Activity
import android.widget.FrameLayout
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.viewcontrollers.child.ChildController
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.parent.ParentController

// Nodes for the unit tests: Android's classes are the stubs of the unit tests, which answer with defaults, so a node
// is made without a device; its view is never made.

/** What a presenter was asked, in order: `merge:<id>`, `repaint:<id>`. */
class Calls {
    val entries = mutableListOf<String>()
    val options = mutableListOf<Options>()
}

class RecordingPresenter(activity: Activity, private val calls: Calls) : Presenter(activity, Options()) {
    override fun mergeOptions(viewController: ViewController<*>, options: Options) {
        calls.entries += "merge:${viewController.id}"
    }

    override fun onConfigurationChanged(controller: ViewController<*>, options: Options) {
        calls.entries += "repaint:${controller.id}"
        calls.options += options
    }
}

/** A screen; [shown] stands for its view being shown. */
class TestScreen(
    activity: Activity,
    registry: ChildControllersRegistry,
    id: String,
    options: Options = Options(),
    presenter: Presenter = Presenter(activity, Options()),
) : ChildController<FrameLayout>(activity, registry, id, presenter, options) {
    var shown = false

    override val isViewShown: Boolean
        get() = shown

    override fun createView(): FrameLayout = FrameLayout(activity)
}

/** A layout of nodes whose current child is [current]; it records the merges of its children into [calls]. */
open class TestLayout(
    activity: Activity,
    registry: ChildControllersRegistry,
    id: String,
    options: Options = Options(),
    presenter: Presenter = Presenter(activity, Options()),
    private val calls: Calls? = null,
) : ParentController<FrameLayout>(activity, registry, id, presenter, options) {
    val children = mutableListOf<ViewController<*>>()
    var current: ViewController<*>? = null

    fun add(child: ViewController<*>) {
        children += child
        child.setParentController(this)
        current = child
    }

    override fun getChildControllers(): Collection<ViewController<*>> = children

    override fun getCurrentChild(): ViewController<*>? = current

    override fun mergeChildOptions(options: Options, child: ViewController<*>) {
        calls?.entries?.add("mergeChild:$id<-${child.id}")
        performOnParentController { it.mergeChildOptions(options, this) }
    }

    override fun createView(): FrameLayout = FrameLayout(activity)
}

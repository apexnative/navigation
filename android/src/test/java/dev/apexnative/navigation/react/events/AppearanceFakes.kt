package dev.apexnative.navigation.react.events

import android.app.Activity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.viewcontrollers.child.ChildController
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.viewcontroller.IReactView
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener

/**
 * What the dispatcher sent and asked, in order, into [entries]: `will:<id>`, `didAppear:<id>`, `didDisappear:<id>`,
 * `popped:<id>`, `modalDismissed:<id>:<name>:<count>`, `released:<id>`, `slotDestroyed:<id>`,
 * `pressed:<id>:<buttonId>`, `tabPressed:<tabsId>:<index>:<screenId>:<wasSelected>`,
 * `tabSelected:<tabsId>:<selected>:<unselected>`, `tabLongPressed:<tabsId>:<index>`, `attempted:<id>`,
 * `detent:<id>:<detent>`, `stateNeeded`, `stateNow`. The
 * appearance events also go into [payloads] as
 * `<event>:<id>:<name>:<type>`. The posted update and its hold are the core's own ([NavigationState.Updates]):
 * `stateNeeded` is the posted update as it runs, at once unless [defersPosts]; while held it waits for the last
 * release. `stateNow` is the state the dispatcher sends itself (a user's transition); a command's goes through its
 * answer.
 */
internal class EventJournal(
    val entries: MutableList<String> = mutableListOf(),
) : AppearanceDispatcher.Sender, AppearanceDispatcher.StateUpdates {
    val payloads = mutableListOf<String>()
    private val posted = mutableListOf<() -> Unit>()
    private val held = NavigationState.Updates(post = { task -> if (defersPosts) posted += task else task() }) {
        entries += "stateNeeded"
    }

    /** The posted update waits for [runPosted], as the main loop's next pass, instead of running at once. */
    var defersPosts = false

    /** Runs the updates posted so far, as the next pass of the main loop. */
    fun runPosted() {
        val tasks = posted.toList()
        posted.clear()
        tasks.forEach { it() }
    }

    /** The holds of the state not released yet. */
    val holds: Int
        get() = held.count

    private fun appearance(event: String, id: String, name: String, type: ComponentType) {
        entries += "$event:$id"
        payloads += "$event:$id:$name:${type.jsName}"
    }

    override fun componentWillAppear(componentId: String, componentName: String, type: ComponentType) =
        appearance("will", componentId, componentName, type)

    override fun componentDidAppear(componentId: String, componentName: String, type: ComponentType) =
        appearance("didAppear", componentId, componentName, type)

    override fun componentDidDisappear(componentId: String, componentName: String, type: ComponentType) =
        appearance("didDisappear", componentId, componentName, type)

    override fun screenPopped(componentId: String) {
        entries += "popped:$componentId"
    }

    override fun modalDismissed(componentId: String, componentName: String, modalsDismissed: Int) {
        entries += "modalDismissed:$componentId:$componentName:$modalsDismissed"
    }

    override fun nativeScreenReleased(componentId: String) {
        entries += "released:$componentId"
    }

    override fun slotDestroyed(componentId: String) {
        entries += "slotDestroyed:$componentId"
    }

    override fun navigationButtonPressed(componentId: String, buttonId: String) {
        entries += "pressed:$componentId:$buttonId"
    }

    override fun bottomTabPressed(tabIndex: Int, bottomTabsId: String, screenId: String?, wasSelected: Boolean) {
        entries += "tabPressed:$bottomTabsId:$tabIndex:$screenId:$wasSelected"
    }

    override fun bottomTabSelected(selectedTabIndex: Int, unselectedTabIndex: Int, bottomTabsId: String) {
        entries += "tabSelected:$bottomTabsId:$selectedTabIndex:$unselectedTabIndex"
    }

    override fun bottomTabLongPressed(selectedTabIndex: Int, bottomTabsId: String) {
        entries += "tabLongPressed:$bottomTabsId:$selectedTabIndex"
    }

    override fun modalAttemptedToDismiss(componentId: String) {
        entries += "attempted:$componentId"
    }

    override fun sheetDetentChanged(componentId: String, detent: Any) {
        entries += "detent:$componentId:$detent"
    }

    override fun setNeedsUpdate() = held.setNeedsUpdate()

    override fun updateNow() {
        entries += "stateNow"
    }

    override fun hold() = held.hold()

    override fun release() = held.release()
}

/** A clock of the main thread's delayed tasks that moves only when [advance] says so. */
internal class FakeScheduler : AppearanceDispatcher.Scheduler {
    private class Task(val at: Long, val run: () -> Unit)

    private val tasks = mutableListOf<Task>()
    var now = 0L
        private set

    /** The tasks waiting to run. */
    val pending: Int
        get() = tasks.size

    override fun schedule(delayMillis: Long, task: () -> Unit): () -> Unit {
        val entry = Task(now + delayMillis, task)
        tasks += entry
        return { tasks.remove(entry) }
    }

    /** Moves the clock by [millis] and runs the tasks that are due, in their order. */
    fun advance(millis: Long) {
        now += millis
        val due = tasks.filter { it.at <= now }.sortedBy { it.at }
        due.forEach {
            tasks.remove(it)
            it.run()
        }
    }
}

/** Puts the dispatcher on a journal and a fake clock, and back on its own afterwards. */
internal class DispatcherFixture(val journal: EventJournal = EventJournal()) {
    val scheduler = FakeScheduler()
    private val sender = AppearanceDispatcher.sender
    private val state = AppearanceDispatcher.state
    private val clock = AppearanceDispatcher.scheduler
    private val log = AppearanceDispatcher.log

    fun install() {
        AppearanceDispatcher.sender = journal
        AppearanceDispatcher.state = journal
        AppearanceDispatcher.scheduler = scheduler
    }

    fun uninstall() {
        AppearanceDispatcher.sender = sender
        AppearanceDispatcher.state = state
        AppearanceDispatcher.scheduler = clock
        AppearanceDispatcher.log = log
    }
}

/** Content that mounts when [mount] says so; ready at once with [ready]. */
internal class FakeContent(private var ready: Boolean = false) : IReactView {
    private val waiting = mutableListOf<Runnable>()

    fun mount() {
        ready = true
        val listeners = waiting.toList()
        waiting.clear()
        listeners.forEach { it.run() }
    }

    override fun asView(): ViewGroup = throw UnsupportedOperationException()

    override fun isReady(): Boolean = ready

    override fun isRendered(): Boolean = ready

    override fun destroy() = Unit

    override fun sendOnNavigationButtonPressed(buttonId: String) = Unit

    override fun getScrollEventListener(): ScrollEventListener? = null

    override fun dispatchTouchEventToJs(event: MotionEvent) = Unit

    override fun whenReady(listener: Runnable) {
        if (ready) listener.run() else waiting += listener
    }
}

/**
 * A screen node that reports its appearance through its own record, ready at once, as a native screen does; the
 * view is never made.
 */
internal class ReportingScreen(
    activity: Activity,
    registry: ChildControllersRegistry,
    id: String,
    name: String = id,
) : ChildController<FrameLayout>(activity, registry, id, Presenter(activity, Options()), Options()),
    AppearanceDispatcher.RecordOwner {
    val appearance = ScreenAppearance(id, name, ComponentType.Component, null)

    override val appearanceRecord: ScreenAppearance
        get() = appearance

    override val currentComponentName: String = name

    override fun createView(): FrameLayout = FrameLayout(activity)

    override fun onViewWillAppear() = AppearanceDispatcher.willAppear(appearance)

    override fun onViewDidAppear() {
        AppearanceDispatcher.didAppear(appearance)
        super.onViewDidAppear()
    }

    override fun onViewDisappear() = AppearanceDispatcher.didDisappear(appearance)
}

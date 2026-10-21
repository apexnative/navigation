package dev.apexnative.navigation.viewcontrollers.stack

import android.app.Activity
import dev.apexnative.navigation.layout.LayoutNode
import dev.apexnative.navigation.layout.LayoutType
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.FakeEvents
import dev.apexnative.navigation.react.Journal
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.react.events.FakeContent
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.navigator.LayoutFactory
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.Assert.assertEquals

/**
 * A stack `Stack1` over the fakes (`StackFakes.kt`), with the real dispatcher of appearance events on a fake clock,
 * the real answers of commands and the real commands of the stack. Everything that reached JavaScript or happened to a
 * node goes into [journal], in order; the views into [views]; what the nodes were told into [calls].
 */
internal class StackQueueHarness(realHost: Boolean = false) {
    private val record = Journal()
    val journal: MutableList<String> = record.entries
    val views = mutableListOf<String>()
    val calls = mutableListOf<String>()
    val fixture = DispatcherFixture(EventJournal(journal))
    val scheduler = fixture.scheduler
    val activity = Activity()
    val registry = ChildControllersRegistry()
    val display = FakeStackDisplay(views, scheduler)
    val host = QueueHost(journal)
    val prepared = PreparedPushes()
    val readiness = QueueReadiness(journal)
    val stack = StackController(activity, registry, STACK, StackPresenter(activity), Options()).also {
        it.useQueueParts(display, readiness, if (realHost) null else host)
        host.answers = it::answerIdOf
        it.preparedPushes = { prepared }
    }
    val commands = StackCommands(registry, LayoutFactory(activity, registry), prepared)
    val queue: StackQueue
        get() = stack.queue
    private var count = 0

    fun install() = fixture.install()

    fun uninstall() {
        fixture.uninstall()
        StackSwitches.preparesOwnRootPushes = false
        // What the React screens of a test told of their readiness holds for no other test.
        ScreenReadiness.reset()
    }

    // Screens.

    fun screen(id: String, ready: Boolean = true, options: Options = Options(), slot: Boolean = false) =
        QueueScreen(activity, registry, id, journal, calls, options, FakeContent(ready), slot)

    /** A screen whose push waits for its content (`animations.push.waitForRender: true`), not rendered yet. */
    fun waiting(id: String, ready: Boolean = false) = screen(id, ready, Options().apply {
        animations.push.waitForRender = Bool(true)
    })

    fun options(change: Options.() -> Unit): Options = Options().apply(change)

    /** The stack shows [screens]; the last has appeared, its view is in the container. Then everything is cleared. */
    fun start(vararg screens: QueueScreen, keys: List<String?> = emptyList()) {
        stack.setInitialChildren(screens.toList(), keys)
        val top = screens.last()
        display.raise(top)
        top.onViewWillAppear()
        top.onViewDidAppear()
        clear()
    }

    fun clear() {
        journal.clear()
        views.clear()
        calls.clear()
        display.starts.clear()
    }

    // What reached JavaScript, without the requests of the navigation state of each pass (one update per pass).
    fun events(): List<String> = journal.filter { it != "stateNeeded" }

    fun assertEvents(vararg expected: String) {
        assertEquals(expected.toList(), events())
        journal.clear()
    }

    fun assertViews(vararg expected: String) {
        assertEquals(expected.toList(), views.toList())
        views.clear()
    }

    // Commands: pop, popTo and popToRoot through the stack's commands; push and setStackRoot of the test's own screens
    // as the commands put them into the queue (the commands make their screens from layouts).

    fun answer(name: String): CommandAnswer {
        val tag = "$name${++count}"
        return CommandAnswer(name, tag, TaggedPromise(tag, journal), ReloadCoordinator.generation, FakeEvents(record))
    }

    fun push(node: ViewController<*>, from: String = STACK, key: String? = null, prepared: Boolean = false) {
        stack.join(node, first = false, key = key)
        queue.push(node, answer("push"), from, "push the screen '${node.id}'", prepared)
    }

    /** `pop` from [from] (the top for the stack's id): it and every screen over it. */
    fun pop(from: ViewController<*>? = null) {
        val componentId = from?.id ?: STACK
        commands.pop(componentId, answer("pop"), "pop the screen '$componentId'")
    }

    fun popTo(target: ViewController<*>) =
        commands.popTo(target.id, answer("popTo"), "pop to the screen '${target.id}'")

    fun popToRoot() = commands.popToRoot(STACK, answer("popToRoot"), "pop the stack of the screen '$STACK'")

    fun setStackRoot(vararg nodes: ViewController<*>, animated: Boolean = true, asPush: Boolean = false) {
        nodes.forEachIndexed { index, node -> stack.join(node, first = index == 0, key = null) }
        val what = "set the stack of the screen '$STACK'"
        queue.replace(nodes.toList(), animated, asPush, answer("setStackRoot"), null, STACK, what)
    }

    /** The split view's `setRoot`, its listener's answers into [journal]. */
    fun splitSetRoot(vararg nodes: ViewController<*>) {
        stack.setRoot(nodes.toList(), false, object : CommandListener {
            override fun onSuccess(childId: String) {
                journal += "listener success $childId"
            }

            override fun onError(message: String) {
                journal += "listener error $message"
            }
        })
    }

    /** System back, after JavaScript didn't take it. */
    fun back(): Boolean = stack.handleBack(object : CommandListener {
        override fun onSuccess(childId: String) = Unit

        override fun onError(message: String) = Unit
    })

    /** The layout of a screen as a command brings it. */
    fun layout(id: String, key: String? = null, name: String = "Details", type: LayoutType = LayoutType.Component) =
        LayoutNode(
            id = id,
            type = type,
            name = name,
            options = Options(),
            key = key,
            passProps = null,
            functions = emptyList(),
            columns = emptyList(),
            arrangement = null,
            children = emptyList(),
        )

    /** The ids of the entries and of what shows. */
    fun entries(): List<String> = queue.entries.map { it.id }

    fun shown(): List<String> = queue.shown.map { it.id }

    companion object {
        const val STACK = "Stack1"
    }
}

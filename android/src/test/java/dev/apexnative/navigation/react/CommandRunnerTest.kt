package dev.apexnative.navigation.react

import dev.apexnative.navigation.layout.LayoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRunnerTest {
    private val journal = Journal()
    private val tasks = ArrayDeque<Runnable>()
    private val logged = mutableListOf<String>()
    private var hasActivity = true
    private val runner = CommandRunner(
        post = { tasks.addLast(it) },
        activityPresent = { hasActivity },
        events = FakeEvents(journal),
        log = { logged += it },
    )

    // The main thread: runs what was posted, in order.
    private fun runMain() {
        while (tasks.isNotEmpty()) tasks.removeFirst().run()
    }

    @Test
    fun checkFailureRejectsAtOnceAndQueuesNothing() {
        runner.run("push", "push1", "push the screen 'Details'", FakePromise(journal), check = {
            throw LayoutException("the stack 'Stack1' has no screens")
        }) { _: Unit, answer -> answer.resolve("Details") }
        assertEquals(listOf("reject:the stack 'Stack1' has no screens"), journal.entries)
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun exceptionWhileReadingRejectsWithTheGenericText() {
        runner.run("push", "push1", "push the screen 'Details'", FakePromise(journal), check = {
            throw IllegalStateException("the map broke")
        }) { _: Unit, answer -> answer.resolve("Details") }
        assertEquals(listOf("reject:could not push the screen 'Details': the map broke"), journal.entries)
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun bodyRunsOnMainWithTheReadLayout() {
        runner.run(
            name = "push",
            commandId = "push1",
            what = "push the screen 'Details'",
            promise = FakePromise(journal),
            check = { "Details" },
        ) { layout, answer -> answer.resolve(layout) }
        assertTrue(journal.entries.isEmpty())
        runMain()
        assertEquals(listOf("state", "resolve:Details", "completed:push"), journal.entries)
    }

    @Test
    fun commandsRunInCallOrderOneTaskEach() {
        listOf("A", "B", "C").forEach { id ->
            runner.run("pop", "pop$id", "pop the screen '$id'", FakePromise(journal), check = {}) { _, answer ->
                answer.resolve(id)
            }
        }
        assertEquals(3, tasks.size)
        runMain()
        assertEquals(listOf("resolve:A", "resolve:B", "resolve:C"), journal.entries.filter { it.startsWith("resolve") })
    }

    @Test
    fun exceptionInTheBodyRejects() {
        runner.run("pop", "pop1", "pop the screen 'Home'", FakePromise(journal), check = {}) { _, _ ->
            throw IllegalArgumentException("no view")
        }
        runMain()
        assertEquals(listOf("reject:could not pop the screen 'Home': no view"), journal.entries)
    }

    @Test
    fun aLayoutThatCantBeMadeInTheBodyRejectsWithItsText() {
        val scopes = mutableListOf<Boolean>()
        runner.run("setRoot", "setRoot1", "show the root 'Profile1'", FakePromise(journal), check = {}) { _, _ ->
            scopes += CommandScope.isRunning
            throw LayoutException(ErrorTexts.nativeScreenNotCreated("Profile"))
        }
        runMain()
        assertEquals(listOf("reject:${ErrorTexts.nativeScreenNotCreated("Profile")}"), journal.entries)
        assertEquals(listOf(true), scopes)
        assertFalse(CommandScope.isRunning)
    }

    @Test
    fun exceptionWithoutMessageGivesItsClass() {
        runner.run("pop", "pop1", "pop the screen 'Home'", FakePromise(journal), check = {}) { _, _ ->
            throw IllegalStateException()
        }
        runMain()
        assertEquals(listOf("reject:could not pop the screen 'Home': IllegalStateException"), journal.entries)
    }

    @Test
    fun nowhereToShow() {
        hasActivity = false
        runner.run("showModal", "showModal1", "show the screen 'Login'", FakePromise(journal), check = {}) { _, a ->
            a.resolve("Login")
        }
        runMain()
        assertEquals(
            listOf("reject:the navigation has nowhere to show screens: the app has no activity or window"),
            journal.entries,
        )
    }

    @Test
    fun commandsThatDontNeedAnActivity() {
        hasActivity = false
        runner.run(
            "getLaunchLink",
            null,
            "read the launch link",
            FakePromise(journal),
            needsActivity = false,
            check = {},
        ) { _, answer -> answer.resolve(null) }
        runMain()
        assertEquals(listOf("resolve:null"), journal.entries)
    }

    @Test
    fun queuedCommandOfJavaScriptThatWentIsDropped() {
        runner.run("push", "push1", "push the screen 'Details'", FakePromise(journal), check = {}) { _, answer ->
            answer.resolve("Details")
        }
        var voidRan = false
        runner.runVoid("mergeOptions", "merge the options of the screen 'Home'") { voidRan = true }
        ReloadCoordinator.advance()
        runMain()
        assertTrue(journal.entries.isEmpty())
        assertFalse(voidRan)
    }

    @Test
    fun voidFailureIsOneLogLine() {
        runner.runVoid("mergeOptions", "merge the options of the screen 'Home'") {
            throw IllegalStateException("bad color")
        }
        runMain()
        assertEquals(
            listOf("[ApexNavigation] mergeOptions: could not merge the options of the screen 'Home': bad color"),
            logged,
        )
    }

    @Test
    fun voidCommandsWithoutActivity() {
        hasActivity = false
        val ran = mutableListOf<String>()
        runner.runVoid("setDefaultOptions", "set the default options") { ran += "setDefaultOptions" }
        runner.runVoid("setBackListeners", "setBackListeners") { ran += "setBackListeners" }
        runner.runVoid("resolveNativeScreenCall", "resolveNativeScreenCall") { ran += "resolveNativeScreenCall" }
        // As the module's mergeOptions: without an activity it has nothing to merge into.
        runner.runVoid("mergeOptions", "merge the options of the screen 'Home'") {
            ran += "mergeOptions"
            if (!runner.hasActivity()) runner.logFailure("mergeOptions", ErrorTexts.mergeOptionsNoActivity())
        }
        runMain()
        assertEquals(listOf("setDefaultOptions", "setBackListeners", "resolveNativeScreenCall", "mergeOptions"), ran)
        assertEquals(
            listOf(
                "[ApexNavigation] mergeOptions: the navigation has nowhere to show screens: the app has no activity " +
                    "or window",
            ),
            logged,
        )
    }

    @Test
    fun voidCommandsWithAnActivityDontLog() {
        runner.runVoid("mergeOptions", "merge the options of the screen 'Home'") {
            if (!runner.hasActivity()) runner.logFailure("mergeOptions", ErrorTexts.mergeOptionsNoActivity())
        }
        runMain()
        assertTrue(logged.isEmpty())
    }

    @Test
    fun stateStepFailureIsLoggedAndThePromiseGoes() {
        val failing = object : AnswerEvents {
            override fun beforeAnswer() = throw IllegalStateException("no navigator")

            override fun commandCompleted(commandName: String, commandId: String, completionTime: Long) {
                journal.entries += "completed:$commandName"
            }
        }
        val runner = CommandRunner({ tasks.addLast(it) }, { true }, failing, { logged += it })
        runner.run("push", "push1", "push the screen 'Details'", FakePromise(journal), check = {}) { _, answer ->
            answer.resolve("Details")
        }
        runMain()
        assertEquals(listOf("resolve:Details", "completed:push"), journal.entries)
        assertEquals(
            listOf("[ApexNavigation] push: could not report the navigation state: no navigator"),
            logged,
        )
    }

    @Test
    fun bodyLogsItsOwnText() {
        runner.logFailure("setDefaultOptions", ErrorTexts.unreadableOption("topBar.title.color", "Home"))
        assertEquals(
            listOf(
                "[ApexNavigation] setDefaultOptions: the option 'topBar.title.color' of the screen 'Home' " +
                    "has a value the navigation can't read",
            ),
            logged,
        )
    }
}

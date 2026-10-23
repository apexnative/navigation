package dev.apexnative.navigation.react

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandAnswerTest {
    private val journal = Journal()
    private val promise = FakePromise(journal)

    private fun answer(name: String, commandId: String? = "${name}7", generation: Int = ReloadCoordinator.generation) =
        CommandAnswer(name, commandId, promise, generation, FakeEvents(journal), clock = { 1_700_000_000_123L })

    @Test
    fun resolveSendsStateThenPromiseThenCompleted() {
        answer("push").resolve("Details")
        assertEquals(listOf("state", "resolve:Details", "completed:push"), journal.entries)
        assertEquals(Triple("push", "push7", 1_700_000_000_123L), journal.completed.single())
    }

    @Test
    fun everyShowOrDismissCommandSendsCompleted() {
        val commands = listOf(
            "setRoot",
            "push",
            "pop",
            "popTo",
            "popToRoot",
            "setStackRoot",
            "showInColumn",
            "showModal",
            "dismissModal",
            "dismissAllModals",
            "showOverlay",
            "dismissOverlay",
            "dismissAllOverlays",
        )
        commands.forEach { answer(it).resolve("") }
        assertEquals(commands, journal.completed.map { it.first })
        assertEquals(commands.map { "${it}7" }, journal.completed.map { it.second })
    }

    @Test
    fun otherCommandsDontSendCompleted() {
        answer("setColumnShown").resolve(null)
        answer("preparePush", "push3").resolve(null)
        answer("getLaunchLink", null).resolve(null)
        assertTrue(journal.completed.isEmpty())
        // The state goes before setColumnShown's answer only: preparePush and getLaunchLink change nothing shown.
        assertEquals(listOf("state", "resolve:null", "resolve:null", "resolve:null"), journal.entries)
    }

    @Test
    fun failingStepsAreLoggedAndThePromiseGoes() {
        val logged = mutableListOf<String>()
        val failing = object : AnswerEvents {
            override fun stateNow() = throw IllegalStateException("no navigator")

            override fun commandCompleted(commandName: String, commandId: String, completionTime: Long) =
                throw IllegalStateException()
        }
        val answer = CommandAnswer("pop", "pop4", promise, ReloadCoordinator.generation, failing, { 0L }) {
            logged += it
        }
        answer.resolve("Home")
        assertEquals(listOf("resolve:Home"), journal.entries)
        assertTrue(answer.isAnswered)
        assertEquals(
            listOf(
                "[ApexNavigation] pop: could not report the navigation state: no navigator",
                "[ApexNavigation] pop: could not send ANN.CommandCompleted: IllegalStateException",
            ),
            logged,
        )
    }

    @Test
    fun answersOnce() {
        val answer = answer("pop")
        answer.resolve("Home")
        answer.resolve("Home")
        answer.reject("the screen 'Home' is not shown")
        assertEquals(listOf("state", "resolve:Home", "completed:pop"), journal.entries)
        assertTrue(answer.isAnswered)
    }

    @Test
    fun rejectsWithTheTextAloneAndOnce() {
        val answer = answer("pop")
        answer.reject("the screen 'Home' is not shown")
        answer.reject("again")
        answer.resolve("Home")
        assertEquals(listOf("reject:the screen 'Home' is not shown"), journal.entries)
        assertEquals(listOf<String?>(null), promise.codes)
    }

    @Test
    fun nothingAfterTheJavaScriptWent() {
        val resolved = answer("push")
        val rejected = answer("pop")
        ReloadCoordinator.advance()
        assertFalse(resolved.canAnswer)
        resolved.resolve("Details")
        rejected.reject("the screen 'Home' is not shown")
        assertTrue(journal.entries.isEmpty())
        assertFalse(resolved.isAnswered)
    }

    @Test
    fun theStateGoesOncePerAnswer() {
        val answer = answer("push")
        answer.reportStateNow()
        answer.reportStateNow()
        answer.resolve("Details")
        assertEquals(listOf("state", "resolve:Details", "completed:push"), journal.entries)
    }

    @Test
    fun anAnswerOfAJavaScriptThatWentSendsNoState() {
        val answer = answer("push")
        ReloadCoordinator.advance()
        answer.reportStateNow()
        answer.resolve("Details")
        assertTrue(journal.entries.isEmpty())
    }

    @Test
    fun listenerAnswers() {
        answer("push").asListener().onSuccess("Details")
        answer("pop").asListener().onError("the screen 'Home' is the first screen of its stack")
        assertEquals(
            listOf(
                "state",
                "resolve:Details",
                "completed:push",
                "reject:the screen 'Home' is the first screen of its stack",
            ),
            journal.entries,
        )
    }
}

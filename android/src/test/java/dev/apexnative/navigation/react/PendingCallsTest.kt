package dev.apexnative.navigation.react

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The way of a task behind the calls of JavaScript (`PendingCalls.after`): once through the JavaScript thread, the
 * module's thread and the main thread, each in its order; straight to the main thread without a queue, or from where a
 * queue that ended refused it.
 */
class PendingCallsTest {
    // A queue that runs what it took when the test says, in order; one that [ended] takes nothing.
    private class FakeQueue(val ended: Boolean = false) : PendingCalls.Queue {
        val tasks = ArrayDeque<Runnable>()

        override fun run(task: Runnable): Boolean {
            if (ended) return false
            tasks.addLast(task)
            return true
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }

    private val js = FakeQueue()
    private val module = FakeQueue()
    private val main = FakeQueue()
    private val ran = mutableListOf<String>()

    private fun after(js: PendingCalls.Queue?, module: PendingCalls.Queue?) =
        PendingCalls.after(js, module, { main.run(it) }) { ran += "task" }

    @Test
    fun theTaskGoesThroughTheThreeQueuesInTurn() {
        after(js, module)
        assertEquals(1, js.tasks.size)
        assertTrue(module.tasks.isEmpty() && main.tasks.isEmpty())
        js.runAll()
        assertEquals(1, module.tasks.size)
        assertTrue(main.tasks.isEmpty())
        module.runAll()
        assertEquals(1, main.tasks.size)
        assertTrue(ran.isEmpty())
        main.runAll()
        assertEquals(listOf("task"), ran)
    }

    // A `mergeOptions` JavaScript made before: its method goes to the module's thread, its body to the main thread.
    @Test
    fun aCallMadeBeforeAppliesFirst() {
        js.run {
            module.run { main.run { ran += "mergeOptions" } }
        }
        after(js, module)
        js.runAll()
        module.runAll()
        main.runAll()
        assertEquals(listOf("mergeOptions", "task"), ran)
    }

    @Test
    fun withoutJavaScriptTheTaskGoesToTheMainThreadAtOnce() {
        after(null, null)
        after(js, null)
        after(null, module)
        assertTrue(js.tasks.isEmpty() && module.tasks.isEmpty())
        main.runAll()
        assertEquals(listOf("task", "task", "task"), ran)
    }

    @Test
    fun aJavaScriptQueueThatEndedSendsTheTaskToTheMainThread() {
        after(FakeQueue(ended = true), module)
        assertTrue(module.tasks.isEmpty())
        main.runAll()
        assertEquals(listOf("task"), ran)
    }

    @Test
    fun aModuleQueueThatEndedSendsTheTaskToTheMainThread() {
        after(js, FakeQueue(ended = true))
        js.runAll()
        main.runAll()
        assertEquals(listOf("task"), ran)
    }
}

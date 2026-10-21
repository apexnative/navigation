package dev.apexnative.navigation.views.element

import dev.apexnative.navigation.react.events.FakeScheduler
import dev.apexnative.navigation.views.element.DrawOrder.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The order of a transition's views in its layer on paths of steps, and the wait for the images of shared elements:
 * loaded ones go on, one that doesn't load in 1 s drops out with a line in the log, and the wait ends either way.
 */
class ElementOrderAndImagesTest {
    private fun path(vararg steps: Pair<Float, Int>) = steps.map { Step(it.first, it.second) }

    @Test
    fun theOrderIsTheDrawOrderAlongThePaths() {
        val first = path(0f to 0, 0f to 2)
        val later = path(0f to 0, 0f to 3)
        val raised = path(0f to 0, 4f to 1)
        val inLaterScreen = path(0f to 1, 0f to 0)
        val ancestor = path(0f to 0)
        val sorted = listOf(inLaterScreen, raised, later, ancestor, first).sortedWith(DrawOrder.comparator)
        // An ancestor before what is in it; a higher elevation after a later index; a later screen after all.
        assertEquals(listOf(ancestor, first, later, raised, inLaterScreen), sorted)
    }

    @Test
    fun elevationDecidesBeforeTheIndexAtTheFirstStepThatParts() {
        val low = path(2f to 5, 0f to 0)
        val high = path(3f to 1, 0f to 9)
        assertTrue(DrawOrder.comparator.compare(low, high) < 0)
        assertEquals(0, DrawOrder.comparator.compare(path(1f to 1), path(1f to 1)))
    }

    // Frames by hand: each call to the next frame waits until the test draws one.
    private class Frames {
        private val waiting = mutableListOf<() -> Unit>()

        fun next(frame: () -> Unit): () -> Unit {
            waiting += frame
            return { waiting.remove(frame) }
        }

        fun draw() {
            val due = waiting.toList()
            waiting.clear()
            due.forEach { it() }
        }

        val pending: Int
            get() = waiting.size
    }

    @Test
    fun anImageThatDoesntLoadDropsOutAfterOneSecond() {
        val scheduler = FakeScheduler()
        val frames = Frames()
        val log = mutableListOf<String>()
        var avatarLoaded = false
        var ended = 0
        val wait = ImageWait(
            listOf(
                ImageWait.Image({ avatarLoaded }, { log += "failed:Avatar" }),
                ImageWait.Image({ false }, { log += ImageLoads.textOf("Photo") }),
            ),
            scheduler,
            frames::next,
        ) { ended++ }
        wait.start()
        frames.draw()
        assertEquals(0, ended)
        avatarLoaded = true
        frames.draw()
        assertEquals(0, ended)
        scheduler.advance(ImageLoads.LIMIT_MILLIS - 1)
        assertEquals(0, ended)
        scheduler.advance(1)
        assertEquals(listOf("The image 'Photo' did not load in 1000 ms: no transition for it"), log)
        assertEquals(1, ended)
        // Nothing is left to run.
        assertEquals(0, scheduler.pending)
        assertEquals(0, frames.pending)
    }

    @Test
    fun loadedImagesEndTheWaitAtOnceAndAStoppedWaitEndsNothing() {
        val scheduler = FakeScheduler()
        val frames = Frames()
        var ended = 0
        ImageWait(listOf(ImageWait.Image({ true }, {})), scheduler, frames::next) { ended++ }.start()
        assertEquals(1, ended)
        var loaded = false
        val stop = ImageWait(listOf(ImageWait.Image({ loaded }, {})), scheduler, frames::next) { ended++ }.start()
        stop()
        loaded = true
        frames.draw()
        scheduler.advance(ImageLoads.LIMIT_MILLIS)
        assertEquals(1, ended)
        assertEquals(0, scheduler.pending)
    }
}

package dev.apexnative.navigation.react

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** `ANN.AppLaunched` once per pair of an activity and a React context (EVT-AppLaunched); plain objects for both. */
class AppLaunchesTest {
    private class Activity
    private class Context

    private val launches = AppLaunches<Activity, Context>()
    private val activity = Activity()
    private val context = Context()

    @Test
    fun nothingBeforeTheContextIsReady() {
        assertNull(launches.takeDue(activity, resumed = true))
    }

    @Test
    fun oncePerActivityAndContext() {
        launches.contextReady(context)
        assertSame(context, launches.takeDue(activity, resumed = true))
        assertNull(launches.takeDue(activity, resumed = true))
    }

    @Test
    fun againForANewContext() {
        launches.contextReady(context)
        launches.takeDue(activity, resumed = true)
        launches.contextGone()
        assertNull(launches.takeDue(activity, resumed = true))
        val next = Context()
        launches.contextReady(next)
        assertSame(next, launches.takeDue(activity, resumed = true))
    }

    @Test
    fun againForANewActivity() {
        launches.contextReady(context)
        launches.takeDue(activity, resumed = true)
        assertSame(context, launches.takeDue(Activity(), resumed = true))
    }

    @Test
    fun heldWhilePausedAndSentAtTheNextResume() {
        launches.contextReady(context)
        assertNull(launches.takeDue(activity, resumed = false))
        assertSame(context, launches.takeDue(activity, resumed = true))
        assertNull(launches.takeDue(activity, resumed = true))
    }
}

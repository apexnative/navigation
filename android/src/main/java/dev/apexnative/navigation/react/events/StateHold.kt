package dev.apexnative.navigation.react.events

/**
 * The navigation state's posted update and its hold. [setNeedsUpdate] posts one update ([post]), coalesced: the asks of
 * one pass of the main loop make one, which runs [update] in a later pass. While a `setRoot` shows the modals and the
 * overlays of its layout the updates are held: an ask, or a posted update that runs while held (asked before the hold
 * was taken), only marks one as wanted, and the last release posts it. Released right before the answer, that update
 * finds nothing new: the state before the answer is sent at once, never held, and an identical state is never sent
 * twice. Every [hold] has one [release]; a release without a hold does nothing, so the count never goes below zero. An
 * update keeps the JavaScript it was asked for ([generation]): one asked by a JavaScript that went before it runs is
 * dropped; a newer ask of the new JavaScript still goes. Main thread only.
 */
internal class StateHold(
    private val post: (() -> Unit) -> Unit,
    private val generation: () -> Int = { 0 },
    private val update: () -> Unit,
) {
    /** The holds not released yet. */
    var count = 0
        private set

    // An update was asked while held.
    private var wanted = false
    // An update is posted and hasn't run yet.
    private var scheduled = false
    // The JavaScript the last ask was for.
    private var askedIn = 0

    /** Asks for one update in a later pass of the main loop; while held, once the last hold is released. */
    fun setNeedsUpdate() {
        askedIn = generation()
        if (count > 0) wanted = true else schedule()
    }

    /** The updates asked from now on, and those posted that run from now on, wait for as many [release]s. */
    fun hold() {
        count++
    }

    /** One hold ends; with the last, the update asked while held is posted now. */
    fun release() {
        if (count == 0) return
        count--
        if (count > 0 || !wanted) return
        wanted = false
        schedule()
    }

    private fun schedule() {
        if (scheduled) return
        scheduled = true
        post {
            scheduled = false
            // A hold taken since it was posted: the update waits for the release. The JavaScript it was asked for
            // went: nothing goes to the new one, which hears its first state after its own changes.
            when {
                count > 0 -> wanted = true
                askedIn == generation() -> update()
            }
        }
    }
}

package dev.apexnative.navigation.viewcontrollers.component

import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The one wait for screens to render before they come in: a new root, a push or a `setStackRoot` that waits. It waits
 * for the content of its screens, within [limitMillis] or as long as it takes, and marks the content not ready at the
 * limit as mounted; the components of the bars over the screens drawn in advance join it as [bar] says. Then [onEnd],
 * once. [cancel] ends it without [onEnd]; [retake] waits for other screens with the same limit, and a bar phase that
 * runs goes on.
 *
 * Screens made with the layer that don't show when it shows (the unselected tabs of `together`) count too, for their
 * content only and only within their first [UNSEEN_LIMIT_MILLIS]: a screen the user doesn't see never holds a wait
 * without a limit.
 *
 * What differs between its users are its values, never its steps: the readiness and the clock (fakes in the unit
 * tests), [countsUnknownContent], how a readiness signal is delivered ([deliver]: at once, posted to the next turn of
 * the main loop, or behind the calls of JavaScript), whether content ready at once is delivered too
 * ([deliversReadyContent]), and [step], the guard the user runs every later step in (a timer, a signal). Main thread
 * only.
 */
internal class RenderWait(
    private val readiness: Readiness,
    private val scheduler: AppearanceDispatcher.Scheduler,
    /** At most this long for the content (and the bar's components within it); none: as long as it takes. */
    private val limitMillis: Long?,
    private val bar: Bar,
    /**
     * Without a limit, a screen the readiness store still knows nothing of after [UNKNOWN_CONTENT_MILLIS] counts as
     * ready (`Readiness.isRenderUnknown`): it is marked and the wait ends.
     */
    private val countsUnknownContent: Boolean,
    /** How a readiness signal reaches the wait: at once by default; posted, when a signal may come inside a layout. */
    private val deliver: (() -> Unit) -> Unit = { it() },
    /**
     * Content ready when the wait starts (or retakes) still reaches its end through [deliver], within the limit that
     * runs: what [deliver] waits behind (a push's: the calls JavaScript made in the commit of that content) comes
     * first. Off: such content ends the wait at once.
     */
    private val deliversReadyContent: Boolean = false,
    /** Runs a later step of the wait (a timer, a signal): the user's guards and protection; as it is by default. */
    private val step: (() -> Unit) -> Unit = { it() },
    private val onEnd: () -> Unit,
) {
    /** How the components of the bars drawn in advance count. */
    enum class Bar {
        /** They don't. */
        None,

        /** Within the same limit as the content: the wait ends when both are ready, or at the limit. */
        WithinLimit,

        /** In a phase of their own once the content is ready, [BAR_PHASE_MILLIS] at most; its end marks nothing. */
        AfterContent,
    }

    private var screens: List<ViewController<*>> = emptyList()
    private var unseen: List<ViewController<*>> = emptyList()
    // The unseen screens no longer count: their limit passed.
    private var unseenDone = true
    private var cancelUnseenTimer: (() -> Unit)? = null
    private var waiting = false
    // The signals of the screens waited for now: they can't be taken back, so a newer set (or the end) wins.
    private var token: Any? = null
    // The limit of the content, or the time after which content of unknown readiness counts.
    private var cancelContentTimer: (() -> Unit)? = null
    private var cancelBarPhase: (() -> Unit)? = null

    /** The wait runs: it neither ended nor was cancelled. */
    val isWaiting: Boolean
        get() = waiting

    /** The screens waited for now. */
    val waitedScreens: List<ViewController<*>>
        get() = screens

    /**
     * Starts waiting for [screens], and for the content of [unseen] within its first [UNSEEN_LIMIT_MILLIS]; with all
     * of them ready (and their bars' components, when they count within the limit) it ends at once, before it returns,
     * or through [deliver] with [deliversReadyContent].
     */
    fun start(screens: List<ViewController<*>>, unseen: List<ViewController<*>> = emptyList()) {
        this.screens = screens
        this.unseen = unseen
        unseenDone = unseen.isEmpty()
        waiting = true
        token = Any()
        // Content ready at once ends the wait, unless the bar's components count within a limit (it runs for them) or
        // the content goes through [deliver] too.
        val ready = contentReady()
        if (ready && !deliversReadyContent && !(bar == Bar.WithinLimit && limitMillis != null)) return contentDone()
        // The timers first: a [deliver] that runs at once may end the wait before this returns.
        cancelContentTimer = when {
            limitMillis != null -> scheduler.schedule(limitMillis) { step { atLimit() } }
            countsUnknownContent -> scheduler.schedule(UNKNOWN_CONTENT_MILLIS) { step { atUnknownContentLimit() } }
            else -> null
        }
        startUnseenLimit()
        if (ready && deliversReadyContent) deliverReady() else await()
    }

    /**
     * The screens to wait for changed (a command changed a top of the layer that waits): the wait goes on for
     * [screens], with the limit that runs and a bar phase that runs; with all of them ready it ends now.
     */
    fun retake(screens: List<ViewController<*>>, unseen: List<ViewController<*>> = this.unseen) {
        if (!waiting) return
        this.screens = screens
        this.unseen = unseen
        token = Any()
        if (deliversReadyContent && contentReady()) deliverReady() else await()
    }

    /** Ends the wait without [onEnd]: its timers stop, and the signals still to come do nothing. */
    fun cancel() {
        waiting = false
        token = null
        cancelContentTimer?.invoke()
        cancelContentTimer = null
        cancelBarPhase?.invoke()
        cancelBarPhase = null
        cancelUnseenTimer?.invoke()
        cancelUnseenTimer = null
    }

    // The unseen screens count within their own limit, unless the wait's limit ends it before.
    private fun startUnseenLimit() {
        if (unseenDone || cancelUnseenTimer != null) return
        if (limitMillis != null && limitMillis <= UNSEEN_LIMIT_MILLIS) return
        cancelUnseenTimer = scheduler.schedule(UNSEEN_LIMIT_MILLIS) { step { atUnseenLimit() } }
    }

    // Their time is up: what of them isn't ready counts as mounted, and the wait goes on for the screens that show.
    private fun atUnseenLimit() {
        cancelUnseenTimer = null
        if (!waiting || unseenDone) return
        unseen.filterNot(readiness::isReady).forEach(readiness::markReady)
        unseenDone = true
        if (contentReady() && cancelBarPhase == null) contentDone()
    }

    // The content is ready already: it goes once through [deliver] as a signal would, then on as [await] says.
    private fun deliverReady() {
        val token = token
        deliver {
            step { if (waiting && this.token === token) await() }
        }
    }

    // The content of every screen ready: on to the bars; else the signals of those not ready.
    private fun await() {
        if (contentReady()) return contentDone()
        val token = token
        for (screen in screens + countedUnseen()) {
            if (this.token !== token) return
            if (readiness.isReady(screen)) continue
            readiness.whenReady(screen) {
                deliver {
                    step { if (waiting && this.token === token && contentReady()) contentDone() }
                }
            }
        }
    }

    // The content is ready: the wait ends, or goes on for the bars' components drawn in advance — within the limit
    // that runs, or in a phase of their own after the content.
    private fun contentDone() {
        if (bar == Bar.None || barsReady()) return finish()
        val token = Any()
        this.token = token
        if (bar == Bar.AfterContent && cancelBarPhase == null) {
            cancelContentTimer?.invoke()
            cancelContentTimer = null
            // The bars' components not drawn in time: the screens come as they are, nothing is marked.
            cancelBarPhase = scheduler.schedule(BAR_PHASE_MILLIS) { step { if (waiting) finish() } }
        }
        for (screen in screens) {
            if (this.token !== token) return
            if (readiness.areBarComponentsReady(screen)) continue
            readiness.whenBarComponentsReady(screen) {
                deliver {
                    step { if (waiting && this.token === token && barsReady()) finish() }
                }
            }
        }
    }

    // Not rendered in time: the screens come as they are, and the events held for their content go now; the bars'
    // components are not marked (their own events stay with the dispatcher).
    private fun atLimit() {
        if (!waiting) return
        (screens + countedUnseen()).filterNot(readiness::isReady).forEach(readiness::markReady)
        finish()
    }

    // Without a limit: content the readiness store still knows nothing of counts as ready, as content that renders
    // `null` would never report; once a safe area view reported the id, the wait has no limit.
    private fun atUnknownContentLimit() {
        if (!waiting || contentReady()) return
        val notReady = screens.filterNot(readiness::isReady)
        if (!notReady.all(readiness::isRenderUnknown)) return
        notReady.forEach(readiness::markReady)
        finish()
    }

    private fun finish() {
        if (!waiting) return
        cancel()
        onEnd()
    }

    private fun contentReady(): Boolean = screens.all(readiness::isReady) && countedUnseen().all(readiness::isReady)

    private fun countedUnseen(): List<ViewController<*>> = if (unseenDone) emptyList() else unseen

    private fun barsReady(): Boolean = screens.all(readiness::areBarComponentsReady)

    companion object {
        /** How long a bar phase after the content lasts at most. */
        const val BAR_PHASE_MILLIS = 500L

        /**
         * After how long, from the start of a wait without a limit, content whose id no safe area view reported yet
         * counts as ready ([countsUnknownContent]).
         */
        const val UNKNOWN_CONTENT_MILLIS = 1000L

        /** How long the content of the screens that don't show counts at most, from the start of a wait. */
        const val UNSEEN_LIMIT_MILLIS = 500L

        /**
         * The screens that show when [node] shows, which a wait for it waits for: [node] itself, or down through the
         * waited children of its layouts at every level (`ParentController.waitedChildren`).
         */
        fun waitedScreens(node: ViewController<*>): List<ViewController<*>> =
            if (node is ParentController<*>) node.waitedChildren().flatMap(::waitedScreens) else listOf(node)

        /**
         * The screens made with [node] that don't show when it shows, which a wait for it counts within
         * [UNSEEN_LIMIT_MILLIS] (`ParentController.waitedUnseenChildren`), at every level; their bars' components
         * don't count (only the bar of the stack the layout shows).
         */
        fun unseenScreens(node: ViewController<*>): List<ViewController<*>> {
            if (node !is ParentController<*>) return emptyList()
            return node.waitedUnseenChildren().flatMap { waitedScreens(it) + unseenScreens(it) } +
                node.waitedChildren().flatMap(::unseenScreens)
        }
    }
}

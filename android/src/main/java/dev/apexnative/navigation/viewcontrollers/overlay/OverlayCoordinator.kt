package dev.apexnative.navigation.viewcontrollers.overlay

import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.viewcontrollers.component.Readiness
import dev.apexnative.navigation.viewcontrollers.component.RenderWait
import dev.apexnative.navigation.viewcontrollers.modal.ModalCoordinator
import dev.apexnative.navigation.viewcontrollers.modal.ModalRecord
import dev.apexnative.navigation.viewcontrollers.navigator.WaitingLayer
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The overlays of one activity (`showOverlay`, `dismissOverlay`, `dismissAllOverlays`): in the order they showed, later
 * above, each waiting for its screens first (the core's one wait, at most [WAIT_LIMIT_MILLIS]) unseen and without
 * touches, then shown at once — no animation, no part of the navigation state, untouched by `setRoot`, modals and the
 * rest. An overlay with the root id of a shown one replaces it after its wait. `overlay.interceptTouchOutside` is read
 * at the show and after each merge and kept ([OverlayRecord.intercepts]): the touches never resolve options. The views,
 * the readiness and the clock are given, so it runs in unit tests. Main thread only.
 *
 * The steps that come later (the wait's timers and readiness signals, and the show at its end with the removal of the
 * overlay it replaces) run under no runner: each goes through one guard, and a failure there ends the show as one
 * that couldn't be made, rejecting it with its error text (ARCHITECTURE.md section 5).
 */
internal class OverlayCoordinator(
    private val views: Views,
    private val readiness: Readiness,
    private val scheduler: AppearanceDispatcher.Scheduler,
    private val host: Host,
) {
    /** What the overlays ask of their activity's navigation. */
    interface Host {
        /** The node with [id] in the activity's registry, as the commands find it; none. */
        fun find(id: String): ViewController<*>?

        /** [layer] waits to show from now on (`Navigator.addWaitingLayer`). */
        fun addWaitingLayer(layer: WaitingLayer)

        /** [layer] no longer waits to show. */
        fun removeWaitingLayer(layer: WaitingLayer)

        /** Writes [text] to the log. */
        fun log(text: String)
    }

    /** The views of the overlays layer. Main thread only. */
    interface Views {
        /**
         * The overlay's container joins the overlays layer at the top, waiting: transparent, without touches, unseen by
         * accessibility, with its root's view in it. It may throw: nothing of it stays then.
         */
        fun place(record: OverlayRecord)

        /** The overlay shows: on top, opaque, its touches by [OverlayTouches] with [OverlayRecord.intercepts]. */
        fun show(record: OverlayRecord)

        /** The overlay's view leaves; the layer hides when it holds none. */
        fun remove(record: OverlayRecord)

        /** New [options] (resolved) of the overlay: its `testID` (`overlay.testID`). */
        fun update(record: OverlayRecord, options: Options)

        /** The overlay's views lay out again (the activity resumed). */
        fun requestLayout(record: OverlayRecord) = Unit
    }

    private val records = mutableListOf<OverlayRecord>()

    /** Every overlay that waits or shows, bottom to top. */
    val overlays: List<OverlayRecord>
        get() = records.toList()

    /** The tops of the overlays, bottom to top: the last layers the repaint walks. */
    fun layers(): List<ViewController<*>> = records.map { it.root }

    /**
     * Whether [node] is in an overlay: what the system UI of the top layer asks.
     */
    fun isInOverlay(node: ViewController<*>): Boolean = recordOf(node) != null

    /** The overlay [node] is in; none. */
    fun recordOf(node: ViewController<*>): OverlayRecord? {
        val top = topOf(node)
        return records.firstOrNull { it.root === top }
    }

    /**
     * The trees of the overlays a new one with the root id [id] replaces: their ids are no other screen's for its
     * "shows already".
     */
    fun isReplacedBy(id: String, node: ViewController<*>): Boolean {
        val top = topOf(node)
        return records.any { it.rootId == id && it.root === top }
    }

    /**
     * `showOverlay` after its checks: [make] makes the tree, a waiting overlay with the same root id is cancelled (its
     * show resolves), and [answer] resolves with the overlay's id once it shows. A failure to make or place it goes to
     * the caller, which rejects.
     */
    fun show(make: () -> ViewController<*>, answer: CommandAnswer) {
        start(make, answer, null)
    }

    /**
     * An overlay of a `setRoot`'s layout: made now; [done] once it showed or went. A failure is logged and skipped. The
     * function returned stops it while it waits (a newer `setRoot`): it goes without a word.
     */
    fun showInChain(id: String, make: () -> ViewController<*>, done: () -> Unit): () -> Unit {
        val record = try {
            start(make, null, done)
        } catch (e: Throwable) {
            val reason = ErrorTexts.reasonOf(e)
            host.log("[ApexNavigation] setRoot: ${ErrorTexts.couldNot("show the screen '$id'", reason)}")
            done()
            return {}
        }
        return {
            record.chainDone = null
            if (record.state == OverlayRecord.State.Waiting) cancel(record)
        }
    }

    /**
     * `dismissOverlay`: the overlay [componentId] is in — its root or any screen inside it — goes with every overlay of
     * the same root id: one that waits is cancelled, one that shows goes with its events (both, when a replacement of
     * a shown one waits). [answer] resolves with the overlay's root id, as on iOS. An id no node has: `not shown`; a
     * node in no overlay: `not in an overlay`.
     */
    fun dismiss(componentId: String, answer: CommandAnswer) {
        val node = host.find(componentId) ?: return answer.reject(ErrorTexts.screenNotShown(componentId))
        val rootId = recordOf(node)?.rootId ?: return answer.reject(ErrorTexts.notInOverlay(componentId))
        val matches = records.filter { it.rootId == rootId }
        matches.filter { it.state == OverlayRecord.State.Waiting }.forEach(::cancel)
        val shown = matches.filter { it.state == OverlayRecord.State.Shown }
        if (shown.isEmpty()) return answer.resolve(rootId)
        shown.dropLast(1).forEach { remove(it, null) }
        remove(shown.last(), answer)
    }

    /** `dismissAllOverlays`: every overlay, last to first, as [dismiss] does; `''`. */
    fun dismissAll(answer: CommandAnswer) {
        for (record in records.reversed()) {
            if (record.state == OverlayRecord.State.Waiting) cancel(record) else remove(record, null)
        }
        answer.resolve("")
    }

    /** A `mergeOptions` went into [node]: when it is in an overlay, its options are read again. */
    fun optionsMergedInto(node: ViewController<*>) {
        recordOf(node)?.let(::readOptions)
    }

    /** New default options reach the overlays without their own. */
    fun defaultOptionsChanged() {
        records.forEach(::readOptions)
    }

    /** The activity resumed: the overlays lay out again; nothing is sent. */
    fun onResume() {
        records.forEach(views::requestLayout)
    }

    /**
     * The activity's layers go: JavaScript reloads (nothing sent, nothing answered) or the activity is destroyed (the
     * destroy events; a show that waits resolves with its id).
     */
    fun destroyAll() {
        val all = records.toList()
        records.clear()
        for (record in all) {
            record.wait?.cancel()
            record.wait = null
            host.removeWaitingLayer(record)
            record.state = OverlayRecord.State.Gone
            record.chainDone = null
            views.remove(record)
            if (!record.root.isDestroyed) record.root.destroy()
            record.answer?.resolve(record.rootId)
            record.answer = null
        }
    }

    private fun start(make: () -> ViewController<*>, answer: CommandAnswer?, chainDone: (() -> Unit)?): OverlayRecord {
        val root = make()
        // Before the root's view is made: its screens are drawn under the status bar from their first layout.
        root.shownAsOverlay = true
        val record = OverlayRecord(root)
        // A waiting layer before its views join the window: what is in it isn't on screen from its first layout.
        record.screensChanged = { screensChanged(record) }
        host.addWaitingLayer(record)
        try {
            views.place(record)
        } catch (e: Throwable) {
            host.removeWaitingLayer(record)
            root.destroy()
            throw e
        }
        record.answer = answer
        record.chainDone = chainDone
        // From here the record is placed: a failure ends its show (failed), so it doesn't stay in the window.
        guarded(record) {
            // A waiting overlay of the same id never shows: the new one comes instead.
            records.filter { it.rootId == record.rootId && it.state == OverlayRecord.State.Waiting }.forEach(::cancel)
            records += record
            val wait = RenderWait(
                readiness = readiness,
                scheduler = scheduler,
                limitMillis = WAIT_LIMIT_MILLIS,
                bar = RenderWait.Bar.WithinLimit,
                countsUnknownContent = false,
                step = { work -> guarded(record, work) },
                onEnd = { guarded(record) { ready(record) } },
            )
            record.wait = wait
            wait.start(RenderWait.waitedScreens(root), RenderWait.unseenScreens(root))
        }
        return record
    }

    private fun screensChanged(record: OverlayRecord) {
        if (record.state != OverlayRecord.State.Waiting) return
        val wait = record.wait ?: return
        guarded(record) { wait.retake(RenderWait.waitedScreens(record.root), RenderWait.unseenScreens(record.root)) }
    }

    // The wait ended: a shown overlay of the same id goes first, as `dismissOverlay` without an answer of its own; then
    // this one shows on top, with WillAppear and DidAppear at once and its answer.
    private fun ready(record: OverlayRecord) {
        if (record.state != OverlayRecord.State.Waiting) return
        record.wait = null
        host.removeWaitingLayer(record)
        records.filter { it !== record && it.rootId == record.rootId && it.state == OverlayRecord.State.Shown }
            .forEach { remove(it, null) }
        record.state = OverlayRecord.State.Shown
        try {
            readOptions(record)
            views.show(record)
        } catch (e: Throwable) {
            return failed(record, e)
        }
        // The answer and the chain's step stay on the record until the events went: a failure among them still answers.
        val answer = record.answer
        val done = record.chainDone
        AppearanceDispatcher.beginTransition(listOf(record.root))
        // In a `setRoot`'s chain the state goes once, before its answer.
        AppearanceDispatcher.endTransition(
            hiding = emptyList(),
            showing = listOf(record.root),
            events = null,
            answer = answer,
            value = record.rootId,
            reportsState = done == null,
        )
        record.answer = null
        record.chainDone = null
        done?.invoke()
    }

    // The show couldn't be made, or a later step of it failed: the overlay goes, its show rejects (the chain logs and
    // goes on). Its views and its root go each on their own: a part that fails again doesn't keep the rest from
    // running.
    private fun failed(record: OverlayRecord, e: Throwable) {
        val text = ErrorTexts.couldNot("show the screen '${record.rootId}'", ErrorTexts.reasonOf(e))
        record.wait?.cancel()
        record.wait = null
        host.removeWaitingLayer(record)
        record.state = OverlayRecord.State.Gone
        records.remove(record)
        runCatching { views.remove(record) }
        runCatching { if (!record.root.isDestroyed) record.root.destroy() }
        if (record.answer == null && record.chainDone == null) host.log("[ApexNavigation] $text")
        record.answer?.reject(text)
        record.answer = null
        record.chainDone?.let {
            record.chainDone = null
            host.log("[ApexNavigation] setRoot: $text")
            it()
        }
    }

    // A waiting overlay goes: nothing was sent for it; its show resolves with its id.
    private fun cancel(record: OverlayRecord) {
        record.wait?.cancel()
        record.wait = null
        host.removeWaitingLayer(record)
        record.state = OverlayRecord.State.Gone
        records.remove(record)
        views.remove(record)
        if (!record.root.isDestroyed) record.root.destroy()
        record.answer?.resolve(record.rootId)
        record.answer = null
        record.chainDone?.let {
            record.chainDone = null
            it()
        }
    }

    // A shown overlay goes at once: DidDisappear of its screens, then its content stops, then [answer] with its id. Its
    // view leaves whatever happens to its events: the record is gone, nothing would remove it later.
    private fun remove(record: OverlayRecord, answer: CommandAnswer?) {
        record.state = OverlayRecord.State.Gone
        records.remove(record)
        try {
            AppearanceDispatcher.endTransition(
                hiding = listOf(record.root),
                showing = emptyList(),
                events = { if (!record.root.isDestroyed) record.root.destroy() },
                answer = answer,
                value = record.rootId,
            )
        } finally {
            views.remove(record)
        }
    }

    // A step of [record]'s show that comes later, under no runner: a failure ends the show ([failed]).
    private inline fun guarded(record: OverlayRecord, work: () -> Unit) {
        try {
            work()
        } catch (e: Throwable) {
            failed(record, e)
        }
    }

    private fun readOptions(record: OverlayRecord) {
        if (record.root.isDestroyed) return
        val options = record.root.resolveShownOptions(DefaultOptions.current)
        record.intercepts = options.overlay.interceptTouchOutside.isTrue
        views.update(record, options)
    }

    private fun topOf(node: ViewController<*>): ViewController<*> {
        var current = node
        while (true) current = current.parentController ?: return current
    }

    companion object {
        /** How long an overlay waits for its screens at most: the unset rule of the modals. */
        const val WAIT_LIMIT_MILLIS = ModalCoordinator.WAIT_LIMIT_MILLIS
    }
}

/**
 * One overlay of an activity: its layout's top node, whether it waits or shows, and what it keeps for the touches.
 * While it waits it is a [WaitingLayer] of its activity. Main thread only.
 */
internal class OverlayRecord(
    /** The top node of the overlay's layout. */
    val root: ViewController<*>,
) : WaitingLayer {
    /** Where the overlay is. */
    enum class State { Waiting, Shown, Gone }

    /** The id `showOverlay` answers with, which `dismissOverlay` takes. */
    val rootId: String = root.id

    /** The state; the coordinator moves it. */
    var state = State.Waiting

    /** `overlay.interceptTouchOutside` of its options, read at the show and after each merge. */
    var intercepts = false

    /** The answer of its `showOverlay`; none in a `setRoot`'s chain, or once answered. */
    var answer: CommandAnswer? = null

    /** The step of a `setRoot`'s chain that waits for its show; none. */
    var chainDone: (() -> Unit)? = null

    /** The wait for its screens while it waits. */
    var wait: RenderWait? = null

    /** A stack in it changed its top while it waits: the coordinator's handler. */
    var screensChanged: () -> Unit = {}

    override val waitingNode: ViewController<*>?
        get() = root.takeIf { state == State.Waiting }

    override fun shownScreensChanged() = screensChanged()

    /** The ids of every node of the overlay now. */
    fun ids(): Set<String> = ModalRecord.idsOf(root)
}

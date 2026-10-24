package dev.apexnative.navigation.viewcontrollers.modal

import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.LayoutAnimation
import dev.apexnative.navigation.options.ModalAnimationOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.SheetDetent
import dev.apexnative.navigation.options.ViewAnimationOptions
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.viewcontrollers.component.Readiness
import dev.apexnative.navigation.viewcontrollers.component.RenderWait
import dev.apexnative.navigation.viewcontrollers.modal.ModalRecord.Dismissal
import dev.apexnative.navigation.viewcontrollers.modal.ModalRecord.State
import dev.apexnative.navigation.viewcontrollers.navigator.RootCoordinator
import dev.apexnative.navigation.viewcontrollers.navigator.WaitingLayer
import dev.apexnative.navigation.viewcontrollers.stack.StackController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.AnimationRules

/**
 * The modals of one activity: one [ModalRecord] per modal, in the order of their shows, the newest last. Every rule
 * about modals is a move of one record, applied when the step runs;
 * nothing else keeps a list of modals: the navigator, the navigation state, back and the reload ask here.
 *
 * A modal is made, joins the modals layer at the top, unseen, and waits for its screens with the core's one wait
 * ([RenderWait], `animations.showModal.enter.waitForRender` unset — the content and the bars' components at most
 * [WAIT_LIMIT_MILLIS] together; `true` — as long as it takes, the bars 0.5 s after; `false` — not at all). Then it
 * shows, once every older waiting modal showed or went (N1): `ComponentWillAppear` of its screens, its slide up (or the
 * Material sheet's motion), then the layer under it leaves its layer when the modal covers it, and the end steps of a
 * transition. Android has no gate: transitions overlap.
 *
 * The views, the readiness of the screens and the clock are given ([Views], [Readiness], the scheduler), so the table
 * runs in unit tests. Main thread only.
 *
 * The steps that come later (the wait's timers and readiness signals, the end of the targets' wait, an animator's end,
 * the sheet's callbacks, back of the user) run under no runner: each goes through one guard ([guarded]), and a
 * failure there ends the record it concerns by its own failure path, rejects what waits for it with its error text and
 * lets the modals that wait go on (ARCHITECTURE.md section 5).
 */
internal class ModalCoordinator(
    private val views: Views,
    private val readiness: Readiness,
    private val scheduler: AppearanceDispatcher.Scheduler,
    private val host: Host,
) : RootCoordinator.ModalsOverRoot {
    /** What the modals ask of their activity's navigation. */
    interface Host {
        /** The root the modals lie over; none before the first `setRoot` (the splash is under them then). */
        val root: ViewController<*>?

        /** The node with [id] in the activity's registry; none. */
        fun find(id: String): ViewController<*>?

        /** Whether the activity can be seen (at least started): animations play only then, as frames come only then. */
        fun isSeen(): Boolean

        /** [layer] waits to show from now on (`Navigator.addWaitingLayer`). */
        fun addWaitingLayer(layer: WaitingLayer)

        /** [layer] no longer waits to show. */
        fun removeWaitingLayer(layer: WaitingLayer)

        /** What back does may have changed (`BackCallbackState.update`). */
        fun backChanged()

        /** Writes [text] to the log. */
        fun log(text: String)
    }

    /** The views of the modals layer and of the root layer under it. Main thread only. */
    interface Views {
        /**
         * The record's container joins the modals layer at the top, waiting: transparent, without touches, unseen by
         * accessibility, with its root's view in it (a sheet's at the sheet's full height). It may throw: nothing of
         * it stays then.
         */
        fun place(record: ModalRecord)

        /**
         * The record shows with [options] (resolved): its container visible, its slide up when [animated] — or
         * [motion], an animation of its own —, at once otherwise and while nobody [seen] it; a sheet's root view goes
         * into a Material sheet that slides to its first detent ([events] hear the user), whatever the options' own.
         * [onEnd] once it is in place, at most once. The function returned stops it where it is, without [onEnd]; a
         * screen under it that its [motion] moved goes back to rest then.
         */
        fun present(
            record: ModalRecord,
            options: Options,
            animated: Boolean,
            seen: Boolean,
            events: ModalSheet.Events,
            onEnd: () -> Unit,
            motion: Motion?,
        ): () -> Unit

        /**
         * The record leaves: its slide down (or [motion]) when [animated] and [seen], a sheet's when [seen]; as
         * [present].
         */
        fun dismiss(
            record: ModalRecord,
            animated: Boolean,
            seen: Boolean,
            onEnd: () -> Unit,
            motion: Motion?,
        ): () -> Unit

        /**
         * The wait of the record's show for the targets of [animation]'s elements in its screens (and in [under]'s),
         * as a stack's, while [waiting] holds; [done] once, not after the function returned stopped it.
         */
        fun awaitTargets(
            record: ModalRecord,
            animation: ModalAnimationOptions,
            under: ViewController<*>?,
            waiting: () -> Boolean,
            done: () -> Unit,
        ): () -> Unit {
            done()
            return {}
        }

        /** The record's views leave (a sheet's `remove()`); the modals layer hides when it holds none. */
        fun remove(record: ModalRecord)

        /** The record's show was stopped: its views stand where it ends (a sheet's goes on as its view is shown). */
        fun settle(record: ModalRecord)

        /** The record's container leaves the modals layer: a modal over it covers it. */
        fun takeOut(record: ModalRecord)

        /** The record's container comes back into the modals layer, at the place of [at], else at the top. */
        fun putBack(record: ModalRecord, at: ModalRecord?)

        /** [root]'s view leaves the root layer: a modal over it covers it. */
        fun takeOutRoot(root: ViewController<*>)

        /** [root]'s view comes back into the root layer. */
        fun putBackRoot(root: ViewController<*>)

        /**
         * The record goes with [oldRoot]: its container moves into the root layer right above the old root's view
         * (under the new root), and leaves with the old root's part of the transition.
         */
        fun carry(record: ModalRecord, oldRoot: ViewController<*>?)

        /**
         * New [options] (resolved) of the record: its `testID`s, its sheet's `update`; [merge] is the `mergeOptions`
         * that brought them, which alone may move the sheet, or null when the shown screen changed.
         */
        fun update(record: ModalRecord, options: Options, merge: Options?)

        /** The colors of the record's own views again (a light/dark switch). */
        fun repaint(record: ModalRecord) = Unit

        /** The record's views lay out again (the activity resumed). */
        fun requestLayout(record: ModalRecord) = Unit

        /** The record's sheet as back sees it; none before its show and for a modal that isn't a sheet. */
        fun sheetBack(record: ModalRecord): SheetBack? = null
    }

    /**
     * A show or a dismissal of the modal's own: [side] plays on the modal (the show's `enter`, the dismissal's `exit`),
     * or it fades ([fades]: in at a show, out at a dismissal, 300 ms) around elements; with neither it doesn't move —
     * it appears on the first frame, or stands to the end and goes. [underSide] plays on [under], the layer under it
     * (the show's `exit`, the dismissal's `enter`). [elements]: the animation's elements from [from] to [to] (only with
     * a layer under it).
     */
    class Motion(
        val side: ViewAnimationOptions?,
        val fades: Boolean,
        val under: ViewController<*>?,
        val underSide: ViewAnimationOptions?,
        val elements: LayoutAnimation?,
        val from: ViewController<*>?,
        val to: ViewController<*>?,
    )

    // A `dismissAllModals` on its way.
    private class Batch(val answer: CommandAnswer?) {
        var members: List<ModalRecord> = emptyList()
        // The record that leaves with its animation; none: they all go at once.
        var top: ModalRecord? = null
        // The modals it reports, and the screen the event names (the topmost counted record's).
        var count = 0
        var screen: ViewController<*>? = null
        // The layer it reveals, when that had disappeared.
        var revealed: ViewController<*>? = null
        // The `dismissAllModals` that came during it and found nothing else (N6).
        val later = mutableListOf<CommandAnswer>()
    }

    private val records = mutableListOf<ModalRecord>()
    private val batches = mutableListOf<Batch>()
    // The records that go with the old root of a `setRoot` in its transition (row 13), and the answers they hold.
    private val carried = mutableListOf<ModalRecord>()
    private val carriedAnswers = mutableListOf<CommandAnswer>()
    // The root's view is out of the root layer: a modal covers it.
    private var rootOut = false
    // The ids of the modals that closed while no command waits for them: the user closed them (a drag, the scrim,
    // back) or they were dismissed from below a modal that stays. `dismissModal` with one resolves.
    private val closedAnswerable = HashMap<String, String>()
    // A setRoot takes the modals now: one that waits goes, and none starts to show meanwhile.
    private var taking = false

    /** Every modal not gone, bottom to top. */
    val modals: List<ModalRecord>
        get() = records.toList()

    /** The layout the user sees among the modals: the top modal that shows or comes in; none. */
    val visibleLayout: ViewController<*>?
        get() = records.lastOrNull { it.effective == State.Presenting || it.effective == State.Shown }?.root

    /** The tops of the modals not gone, bottom to top: what the repaint walks. */
    fun layers(): List<ViewController<*>> = records.map { it.root }

    /** The modal [node] is in; none for a node of the root, an overlay, or no modal. */
    fun recordOf(node: ViewController<*>): ModalRecord? {
        val top = topOf(node)
        return records.firstOrNull { it.root === top }
    }

    /**
     * [node] is in a modal of the old root of a `setRoot` on its way (`setRoot` step 3): commands by its id answer "not
     * shown", though it is still on screen.
     */
    fun isWithOldRoot(node: ViewController<*>): Boolean {
        val top = topOf(node)
        return records.any { it.root === top && it.state == State.WithOldRoot } || carried.any { it.root === top }
    }

    // Commands.

    /**
     * `showModal` after its checks: [make] makes the tree; the modal is the top at once, waiting (`showModal` step 2),
     * and [answer] resolves with its root's id after its show. A failure to make or place it goes to the caller, which
     * rejects; nothing of it stays then.
     */
    fun show(make: () -> ViewController<*>, answer: CommandAnswer) {
        start(make, answer, null)
    }

    /**
     * A modal of a `setRoot`'s layout: made now, shown without animation unless [animated] (the last modal, with its
     * `animations.showModal`); [done] once it showed or went. A failure is logged and skipped. The function returned
     * stops the step while the modal waits (a newer `setRoot`): it goes without a word.
     */
    fun showInChain(id: String, make: () -> ViewController<*>, animated: Boolean, done: () -> Unit): () -> Unit {
        val record = try {
            start(make, null, ModalRecord.Chain(animated, done))
        } catch (e: Throwable) {
            val reason = ErrorTexts.reasonOf(e)
            host.log("[ApexNavigation] setRoot: ${ErrorTexts.couldNot("show the screen '$id'", reason)}")
            done()
            return {}
        }
        return {
            record.chain = null
            if (record.state == State.Waiting) cancel(record, null, reports = false)
        }
    }

    /**
     * A modal of a `setRoot`'s layout made with the root, before its turn: it joins the modals layer and renders
     * unseen while the root waits and comes in, held — it doesn't show, and commands and back don't count it — until
     * [release]; [animated] as in [showInChain]. A failure is logged, and nothing is made (null).
     */
    fun hold(id: String, make: () -> ViewController<*>, animated: Boolean): ModalRecord? = try {
        start(make, null, ModalRecord.Chain(animated) {}, held = true)
    } catch (e: Throwable) {
        val reason = ErrorTexts.reasonOf(e)
        host.log("[ApexNavigation] setRoot: ${ErrorTexts.couldNot("show the screen '$id'", reason)}")
        null
    }

    /**
     * The turn of a [hold]'s modal in its `setRoot`'s chain: it shows as soon as it rendered, as [showInChain]'s
     * would, with [done] once it showed or went; at once when it went meanwhile. The function returned stops it while
     * it waits.
     */
    fun release(record: ModalRecord, done: () -> Unit): () -> Unit {
        if (!record.held || record.state != State.Waiting) {
            done()
            return {}
        }
        record.chain = ModalRecord.Chain(record.chain?.animated ?: true, done)
        record.held = false
        guarded(record) {
            host.backChanged()
            // Rendered while held: the targets of its elements are in the layer under it only now.
            if (record.isReady && !record.targetsAwaited && awaitsTargets(record)) {
                record.isReady = false
                awaitTargets(record)
            } else {
                presentReady()
            }
        }
        return {
            record.chain = null
            if (record.state == State.Waiting) cancel(record, null, reports = false)
        }
    }

    /** A [hold]'s modal whose `setRoot` ended before its turn: it goes without a word. */
    fun drop(record: ModalRecord) {
        if (!record.held) return
        record.chain = null
        if (record.state == State.Waiting) cancel(record, null, reports = false)
    }

    /**
     * `dismissModal` after its shared steps: the record of [componentId], Android's rule of the last modal without a
     * root, [options] merged into the modal's visible screen, the animation of the command's screen with its parents
     * and the defaults, then the record's move (row 4).
     */
    fun dismiss(componentId: String, options: Options?, answer: CommandAnswer) {
        val node = host.find(componentId)
        val record = when (val found = lookup(componentId, node)) {
            Lookup.NotShown -> return answer.reject(ErrorTexts.screenNotShown(componentId))
            Lookup.NotInModal -> return answer.reject(ErrorTexts.notInModal(componentId))
            is Lookup.Closed -> return answer.resolve(found.rootId)
            is Lookup.Found -> found.record
        }
        when (record.state) {
            State.WithOldRoot, State.Gone -> return answer.reject(ErrorTexts.screenNotShown(componentId))
            State.ClosedBelow -> return answer.resolve(record.rootId)
            else -> Unit
        }
        if (record.held) return answer.reject(ErrorTexts.screenNotShown(componentId))
        if (isLastWithoutRoot(record)) return answer.reject(ErrorTexts.modalWithoutRoot(componentId))
        options?.let { mergeInto(record, it) }
        val from = node?.takeUnless { it.isDestroyed } ?: record.root
        val animation = from.resolveCurrentOptions(DefaultOptions.current).animations.dismissModal
        request(record, Dismissal(answer, AnimationRules.dismissAnimated(animation), byUser = false, animation))
    }

    /**
     * `dismissAllModals` after its shared steps: [options] merge into the top modal's visible screen and its
     * `animations.dismissModal` decides the animation of the top one; every modal goes in one batch with one
     * `ANN.ModalDismissed`; the answer is `''`. A value of [options] that can't be read ([unreadablePath]) rejects
     * before anything changes, with the id of the top modal's visible screen.
     */
    fun dismissAll(options: Options?, unreadablePath: String?, answer: CommandAnswer) {
        val members = records.filter { it.batch == null && !it.held && it.state in BATCHED }
        if (members.isEmpty()) {
            // Nothing else to take: the second one answers with the running batch (N6), else at once.
            val running = batches.lastOrNull()
            if (running != null) running.later += answer else answer.resolve("")
            return
        }
        val top = topRecord() ?: members.last()
        if (unreadablePath != null) {
            val screen = AppearanceDispatcher.visibleNode(top.root)
            return answer.reject(ErrorTexts.unreadableOption(unreadablePath, screen.id))
        }
        options?.let { mergeInto(top, it) }
        val animation = top.root.resolveShownOptions(DefaultOptions.current).animations.dismissModal
        startBatch(members, answer, AnimationRules.dismissAnimated(animation), animation)
    }

    // Back.

    /**
     * Back after JavaScript's listeners: the top modal that waits, comes in or shows, first — its content goes back if
     * it can; else `hardwareBackButton.dismissModalOnPress` of its options at this moment: `true` closes it as
     * `dismissModal` of its root, without a promise; `false` sends `ANN.NavigationButtonPressed` with
     * `ApexNavigation.hardwareBackButton`. Null without such a modal: back goes to the root. False when back isn't
     * taken: the only modal over no root.
     */
    fun handleBack(listener: CommandListener): Boolean? {
        val record = backTarget() ?: return null
        val what = "go back from the screen '${record.rootId}'"
        // Only a failure of the modal's own dismissal ends the modal; one of its content's back or of the event leaves
        // it as it was (a modal that waits or comes in goes on with its show).
        var dismissal: Dismissal? = null
        logged(what) {
            if (record.root.handleBack(listener)) return true
            if (holdsBack(record)) {
                AppearanceDispatcher.navigationButtonPressed(
                    AppearanceDispatcher.visibleNode(record.root),
                    StackController.HARDWARE_BACK_BUTTON_ID,
                )
                return true
            }
            if (isLastWithoutRoot(record)) return false
            val animation = record.root.resolveCurrentOptions(DefaultOptions.current).animations.dismissModal
            dismissal = Dismissal(null, AnimationRules.dismissAnimated(animation), byUser = true, animation)
        }
        val taken = dismissal ?: return true
        try {
            request(record, taken)
        } catch (e: Throwable) {
            fail(record, e, what)
        }
        return true
    }

    /**
     * The root of the modal that back goes to first; none without one. A back gesture previews a pop in it, or the
     * modal's dismissal when it is a sheet ([backTargetSheet]).
     */
    fun backTargetRoot(): ViewController<*>? = backTarget()?.root

    /**
     * The sheet back would dismiss now, as [handleBack] would, and as a drag down of the user would: the top modal is
     * a sheet that shows, its content can't go back (else back goes there, and a pop in it has its own preview),
     * `modal.swipeToDismiss` isn't false, `hardwareBackButton.dismissModalOnPress` doesn't hold back, it isn't the only
     * modal over no root, and its sheet would hide, not collapse, and isn't closing already ([SheetBack.hidesOnBack]).
     * None otherwise. The back gesture previews its hide on Android 14+, and its release closes it as the user does.
     */
    fun backTargetSheet(): SheetBack? {
        val record = backTarget()?.takeIf { it.isSheet && it.state == State.Shown } ?: return null
        if (record.root.canHandleBack() || holdsBack(record) || isLastWithoutRoot(record)) return null
        val options = record.root.resolveShownOptions(DefaultOptions.current)
        if (options.modal.swipeToDismiss.isFalse) return null
        return views.sheetBack(record)?.takeIf { it.hidesOnBack }
    }

    /** The mirror of [handleBack], without acting; null without a modal to ask. */
    fun canHandleBack(): Boolean? {
        val record = backTarget() ?: return null
        return record.root.canHandleBack() || holdsBack(record) || !isLastWithoutRoot(record)
    }

    // Options.

    /**
     * A `mergeOptions` of [merge] went into [node] (`mergeOptions` step 5): when it is in a modal, the modal's sheet
     * takes its resolved options again, moving only to a `selectedDetent` that [merge] names, and its `testID`s apply
     * again.
     */
    fun optionsMergedInto(node: ViewController<*>, merge: Options) {
        val record = recordOf(node) ?: return
        if (record.root.isDestroyed) return
        views.update(record, record.root.resolveShownOptions(DefaultOptions.current), merge)
    }

    /**
     * A stack or the tabs of [node] changed the screen they show (`Navigator.shownScreensChanged`): a modal's sheet
     * takes the hold of the new screen, without moving.
     */
    fun visibleScreenChanged(node: ViewController<*>) {
        val record = recordOf(node) ?: return
        if (record.state == State.Waiting || record.root.isDestroyed) return
        views.update(record, record.root.resolveShownOptions(DefaultOptions.current), merge = null)
    }

    /** The colors of the modals' own views again, after a light/dark switch. */
    fun repaint() {
        records.forEach(views::repaint)
    }

    /** The activity resumed: the modals lay out again; nothing is sent. */
    fun onResume() {
        records.forEach(views::requestLayout)
    }

    // A `setRoot` (RootCoordinator.ModalsOverRoot).

    // A modal still waiting would only show over the root that goes: cancelled; the others go with the old
    // root and commands don't find them; a dismissal finishes on its own.
    override fun rootAccepted() = whileTaking {
        for (record in records.toList()) {
            when (record.state) {
                State.Waiting -> cancel(record, null, reports = false)
                State.Presenting, State.Shown, State.ClosedBelow -> {
                    record.takeIds()
                    record.before = record.state
                    record.state = State.WithOldRoot
                }
                else -> Unit
            }
        }
        host.backChanged()
    }

    // The `setRoot` that took them ended without replacing the root.
    override fun rootKept() {
        records.filter { it.state == State.WithOldRoot }.forEach { it.state = it.before }
        host.backChanged()
    }

    // Every modal goes with the old root; one that waits is cancelled. Their views go into the root layer over
    // the old root (put back first when a modal covered it), and leave with its part of the transition.
    override fun rootReplaced(old: ViewController<*>?) = whileTaking {
        if (rootOut && old != null) views.putBackRoot(old)
        rootOut = false
        for (batch in batches) {
            batch.answer?.let(carriedAnswers::add)
            carriedAnswers += batch.later
        }
        batches.clear()
        for (record in records.toList()) {
            // A modal of the new root's layout, made with it, stays: it shows over the new root in its turn.
            if (record.held) continue
            if (record.state == State.Waiting) {
                cancel(record, null, reports = false)
                continue
            }
            record.stopAnimation?.invoke()
            record.stopAnimation = null
            if (!record.root.isDestroyed) record.root.onLeaveStarted()
            views.carry(record, old)
            carried += record
        }
        records.retainAll { it.held }
        host.backChanged()
    }

    // The transition that replaced the root ended (or was cut): the modals that went with it are destroyed after it,
    // their shown screens' DidDisappear first, without `ANN.ModalDismissed`; what waited for them resolves.
    override fun replacedRootGone() {
        val taken = carried.toList()
        carried.clear()
        for (record in taken) {
            views.remove(record)
            record.state = State.Gone
            if (!record.root.isDestroyed) record.root.destroy()
            answerAll(record)
        }
        val answers = carriedAnswers.toList()
        carriedAnswers.clear()
        answers.forEach { it.resolve("") }
    }

    /**
     * The activity's layers go (12): JavaScript reloads (nothing is sent, nothing answers: the generation moved) or the
     * activity is destroyed (the destroy events; shows and the commands that wait resolve with their roots' ids, open
     * point 5). A `setRoot`'s chain gets nothing: the root coordinator answers it.
     */
    fun destroyAll() {
        val all = records.toList() + carried
        records.clear()
        carried.clear()
        for (record in all) {
            stopWaits(record)
            host.removeWaitingLayer(record)
            record.stopAnimation?.invoke()
            record.stopAnimation = null
            views.remove(record)
            record.state = State.Gone
            record.chain = null
            if (!record.root.isDestroyed) record.root.destroy()
            answerAll(record)
        }
        val answers = batches.flatMap { listOfNotNull(it.answer) + it.later } + carriedAnswers
        batches.clear()
        carriedAnswers.clear()
        answers.forEach { it.resolve("") }
        closedAnswerable.clear()
        rootOut = false
    }

    // The show (3, 4).

    private fun start(
        make: () -> ViewController<*>,
        answer: CommandAnswer?,
        chain: ModalRecord.Chain?,
        held: Boolean = false,
    ): ModalRecord {
        val root = make()
        val record: ModalRecord
        val options: Options
        try {
            options = root.resolveShownOptions(DefaultOptions.current)
            // A sheet or not is decided before the root's view is made: its screens keep no room for the status bar.
            val sheet = ModalRecord.isSheet(options)
            root.shownAsSheet = sheet
            record = ModalRecord(root, sheet)
            record.held = held
        } catch (e: Throwable) {
            root.destroy()
            throw e
        }
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
        record.takeIds()
        record.ids.forEach { closedAnswerable.remove(it) }
        record.showAnswer = answer
        record.chain = chain
        records += record
        // From here the record is listed: a failure ends it by its own path (the show rejects, the chain goes on),
        // so no record stays waiting and the modals after it don't hang.
        guarded(record) {
            host.backChanged()
            await(record, options)
        }
        return record
    }

    // The one wait of the core with the root's values: the screens the modal shows and, with `together` tabs, those it
    // doesn't, within their own limit.
    private fun await(record: ModalRecord, options: Options) {
        val waitForRender = options.animations.showModal.enter.waitForRender
        val unset = !waitForRender.isTrue && !waitForRender.isFalse
        // Without `waitForRender` the targets of its elements wait within the same 0.5 s.
        if (unset) record.cancelLimit = scheduler.schedule(WAIT_LIMIT_MILLIS) { record.overLimit = true }
        val wait = RenderWait(
            readiness = readiness,
            scheduler = scheduler,
            limitMillis = if (unset) WAIT_LIMIT_MILLIS else null,
            bar = if (waitForRender.isTrue) RenderWait.Bar.AfterContent else RenderWait.Bar.WithinLimit,
            countsUnknownContent = false,
            step = { work -> guarded(record, work) },
            onEnd = { guarded(record) { ready(record) } },
        )
        record.wait = wait
        if (waitForRender.isFalse) {
            wait.start(emptyList())
        } else {
            wait.start(RenderWait.waitedScreens(record.root), RenderWait.unseenScreens(record.root))
        }
    }

    // A stack of the waiting modal changed its top: the wait follows the screens it shows now.
    private fun screensChanged(record: ModalRecord) {
        if (record.state != State.Waiting) return
        val wait = record.wait ?: return
        guarded(record) { wait.retake(RenderWait.waitedScreens(record.root), RenderWait.unseenScreens(record.root)) }
    }

    // Its wait ended; with elements, then the wait for their targets, hidden still.
    private fun ready(record: ModalRecord) {
        if (record.state != State.Waiting) return
        record.wait = null
        if (!record.targetsAwaited && !record.held && awaitsTargets(record)) return awaitTargets(record)
        record.cancelLimit()
        record.isReady = true
        presentReady()
    }

    // A modal not shown as a sheet, whose show animates with elements over a layer under it: unless `waitForRender:
    // false`, its show waits for their targets too.
    private fun awaitsTargets(record: ModalRecord): Boolean {
        if (record.isSheet || record.chain?.animated == false || !host.isSeen()) return false
        val animation = record.root.resolveShownOptions(DefaultOptions.current).animations.showModal
        return AnimationRules.showAnimated(animation) && AnimationRules.hasElements(animation) &&
            !animation.enter.waitForRender.isFalse && nodeOf(layerUnder(record)) != null
    }

    private fun awaitTargets(record: ModalRecord) {
        record.targetsAwaited = true
        val animation = record.root.resolveShownOptions(DefaultOptions.current).animations.showModal
        var stopped = false
        val stop = views.awaitTargets(
            record,
            animation,
            nodeOf(layerUnder(record)),
            waiting = { !stopped && record.state == State.Waiting && !record.overLimit },
        ) {
            if (stopped) return@awaitTargets
            stopped = true
            record.stopTargets = null
            guarded(record) { ready(record) }
        }
        if (!stopped) {
            record.stopTargets = {
                stopped = true
                stop()
            }
        }
    }

    // What a record waits for stops: its screens, the targets of its elements, the limit.
    private fun stopWaits(record: ModalRecord) {
        record.wait?.cancel()
        record.wait = null
        record.stopTargets?.invoke()
        record.stopTargets = null
        record.cancelLimit()
    }

    // N1: a ready modal shows once every older waiting one showed or went.
    private fun presentReady() {
        if (taking) return
        for (record in records.toList()) {
            if (record.state != State.Waiting) continue
            if (record.held || !record.isReady) return
            present(record)
        }
    }

    // W → P: the decisions of the show, WillAppear of its screens, its animation.
    private fun present(record: ModalRecord) {
        host.removeWaitingLayer(record)
        record.state = State.Presenting
        try {
            val options = record.root.resolveShownOptions(DefaultOptions.current)
            record.coversUnder = ModalRecord.coversUnder(record.isSheet, options)
            val animation = options.animations.showModal
            val animated = (record.chain?.animated ?: true) && AnimationRules.showAnimated(animation)
            // Its own animation over the layer under it; a sheet has only its Material motion.
            val motion = if (animated && !record.isSheet) motionOf(animation, record, showing = true) else null
            AppearanceDispatcher.beginTransition(listOf(record.root))
            var ended = false
            val stop = views.present(record, options, animated, host.isSeen(), SheetEvents(record), {
                ended = true
                guarded(record) { presented(record) }
            }, motion)
            if (!ended) record.stopAnimation = stop
        } catch (e: Throwable) {
            failed(record, e)
        }
    }

    // The show couldn't be made, or a later step of it failed: the modal goes as a cancel, the show rejects (the chain
    // logs and goes on), the layer it took out comes back, and the modals that wait go on. Its views and its root go
    // each on their own: a part that fails again doesn't keep the rest from running.
    private fun failed(record: ModalRecord, e: Throwable) {
        val text = ErrorTexts.couldNot("show the screen '${record.rootId}'", ErrorTexts.reasonOf(e))
        val under = layerUnder(record)
        runCatching { record.stopAnimation?.invoke() }
        record.stopAnimation = null
        runCatching { stopWaits(record) }
        record.state = State.Gone
        val listed = records.remove(record)
        host.removeWaitingLayer(record)
        runCatching { views.remove(record) }
        runCatching { if (!record.root.isDestroyed) record.root.destroy() }
        if (record.showAnswer == null && record.chain == null) host.log("[ApexNavigation] $text")
        record.showAnswer?.reject(text)
        record.showAnswer = null
        record.chain?.let {
            record.chain = null
            host.log("[ApexNavigation] setRoot: $text")
            it.done()
        }
        record.answers.forEach { it.resolve(record.rootId) }
        record.waitingDismissals.forEach { it.answer?.resolve(record.rootId) }
        if (listed) runCatching { revealIfUncovered(under) }
        host.backChanged()
        presentReady()
    }

    // A later step of a dismissal failed (its end, the close of a modal below, back): the modal goes as at that end,
    // without the events not sent yet; what waits for it rejects, the layer it took out comes back, and the modals that
    // wait go on.
    private fun dismissFailed(record: ModalRecord, e: Throwable) {
        val text = ErrorTexts.couldNot("dismiss the modal '${record.rootId}'", ErrorTexts.reasonOf(e))
        val listed = record in records
        val under = layerUnder(record)
        runCatching { record.stopAnimation?.invoke() }
        record.stopAnimation = null
        runCatching { stopWaits(record) }
        if (record.ending?.byUser == true) record.ids.forEach { closedAnswerable[it] = record.rootId }
        record.state = State.Gone
        records.remove(record)
        host.removeWaitingLayer(record)
        runCatching { views.remove(record) }
        runCatching { if (!record.root.isDestroyed) record.root.destroy() }
        val answers = listOfNotNull(record.showAnswer) + record.answers +
            record.waitingDismissals.mapNotNull { it.answer }
        record.showAnswer = null
        record.answers.clear()
        record.waitingDismissals.clear()
        if (answers.none { !it.isAnswered }) host.log("[ApexNavigation] $text")
        answers.forEach { it.reject(text) }
        record.chain?.let {
            record.chain = null
            it.done()
        }
        if (listed) runCatching { revealIfUncovered(under) }
        host.backChanged()
        presentReady()
    }

    // The show ended. The layer under leaves its layer when the modal covers it; DidDisappear of its
    // screens, DidAppear of the modal's, the state, the answer. In a `setRoot`'s chain: no answer, no state of its own.
    // A modal a `setRoot` took while it came in ends its show the same way and stays taken.
    private fun presented(record: ModalRecord) {
        record.stopAnimation = null
        if (record.effective != State.Presenting) return
        move(record, State.Shown)
        val hiding = ArrayList<ViewController<*>>(1)
        if (record.coversUnder) {
            var under = layerUnder(record)
            // A modal under it still coming in ends its show at its place first (its DidAppear, its answer): out of
            // the window its slide or its sheet's settle would end unseen or never (a hung show).
            // Under its own guard: a failure there ends that modal, and this one goes on.
            if (under != null && under.effective == State.Presenting) {
                val lower: ModalRecord = under
                guarded(lower) {
                    lower.stopAnimation?.invoke()
                    views.settle(lower)
                    presented(lower)
                }
                under = layerUnder(record)
            }
            if (under == null) {
                host.root?.let {
                    if (!rootOut) views.takeOutRoot(it)
                    rootOut = true
                    hiding += it
                }
            } else {
                if (!under.isOut) views.takeOut(under)
                under.isOut = true
                hiding += under.root
            }
        }
        // The answer and the chain stay on the record until the events went: a failure among them still answers.
        val answer = record.showAnswer
        val chain = record.chain
        AppearanceDispatcher.endTransition(
            hiding = hiding,
            showing = listOf(record.root),
            events = null,
            answer = answer,
            value = record.rootId,
            reportsState = chain == null,
        )
        record.showAnswer = null
        record.chain = null
        // The chain and a dismissal that waited for the show go on whatever back's update does: they are no longer on
        // the record, nothing else would start them.
        try {
            host.backChanged()
        } finally {
            chain?.done?.invoke()
            // A `setRoot` took it: commands wait for the old root (row 13), back and the user act as on a shown modal.
            val next = when (record.state) {
                State.Shown -> record.waitingDismissals.removeFirstOrNull()
                State.WithOldRoot -> record.waitingDismissals.firstOrNull { it.byUser }
                    ?.also(record.waitingDismissals::remove)
                else -> null
            }
            next?.let { request(record, it) }
        }
    }

    // A record a `setRoot` took keeps being taken: its own move goes to the state it has under it.
    private fun move(record: ModalRecord, state: State) {
        if (record.state == State.WithOldRoot) record.before = state else record.state = state
    }

    // The table, row 4 (a command) and rows 6, 7 (the user, back): what a dismissal does in each state, when it runs.
    private fun request(record: ModalRecord, dismissal: Dismissal) {
        when (record.effective) {
            State.Waiting -> cancel(record, dismissal, reports = true)
            State.Presenting -> record.waitingDismissals += dismissal
            State.Shown -> if (isTop(record)) beginDismissal(record, dismissal) else closeBelow(record, dismissal)
            State.Dismissing -> dismissal.answer?.let(record.answers::add)
            // It went already, or goes with the old root: the dismissal just completes, as on iOS.
            State.ClosedBelow, State.WithOldRoot, State.Gone -> dismissal.answer?.resolve(record.rootId)
        }
    }

    // (e) W → X: the wait stops, its views leave, its root goes without events (no WillAppear went); the layer under
    // isn't touched. The show resolves first, then — for a dismissal or back — one `ANN.ModalDismissed`, then the
    // dismissal's answer.
    private fun cancel(record: ModalRecord, dismissal: Dismissal?, reports: Boolean) {
        val under = layerUnder(record)
        stopWaits(record)
        host.removeWaitingLayer(record)
        record.state = State.Gone
        records.remove(record)
        views.remove(record)
        val screen = AppearanceDispatcher.modalScreen(record.root)
        record.showAnswer?.resolve(record.rootId)
        record.showAnswer = null
        record.chain?.let {
            record.chain = null
            it.done()
        }
        if (reports) {
            record.reported = true
            AppearanceDispatcher.modalDismissed(screen, 1)
        }
        if (dismissal?.byUser == true) record.ids.forEach { closedAnswerable[it] = record.rootId }
        if (!record.root.isDestroyed) record.root.destroy()
        dismissal?.answer?.resolve(record.rootId)
        revealIfUncovered(under)
        host.backChanged()
        presentReady()
    }

    // A layer a modal closed below kept out stays out while modals are over it; once none is (the last one above was
    // cancelled while it waited), it comes back with its appearance.
    private fun revealIfUncovered(layer: ModalRecord?) {
        if (!isOut(layer)) return
        val above = if (layer == null) records else records.drop(records.indexOf(layer) + 1)
        if (above.any { it.effective in COVERING }) return
        val node = nodeOf(layer) ?: return
        putBack(layer, null)
        AppearanceDispatcher.beginTransition(listOf(node))
        AppearanceDispatcher.endTransition(emptyList(), listOf(node), null, null, null)
    }

    // (d) S → D: the leaving screens let go of the focus, the layer revealed comes back when it had disappeared with
    // its WillAppear, then the animation.
    private fun beginDismissal(record: ModalRecord, dismissal: Dismissal) {
        move(record, State.Dismissing)
        dismissal.answer?.let(record.answers::add)
        record.waitingDismissals.forEach { it.answer?.let(record.answers::add) }
        record.waitingDismissals.clear()
        if (!record.root.isDestroyed) record.root.onLeaveStarted()
        val revealed = layerUnder(record)
        val wasOut = isOut(revealed)
        if (wasOut) putBack(revealed, record)
        val node = if (wasOut) nodeOf(revealed) else null
        node?.let { AppearanceDispatcher.beginTransition(listOf(it)) }
        record.ending = ModalRecord.Ending(node, dismissal.byUser)
        if (dismissal.byUser) record.takeIds()
        var ended = false
        val motion = dismissal.animation?.let { motionOf(it, record, showing = false) }
        val stop = views.dismiss(record, dismissal.animated, host.isSeen(), {
            ended = true
            guarded(record) { dismissed(record) }
        }, motion)
        if (!ended) record.stopAnimation = stop
        host.backChanged()
    }

    // Its views leave, DidDisappear of its screens, DidAppear of the layer revealed, the state,
    // `ANN.ModalDismissed` unless it went already, its content stops, the answer.
    private fun dismissed(record: ModalRecord) {
        record.stopAnimation = null
        (record.batch as? Batch)?.let { return memberEnded(it, record) }
        if (record.effective != State.Dismissing) return
        record.state = State.Gone
        records.remove(record)
        views.remove(record)
        val ending = record.ending
        if (ending?.byUser == true) record.ids.forEach { closedAnswerable[it] = record.rootId }
        // The answers stay on the record until the events went: a failure among them still answers them.
        val answers = record.answers.toList()
        AppearanceDispatcher.endTransition(
            hiding = listOf(record.root),
            showing = listOfNotNull(ending?.revealed),
            events = {
                if (!record.reported) {
                    record.reported = true
                    AppearanceDispatcher.modalDismissed(record.root, 1)
                }
                record.root.destroy()
            },
            answer = answers.firstOrNull(),
            value = record.rootId,
        )
        record.answers.clear()
        answers.drop(1).forEach { it.resolve(record.rootId) }
        host.backChanged()
    }

    // (c) S → B: dismissed while a modal above it stays: reported at once, its screens go, then its exit plays unseen
    // and its view leaves (`dismissModal` step 5).
    private fun closeBelow(record: ModalRecord, dismissal: Dismissal) {
        move(record, State.ClosedBelow)
        record.takeIds()
        // Its record may leave at once (out of the window): a later `dismissModal` of it still resolves.
        record.ids.forEach { closedAnswerable[it] = record.rootId }
        // The answers wait on the record until the events went: a failure among them still answers them.
        dismissal.answer?.let(record.answers::add)
        record.waitingDismissals.forEach { it.answer?.let(record.answers::add) }
        record.waitingDismissals.clear()
        val answers = record.answers.toList()
        AppearanceDispatcher.endTransition(
            hiding = listOf(record.root),
            showing = emptyList(),
            events = {
                record.reported = true
                AppearanceDispatcher.modalDismissed(record.root, 1)
                record.root.destroy()
            },
            answer = answers.firstOrNull(),
            value = record.rootId,
        )
        record.answers.clear()
        answers.drop(1).forEach { it.resolve(record.rootId) }
        var ended = false
        // Out of the window under a covering modal, its exit would end unseen or never (a sheet's settle): at once.
        val stop = views.dismiss(record, dismissal.animated, host.isSeen() && !record.isOut, {
            ended = true
            guarded(record) { closedBelowEnded(record) }
        }, null)
        if (!ended) record.stopAnimation = stop
        host.backChanged()
    }

    // The unseen exit of a modal closed below ended: its view leaves, and so does the record.
    private fun closedBelowEnded(record: ModalRecord) {
        record.stopAnimation = null
        if (record.effective != State.ClosedBelow || record.batch != null) return
        record.state = State.Gone
        records.remove(record)
        views.remove(record)
    }

    // (f) The batch of `dismissAllModals`: the top one leaves with its animation, every other at once.
    private fun startBatch(
        members: List<ModalRecord>,
        answer: CommandAnswer,
        animated: Boolean,
        animation: ModalAnimationOptions,
    ) {
        val batch = Batch(answer)
        batches += batch
        batch.members = members
        // The batch is registered from here: a failure takes it out with its members (as a later step's would), so no
        // later `dismissAllModals` waits for it; the runner's answer then is a second one and is dropped.
        try {
            takeBatch(batch, members, animated, animation)
        } catch (e: Throwable) {
            batchFailed(batch, e)
            throw e
        }
    }

    private fun takeBatch(
        batch: Batch,
        members: List<ModalRecord>,
        animated: Boolean,
        animation: ModalAnimationOptions,
    ) {
        val lowest = members.first()
        val revealed = layerUnder(lowest)
        val wasOut = isOut(revealed)
        val counted = members.filter { it.state != State.ClosedBelow && !it.reported }
        batch.count = counted.size
        batch.screen = counted.lastOrNull()?.let { AppearanceDispatcher.modalScreen(it.root) }
        // The top that leaves with its animation: one already leaving keeps its own.
        val top = members.lastOrNull { it.state in ANIMATED_IN_BATCH }
        batch.top = top
        val keepsItsOwn = top?.state == State.Dismissing && top.stopAnimation != null
        for (record in members) {
            record.batch = batch
            stopWaits(record)
            host.removeWaitingLayer(record)
            record.waitingDismissals.forEach { it.answer?.let(record.answers::add) }
            record.waitingDismissals.clear()
            if (record.state != State.ClosedBelow && !record.root.isDestroyed) record.root.onLeaveStarted()
            if (record.state != State.ClosedBelow) record.state = State.Dismissing
            if (record === top && keepsItsOwn) continue
            record.stopAnimation?.invoke()
            record.stopAnimation = null
            if (record !== top) views.remove(record)
        }
        if (wasOut) putBack(revealed, lowest)
        batch.revealed = if (wasOut) nodeOf(revealed) else null
        batch.revealed?.let { AppearanceDispatcher.beginTransition(listOf(it)) }
        // A member leaving on its own revealed that layer already (its WillAppear went): it appears at the batch's end.
        if (!wasOut) {
            batch.revealed = members.firstNotNullOfOrNull { it.ending?.revealed }?.takeIf { it === nodeOf(revealed) }
        }
        host.backChanged()
        if (top == null) return endBatch(batch)
        if (keepsItsOwn) return
        var ended = false
        // The top leaves by the dismissal's animation of the top modal over what the batch reveals.
        val motion = motionOf(animation, top, showing = false, under = nodeOf(revealed))
        val stop = views.dismiss(top, animated, host.isSeen(), {
            ended = true
            guarded(top) { dismissed(top) }
        }, motion)
        if (!ended) top.stopAnimation = stop
    }

    private fun memberEnded(batch: Batch, record: ModalRecord) {
        if (record === batch.top && batch in batches) endBatch(batch)
    }

    // (f)'s end: the screens of every modal of the batch disappear, the layer revealed appears, the state, one
    // `ANN.ModalDismissed` with the number of modals not reported before, the content stops, every show and command
    // that waited resolves with its own root's id, then the answer `''`.
    private fun endBatch(batch: Batch) {
        batches.remove(batch)
        val members = batch.members
        // The others' views went at its start.
        batch.top?.let(views::remove)
        for (record in members) {
            record.stopAnimation?.invoke()
            record.stopAnimation = null
            record.state = State.Gone
            records.remove(record)
        }
        AppearanceDispatcher.endTransition(
            hiding = members.map { it.root }.filterNot { it.isDestroyed },
            showing = listOfNotNull(batch.revealed),
            events = {
                if (batch.count > 0) batch.screen?.let { AppearanceDispatcher.modalDismissed(it, batch.count) }
                for (record in members) {
                    record.reported = true
                    if (!record.root.isDestroyed) record.root.destroy()
                }
                members.forEach(::answerAll)
            },
            answer = batch.answer,
            value = "",
        )
        batch.later.forEach { it.resolve("") }
        host.backChanged()
        presentReady()
    }

    // A later step of a `dismissAllModals` failed: every modal of it goes as at its end, without the events not sent
    // yet; what waited for each resolves as at that end, the batch's answers reject, and the modals that wait go on.
    private fun batchFailed(batch: Batch, e: Throwable) {
        val text = ErrorTexts.couldNot("dismiss all modals", ErrorTexts.reasonOf(e))
        batches.remove(batch)
        for (record in batch.members) {
            runCatching { record.stopAnimation?.invoke() }
            record.stopAnimation = null
            record.state = State.Gone
            records.remove(record)
            runCatching { views.remove(record) }
            runCatching { if (!record.root.isDestroyed) record.root.destroy() }
            runCatching { answerAll(record) }
        }
        val answers = listOfNotNull(batch.answer) + batch.later
        batch.later.clear()
        if (answers.none { !it.isAnswered }) host.log("[ApexNavigation] $text")
        answers.forEach { it.reject(text) }
        host.backChanged()
        presentReady()
    }

    // What waited for a record that went without its own end resolves with its root's id; a chain goes on.
    private fun answerAll(record: ModalRecord) {
        record.showAnswer?.resolve(record.rootId)
        record.showAnswer = null
        record.chain?.let {
            record.chain = null
            it.done()
        }
        record.answers.forEach { it.resolve(record.rootId) }
        record.answers.clear()
        record.waitingDismissals.forEach { it.answer?.resolve(record.rootId) }
        record.waitingDismissals.clear()
    }

    // The user (the sheet's events).

    // The user closed the sheet (a drag down, the scrim): as back with `dismissModalOnPress: true`. While it comes in,
    // its show ends first, so it answers.
    private fun closedByTheUser(record: ModalRecord) {
        try {
            if (record.effective == State.Presenting) {
                record.stopAnimation?.invoke()
                presented(record)
            }
            if (record.effective != State.Shown) return
            request(record, Dismissal(null, animated = true, byUser = true))
        } catch (e: Throwable) {
            fail(record, e, "close the screen '${record.rootId}'")
        }
    }

    private inner class SheetEvents(private val record: ModalRecord) : ModalSheet.Events {
        override fun onDetentChanged(detent: SheetDetent) {
            if (record.root.isDestroyed) return
            logged("tell the sheet's detent of the screen '${record.rootId}'") {
                AppearanceDispatcher.sheetDetentChanged(record.root, detent.toValue())
            }
        }

        override fun onClosedByUser() = closedByTheUser(record)

        // The user tried to close a sheet that holds them.
        override fun onCloseAttempted() {
            if (record.effective != State.Shown || record.root.isDestroyed) return
            logged("tell the attempt to close the screen '${record.rootId}'") {
                AppearanceDispatcher.modalAttemptedToDismiss(record.root)
            }
        }
    }

    // Protection (ARCHITECTURE.md section 5).

    // A step of [record] that comes later, under no runner: a failure ends what the record was doing ([fail]).
    private inline fun guarded(record: ModalRecord, work: () -> Unit) {
        try {
            work()
        } catch (e: Throwable) {
            fail(record, e, "show the screen '${record.rootId}'")
        }
    }

    // A failure in a later step of [record], by what it was doing: a `dismissAllModals` it is in fails as a whole
    // (batchFailed); a show not answered yet fails as one that couldn't be made (failed); a dismissal or a close below
    // on its way fails as its end (dismissFailed). A record the step left as it was stays; with no answer to give, the
    // failure is logged as [what].
    private fun fail(record: ModalRecord, e: Throwable, what: String) {
        (record.batch as? Batch)?.let { return batchFailed(it, e) }
        val showing = record.state == State.Waiting || record.effective == State.Presenting
        when {
            showing || record.showAnswer != null || record.chain != null -> failed(record, e)
            record.state == State.Gone || record.effective in LEAVING -> dismissFailed(record, e)
            else -> host.log("[ApexNavigation] ${ErrorTexts.couldNot(what, ErrorTexts.reasonOf(e))}")
        }
    }

    // A callback of the user that only sends an event: a failure is logged.
    private inline fun logged(what: String, work: () -> Unit) {
        try {
            work()
        } catch (e: Throwable) {
            host.log("[ApexNavigation] ${ErrorTexts.couldNot(what, ErrorTexts.reasonOf(e))}")
        }
    }

    // Rules across records.

    // The record a dismissal of [node] (or of [id] without a node) concerns.
    private fun lookup(id: String, node: ViewController<*>?): Lookup {
        if (node != null) {
            val top = topOf(node)
            if (carried.any { it.root === top }) return Lookup.NotShown
            return records.firstOrNull { it.root === top }?.let(Lookup::Found) ?: Lookup.NotInModal
        }
        val gone = records.firstOrNull {
            (it.state == State.ClosedBelow || it.state == State.WithOldRoot) && id in it.ids
        }
        if (gone != null) return Lookup.Found(gone)
        closedAnswerable[id]?.let { return Lookup.Closed(it) }
        return Lookup.NotShown
    }

    private sealed class Lookup {
        object NotShown : Lookup()
        object NotInModal : Lookup()
        class Closed(val rootId: String) : Lookup()
        class Found(val record: ModalRecord) : Lookup()
    }

    // A record is the top when no record above it waits, comes in or shows; one that leaves doesn't count.
    private fun isTop(record: ModalRecord): Boolean =
        records.drop(records.indexOf(record) + 1).none(::countsAsTop)

    // The top record, as the commands see it; none.
    private fun topRecord(): ModalRecord? = records.lastOrNull(::countsAsTop)

    // Back goes to the top modal that waits, comes in or shows.
    private fun backTarget(): ModalRecord? = records.lastOrNull(::countsAsTop)

    // A record that waits, comes in or shows; a held one doesn't count before its turn.
    private fun countsAsTop(record: ModalRecord): Boolean = !record.held && record.effective in TOP_STATES

    // `hardwareBackButton.dismissModalOnPress: false` of the modal's options now.
    private fun holdsBack(record: ModalRecord): Boolean =
        record.root.resolveShownOptions(DefaultOptions.current).hardwareBackButton.dismissModalOnPress.isFalse

    // The only modal that isn't closed below, with the old root or gone, over no root.
    private fun isLastWithoutRoot(record: ModalRecord): Boolean = host.root == null &&
        records.none { it !== record && it.state != State.ClosedBelow && it.state != State.WithOldRoot }

    // The animation of a show ([showing]) or a dismissal of [record]'s own, over [under] (the layer under it); none
    // when [animation] has none (`AnimationRules.modalIsOwn`) or the record is a sheet. A side that doesn't play
    // leaves its view as it is; with elements the modal fades (by its own side only when it is a single `alpha`) and
    // the layer under stands.
    private fun motionOf(
        animation: ModalAnimationOptions,
        record: ModalRecord,
        showing: Boolean,
        under: ViewController<*>? = nodeOf(layerUnder(record)),
    ): Motion? {
        if (record.isSheet || !AnimationRules.modalIsOwn(animation)) return null
        val modalSide = if (showing) animation.enter else animation.exit
        val underSide = if (showing) animation.exit else animation.enter
        val elements = AnimationRules.hasElements(animation)
        val side = if (elements) {
            modalSide.takeIf(AnimationRules::isSingleAlpha)
        } else {
            modalSide.takeIf(AnimationRules::sidePlays)
        }
        val screen = AppearanceDispatcher.visibleNode(record.root)
        return Motion(
            side = side,
            fades = elements && side == null,
            under = under,
            underSide = underSide.takeIf { !elements && under != null && AnimationRules.sidePlays(it) },
            elements = animation.takeIf { elements && under != null },
            from = if (showing) under else screen,
            to = if (showing) screen else under,
        )
    }

    // The layer under a record: the nearest record below it that comes in or shows; none for the root.
    private fun layerUnder(record: ModalRecord): ModalRecord? {
        val index = records.indexOf(record).let { if (it < 0) records.size else it }
        for (i in index - 1 downTo 0) {
            val below = records[i]
            if (below.effective == State.Presenting || below.effective == State.Shown) return below
        }
        return null
    }

    private fun isOut(layer: ModalRecord?): Boolean = layer?.isOut ?: (rootOut && host.root != null)

    private fun nodeOf(layer: ModalRecord?): ViewController<*>? = layer?.root ?: host.root

    // The layer comes back into its layer: a record's container at the place of [at], the root's view.
    private fun putBack(layer: ModalRecord?, at: ModalRecord?) {
        if (layer == null) {
            host.root?.let(views::putBackRoot)
            rootOut = false
        } else {
            views.putBack(layer, at)
            layer.isOut = false
        }
    }

    // [options] of a command go into the modal's visible screen, as `mergeOptions` would.
    private fun mergeInto(record: ModalRecord, options: Options) {
        if (record.root.isDestroyed) return
        val screen = AppearanceDispatcher.visibleNode(record.root)
        if (screen.isDestroyed) return
        screen.mergeOptions(options)
        optionsMergedInto(screen, options)
    }

    private inline fun whileTaking(work: () -> Unit) {
        taking = true
        try {
            work()
        } finally {
            taking = false
        }
    }

    private fun topOf(node: ViewController<*>): ViewController<*> {
        var current = node
        while (true) current = current.parentController ?: return current
    }

    companion object {
        /** How long a modal waits for its screens at most, unless `waitForRender` says otherwise. */
        const val WAIT_LIMIT_MILLIS = 500L

        // What counts for the top of the modals.
        private val TOP_STATES = setOf(State.Waiting, State.Presenting, State.Shown)

        // What a `dismissAllModals` takes (row 5): all but the old root's and what is gone.
        private val BATCHED = setOf(State.Waiting, State.Presenting, State.Shown, State.Dismissing, State.ClosedBelow)

        // What may leave with the batch's animation.
        private val ANIMATED_IN_BATCH = setOf(State.Presenting, State.Shown, State.Dismissing)

        // What keeps a layer under it covered.
        private val COVERING = setOf(State.Waiting, State.Presenting, State.Shown, State.Dismissing)

        // A record on its way out: a failure of its step ends it.
        private val LEAVING = setOf(State.Dismissing, State.ClosedBelow)
    }
}

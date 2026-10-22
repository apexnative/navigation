package dev.apexnative.navigation.viewcontrollers.modal

import dev.apexnative.navigation.options.ModalAnimationOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.viewcontrollers.component.RenderWait
import dev.apexnative.navigation.viewcontrollers.navigator.WaitingLayer
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * One modal of an activity: its layout's top node, its state, and what waits for its transitions. Every rule about
 * modals is a move of one record ([ModalCoordinator]); nothing else keeps a list of modals. While it waits to show it
 * is a [WaitingLayer] of its activity: a stack in it runs its commands at once and tells it when its top changes. Main
 * thread only.
 */
internal class ModalRecord(
    /** The top node of the modal's layout. */
    val root: ViewController<*>,
    /** The modal shows as a Material sheet (`modal.sheet`), decided before its root's view is made. */
    val isSheet: Boolean,
) : WaitingLayer {
    /** Where the modal is. */
    enum class State {
        /** Made and findable; waits for its screens, unseen. No appearance event went. */
        Waiting,

        /** Its show runs; its screens' `ComponentWillAppear` went. */
        Presenting,

        /** On screen; its show answered. */
        Shown,

        /** Its dismissal runs (a command, back, the user, a `dismissAllModals`). */
        Dismissing,

        /** Dismissed while a modal above it stays: reported and destroyed, its exit plays unseen. */
        ClosedBelow,

        /** A `setRoot` was accepted: commands don't find it; it goes with the old root. */
        WithOldRoot,

        /** Out of the list. */
        Gone,
    }

    /** The id `showModal` answers with, which `dismissModal` takes. */
    val rootId: String = root.id

    /** The state; the coordinator moves it. */
    var state = State.Waiting

    /** The state the record had when a `setRoot` took it ([State.WithOldRoot]). */
    var before = State.Waiting

    /**
     * The state for everything that isn't a command (the user, back, its own transitions, the layers): the one it
     * had before a `setRoot` took it, else its own.
     */
    val effective: State
        get() = if (state == State.WithOldRoot) before else state

    /** The answer of its `showModal`; none in a `setRoot`'s chain, or once answered. */
    var showAnswer: CommandAnswer? = null

    /** The step of a `setRoot`'s chain that waits for its show; none otherwise, or once told. */
    var chain: Chain? = null

    /** `ANN.ModalDismissed` went for it: never twice. */
    var reported = false

    /** It covers the layer under it once shown: not a sheet, not `overFullScreen` / `overCurrentContext`. */
    var coversUnder = false

    /** Its container is out of the modals layer: a modal over it covers it. */
    var isOut = false

    /** Its wait ended: it shows once every older waiting modal showed or went (N1). */
    var isReady = false

    /**
     * A modal of a `setRoot`'s layout made with the root, before its turn: it renders unseen while the root waits and
     * comes in, and neither shows nor counts for commands or back until its step of the chain lets it go.
     */
    var held = false

    /** The wait for its screens while it waits. */
    var wait: RenderWait? = null

    /** Stops its running show or dismissal where it is, without its end; none while nothing runs. */
    var stopAnimation: (() -> Unit)? = null

    /** The dismissals asked while it presents: the first runs at the end of the show. */
    val waitingDismissals = mutableListOf<Dismissal>()

    /** The commands that resolve with [rootId] at the end of its dismissal or its batch. */
    val answers = mutableListOf<CommandAnswer>()

    /** What its running dismissal reveals and who asked; none while it isn't dismissed on its own. */
    var ending: Ending? = null

    /** The `dismissAllModals` it goes with; none. */
    var batch: Any? = null

    /** The ids of its nodes, taken when they may go before the record does (closed below, a new root, the user). */
    var ids: Set<String> = emptySet()

    /** Its wait for the targets of its elements, after its content: what stops it. */
    var stopTargets: (() -> Unit)? = null

    /** That wait was asked: the next end of a wait shows the modal. */
    var targetsAwaited = false

    /** The 0.5 s of a wait without `waitForRender` passed: the targets get nothing of it any more. */
    var overLimit = false

    /** Cancels that limit. */
    var cancelLimit: () -> Unit = {}

    /** A stack in it changed its top while it waits: the coordinator's handler. */
    var screensChanged: () -> Unit = {}

    override val waitingNode: ViewController<*>?
        get() = root.takeIf { state == State.Waiting }

    override fun shownScreensChanged() = screensChanged()

    /** The ids of every node of the modal now. */
    fun takeIds() {
        ids = idsOf(root)
    }

    /**
     * A dismissal asked of the record: the command's answer (none for back and the user), whether it animates, and
     * its `animations.dismissModal` (none: the default one).
     */
    class Dismissal(
        val answer: CommandAnswer?,
        val animated: Boolean,
        val byUser: Boolean,
        val animation: ModalAnimationOptions? = null,
    )

    /**
     * A running dismissal: the layer it reveals when that had disappeared (its screens appear at the end), and whether
     * the user (or back) closed it — its ids stay answerable then.
     */
    class Ending(val revealed: ViewController<*>?, val byUser: Boolean)

    /** The step of a `setRoot`'s chain: [animated] for its last modal; [done] once the modal showed or went. */
    class Chain(val animated: Boolean, val done: () -> Unit)

    companion object {
        /** The ids of [node] and every node under it. */
        fun idsOf(node: ViewController<*>): Set<String> {
            val ids = LinkedHashSet<String>()
            collect(node, ids)
            return ids
        }

        private fun collect(node: ViewController<*>, into: MutableSet<String>) {
            into += node.id
            (node as? ParentController<*>)?.childControllers?.forEach { collect(it, into) }
        }

        /** `modalPresentationStyle` values that keep the layer under in place. */
        private val OVER_STYLES = setOf("overFullScreen", "overCurrentContext")

        /** The styles a sheet shows with: unset, `pageSheet`, `formSheet`. */
        private val SHEET_STYLES = setOf(null, "pageSheet", "formSheet")

        /**
         * Whether a modal with [options] (resolved, with the defaults) shows as a sheet: any `modal.sheet` field set,
         * and the style unset, `pageSheet` or `formSheet`.
         */
        fun isSheet(options: Options): Boolean = options.modal.sheet.hasValue && styleOf(options) in SHEET_STYLES

        /**
         * Whether a modal covers the layer under it at the end of its show: full screen, unless it is a sheet or
         * `overFullScreen` / `overCurrentContext`; an unknown style is unset.
         */
        fun coversUnder(isSheet: Boolean, options: Options): Boolean = !isSheet && styleOf(options) !in OVER_STYLES

        /** The styles `modalPresentationStyle` takes (`modalPresentationStyle`). */
        private val STYLES = setOf(
            "pageSheet", "formSheet", "fullScreen", "overFullScreen", "overCurrentContext", "currentContext", "popover",
        )

        // The style the options give; `null` and an unknown string (`'none'` too) read as unset.
        private fun styleOf(options: Options): String? = options.modalPresentationStyle.value?.takeIf { it in STYLES }
    }
}

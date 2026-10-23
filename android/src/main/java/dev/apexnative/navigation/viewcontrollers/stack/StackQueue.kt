package dev.apexnative.navigation.viewcontrollers.stack

import dev.apexnative.navigation.options.BottomTabsEnterExitAnimationOptions
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.StackAnimationOptions
import dev.apexnative.navigation.options.TopBarEnterExitAnimationOptions
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.viewcontrollers.bottomtabs.TabBarMotion
import dev.apexnative.navigation.viewcontrollers.component.Readiness
import dev.apexnative.navigation.viewcontrollers.component.RenderWait
import dev.apexnative.navigation.viewcontrollers.parent.OptionsScreen
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.BarMotion
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.BarTravel
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.BarVisibility
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.Custom
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.Elements
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.Fade
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.Motion
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.AnimationRules
import java.util.WeakHashMap

private typealias Node = ViewController<*>

/**
 * The one queue of a stack: every command of the stack, every back of the user and every return of its container goes
 * through it, runs by one table and answers once. It knows no views: it drives a [StackDisplay], the readiness of the
 * screens and a clock, so the unit tests run it with fakes. Main thread only.
 *
 * It keeps two lists, bottom to top. [entries] is the stack as JavaScript sees it: changed at once when an operation
 * is accepted, and every check of a later command reads it. [shown] is what the container shows, changed as each
 * operation runs; its last is the current child. The state is "rest", "waiting" (the newest started operation, a push
 * or a `setStackRoot`, waits for its screen to render) or "transition" (transitions run; on Android they may overlap).
 *
 * The table, as the state reads it: a push waits behind a waiting push and completes a waiting `setStackRoot` at
 * once; a cut (pop, `popTo`, `popToRoot`, a key that goes back, back of the user) and a replace (`setStackRoot`, the
 * split view's `setRoot`) interrupt a waiting push and complete a waiting `setStackRoot` at once; a cut or a replace
 * accepted while pushes are queued interrupts those it removes; a `none` (`popTo` of the top) waits behind a waiting
 * operation. Otherwise everything runs at once: a transition that starts takes the screens it moves over from the
 * transitions running, which stop them where they are, and each transition ends when the last screen it still owns
 * comes to rest, reporting only what it still owns. A running push whose screens a cut takes over ends then; a pop
 * never does; a replace accepted while a `setStackRoot` runs completes it at once, and the end of a replace stops only
 * the screens it destroys. Off screen or without a view, every operation runs at once, without a wait or a transition.
 * Each end: `ANN.ComponentWillAppear` at the start; at the end `DidDisappear`, `DidAppear`, the navigation state,
 * `ANN.ScreenPopped` of each screen that left (top to bottom), their destroys, the answer, `ANN.CommandCompleted`
 * (`AppearanceDispatcher.endTransition`).
 *
 * Its waits for a screen to render are the core's one wait ([RenderWait]), on the screen it pushes or sets as the top;
 * the readiness signals reach it through [deliver] (the stack posts them to the next turn of the main loop: a signal
 * that comes inside a layout pass or a view's attach moves no view of the container there). A push of a screen with
 * React content waits for its first mount: [PUSH_WAIT_MILLIS] at most without `waitForRender`, as long as it takes
 * with `true`, not at all with `false`; its signal reaches the wait through [afterCalls], behind the calls JavaScript
 * made in the commit of that mount (its `mergeOptions`), so the transition starts with the options of that commit —
 * content mounted already goes that way too. A push or a `setStackRoot` with elements waits for its targets after
 * that, a wait like the others, within what is left of its limit without `waitForRender`: a cut or a replace during it
 * interrupts the push, which moves no view into a layer.
 *
 * The transition is the screens' own when the options say so (`AnimationRules.stackIsOwn`): the display plays each
 * screen's side ([StackDisplay.animateCustom]) with the same owners as the default transitions; the bar's row then
 * changes at once, and with elements the bars show or hide at once too.
 */
internal class StackQueue(
    display: StackDisplay,
    private val readiness: Readiness,
    private val scheduler: AppearanceDispatcher.Scheduler,
    private val host: Host,
    private val deliver: (() -> Unit) -> Unit = { it() },
    /**
     * How the mount of a pushed screen reaches the push's wait: on the main thread behind every call JavaScript made
     * before it (the stack: `PendingCalls`, on a later turn of the main loop); at once by default.
     */
    private val afterCalls: (() -> Unit) -> Unit = { it() },
) {
    /** What the queue tells the stack. */
    interface Host {
        /** The current child (the top of [shown]) changed: a transition started, or an operation ran at once. */
        fun currentChildChanged()

        /** An operation was accepted: a back gesture's preview that shows ends, the stack as it was. */
        fun operationAccepted() = Unit

        /**
         * The current child is about to change, right before [currentChildChanged]: [motion] is the default transition
         * the bar's row goes with, none when the change comes at once or with an animation of its own; [visibility] is
         * how the bar shows or hides when the new top wants it otherwise; [travel]: the bar goes with the screens of an
         * animation of its own without elements.
         */
        fun barTransition(
            motion: StackDisplay.BarMotion?,
            visibility: BarVisibility,
            travel: StackDisplay.BarTravel? = null,
        ) = Unit

        /**
         * The current child is about to change, right before [barTransition]: [move] is how the transition goes, which
         * the tab bar of tabs around the stack follows when the new top shows or hides it; [side] the transition's
         * `bottomTabs` with [TabBarMotion.Move.Slide].
         */
        fun tabBarTransition(move: TabBarMotion.Move, side: BottomTabsEnterExitAnimationOptions?) = Unit

        /** [node] was accepted to show as the top (a push, the new top of a replace). */
        fun willShow(node: Node) = Unit

        /**
         * An operation ended or was rejected: what back does may have changed. [topSettled]: the screen on top came to
         * rest — the queue is at rest, or the operation that ended brought the current child (the FAB follows it then).
         */
        fun operationEnded(topSettled: Boolean)

        /**
         * Whether [node] shows React content whose first mount its push waits for: a React screen (a root of its own or
         * a slot), or an entry that shows one. A native screen has none: its push starts at once.
         */
        fun hasReactContent(node: Node): Boolean = false

        /** One line for the log: a failure that has no command to answer. */
        fun log(text: String) = Unit

        /** The id the command of [kind] answers with for [entry] (`StackController.answerIdOf`). */
        fun answerIdOf(entry: Node, kind: AnswerKind): String
    }

    /** The command an answer is for: what the stack answers with depends on it ([Host.answerIdOf]). */
    enum class AnswerKind {
        /** `push`. */
        Push,

        /** `setStackRoot`, and the split view's `showInColumn` through it. */
        Replace,

        /** `pop`, `popToRoot`, a key that goes back to its entry. */
        Back,
    }

    /** What shows the stack; the unit tests may give another. */
    var display: StackDisplay = display

    private val entryList = ArrayList<Node>()
    private val shownList = ArrayList<Node>()
    // Screens that left [shownList] and are not destroyed yet: their transition still moves them.
    private val leavingList = ArrayList<Node>()
    private var waiting: Op? = null
    private val queued = ArrayDeque<Op>()
    private val running = ArrayList<Op>()
    // Lower screens whose content isn't made yet, and those placed in the container until it is.
    private val unrendered = LinkedHashSet<Node>()
    private val lowerWaits = HashMap<Node, () -> Unit>()
    // The operation, by its sequence, whose acceptance took each entry away.
    private val removedAt = WeakHashMap<Node, Int>()
    private var sequence = 0
    private var draining = false
    // Operations end because a newer one took over all they moved (finishTakenOver).
    private var takenOver = false
    private var destroyed = false

    /** [node]'s content is made and nothing waits for it to render as a lower screen. */
    fun isRendered(node: Node): Boolean = node !in unrendered && node !in lowerWaits

    /** The stack as JavaScript sees it, bottom to top. */
    val entries: List<Node>
        get() = entryList

    /** What the stack's container shows, bottom to top. */
    val shown: List<Node>
        get() = shownList

    /** The top of [shown]: the screen the user sees; none before the first. */
    val currentChild: Node?
        get() = shownList.lastOrNull()

    /** The screen of the operation that waits for it to render; none. */
    val waitingScreen: Node?
        get() = when (val op = waiting) {
            is PushOp -> op.node
            is ReplaceOp -> op.top
            else -> null
        }

    /** Nothing runs, nothing waits, nothing is queued. */
    val isAtRest: Boolean
        get() = waiting == null && running.isEmpty() && queued.isEmpty()

    /**
     * Every node the stack holds, bottom first: [entries] first (the split view reads the first screen there), then
     * what still shows or leaves, and the nodes of operations accepted and not run yet.
     */
    fun nodes(): List<Node> {
        val nodes = LinkedHashSet<Node>(entryList)
        nodes += shownList
        nodes += leavingList
        (waiting as? ReplaceOp)?.let { nodes += it.nodes }
        queued.forEach { if (it is PushOp) nodes += it.node }
        return nodes.toList()
    }

    /** The screens a stack is made with (a root, a tab, a modal): the last shows, the others render after it. */
    fun setInitial(screens: List<Node>) {
        entryList += screens
        shownList += screens
        unrendered += screens.dropLast(1)
    }

    // Acceptance: the lists JavaScript relies on change now; the operation runs in its turn.

    /** `push` of [node], accepted: it is an entry now, and runs in its turn ([answer] with its id). */
    fun push(node: Node, answer: CommandAnswer?, componentId: String, what: String, prepared: Boolean = false) {
        if (destroyed) return refuse(answer, null, componentId, listOf(node))
        entryList += node
        host.willShow(node)
        val op = PushOp(nextSequence(), answer, componentId, what, node, prepared)
        // A push completes a waiting `setStackRoot` at once, whatever waits behind it.
        (waiting as? ReplaceOp)?.let(::completeAtOnce)
        enqueue(op)
    }

    /**
     * A cut to [target], an entry that becomes the top: every entry above it leaves. [answer] gets [value]; a back of
     * the user has none. [silent]: no animation and no appearance events (`popToRootOnBlur`). [gesture]: the release of
     * a back gesture whose preview shows the pop already (`StackDisplay.finishBackGesture` ends it, the bar shows the
     * target at once).
     */
    fun cut(
        target: Node,
        answer: CommandAnswer?,
        componentId: String,
        what: String,
        value: String?,
        silent: Boolean = false,
        gesture: Boolean = false,
    ) {
        val index = entryList.indexOf(target)
        if (destroyed || index < 0) return refuse(answer, null, componentId, emptyList())
        val removed = entryList.subList(index + 1, entryList.size).toList()
        entryList.subList(index + 1, entryList.size).clear()
        val seq = nextSequence()
        removed.forEach { removedAt[it] = seq }
        interruptPushesOf(removed)
        (waiting as? ReplaceOp)?.let(::completeAtOnce)
        val showsAbove = target in shownList && shownList.last() !== target
        val op = if (showsAbove) {
            CutOp(seq, answer, componentId, what, target, value, silent, gesture)
        } else {
            // A cut that moves nothing more: its answer at its turn.
            NoneOp(seq, answer, null, componentId, what, value, null)
        }
        enqueue(op)
    }

    /**
     * A replace of every entry by [nodes] (`setStackRoot`, the split view's `setRoot`): [animated] or not, [asPush]
     * with the push transition; [waits] for its new top to render (not for the split view). [answer] or [listener] get
     * the id of the new top.
     */
    fun replace(
        nodes: List<Node>,
        animated: Boolean,
        asPush: Boolean,
        answer: CommandAnswer?,
        listener: CommandListener?,
        componentId: String,
        what: String,
        waits: Boolean = true,
    ) {
        if (destroyed || nodes.isEmpty()) return refuse(answer, listener, componentId, nodes)
        val old = entryList.toList()
        entryList.clear()
        entryList += nodes
        host.willShow(nodes.last())
        val seq = nextSequence()
        old.forEach { if (it !in nodes) removedAt[it] = seq }
        interruptPushesOf(old)
        (waiting as? ReplaceOp)?.let(::completeAtOnce)
        running.filterIsInstance<ReplaceOp>().forEach(::completeRunning)
        enqueue(ReplaceOp(seq, answer, listener, componentId, what, nodes.toList(), animated, asPush, waits))
    }

    /**
     * An operation that moves nothing (`popTo` of the top): [value] in its turn, decided at its acceptance (C6); it
     * rejects only when an operation accepted before it took [target] away.
     */
    fun none(value: String, target: Node?, answer: CommandAnswer?, componentId: String, what: String) {
        if (destroyed) return refuse(answer, null, componentId, emptyList())
        enqueue(NoneOp(nextSequence(), answer, null, componentId, what, value, target))
    }

    // An operation that comes to a destroyed stack, or for a target that isn't an entry: it rejects, and what was made
    // for it goes (nothing hangs).
    private fun refuse(answer: CommandAnswer?, listener: CommandListener?, componentId: String, made: List<Node>) {
        made.forEach { if (it !in entryList) it.destroy() }
        val text = ErrorTexts.screenNotShown(componentId)
        answer?.reject(text)
        listener?.onError(text)
    }

    /**
     * The stack went off screen (its tab unselected, a modal over it): what waits for its screen runs now as off
     * screen, without a transition and without marking the content ready.
     */
    fun wentOffScreen() {
        val op = waiting ?: return
        stopWaiting(op)
        guarded(op) {
            when (op) {
                is PushOp -> pushAtOnce(op)
                is ReplaceOp -> beginReplace(op, atOnce = true)
                else -> Unit
            }
        }
        drain()
    }

    /**
     * The lower screens make their content in the window: each is placed in the container under the top until its
     * content is ready or [LOWER_SCREEN_MILLIS] passed. Only while the stack is on screen; the others wait for its next
     * appearance.
     */
    fun renderLowerScreens() {
        if (destroyed || !onScreen()) return
        val top = currentChild
        for (screen in unrendered.toList()) {
            unrendered -= screen
            if (screen === top || screen !in shownList || screen.isDestroyed) continue
            protectLower(screen) { placeLower(screen) }
        }
    }

    /**
     * The stack is destroyed (a new root, its modal dismissed, its tab gone): what waits, what is queued and what
     * runs rejects with `the screen '<id>' is not shown`, in the order they came; their animators stop (on a copy); a
     * back of the user ends without a word. The nodes stay for the stack to destroy.
     */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        val ops = ArrayList<Op>()
        waiting?.let {
            stopWaiting(it)
            ops += it
        }
        ops += queued
        queued.clear()
        val transitions = running.toList()
        running.clear()
        for (op in transitions) {
            op.animating.toList().forEach(display::stop)
            op.animating.clear()
            op.owned.clear()
            ops += op
        }
        lowerWaits.values.toList().forEach { it() }
        lowerWaits.clear()
        for (op in ops.sortedBy { it.seq }) {
            op.cancelLimit?.invoke()
            if (op.done) continue
            op.done = true
            reject(op, ErrorTexts.screenNotShown(op.componentId))
        }
    }

    // The table.

    private fun enqueue(op: Op) {
        // Anything but the release of a back gesture ends its preview first.
        if (!(op is CutOp && op.gesture)) host.operationAccepted()
        queued.addLast(op)
        drain()
    }

    private fun drain() {
        if (draining) return
        draining = true
        try {
            while (!destroyed) {
                val op = queued.firstOrNull() ?: break
                val current = waiting
                if (current != null) {
                    if (current is ReplaceOp && op !is NoneOp) {
                        completeAtOnce(current)
                        continue
                    }
                    if (current is PushOp && (op is CutOp || op is ReplaceOp) && current.node !in entryList) {
                        interrupt(current)
                        continue
                    }
                    break
                }
                queued.removeFirst()
                start(op)
            }
        } finally {
            draining = false
        }
    }

    private fun start(op: Op) {
        if (!op.isCurrent()) {
            // The JavaScript of the command went: nothing moves, and the reset takes the stack.
            op.done = true
            return
        }
        guarded(op) {
            when (op) {
                is NoneOp -> runNone(op)
                is PushOp -> startPush(op)
                is CutOp -> startCut(op)
                is ReplaceOp -> startReplace(op)
            }
        }
    }

    // None.

    private fun runNone(op: NoneOp) {
        op.done = true
        val target = op.target
        val removedBy = target?.let { removedAt[it] }
        if (target != null && target !in entryList && removedBy != null && removedBy < op.seq) {
            reject(op, ErrorTexts.screenNotShown(op.componentId))
        } else {
            op.value?.let { resolve(op, it) }
        }
        settled()
    }

    // Push.

    private fun startPush(op: PushOp) {
        // Its node went before its turn (a completed iOS swipe; here, the defense): it resolves as interrupted (C8).
        if (op.node !in entryList) return interrupt(op)
        if (!onScreen()) return pushAtOnce(op)
        val node = op.node
        val push = resolved(node).animations.push
        op.awaitsTargets = awaitsTargets(push, !push.enabled.isFalse)
        // A prepared screen rendered before its push; `false`, and a node without React content, start at once.
        val wait = when {
            op.prepared -> Wait.Prepared
            push.waitForRender.isTrue -> Wait.Content
            push.waitForRender.isFalse || !host.hasReactContent(node) -> Wait.None
            else -> Wait.Limited
        }
        if (wait == Wait.None) return if (op.awaitsTargets) awaitTargets(op) else beginPush(op)
        waiting = op
        when (wait) {
            Wait.Prepared -> awaitPrepared(op)
            Wait.Content -> {
                // Then the components of the bar drawn in advance, 0.5 s at most after the content (a title or a
                // background with its own `waitForRender: false` not). React content mounted already goes behind the
                // calls too; a native screen starts at once.
                display.placeWaiting(node)
                awaitContent(
                    op,
                    node,
                    limit = null,
                    bar = RenderWait.Bar.AfterContent,
                    deliver = afterCalls,
                    deliversReady = host.hasReactContent(node),
                )
            }
            else -> {
                // The screen renders hidden under the top, its signal (or its content mounted already) behind the
                // calls; the bar's components don't count. The 0.5 s hold for the content and the targets together.
                display.placeWaiting(node)
                op.cancelLimit = scheduler.schedule(PUSH_WAIT_MILLIS) { op.overLimit = true }
                awaitContent(
                    op,
                    node,
                    limit = PUSH_WAIT_MILLIS,
                    bar = RenderWait.Bar.None,
                    deliver = afterCalls,
                    deliversReady = true,
                )
            }
        }
    }

    private fun beginPush(op: PushOp) {
        op.cancelLimit?.invoke()
        val node = op.node
        val covered = shownList.lastOrNull()
        // The views first: a failure here leaves the lists as they were.
        display.raise(node)
        shownList += node
        unrendered -= node
        op.covered = covered
        val moved = listOfNotNull(covered, node)
        val ended = takeOver(moved, op)
        // The arriving screen's `animations.push`; Android has no zoom.
        val push = resolved(node).animations.push
        val animated = display.canAnimate && !push.enabled.isFalse
        val own = animated && AnimationRules.stackIsOwn(push)
        val elements = own && AnimationRules.hasElements(push)
        // The bar's row goes with the default transition only; with an animation of its own it changes at once. The tab
        // bar moves with its screens or by its own side.
        tellBars(animated, own, elements, push, TabBarMotion.Move.WithPush, BarMotion.Push, BarTravel.Forward)
        host.currentChildChanged()
        finishTakenOver(ended)
        running += op
        AppearanceDispatcher.beginTransition(listOf(node))
        op.owned += moved
        // Without animation the screen shows at the moment the animated one would start: not a transition.
        if (!animated) {
            display.rest(node)
            return finishPush(op)
        }
        if (own) {
            op.keepsEnd = true
            return animate(op, forwardMoves(node, covered, push, elements))
        }
        animate(op, listOfNotNull(Move(node, Motion.PushIn, false), covered?.let { Move(it, Motion.PushCover, true) }))
    }

    private fun finishPush(op: PushOp) {
        op.done = true
        running -= op
        val node = op.node
        val covered = op.covered
        val hiding = ArrayList<Node>(1)
        if (covered != null && covered in op.owned) {
            hiding += covered
            // The covered screen leaves the container now, not before; its React root lives.
            if (covered !== currentChild && covered in shownList) display.detach(covered)
        }
        // The screen that came by its own `enter` stays at its end values.
        if (node in op.owned && !op.keepsEnd) display.rest(node)
        val showing = if (node === currentChild && onScreen()) listOf(node) else emptyList()
        AppearanceDispatcher.endTransition(hiding, showing, null, op.answer, host.answerIdOf(node, AnswerKind.Push))
        settled(node)
    }

    // Off screen or without a view: at once, without a transition; the top appears when the stack shows.
    private fun pushAtOnce(op: PushOp) {
        op.cancelLimit?.invoke()
        val node = op.node
        val covered = shownList.lastOrNull()
        if (display.hasView) {
            display.raise(node)
            display.rest(node)
        }
        shownList += node
        unrendered -= node
        val ended = takeOver(listOfNotNull(covered, node), op)
        host.tabBarTransition(TabBarMotion.Move.AtOnce, null)
        host.barTransition(null, BarVisibility.AT_ONCE)
        host.currentChildChanged()
        finishTakenOver(ended)
        if (display.hasView && covered != null) display.detach(covered)
        op.done = true
        val value = host.answerIdOf(node, AnswerKind.Push)
        AppearanceDispatcher.endTransition(listOfNotNull(covered), emptyList(), null, op.answer, value)
        settled(node)
    }

    // A push whose screen never appeared ends: its wait ends, its screen goes without appearance events and without
    // `ANN.ScreenPopped` (its destroy's own events go), it resolves with its id, and the screen under it is not
    // touched. It runs outside any guard (the acceptance of a cut or a replace, the drain): a teardown that fails is
    // logged, so the push still answers and the operation that took its screen is still queued.
    private fun interrupt(op: PushOp) {
        if (op.done) return
        op.done = true
        if (waiting === op) stopWaiting(op)
        op.cancelLimit?.invoke()
        queued.remove(op)
        val node = op.node
        entryList.remove(node)
        // The view and the node go each on their own: a view that fails to leave doesn't keep the screen alive.
        try {
            if (display.hasView) display.remove(node)
        } catch (e: Throwable) {
            val reason = ErrorTexts.couldNot("remove the view of the screen '${node.id}'", ErrorTexts.reasonOf(e))
            host.log("[ApexNavigation] $reason")
        }
        try {
            node.destroy()
        } catch (e: Throwable) {
            val reason = ErrorTexts.couldNot("destroy the screen '${node.id}'", ErrorTexts.reasonOf(e))
            host.log("[ApexNavigation] $reason")
        }
        op.answer?.resolve(host.answerIdOf(node, AnswerKind.Push))
        settled()
    }

    // Every push accepted or waiting whose screen [removed] takes away, oldest first.
    private fun interruptPushesOf(removed: Collection<Node>) {
        if (removed.isEmpty()) return
        val pushes = ArrayList<PushOp>()
        (waiting as? PushOp)?.let { if (it.node in removed) pushes += it }
        queued.forEach { if (it is PushOp && it.node in removed) pushes += it }
        pushes.sortedBy { it.seq }.forEach(::interrupt)
    }

    // Cut.

    private fun startCut(op: CutOp) {
        val target = op.target
        if (target !in shownList) {
            // Its target went before its turn: a command rejects, a back of the user ends.
            op.done = true
            reject(op, ErrorTexts.screenNotShown(op.componentId))
            return settled()
        }
        val index = shownList.indexOf(target)
        val leaving = shownList.subList(index + 1, shownList.size).asReversed().toList()
        if (leaving.isEmpty()) {
            op.done = true
            op.value?.let { resolve(op, it) }
            return settled()
        }
        val top = leaving.first()
        val atOnce = !onScreen()
        val moving = running.any { target in it.animating }
        if (display.hasView) display.reveal(target, top)
        // The screens that leave let go of the focus (`blurOnUnmount`).
        leaving.forEach { it.onLeaveStarted() }
        endLowerWaits(leaving + target)
        shownList.subList(index + 1, shownList.size).clear()
        leavingList += leaving
        unrendered -= target
        unrendered.removeAll(leaving.toSet())
        op.leaving = leaving
        val moved = leaving + target
        val ended = takeOver(moved, op)
        // The leaving top's `animations.pop` decides, `enabled` too.
        val pop = resolved(top).animations.pop
        val animated = !atOnce && !op.silent && display.canAnimate && !pop.enabled.isFalse
        val own = animated && AnimationRules.stackIsOwn(pop)
        val elements = own && AnimationRules.hasElements(pop)
        // A back gesture's preview shows the target and its bar already: the bar stays as it is.
        val gesture = op.gesture && animated && !own && leaving.size == 1 && display.hasBackPreview(top, target)
        tellBars(animated, own, elements, pop, TabBarMotion.Move.WithPop, BarMotion.Pop, BarTravel.Back, gesture)
        host.currentChildChanged()
        finishTakenOver(ended)
        if (display.hasView) target.onViewBroughtToFront()
        op.owned += moved
        if (atOnce) return finishCut(op)
        running += op
        if (!op.silent) AppearanceDispatcher.beginTransition(listOf(target))
        // The screens between the top and the target leave without animation.
        leaving.drop(1).forEach(display::hide)
        if (!animated) return finishCut(op)
        if (gesture) {
            op.animating += top
            op.animating += target
            display.finishBackGesture(
                top,
                target,
                { step(op) { screenDone(op, top) } },
                { step(op) { screenDone(op, target) } },
            )
            return
        }
        // Its own: no wait on a pop; the leaving screen starts from where it is.
        if (own) return animate(op, backMoves(top, target, pop, elements, moving))
        animate(op, listOf(Move(top, Motion.PopOut, true), Move(target, Motion.PopReveal, moving)))
    }

    private fun finishCut(op: CutOp) {
        op.done = true
        running -= op
        val target = op.target
        if (target in op.owned && display.hasView) display.rest(target)
        val showing = if (target === currentChild && onScreen() && !op.silent) listOf(target) else emptyList()
        val leaving = op.leaving
        val events = { popAndDestroy(leaving, emptyList()) }
        AppearanceDispatcher.endTransition(leaving, showing, events, op.answer, op.value)
        settled(target)
    }

    // Replace.

    private fun startReplace(op: ReplaceOp) {
        if (!onScreen() || !op.waits) return beginReplace(op, atOnce = !onScreen())
        val top = op.top
        val setStackRoot = resolved(top).animations.setStackRoot
        val waitForRender = setStackRoot.waitForRender
        // `enabled: false`: the screens stand at once, without the wait (`animations.setStackRoot.enabled`).
        if (waitForRender.isFalse || !op.animated) return beginReplace(op, atOnce = false)
        // Elements play as at a push and wait the same way; with `asPush` the push plays without them.
        op.awaitsTargets = !op.asPush && awaitsTargets(setStackRoot, true)
        waiting = op
        display.placeWaiting(top)
        // The components of the bar drawn in advance join the wait: within the same 0.5 s without `waitForRender`;
        // after the content, 0.5 s at most, with `true`. The targets take what is left of the 0.5 s without it.
        if (waitForRender.isTrue) {
            awaitContent(op, top, limit = null, bar = RenderWait.Bar.AfterContent)
        } else {
            op.cancelLimit = scheduler.schedule(SET_STACK_ROOT_WAIT_MILLIS) { op.overLimit = true }
            awaitContent(op, top, limit = SET_STACK_ROOT_WAIT_MILLIS, bar = RenderWait.Bar.WithinLimit)
        }
    }

    private fun beginReplace(op: ReplaceOp, atOnce: Boolean) {
        op.cancelLimit?.invoke()
        val top = op.top
        val old = shownList.toList()
        val placed = if (display.hasView) old.filter(display::isPlaced) else emptyList()
        if (display.hasView) display.raise(top)
        old.forEach { it.onLeaveStarted() }
        endLowerWaits(old)
        shownList.clear()
        shownList += op.nodes
        leavingList += old
        unrendered.removeAll(old.toSet())
        unrendered += op.nodes.dropLast(1)
        op.old = old
        val moved = old + top
        val ended = takeOver(moved, op)
        // The new top's `animations.setStackRoot`; with `asPush` its `animations.push` without elements, which wins
        // over a `content` of its own.
        val animations = resolved(top).animations
        val played: StackAnimationOptions = if (op.asPush) animations.push else animations.setStackRoot
        val animated = !atOnce && op.animated && display.canAnimate
        val own = animated && if (op.asPush) AnimationRules.contentIsOwn(played) else AnimationRules.stackIsOwn(played)
        val elements = own && !op.asPush && AnimationRules.hasElements(played)
        val motion = if (op.asPush) BarMotion.Push else BarMotion.FadeThrough
        val system = if (op.asPush) TabBarMotion.Move.WithPush else TabBarMotion.Move.Slide
        tellBars(animated, own, elements, played, system, motion, BarTravel.Forward)
        host.currentChildChanged()
        finishTakenOver(ended)
        op.owned += moved
        if (atOnce) {
            if (display.hasView) display.rest(top)
            return finishReplace(op)
        }
        running += op
        AppearanceDispatcher.beginTransition(listOf(top))
        if (!op.animated || !display.canAnimate) {
            display.rest(top)
            return finishReplace(op)
        }
        // Its own: the new top's `content.enter` (and the old top's `content.exit`) instead of the fade through; the
        // other old screens hide at once. The old title's picture goes only with the fade through.
        if (own) {
            op.keepsEnd = true
            placed.dropLast(1).forEach(display::hide)
            return animate(op, forwardMoves(top, placed.lastOrNull(), played, elements))
        }
        if (op.asPush) {
            placed.dropLast(1).forEach(display::hide)
            animate(op, listOfNotNull(Move(top, Motion.PushIn, false), placed.lastOrNull()?.let {
                Move(it, Motion.PushCover, true)
            }))
        } else {
            animate(op, listOf(Move(top, Motion.FadeIn, false)) + placed.map { Move(it, Motion.FadeOut, true) })
        }
    }

    private fun finishReplace(op: ReplaceOp) {
        op.done = true
        running -= op
        // Its end stops the animations of the screens it destroys only: a push started after it plays on.
        finishAll(takeOver(op.old, op))
        val top = op.top
        if (top in op.owned && display.hasView && !op.keepsEnd) display.rest(top)
        val showing = if (top === currentChild && onScreen()) listOf(top) else emptyList()
        endReplace(op, showing)
        renderLowerScreens()
    }

    // A waiting `setStackRoot` that a push, a cut or a replace completes at once: no transition, its new top lies under
    // what comes next and appears when it is seen.
    private fun completeAtOnce(op: ReplaceOp) {
        if (op.done) return
        stopWaiting(op)
        op.cancelLimit?.invoke()
        guarded(op) {
            val top = op.top
            val old = shownList.toList()
            if (display.hasView) {
                display.raise(top)
                display.rest(top)
            }
            old.forEach { it.onLeaveStarted() }
            endLowerWaits(old)
            shownList.clear()
            shownList += op.nodes
            leavingList += old
            unrendered.removeAll(old.toSet())
            unrendered += op.nodes.dropLast(1)
            op.old = old
            val ended = takeOver(old + top, op)
            host.tabBarTransition(TabBarMotion.Move.AtOnce, null)
            host.barTransition(null, BarVisibility.AT_ONCE)
            host.currentChildChanged()
            finishTakenOver(ended)
            op.done = true
            endReplace(op, emptyList())
            renderLowerScreens()
        }
    }

    // A running `setStackRoot` that a newer replace completes at once.
    private fun completeRunning(op: ReplaceOp) {
        if (op.done) return
        op.animating.toList().forEach(display::stop)
        op.animating.clear()
        guarded(op) { finishReplace(op) }
    }

    // The end of a replace: the old screens disappear, `ANN.ScreenPopped` for each whose id no new screen takes (top to
    // bottom), the destroys, those with a reused id last; then the answer.
    private fun endReplace(op: ReplaceOp, showing: List<Node>) {
        val ids = op.nodes.mapTo(HashSet()) { it.id }
        val old = op.old.asReversed()
        val reused = old.filter { it.id in ids }
        val popped = old.filter { it.id !in ids }
        val value = host.answerIdOf(op.top, AnswerKind.Replace)
        AppearanceDispatcher.endTransition(old, showing, { popAndDestroy(popped, reused) }, op.answer, value)
        if (!op.told) op.listener?.onSuccess(value)
        op.told = true
        settled(op.top)
    }

    // `ANN.ScreenPopped` for each of [popped], then their destroys (the content stops only after all the events), then
    // [after], destroyed without the event.
    private fun popAndDestroy(popped: List<Node>, after: List<Node>) {
        popped.forEach(AppearanceDispatcher::screenPopped)
        for (node in popped + after) {
            leavingList -= node
            node.destroy()
            if (display.hasView) display.remove(node)
        }
    }

    // Waits.

    // The one wait (RenderWait) for [screen], the node the operation brings: at [limit] the content not ready counts
    // as mounted; without one, `waitForRender: true` waits as long as it takes, except for content the readiness store
    // knows nothing of after 1 s, which counts as ready (the same rule as iOS). A push's signals come through
    // [afterCalls] ([deliver]); with [deliversReady], so does content ready at the start.
    private fun awaitContent(
        op: Op,
        screen: Node,
        limit: Long?,
        bar: RenderWait.Bar,
        deliver: (() -> Unit) -> Unit = this.deliver,
        deliversReady: Boolean = false,
    ) {
        val wait = RenderWait(
            readiness = readiness,
            scheduler = scheduler,
            limitMillis = limit,
            bar = bar,
            countsUnknownContent = true,
            deliver = deliver,
            deliversReadyContent = deliversReady,
            step = { work -> step(op) { work() } },
            onEnd = { endWait(op) },
        )
        op.wait = wait
        op.waitedNode = screen
        // An entry that isn't a screen (a pair) waits for the screens it shows, as a root does.
        wait.start(RenderWait.waitedScreens(screen))
    }

    /**
     * The entry [node] (a pair) changed the screens it shows: the operation that waits for it to render waits for
     * those it shows now, and no longer for one that left the window.
     */
    fun shownScreensChanged(node: Node) {
        val op = waiting ?: return
        if (op.waitedNode !== node) return
        val wait = op.wait ?: return
        step(op) { wait.retake(RenderWait.waitedScreens(node)) }
    }

    // The prepared screen's slot mounts (or the display's limit passes), then its transition starts behind the calls
    // JavaScript made before ([afterCalls]): a commit that came after the push (its JavaScript's own limit) brings its
    // `mergeOptions` first. The same 0.5 s from the start hold for both: the transition starts then anyway.
    private fun awaitPrepared(op: PushOp) {
        val token = Any()
        op.waitToken = token
        val node = op.node
        val still = { !destroyed && !op.done && op.waitToken === token && waiting === op && op.isCurrent() }
        var mounted = false
        val markMounted = {
            if (!mounted && !readiness.isReady(node)) readiness.markReady(node)
            mounted = true
        }
        val start = { step(op) { if (still()) endWait(op) } }
        op.cancelCap = scheduler.schedule(PUSH_WAIT_MILLIS) {
            step(op) {
                if (!still()) return@step
                markMounted()
                endWait(op)
            }
        }
        display.awaitPrepared(
            node,
            waiting = still,
            mounted = { step(op) { markMounted() } },
            start = { afterCalls(start) },
        )
    }

    // The content is there: then the targets, when the transition has elements; else its transition.
    private fun endWait(op: Op) {
        stopWaiting(op)
        when {
            op.awaitsTargets && onScreen() -> return awaitTargets(op)
            op is PushOp -> beginPush(op)
            op is ReplaceOp -> beginReplace(op, atOnce = !onScreen())
        }
        drain()
    }

    // The wait for the targets of the transition's elements, behind the display: the new top renders in the container
    // meanwhile; the screen on top now is where the elements come from.
    private fun awaitTargets(op: Op) {
        op.awaitsTargets = false
        val (to, animation) = when (op) {
            is PushOp -> op.node to resolved(op.node).animations.push
            is ReplaceOp -> op.top to resolved(op.top).animations.setStackRoot
            else -> return
        }
        waiting = op
        if (!display.isPlaced(to)) display.placeWaiting(to)
        val token = Any()
        op.waitToken = token
        val still = { !destroyed && !op.done && op.waitToken === token && waiting === op && op.isCurrent() }
        // Without `waitForRender` the 0.5 s of a `setStackRoot` and of a push of React content hold for the content
        // and the targets together.
        val from = currentChild
        op.stopTargets = display.awaitTargets(animation, from, to, { still() && !op.overLimit }) {
            step(op) { if (still()) endWait(op) }
        }
    }

    private fun stopWaiting(op: Op) {
        if (waiting === op) waiting = null
        op.cancelCap?.invoke()
        op.cancelCap = null
        op.waitToken = null
        op.wait?.cancel()
        op.wait = null
        op.stopTargets?.invoke()
        op.stopTargets = null
    }

    // Lower screens.

    private fun placeLower(screen: Node) {
        display.placeLower(screen)
        var ended = false
        var cancelLimit: () -> Unit = {}
        val end = {
            if (!ended) {
                ended = true
                cancelLimit()
                lowerWaits.remove(screen)
                val moved = running.any { screen in it.owned }
                if (!destroyed && screen !== currentChild && screen in shownList && !moved) {
                    protectLower(screen) { display.detach(screen) }
                }
            }
        }
        lowerWaits[screen] = {
            ended = true
            cancelLimit()
        }
        cancelLimit = scheduler.schedule(LOWER_SCREEN_MILLIS) { end() }
        if (readiness.isReady(screen)) end() else readiness.whenReady(screen) { deliver { end() } }
    }

    // The steps of a lower screen come later than any command, under no runner: a failure is logged, the stack goes on.
    private inline fun protectLower(screen: Node, work: () -> Unit) {
        try {
            work()
        } catch (e: Throwable) {
            val reason = ErrorTexts.couldNot("make the content of the screen '${screen.id}'", ErrorTexts.reasonOf(e))
            host.log("[ApexNavigation] $reason")
        }
    }

    // A cut or a replace reaches a lower screen still placed: its wait ends, and the operation moves its view.
    private fun endLowerWaits(screens: List<Node>) {
        for (screen in screens) lowerWaits.remove(screen)?.invoke()
    }

    // Transitions.

    // The screens of [op]'s transition move; it ends with the last of them. All count before any starts, so one that
    // ends at once doesn't end the transition early.
    private fun animate(op: Op, moves: List<Move>) {
        moves.forEach { op.animating += it.screen }
        for (move in moves) {
            if (op.done) return
            val done = { step(op) { screenDone(op, move.screen) } }
            val custom = move.custom
            if (custom != null) {
                display.animateCustom(move.screen, custom, done)
            } else {
                display.animate(move.screen, requireNotNull(move.motion), move.fromCurrent, done)
            }
        }
    }

    // One animator of [op] ended: the transition ends with its last (an animator cancelled counts as ended).
    private fun screenDone(op: Op, screen: Node) {
        if (!op.animating.remove(screen)) return
        if (op.animating.isEmpty()) finish(op)
    }

    // [taker] takes [screens] over from the transitions running: they stop them where they are and own them no
    // more. The transitions left with nothing moving end after the taker changed what shows.
    private fun takeOver(screens: List<Node>, taker: Op): List<Op> {
        val ended = ArrayList<Op>()
        for (other in running.toList()) {
            if (other === taker || other.done) continue
            for (screen in screens) {
                if (!other.owned.remove(screen)) continue
                if (other.animating.remove(screen)) display.stop(screen)
                if (other.animating.isEmpty() && other !in ended) ended += other
            }
        }
        return ended
    }

    // The transitions a newer operation took everything from end now: the stack's top isn't settled by their end, the
    // newer operation still moves it (its own end tells it, the bar's row stays with the newer one).
    private fun finishTakenOver(ops: List<Op>) {
        if (ops.isEmpty()) return
        takenOver = true
        try {
            finishAll(ops)
        } finally {
            takenOver = false
        }
    }

    private fun finishAll(ops: List<Op>) {
        for (op in ops) {
            if (!op.done) guarded(op) { finish(op) }
        }
    }

    private fun finish(op: Op) {
        when (op) {
            is PushOp -> finishPush(op)
            is CutOp -> finishCut(op)
            is ReplaceOp -> finishReplace(op)
            is NoneOp -> Unit
        }
    }

    // Answers and protection.

    private fun resolve(op: Op, value: String) {
        op.answer?.resolve(value)
        if (!op.told) op.listener?.onSuccess(value)
        op.told = true
    }

    private fun reject(op: Op, text: String) {
        op.answer?.reject(text)
        if (!op.told) op.listener?.onError(text)
        op.told = true
    }

    // An operation ended; [top] is the screen it brought, when it brought one.
    private fun settled(top: Node? = null) {
        if (destroyed) return
        host.operationEnded(!takenOver && (isAtRest || (top != null && top === currentChild)))
    }

    // A step of [op] that comes later (a readiness, a limit, an animator's end): nothing once it is done, or once
    // the JavaScript of its command went (a reload takes the stack).
    private inline fun step(op: Op, crossinline work: () -> Unit) {
        if (destroyed || op.done || !op.isCurrent()) return
        guarded(op) { work() }
    }

    // The protection of the runner covers only a command's body: what runs later is protected here.
    private inline fun guarded(op: Op, work: () -> Unit) {
        try {
            work()
        } catch (e: Throwable) {
            fail(op, e)
        }
    }

    // A failure while [op] runs: it rejects with its error text, the nodes it made that never showed go silently, the
    // entries are what still shows, and the queue goes on.
    private fun fail(op: Op, e: Throwable) {
        val text = ErrorTexts.couldNot(op.what, ErrorTexts.reasonOf(e))
        if (op.answer == null && op.listener == null) host.log("[ApexNavigation] $text")
        if (op.done) {
            // It failed at its end: an answer not given yet is a rejection; one given stays.
            destroyLeftBehind(op)
            if (op.answer?.isAnswered == false) op.answer.reject(text)
            if (!op.told) op.listener?.onError(text)
            op.told = true
            running.remove(op)
            settled()
            return drain()
        }
        op.done = true
        stopWaiting(op)
        op.cancelLimit?.invoke()
        queued.remove(op)
        running.remove(op)
        op.animating.toList().forEach { runCatching { display.stop(it) } }
        op.animating.clear()
        // The screens it moved that still show come to rest, as at an end: none stays faded out or moved aside.
        if (display.hasView) op.owned.filter { it in shownList }.forEach { runCatching { display.rest(it) } }
        op.owned.clear()
        val made = when (op) {
            is PushOp -> listOf(op.node)
            is ReplaceOp -> op.nodes
            else -> emptyList()
        }
        for (node in made) {
            if (node in shownList) continue
            entryList.remove(node)
            runCatching { if (display.hasView) display.remove(node) }
            runCatching { node.destroy() }
        }
        destroyLeftBehind(op)
        restoreEntries()
        reject(op, text)
        host.tabBarTransition(TabBarMotion.Move.AtOnce, null)
        host.barTransition(null, BarVisibility.AT_ONCE)
        host.currentChildChanged()
        settled()
        drain()
    }

    // A cut or a replace that failed after it took screens off what shows leaves them in [leavingList] only, where
    // nothing would destroy them before the stack itself: those that are no entry, don't show and no transition moves
    // go now, silently, their views out of the container (as iOS).
    private fun destroyLeftBehind(op: Op) {
        val left = when (op) {
            is CutOp -> op.leaving
            is ReplaceOp -> op.old
            else -> return
        }
        for (node in left) {
            if (node !in leavingList || node in entryList || node in shownList) continue
            if (running.any { node in it.owned }) continue
            leavingList -= node
            runCatching { if (display.hasView) display.remove(node) }
            runCatching { node.destroy() }
        }
    }

    // The entries after a failure: what shows, then the screens of the pushes still on their way.
    private fun restoreEntries() {
        val pending = ArrayList<Node>()
        (waiting as? PushOp)?.let { pending += it.node }
        queued.forEach { if (it is PushOp) pending += it.node }
        val restored = (shownList + pending.filter { it !in shownList }).distinct()
        entryList.clear()
        entryList += restored
    }

    private fun onScreen(): Boolean = display.hasView && display.isOnScreen

    // The options a screen shows with; a pair's are its options screen's.
    private fun resolved(node: Node): Options {
        val screen = (node as? OptionsScreen)?.optionsScreen ?: node
        return screen.resolveCurrentOptions(DefaultOptions.current)
    }

    private fun nextSequence(): Int = ++sequence

    // A push or a `setStackRoot` that will animate waits for its elements' targets, unless `waitForRender: false`.
    private fun awaitsTargets(animation: StackAnimationOptions, enabled: Boolean): Boolean = enabled &&
        display.canAnimate && !animation.waitForRender.isFalse && AnimationRules.hasElements(animation)

    // The bars with a change of the current child. The row goes with the default transition; with an animation of its
    // own the top bar goes with the screens ([travel]). The tab bar rides with the system's [system] move, slides with an
    // animation of its own or a side of its own, and is where the new top wants it at once without animation; with
    // elements every bar changes at once.
    private fun tellBars(
        animated: Boolean,
        own: Boolean,
        elements: Boolean,
        animation: StackAnimationOptions,
        system: TabBarMotion.Move,
        row: BarMotion,
        travel: BarTravel,
        // The top bar shows the new top already (a back gesture's preview): it changes at once, the tab bar still moves.
        barAtOnce: Boolean = false,
    ) {
        val tabs = animation.bottomTabs
        val tabsSide = AnimationRules.barSideGiven(tabs.enter) || AnimationRules.barSideGiven(tabs.exit)
        val move = when {
            !animated || elements -> TabBarMotion.Move.AtOnce
            own || tabsSide -> TabBarMotion.Move.Slide
            else -> system
        }
        host.tabBarTransition(move, tabs.takeIf { move == TabBarMotion.Move.Slide })
        val barMoves = animated && !barAtOnce
        host.barTransition(
            if (barMoves && !own) row else null,
            barVisibility(barMoves && !elements, animation.topBar),
            travel.takeIf { barMoves && own && !elements },
        )
    }

    private fun barVisibility(animated: Boolean, side: TopBarEnterExitAnimationOptions): BarVisibility =
        if (animated) BarVisibility(true, side) else BarVisibility.AT_ONCE

    // Push and `setStackRoot` (forward): the arriving screen plays `enter` with its alpha before any hide, the one it
    // covers plays `exit`; a side that doesn't play leaves its screen as it is. With elements the arriving screen fades
    // in (by its `enter` only when that is a single `alpha`), the other stands, and the arriving screen's animator
    // carries the elements.
    private fun forwardMoves(arriving: Node, covered: Node?, animation: StackAnimationOptions, elements: Boolean) =
        listOfNotNull(
            Move(arriving, custom = if (elements) {
                val side = animation.content.enter.takeIf(AnimationRules::isSingleAlpha)
                val fade = if (side == null) Fade.In else Fade.None
                Custom(side, fade, ARRIVING_ALPHA, Elements(animation, covered, arriving))
            } else {
                Custom(animation.content.enter.takeIf(AnimationRules::sidePlays), alpha = ARRIVING_ALPHA)
            }),
            covered?.let {
                Move(it, custom = Custom(animation.content.exit.takeIf { !elements && AnimationRules.sidePlays(it) }))
            },
        )

    // Pop: the leaving screen plays `exit` from where it is, the revealed one `enter` from its alpha at rest — a lower
    // screen still rendering under the others is transparent, one a running transition still moves starts where it is;
    // with elements the leaving one fades out (its `exit` only when a single `alpha`) and carries them, the revealed
    // one stands.
    private fun backMoves(
        leaving: Node,
        revealed: Node,
        animation: StackAnimationOptions,
        elements: Boolean,
        moving: Boolean,
    ) = listOf(
        Move(leaving, custom = if (elements) {
            val side = animation.content.exit.takeIf(AnimationRules::isSingleAlpha)
            Custom(side, if (side == null) Fade.Out else Fade.None, elements = Elements(animation, leaving, revealed))
        } else {
            Custom(animation.content.exit.takeIf(AnimationRules::sidePlays))
        }),
        Move(revealed, custom = Custom(
            animation.content.enter.takeIf { !elements && AnimationRules.sidePlays(it) },
            alpha = if (moving) null else ARRIVING_ALPHA,
        )),
    )

    private enum class Wait { None, Content, Limited, Prepared }

    // A screen a transition moves: by a default [motion] (from where it is when [fromCurrent]), or by an animation of
    // its own ([custom]).
    private class Move(
        val screen: Node,
        val motion: Motion? = null,
        val fromCurrent: Boolean = false,
        val custom: Custom? = null,
    )

    private abstract class Op(
        val seq: Int,
        val answer: CommandAnswer?,
        val listener: CommandListener?,
        val componentId: String,
        val what: String,
    ) {
        // The JavaScript the operation came from: its later steps do nothing once it went.
        private val generation = ReloadCoordinator.generation
        var done = false
        // The screens its transition moves, and those whose animators still run.
        val owned = LinkedHashSet<Node>()
        val animating = LinkedHashSet<Node>()
        // Its wait for its screen to render; the token of a prepared push's or of the targets' wait.
        var wait: RenderWait? = null
        var waitToken: Any? = null
        // The node its content wait waits for.
        var waitedNode: Node? = null
        // It waits for its elements' targets after its content; what stops that wait.
        var awaitsTargets = false
        var stopTargets: (() -> Unit)? = null
        // The limit of a `setStackRoot` or a push without `waitForRender` passed: its targets get nothing of it any
        // more.
        var overLimit = false
        var cancelLimit: (() -> Unit)? = null
        // The limit of a prepared push's wait, which ends with the wait.
        var cancelCap: (() -> Unit)? = null
        // Its screen came by an animation of its own: it stays at that animation's end values.
        var keepsEnd = false
        // The listener of a module heard its answer: it hears one only.
        var told = false

        fun isCurrent(): Boolean = ReloadCoordinator.isCurrent(generation)
    }

    private class PushOp(
        seq: Int,
        answer: CommandAnswer?,
        componentId: String,
        what: String,
        val node: Node,
        val prepared: Boolean,
    ) : Op(seq, answer, null, componentId, what) {
        var covered: Node? = null
    }

    private class CutOp(
        seq: Int,
        answer: CommandAnswer?,
        componentId: String,
        what: String,
        val target: Node,
        val value: String?,
        val silent: Boolean,
        val gesture: Boolean,
    ) : Op(seq, answer, null, componentId, what) {
        var leaving: List<Node> = emptyList()
    }

    private class ReplaceOp(
        seq: Int,
        answer: CommandAnswer?,
        listener: CommandListener?,
        componentId: String,
        what: String,
        val nodes: List<Node>,
        val animated: Boolean,
        val asPush: Boolean,
        val waits: Boolean,
    ) : Op(seq, answer, listener, componentId, what) {
        val top: Node
            get() = nodes.last()
        var old: List<Node> = emptyList()
    }

    private class NoneOp(
        seq: Int,
        answer: CommandAnswer?,
        listener: CommandListener?,
        componentId: String,
        what: String,
        val value: String?,
        val target: Node?,
    ) : Op(seq, answer, listener, componentId, what)

    companion object {
        /** How long the new top of a `setStackRoot` waits for its content without `waitForRender`. */
        const val SET_STACK_ROOT_WAIT_MILLIS = 500L

        /**
         * After how long, from the start of a `waitForRender: true` wait, a screen whose id no safe area view reported
         * yet counts as ready.
         */
        const val NULL_CONTENT_MILLIS = RenderWait.UNKNOWN_CONTENT_MILLIS

        /** How long a wait goes on for the bar's components after the content, at most. */
        const val BAR_WAIT_MILLIS = RenderWait.BAR_PHASE_MILLIS

        /** How long a lower screen stays placed in the container to make its content, at most. */
        const val LOWER_SCREEN_MILLIS = 500L

        /**
         * How long a push waits for its screen's first mount without `waitForRender`, and a prepared push for its slot
         * and the calls before it.
         */
        const val PUSH_WAIT_MILLIS = 500L

        // The alpha an arriving screen shows with: its own at rest, never the 0 of its wait.
        private const val ARRIVING_ALPHA = 1f
    }
}

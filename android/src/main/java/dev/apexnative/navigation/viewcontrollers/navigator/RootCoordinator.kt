package dev.apexnative.navigation.viewcontrollers.navigator

import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.EnterExitAnimationOptions
import dev.apexnative.navigation.options.ScreenAnimationOptions
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.viewcontrollers.component.Readiness
import dev.apexnative.navigation.viewcontrollers.component.RenderWait
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.AnimationRules

/**
 * The root of one activity and the one `setRoot` on its way (`setRoot`), after the command's checks. The new root's
 * view goes into the root layer at once, under the old root, transparent and hidden from accessibility, so its surfaces
 * start while the old root keeps the touches. It waits for its screens to render
 * (`animations.setRoot.enter.waitForRender`: `false` not at all, `true` as long as it takes, unset at most
 * [WAIT_LIMIT_MILLIS]); then its transition, in the order of the transitions: `ComponentWillAppear` of its screens, it
 * goes up (the current root, at the top of the layer), its own animation (also as the first root), the Material fade
 * through over the old root or at once (at once too while nobody sees the activity), the old root destroyed (its
 * screens' `ComponentDidDisappear`, `NativeScreenReleased`, `SlotDestroyed`) and its view out of the layer, then
 * `ComponentDidAppear`, the navigation state, the promise with the root's id and `ANN.CommandCompleted`. A root of a
 * JavaScript that went never goes up: the reset takes it.
 *
 * The last `setRoot` wins: a new one ends the one on its way first — rejected (`ErrorTexts.setRootReplaced`), its root
 * destroyed unseen while it waited, or put up at once, with the root it replaced destroyed, while its transition ran.
 * No promise hangs and no old root stays under a new one. The views, the readiness of screens and the clock are given,
 * so the sequence runs in unit tests. Main thread only.
 *
 * Its wait is the core's one wait ([RenderWait]), on the screens the root shows when it shows
 * ([RenderWait.waitedScreens]), told at once of their readiness; a root that waits is the activity's [WaitingLayer].
 *
 * The modals over the old root ([ModalsOverRoot]): taken as the `setRoot` is accepted (commands don't find them), given
 * back when it ends without replacing the root, carried with the old root into its transition and destroyed with it,
 * without `ANN.ModalDismissed`. The modals and the overlays of the layout ([LayerStep]) show after the root, one by
 * one, before the answer: a newer `setRoot` stops them. Its modals are made with the root ([LayerStep.prepare]) and
 * render unseen while it waits and comes in, so each shows in its turn without a wait of its own.
 *
 * The steps that come later (the wait's timers and readiness signals, the end of the transition, the end of a layer,
 * a stack of the waiting root that changed its top) run under no runner: each goes through one guard, and a failure
 * there rejects the `setRoot` with its error text and ends it as a newer `setRoot` would (ARCHITECTURE.md section 5).
 */
internal class RootCoordinator(
    private val layer: Layer,
    private val readiness: Readiness,
    private val scheduler: AppearanceDispatcher.Scheduler,
    private val firstRoot: FirstRootDraw,
    /** The current root changed: what the navigation state and the options read. */
    private val onRootChanged: (ViewController<*>?) -> Unit,
    /** A `setRoot` showed its root and answered. */
    private val onRootShown: () -> Unit,
    /**
     * Whether the activity can be seen (at least started): an animation plays only then, as its frames come only
     * then (the display off, the app in the background).
     */
    private val isSeen: () -> Boolean = { true },
    /** The modals over the root (`ModalCoordinator`). */
    private val modals: ModalsOverRoot = ModalsOverRoot.None,
) : WaitingLayer {
    /** The views of the root layer. */
    interface Layer {
        /**
         * Puts [root]'s view at the bottom of the root layer, under the current root, transparent and hidden from
         * accessibility: its content starts as it joins the window, and the root the user sees keeps the touches.
         */
        fun place(root: ViewController<*>)

        /** [root] goes up: its view comes to the top of the root layer, seen by accessibility again. */
        fun raise(root: ViewController<*>)

        /** [root]'s view becomes opaque. */
        fun show(root: ViewController<*>)

        /** [root]'s view leaves the root layer. */
        fun remove(root: ViewController<*>)

        /**
         * Plays the default transition from [old] to [new] (`FadeThrough`) and calls [onEnd] at its end. The function
         * returned ends it at once, with the final values and without [onEnd].
         */
        fun fadeThrough(old: ViewController<*>, new: ViewController<*>, onEnd: () -> Unit): () -> Unit

        /**
         * Plays the new root's own animation: [new], shown over [old] (still laid out), by [enter]; [old] and the old
         * root's modals carried with it by [exit] — a side that doesn't play leaves its view as it is; [onEnd] at the
         * end. The function returned ends it at once, with the final values and without [onEnd]. A layer without
         * animations of its own ends at once.
         */
        fun animate(
            old: ViewController<*>?,
            new: ViewController<*>,
            enter: ScreenAnimationOptions?,
            exit: ScreenAnimationOptions?,
            onEnd: () -> Unit,
        ): () -> Unit {
            show(new)
            onEnd()
            return {}
        }
    }

    /** What a `setRoot` does to the modals over the root (`setRoot` steps 3, 5; rows 12 … 14 of their table). */
    interface ModalsOverRoot {
        /** Step 3: the `setRoot` was accepted, its root made and placed. */
        fun rootAccepted()

        /** The `setRoot` that took them ended without replacing the root: they are as they were. */
        fun rootKept()

        /** The new root replaces [old] (none: the splash): they go with it in its transition. */
        fun rootReplaced(old: ViewController<*>?)

        /** The transition that replaced the root ended, or was cut: they are destroyed with the old root. */
        fun replacedRootGone()

        /** No modals (unit tests of the root alone). */
        object None : ModalsOverRoot {
            override fun rootAccepted() = Unit

            override fun rootKept() = Unit

            override fun rootReplaced(old: ViewController<*>?) = Unit

            override fun replacedRootGone() = Unit
        }
    }

    /**
     * A modal or an overlay of the layout of a `setRoot`, shown after its root: [show] shows it (made then, unless
     * [prepare] made it), and calls its argument once it showed or went (a failure is logged and skipped); what it
     * returns stops it while it waits — a newer `setRoot`: it goes without a word.
     */
    fun interface LayerStep {
        /**
         * Right after the root is placed, before its wait: a step may make its layer now, so that it renders unseen
         * while the root waits and comes in, and shows without a wait of its own in its turn.
         */
        fun prepare() = Unit

        /** The `setRoot` ended before this step's turn: what [prepare] made goes without a word. */
        fun discard() = Unit

        fun show(done: () -> Unit): () -> Unit
    }

    private enum class Phase { Waiting, Transition, Layers }

    // A `setRoot` on its way: its root, its answer, its wait (the screens it waits for are taken again when a stack of
    // the root changes its top while it waits, rootScreensChanged), what ends its transition, and the modals and the
    // overlays of its layout, which show after its root.
    private class Pending(val root: ViewController<*>, val answer: CommandAnswer, val layers: List<LayerStep>) {
        var phase = Phase.Waiting
        var wait: RenderWait? = null
        var endTransition: (() -> Unit)? = null
        // The root this one replaces, from the start of its transition.
        var replaced: ViewController<*>? = null
        var waitForRender: Bool = Bool(false)
        // The next layer to show, and what stops the one that shows now.
        var nextLayer = 0
        var stopLayer: (() -> Unit)? = null
        // It holds the navigation state's updates while its layers show (holdState).
        var holdsState = false
        // The modals over the root it replaces went with that root into its transition (ModalsOverRoot.rootReplaced).
        var carriesModals = false
    }

    /** The current root: the one up in the root layer; none before the first `setRoot`. */
    var root: ViewController<*>? = null
        private set

    private var pending: Pending? = null

    /** The root of the `setRoot` on its way; none. */
    val pendingRoot: ViewController<*>?
        get() = pending?.root

    /**
     * The root of the `setRoot` that still waits for its screens, in the root layer, transparent: what is in it is not
     * on screen yet (a stack runs its commands at once there); none.
     */
    val waitingRoot: ViewController<*>?
        get() = pending?.takeIf { it.phase == Phase.Waiting }?.root

    override val waitingNode: ViewController<*>?
        get() = waitingRoot

    override fun shownScreensChanged() = rootScreensChanged()

    /**
     * A stack of the waiting root changed its top (a command into it): the root waits for the screens it shows now,
     * with the same limit, and no longer for a screen that went; with none left to wait for, or the root itself gone,
     * the wait ends.
     */
    fun rootScreensChanged() {
        val pending = pending ?: return
        if (pending.phase != Phase.Waiting) return
        guarded(pending) {
            if (pending.root.isDestroyed) return rootGone(pending)
            pending.wait?.retake(screensOf(pending), unseenOf(pending))
        }
    }

    /**
     * Shows the root [make] makes, answering [answer]: a `setRoot` on its way is replaced first. A failure of [make] or
     * of placing the root goes to the caller, which rejects; a root made but not placed is destroyed, and the modals
     * over the current root are as they were. Then the modals over the root are taken (step 3); [layers] show after the
     * root, before the answer.
     */
    fun setRoot(make: () -> ViewController<*>, answer: CommandAnswer, layers: List<LayerStep> = emptyList()) {
        supersede()
        val root = try {
            make()
        } catch (e: Throwable) {
            modals.rootKept()
            throw e
        }
        try {
            layer.place(root)
        } catch (e: Throwable) {
            root.destroy()
            modals.rootKept()
            throw e
        }
        // From here the root is placed and the modals are about to be taken: a failure ends this `setRoot` as a later
        // step's would (fail: the root away, the modals as they were, the answer rejected).
        val pending = Pending(root, answer, layers)
        guarded(pending) {
            modals.rootAccepted()
            val waitForRender = animationsOf(root).enter.waitForRender
            this.pending = pending
            layers.forEach { it.prepare() }
            firstRoot.rootSet(waitForRender)
            await(pending, waitForRender)
        }
    }

    /**
     * The activity's layers go: the `setRoot` on its way ends, then the root it replaced, the current root and its root
     * are destroyed, as at any destroy (their events reach only their own JavaScript). [answerPending]: the activity
     * went, the waiting `setRoot` resolves with `''` (JavaScript sets the root again in the next activity); otherwise
     * (a reload) it gets no answer.
     */
    fun destroyAll(answerPending: Boolean) {
        val pending = pending
        this.pending = null
        pending?.wait?.cancel()
        pending?.endTransition?.invoke()
        pending?.stopLayer?.invoke()
        val nodes = listOfNotNull(pending?.replaced, root, pending?.root).distinct()
        root = null
        onRootChanged(null)
        nodes.forEach(::takeAway)
        modals.replacedRootGone()
        pending?.let(::releaseState)
        if (pending != null && pending.phase == Phase.Waiting) firstRoot.rootDestroyed()
        if (answerPending) pending?.answer?.resolve("")
    }

    // Waits for the screens that show when the root shows, then runs its transition. Without `waitForRender` one limit
    // covers their content and the components of the bars over them (drawn in advance with their stacks), and marks the
    // content not ready at its end; with `true` no limit, and the bars' components get 0.5 s at most once the content
    // is ready; with `false` nothing is waited for. Content of unknown readiness gets no rule of its own here.
    private fun await(pending: Pending, waitForRender: Bool) {
        pending.waitForRender = waitForRender
        val unset = !waitForRender.isTrue && !waitForRender.isFalse
        val wait = RenderWait(
            readiness = readiness,
            scheduler = scheduler,
            limitMillis = if (unset) WAIT_LIMIT_MILLIS else null,
            bar = if (waitForRender.isTrue) RenderWait.Bar.AfterContent else RenderWait.Bar.WithinLimit,
            countsUnknownContent = false,
            step = { work -> step(pending, work) },
            onEnd = { guarded(pending) { end(pending) } },
        )
        pending.wait = wait
        wait.start(screensOf(pending), unseenOf(pending))
    }

    // The screens the root shows when it shows; none with `waitForRender: false`.
    private fun screensOf(pending: Pending): List<ViewController<*>> =
        if (pending.waitForRender.isFalse) emptyList() else RenderWait.waitedScreens(pending.root)

    // The screens made with the root that don't show with it (the unselected tabs of `together`): their content
    // counts within 0.5 s, also with `waitForRender: true`; none with `false`.
    private fun unseenOf(pending: Pending): List<ViewController<*>> =
        if (pending.waitForRender.isFalse) emptyList() else RenderWait.unseenScreens(pending.root)

    private fun end(pending: Pending) {
        if (this.pending !== pending || pending.phase != Phase.Waiting) return
        pending.wait?.cancel()
        transition(pending)
    }

    private fun transition(pending: Pending) {
        // The JavaScript of the command went, and the reset of the navigation comes on the main thread after this:
        // the root stays where it is until then, and goes with it (destroyAll).
        if (!ReloadCoordinator.isCurrent(pending.answer.generation)) return
        // A root destroyed while it waited, which nothing else ends: a stack of it tells rootScreensChanged as its top
        // changes; no modal path destroys a waiting root's node. A split view and a pair tell it the same way as the
        // screens they show change (`Navigator.shownScreensChanged`).
        if (pending.root.isDestroyed) return rootGone(pending)
        pending.phase = Phase.Transition
        val old = root
        pending.replaced = old
        // The old root's screens start to leave: those with `blurOnUnmount` let go of the focus before the transition,
        // so the keyboard doesn't stay over it (`blurOnUnmount`).
        old?.onLeaveStarted()
        // Its modals go with it, under the new root.
        modals.rootReplaced(old)
        pending.carriesModals = true
        AppearanceDispatcher.beginTransition(listOf(pending.root))
        layer.raise(pending.root)
        root = pending.root
        onRootChanged(pending.root)
        val animations = animationsOf(pending.root)
        val ends = {
            step(pending) {
                pending.endTransition = null
                firstRoot.rootUp()
                finish(pending)
            }
        }
        // Its own animation plays also as the first root, over the splash; `enter.enabled: false` shows it at once
        // whatever else is given.
        val own = isSeen() && !AnimationRules.rootAtOnce(animations) && AnimationRules.rootPlays(animations)
        if (own) {
            val enter = animations.enter.takeIf(AnimationRules::sidePlays)
            val exit = animations.exit.takeIf { old != null && !old.isDestroyed && AnimationRules.sidePlays(it) }
            pending.endTransition = layer.animate(old, pending.root, enter, exit, ends)
            return
        }
        if (old == null || !isSeen() || !playsFadeThrough(animations)) {
            layer.show(pending.root)
            firstRoot.rootUp()
            return finish(pending)
        }
        pending.endTransition = layer.fadeThrough(old, pending.root, ends)
    }

    private fun finish(pending: Pending) {
        if (pending.layers.isEmpty()) this.pending = null else holdState(pending)
        pending.replaced?.let(::takeAway)
        pending.replaced = null
        modals.replacedRootGone()
        if (pending.layers.isEmpty()) {
            AppearanceDispatcher.endTransition(emptyList(), listOf(pending.root), null, pending.answer, pending.root.id)
            onRootShown()
            return
        }
        // The modals and the overlays of the layout show now, one by one; the state and the answer come after them.
        pending.phase = Phase.Layers
        AppearanceDispatcher.endTransition(emptyList(), listOf(pending.root), null, null, null, reportsState = false)
        onRootShown()
        showNextLayer(pending)
    }

    // The next modal or overlay of the layout; after the last, the answer with the root's id, the state first.
    private fun showNextLayer(pending: Pending) {
        if (this.pending !== pending || pending.phase != Phase.Layers) return
        if (pending.nextLayer >= pending.layers.size) {
            this.pending = null
            releaseState(pending)
            pending.answer.resolve(pending.root.id)
            return
        }
        val layerStep = pending.layers[pending.nextLayer++]
        var done = false
        val stop = layerStep.show {
            if (!done) {
                done = true
                pending.stopLayer = null
                step(pending) { showNextLayer(pending) }
            }
        }
        if (!done) pending.stopLayer = stop
    }

    // The layers of [pending] not shown yet go: what they made ahead goes unseen, the others aren't made. Each on its
    // own: one that fails doesn't keep the others.
    private fun discardLayers(pending: Pending) {
        val rest = pending.layers.drop(pending.nextLayer)
        pending.nextLayer = pending.layers.size
        rest.forEach { attempt { it.discard() } }
    }

    // A new `setRoot` came while this one was on its way.
    private fun supersede() {
        val pending = pending ?: return
        this.pending = null
        when (pending.phase) {
            // It never appeared: it goes without a word, and the current root stays until the new one replaces it.
            Phase.Waiting -> {
                pending.wait?.cancel()
                takeAway(pending.root)
                firstRoot.rootDestroyed()
            }
            // Its root is up at once, without its DidAppear; the root it replaced goes now. The new root replaces it
            // later as an old root: the screens whose WillAppear went get their DidDisappear then.
            Phase.Transition -> {
                pending.endTransition?.invoke()
                pending.endTransition = null
                layer.show(pending.root)
                firstRoot.rootUp()
                pending.replaced?.let(::takeAway)
                pending.replaced = null
                modals.replacedRootGone()
            }
            // Its root is the current root: the modal or the overlay that waits now goes without a word, the others go
            // unseen or aren't made; those shown stay over its root, and go with it when the new root replaces it.
            Phase.Layers -> {
                pending.stopLayer?.invoke()
                pending.stopLayer = null
                releaseState(pending)
            }
        }
        discardLayers(pending)
        pending.answer.reject(ErrorTexts.setRootReplaced(pending.root.id))
    }

    // A step of [pending] that comes later: nothing once a newer `setRoot`, the activity or the JavaScript ended it.
    private inline fun step(pending: Pending, work: () -> Unit) {
        if (this.pending !== pending) return
        guarded(pending, work)
    }

    // The protection of the runner covers only the command's body: what runs later is protected here.
    private inline fun guarded(pending: Pending, work: () -> Unit) {
        try {
            work()
        } catch (e: Throwable) {
            fail(pending, e)
        }
    }

    // A failure in a later step of [pending]: it rejects with its error text and ends as a newer `setRoot` would end it
    // (supersede): a root that waited goes unseen and the modals over the current root are as they were; a root on its
    // way comes up at once, and the root it replaces goes with its modals; the layers not shown yet aren't made. Each
    // part runs on its own, so one that fails again doesn't keep the others from running.
    private fun fail(pending: Pending, e: Throwable) {
        val text = ErrorTexts.couldNot("show the root '${pending.root.id}'", ErrorTexts.reasonOf(e))
        if (this.pending === pending) this.pending = null
        pending.wait?.cancel()
        pending.wait = null
        when (pending.phase) {
            Phase.Waiting -> {
                discardLayers(pending)
                attempt { takeAway(pending.root) }
                attempt { modals.rootKept() }
                attempt { firstRoot.rootDestroyed() }
            }
            Phase.Transition -> {
                attempt { pending.endTransition?.invoke() }
                pending.endTransition = null
                if (root !== pending.root) {
                    attempt { layer.raise(pending.root) }
                    root = pending.root
                    attempt { onRootChanged(pending.root) }
                }
                attempt { layer.show(pending.root) }
                attempt { firstRoot.rootUp() }
                if (!pending.carriesModals) attempt { modals.rootReplaced(pending.replaced) }
                pending.carriesModals = true
                pending.replaced?.let { attempt { takeAway(it) } }
                pending.replaced = null
                attempt { modals.replacedRootGone() }
            }
            Phase.Layers -> {
                attempt { pending.stopLayer?.invoke() }
                pending.stopLayer = null
            }
        }
        discardLayers(pending)
        // A root that is up takes the splash away, as at its show.
        if (pending.phase != Phase.Waiting) attempt { onRootShown() }
        attempt { releaseState(pending) }
        pending.answer.reject(text)
    }

    // One part of a failure's end: a failure of its own is dropped, the one that ends the `setRoot` is in its answer.
    private inline fun attempt(work: () -> Unit) {
        runCatching(work)
    }

    // The navigation state goes once, when the root is in place and its layers have shown, right before the answer:
    // what the old root's destroy, the root's appearance and each layer ask for meanwhile waits (StateHold). Taken once
    // per `setRoot`, before the old root goes.
    private fun holdState(pending: Pending) {
        if (pending.holdsState) return
        pending.holdsState = true
        AppearanceDispatcher.state.hold()
    }

    // Right before the answer, the answer's own state is the one state; on an end without the answer (a newer
    // `setRoot`, the activity or the JavaScript going) the update asked meanwhile goes as at any change.
    private fun releaseState(pending: Pending) {
        if (!pending.holdsState) return
        pending.holdsState = false
        AppearanceDispatcher.state.release()
    }

    // The root was destroyed while it waited, by something other than a newer `setRoot` or the activity going.
    private fun rootGone(pending: Pending) {
        this.pending = null
        discardLayers(pending)
        releaseState(pending)
        layer.remove(pending.root)
        modals.rootKept()
        firstRoot.rootDestroyed()
        pending.answer.reject(ErrorTexts.rootDestroyed(pending.root.id))
    }

    // An old root goes: its screens disappear if they showed, leave the registry, their content stops; then its view
    // leaves the layer. Its modals go right after it (ModalsOverRoot.replacedRootGone).
    private fun takeAway(node: ViewController<*>) {
        node.destroy()
        layer.remove(node)
    }

    private fun animationsOf(root: ViewController<*>): EnterExitAnimationOptions =
        root.resolveCurrentOptions(DefaultOptions.current).animations.setRoot

    // The default transition: not with `enter.enabled: false` or `enter.waitForRender: false`, nor with an animation of
    // its own.
    private fun playsFadeThrough(animations: EnterExitAnimationOptions): Boolean {
        val enter = animations.enter
        if (AnimationRules.rootAtOnce(animations) || enter.waitForRender.isFalse) return false
        return !AnimationRules.rootPlays(animations)
    }

    companion object {
        /** How long a root waits for its screens to render at most, unless `waitForRender` says otherwise. */
        const val WAIT_LIMIT_MILLIS = 500L
    }
}

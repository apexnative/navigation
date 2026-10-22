package dev.apexnative.navigation.viewcontrollers.modal

import dev.apexnative.navigation.options.ModalAnimationOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.ViewAnimationOptions
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.Readiness
import dev.apexnative.navigation.viewcontrollers.navigator.WaitingLayer
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.AnimationRules

// Fakes of the modals' views, readiness and navigation, for unit tests without a device: Android's views are stubs
// there, so the coordinators run over these.

/**
 * The views of the modals as a journal ([log]: `place:<id>`, `present:<id>:<animated|at once>` (an animation of its
 * own adds `:own(…)`, [describe]), `dismiss:<id>:…`, `awaitTargets:<id>:<under>`,
 * `remove:<id>`, `settle:<id>`, `takeOut:<id>`, `putBack:<id>@<at>`, `takeOutRoot:<id>`, `putBackRoot:<id>`,
 * `carry:<id>`,
 * `update:<id>:<merged>`); a show or a dismissal ends when the test says so, or at once with [instant].
 */
internal class FakeModalViews : ModalCoordinator.Views {
    val log = mutableListOf<String>()
    var instant = false
    var failPresent = false
    // Each throws once, at the next call: views that fail in a later step.
    var failDismiss = false
    var failRemove = false
    var failTakeOutRoot = false
    var failSettle = false
    private val shows = LinkedHashMap<String, () -> Unit>()
    private val dismissals = LinkedHashMap<String, () -> Unit>()
    /** The sheet events of each record that showed, by its root id. */
    val sheets = HashMap<String, ModalSheet.Events>()

    override fun place(record: ModalRecord) {
        log += "place:${record.rootId}"
    }

    override fun present(
        record: ModalRecord,
        options: Options,
        animated: Boolean,
        seen: Boolean,
        events: ModalSheet.Events,
        onEnd: () -> Unit,
        motion: ModalCoordinator.Motion?,
    ): () -> Unit {
        if (failPresent) error("no view")
        log += "present:${record.rootId}:${if (animated && seen) "animated" else "at once"}" + describe(motion)
        sheets[record.rootId] = events
        if (instant || !seen || (!animated && !record.isSheet)) {
            onEnd()
            return {}
        }
        shows[record.rootId] = onEnd
        return {
            log += "stopped:${record.rootId}"
            shows.remove(record.rootId)
        }
    }

    override fun dismiss(
        record: ModalRecord,
        animated: Boolean,
        seen: Boolean,
        onEnd: () -> Unit,
        motion: ModalCoordinator.Motion?,
    ): () -> Unit {
        if (failDismiss) {
            failDismiss = false
            error("no view")
        }
        log += "dismiss:${record.rootId}:${if (animated && seen) "animated" else "at once"}" + describe(motion)
        if (instant || !seen || (!animated && !record.isSheet)) {
            onEnd()
            return {}
        }
        dismissals[record.rootId] = onEnd
        return {
            log += "stopped:${record.rootId}"
            dismissals.remove(record.rootId)
        }
    }

    override fun remove(record: ModalRecord) {
        if (failRemove) {
            failRemove = false
            error("no view")
        }
        log += "remove:${record.rootId}"
    }

    override fun settle(record: ModalRecord) {
        if (failSettle) {
            failSettle = false
            error("no view")
        }
        log += "settle:${record.rootId}"
    }

    override fun takeOut(record: ModalRecord) {
        log += "takeOut:${record.rootId}"
    }

    override fun putBack(record: ModalRecord, at: ModalRecord?) {
        log += "putBack:${record.rootId}@${at?.rootId}"
    }

    override fun takeOutRoot(root: ViewController<*>) {
        if (failTakeOutRoot) {
            failTakeOutRoot = false
            error("no view")
        }
        log += "takeOutRoot:${root.id}"
    }

    override fun putBackRoot(root: ViewController<*>) {
        log += "putBackRoot:${root.id}"
    }

    override fun carry(record: ModalRecord, oldRoot: ViewController<*>?) {
        log += "carry:${record.rootId}"
    }

    override fun update(record: ModalRecord, options: Options, merge: Options?) {
        log += "update:${record.rootId}:${merge != null}"
    }

    private val targets = LinkedHashMap<String, Pair<() -> Boolean, () -> Unit>>()

    override fun awaitTargets(
        record: ModalRecord,
        animation: ModalAnimationOptions,
        under: ViewController<*>?,
        waiting: () -> Boolean,
        done: () -> Unit,
    ): () -> Unit {
        log += "awaitTargets:${record.rootId}:${under?.id}"
        targets[record.rootId] = waiting to done
        return {
            log += "stopTargets:${record.rootId}"
            targets.remove(record.rootId)
        }
    }

    /** Whether the wait for [id]'s targets would still go on now (its `waiting`). */
    fun targetsWaiting(id: String): Boolean = targets[id]?.first?.invoke() == true

    /**
     * The wait for [id]'s targets ends: they are found, or its `waiting` stopped holding — as the real wait, which
     * ends either way unless it was stopped.
     */
    fun findTargets(id: String) {
        val (_, done) = targets.remove(id) ?: return
        done()
    }

    /** Whether the show of [id] waits for its targets. */
    fun awaitsTargets(id: String): Boolean = id in targets

    /** The show of [id] ends. */
    fun endShow(id: String) {
        shows.remove(id)?.invoke()
    }

    /** The dismissal of [id] ends. */
    fun endDismiss(id: String) {
        dismissals.remove(id)?.invoke()
    }

    /** Whether the show of [id] runs. */
    fun isShowing(id: String): Boolean = id in shows

    /** Whether the dismissal of [id] runs. */
    fun isDismissing(id: String): Boolean = id in dismissals

    companion object {
        /**
         * An animation of its own as `:own(<modal> <under>@<id>[ fade][ elements:<from>><to>])`: the properties that
         * play on the modal and on the layer under it (`-` for none); nothing for the default one.
         */
        fun describe(motion: ModalCoordinator.Motion?): String {
            motion ?: return ""
            val modal = motion.side?.let(::properties) ?: "-"
            val under = motion.underSide?.let(::properties) ?: "-"
            val fade = if (motion.fades) " fade" else ""
            val elements = if (motion.elements != null) " elements:${motion.from?.id}>${motion.to?.id}" else ""
            return ":own($modal $under@${motion.under?.id}$fade$elements)"
        }

        /** The properties of [side] that play, by name, joined by `+`. */
        fun properties(side: ViewAnimationOptions): String = NAMES.zip(AnimationRules.properties(side))
            .filter { AnimationRules.propertyPlays(it.second) }.joinToString("+") { it.first }

        private val NAMES = listOf(
            "x", "y", "translationX", "translationY", "alpha", "scaleX", "scaleY", "rotationX", "rotationY", "rotation",
        )
    }
}

/** The navigation the modals and the overlays ask: the root, the registry, the waiting layers, the log. */
internal class FakeModalHost(private val registry: ChildControllersRegistry) : ModalCoordinator.Host {
    override var root: ViewController<*>? = null
    var seen = true
    val waiting = mutableListOf<WaitingLayer>()
    val logged = mutableListOf<String>()
    var backChanges = 0
    // Throws once, at the next call: back's update that fails.
    var failBackChanged = false
    /** Nodes commands don't find (the old root's modals); none by default. */
    var isGone: (ViewController<*>) -> Boolean = { false }

    override fun find(id: String): ViewController<*>? = registry.find(id)?.takeUnless(isGone)

    override fun isSeen(): Boolean = seen

    override fun addWaitingLayer(layer: WaitingLayer) {
        if (layer !in waiting) waiting += layer
    }

    override fun removeWaitingLayer(layer: WaitingLayer) {
        waiting -= layer
    }

    override fun backChanged() {
        if (failBackChanged) {
            failBackChanged = false
            error("no back")
        }
        backChanges++
    }

    override fun log(text: String) {
        logged += text
    }

    /** Whether [node] is in a layer that waits to show, as `Navigator.isInWaitingLayer` answers. */
    fun isInWaitingLayer(node: ViewController<*>): Boolean {
        var current: ViewController<*>? = node
        while (current != null) {
            val here = current
            if (waiting.any { it.waitingNode === here }) return true
            current = current.parentController
        }
        return false
    }
}

/** Screens are ready by id; [mount] makes one ready and tells who waits. */
internal class IdReadiness : Readiness {
    val ready = mutableSetOf<String>()
    val marked = mutableListOf<String>()
    // Throws once, at the next signal asked for: a readiness that fails as a wait starts.
    var failWhenReady = false
    private val waiting = mutableMapOf<String, MutableList<() -> Unit>>()

    fun mount(id: String) {
        ready += id
        waiting.remove(id)?.forEach { it() }
    }

    override fun isReady(screen: ViewController<*>): Boolean = screen.id in ready

    override fun whenReady(screen: ViewController<*>, listener: () -> Unit) {
        if (failWhenReady) {
            failWhenReady = false
            error("no readiness")
        }
        if (isReady(screen)) listener() else waiting.getOrPut(screen.id) { mutableListOf() } += listener
    }

    override fun markReady(screen: ViewController<*>) {
        marked += screen.id
        ready += screen.id
    }
}

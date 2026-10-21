package dev.apexnative.navigation.viewcontrollers.stack

import android.view.ViewGroup
import dev.apexnative.navigation.layout.LayoutNode
import dev.apexnative.navigation.options.StackPushAnimationOptions
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The screens of one activity prepared for a push (`preparePush`): each made and in the registry (a `mergeOptions` into
 * it is kept for its show), in its stack's container, hidden, but no child of the stack: pop, back, other pushes and
 * the navigation state don't see it. Its push takes it as it is; a push into another stack, `cancelPreparedPush`, the
 * stack's destroy and the navigation's destroy take it away. Main thread only.
 */
internal class PreparedPushes {
    private class Prepared(val node: ViewController<*>, val stack: StackController)

    private val prepared = LinkedHashMap<String, Prepared>()

    /** [node] is prepared for a push, and not pushed yet. */
    fun isPrepared(node: ViewController<*>): Boolean = prepared[node.id]?.node === node

    /** [node] is prepared for a push into [stack]. */
    fun isPreparedFor(node: ViewController<*>, stack: StackController): Boolean =
        prepared[node.id]?.let { it.node === node && it.stack === stack } == true

    /** [node] is prepared for a push into [stack]: the caller discarded one prepared with its id before. */
    fun add(node: ViewController<*>, stack: StackController) {
        prepared[node.id] = Prepared(node, stack)
    }

    /**
     * The push of [id] into [stack] takes the node prepared for it, which is prepared no more; a node prepared with
     * the id for another stack is destroyed (`push` step 4). None when nothing is prepared with [id].
     */
    fun take(id: String, stack: StackController): ViewController<*>? {
        val entry = prepared.remove(id) ?: return null
        if (entry.stack === stack && !entry.node.isDestroyed) return entry.node
        destroy(entry.node)
        return null
    }

    /** `cancelPreparedPush`, or a new `preparePush` with the same id: the node prepared with [id] goes. */
    fun discard(id: String) {
        prepared.remove(id)?.let { destroy(it.node) }
    }

    /** [stack] is destroyed: the nodes prepared for it go with it. */
    fun discardFor(stack: StackController) {
        val gone = prepared.values.filter { it.stack === stack }
        gone.forEach { prepared.remove(it.node.id) }
        gone.forEach { destroy(it.node) }
    }

    /** The navigation goes: every prepared node goes. */
    fun destroyAll() {
        val gone = prepared.values.toList()
        prepared.clear()
        gone.forEach { destroy(it.node) }
    }

    // Its view leaves the container, its content stops; a slot prepared only sends no `ANN.SlotDestroyed`.
    private fun destroy(node: ViewController<*>) {
        node.peekView()?.let { (it.parent as? ViewGroup)?.removeView(it) }
        node.destroy()
    }

    /** Why a `preparePush` prepares nothing and answers `null` (`preparePush`); none when it prepares. */
    enum class Refusal {
        NoActivity,
        NotASlot,
        NoStack,
        Shown,
        NoStackView,
        StackNotShown,
        ZeroSize,
        KeyInStack,
        WaitsForRender,
        ElementTransitions,
    }

    companion object {
        /**
         * The decision of `preparePush`, in the order of `preparePush`: no activity; [node] not a slot screen (a
         * `Component` named `slot:`); no stack for the command's screen; the node's id shown; the stack's view not
         * made, not shown or of no size; the node's key among the stack's entries; `animations.push.waitForRender:
         * true` of the node's options resolved in the stack; shared elements or element transitions in its push.
         */
        fun refusal(
            hasActivity: Boolean,
            node: LayoutNode,
            hasStack: Boolean,
            idShown: Boolean,
            stackHasView: Boolean,
            stackShown: Boolean,
            stackWidth: Int,
            stackHeight: Int,
            keyInStack: Boolean,
            push: StackPushAnimationOptions,
        ): Refusal? = when {
            !hasActivity -> Refusal.NoActivity
            !node.isSlot -> Refusal.NotASlot
            !hasStack -> Refusal.NoStack
            idShown -> Refusal.Shown
            !stackHasView -> Refusal.NoStackView
            !stackShown -> Refusal.StackNotShown
            stackWidth <= 0 || stackHeight <= 0 -> Refusal.ZeroSize
            keyInStack -> Refusal.KeyInStack
            push.waitForRender.isTrue -> Refusal.WaitsForRender
            push.sharedElements.get().isNotEmpty() || push.elementTransitions.transitions.isNotEmpty() ->
                Refusal.ElementTransitions
            else -> null
        }
    }
}

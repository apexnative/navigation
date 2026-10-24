package dev.apexnative.navigation.viewcontrollers.stack

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import dev.apexnative.navigation.layout.LayoutChecks
import dev.apexnative.navigation.layout.LayoutNode
import dev.apexnative.navigation.layout.LayoutType
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.ScreenEnvironment
import dev.apexnative.navigation.slots.SlotScreenView
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.navigator.LayoutFactory
import dev.apexnative.navigation.viewcontrollers.parent.ShowsColumns
import dev.apexnative.navigation.viewcontrollers.parent.nodeWith
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The commands of the stacks of one activity (`push`, `pop`, `popTo`, `popToRoot`, `setStackRoot`, `preparePush`,
 * `cancelPreparedPush`) and of the split view, whose screens go into a stack or over one (`showInColumn`,
 * `setColumnShown`), after the runner's shared steps, on the main thread. Every check comes before any node is made, in
 * this order: the screen `componentId` names isn't there, or no longer an entry of its stack → `the screen '<id>' is
 * not shown`; it is a stack, or in one, else `the screen '<id>' is not in a stack`; then the command's own checks. They
 * read the stack as JavaScript sees it (`StackQueue.entries`), so a screen pushed without `await` is one of its stack
 * already. Main thread only.
 */
internal class StackCommands(
    private val registry: ChildControllersRegistry,
    private val factory: LayoutFactory,
    private val prepared: PreparedPushes,
    /**
     * A node commands don't find, though the registry has it: a node of a modal that goes with the old root of a
     * `setRoot` on its way.
     */
    private val isGone: (ViewController<*>) -> Boolean = { false },
) {
    /** `push`: [layout] over the screen [componentId] in its stack; [answer] with the id it lands on. */
    fun push(componentId: String, layout: LayoutNode, answer: CommandAnswer, what: String) {
        val stack = stackOf(componentId, answer) ?: return
        val shown = LayoutChecks.firstShownId(listOf(layout), ::isShown) { false }
        if (shown != null) return answer.reject(ErrorTexts.alreadyShown(shown))
        LayoutChecks.checkStackChild(layout, stack.id)?.let { return answer.reject(it) }
        if (!factory.canMake(layout)) return answer.reject(ErrorTexts.notDoneYet("push"))
        pushChecked(stack, componentId, layout, answer, what)
    }

    /**
     * The push of [layout] into [stack], checked: a key of an entry goes back to it, else the node is made (a prepared
     * one taken as it is), joins the stack and is queued.
     */
    fun pushChecked(
        stack: StackController,
        componentId: String,
        layout: LayoutNode,
        answer: CommandAnswer,
        what: String,
    ) {
        if (backToKey(stack, layout, componentId, answer, what)) return
        val taken = prepared.take(layout.id, stack)
        val node = taken ?: make(layout)
        enqueue(stack, node, layout, componentId, answer, what, prepared = taken != null)
    }

    // A key of an entry of [stack] in [layout] goes back to it: on the top it answers at once (C6), deeper as a `popTo`
    // that answers `push`. False without such an entry.
    private fun backToKey(
        stack: StackController,
        layout: LayoutNode,
        componentId: String,
        answer: CommandAnswer,
        what: String,
    ): Boolean {
        val key = keyOf(layout) ?: return false
        val entries = stack.queue.entries
        val keyed = entries.lastOrNull { stack.keyOf(it) == key } ?: return false
        val value = stack.answerIdOf(keyed, StackQueue.AnswerKind.Back)
        if (keyed === entries.last()) {
            answer.resolve(value)
        } else {
            stack.queue.cut(keyed, answer, componentId, what, value)
        }
        return true
    }

    // [node], made for [layout], joins [stack] and is queued as a push.
    private fun enqueue(
        stack: StackController,
        node: ViewController<*>,
        layout: LayoutNode,
        componentId: String,
        answer: CommandAnswer,
        what: String,
        prepared: Boolean,
    ) {
        stack.join(node, first = false, key = keyOf(layout))
        // Pushed now: its destroy tells JavaScript to take the slot's content away.
        ((node as? ComponentViewController)?.content as? SlotScreenView)?.isPreparedPush = false
        stack.queue.push(node, answer, componentId, what, prepared = prepared)
    }

    /**
     * `showInColumn` (Android): [layout] in [column] of the split view of the screen [componentId], or over that screen
     * in its stack. Before any node: the screen is there, else `the screen '<id>' is not shown`; the layout has no id
     * that shows already; out of a split view, the push's check of the layout (a container, which the stack doesn't
     * take); a screen prepared with the id goes. The screen is made at once, a native screen's view too. The split view
     * takes it — in place of a stack column's screens or of a column's content — with the answer once its pane
     * stopped, or rejects (a column it doesn't have, a container for a stack column); with the compact layout on
     * screen, or without a split view, it is pushed over [componentId] in its stack, with the push's checks, answer and
     * errors; neither: `the screen '<id>' is neither in a split view nor in a stack`. Every rejection after the screen
     * was made destroys it. Two calls in a row each make their screen the column's content in turn, and each answers
     * its own id.
     */
    fun showInColumn(componentId: String, column: String, layout: LayoutNode, answer: CommandAnswer, what: String) {
        val source = find(componentId)?.takeIf { !prepared.isPrepared(it) }
            ?: return answer.reject(ErrorTexts.screenNotShown(componentId))
        val shown = LayoutChecks.firstShownId(listOf(layout), ::isShown) { false }
        if (shown != null) return answer.reject(ErrorTexts.alreadyShown(shown))
        val columns = nodeWith<ShowsColumns>(source)
        val stack = source as? StackController ?: source.findParentStack()
        // Out of a split view it can only be a push: the stack's check comes before any node.
        if (columns == null && stack != null) {
            LayoutChecks.checkStackChild(layout, stack.id)?.let { return answer.reject(it) }
        }
        if (!factory.canMake(layout)) return answer.reject(ErrorTexts.notDoneYet("showInColumn"))
        prepared.discard(layout.id)
        val node = make(layout)
        try {
            val goesInStack = LayoutChecks.isStackChild(layout)
            if (columns?.showScreen(node, column, goesInStack, source, destroyingOnError(node, answer)) == true) return
            if (stack == null) {
                node.destroy()
                return answer.reject(ErrorTexts.neitherSplitViewNorStack(componentId))
            }
            LayoutChecks.checkStackChild(layout, stack.id)?.let {
                node.destroy()
                return answer.reject(it)
            }
            pushMade(stack, source, node, layout, answer, what)
        } catch (e: Throwable) {
            node.destroy()
            throw e
        }
    }

    // The push of [node], made already for [layout] (`showInColumn`), over [source] in [stack]: as [pushChecked], with
    // the made node (no prepared one is taken), which a key of an entry destroys as it goes back to that entry.
    private fun pushMade(
        stack: StackController,
        source: ViewController<*>,
        node: ViewController<*>,
        layout: LayoutNode,
        answer: CommandAnswer,
        what: String,
    ) {
        // A screen an accepted cut or replace took away is gone for later commands.
        if (source !== stack && stack.entryOf(source)?.let { it in stack.queue.entries } != true) {
            node.destroy()
            return answer.reject(ErrorTexts.screenNotShown(source.id))
        }
        if (backToKey(stack, layout, source.id, answer, what)) return node.destroy()
        enqueue(stack, node, layout, source.id, answer, what, prepared = false)
    }

    // The answer of the split view's show; a rejection destroys [node], which never showed.
    private fun destroyingOnError(node: ViewController<*>, answer: CommandAnswer) = object : CommandListener {
        override fun onSuccess(childId: String) = answer.resolve(childId)

        override fun onError(message: String) {
            node.destroy()
            answer.reject(message)
        }
    }

    /**
     * `setColumnShown` (Android): shows or hides [column] of the split view of the screen [componentId], and [answer]
     * with nothing once its panes stopped. The screen is there, else `the screen '<id>' is not shown`; in a split view,
     * else `the screen '<id>' is not in a split view`.
     */
    fun setColumnShown(componentId: String, column: String, shown: Boolean, answer: CommandAnswer) {
        val node = find(componentId) ?: return answer.reject(ErrorTexts.screenNotShown(componentId))
        val columns = nodeWith<ShowsColumns>(node) ?: return answer.reject(ErrorTexts.notInSplitView(componentId))
        columns.setColumnShown(column, shown) { answer.resolve(null) }
    }

    /** `pop`: the screen [componentId] (the top for the stack's id) and every screen over it. */
    fun pop(componentId: String, answer: CommandAnswer, what: String) {
        val stack = stackOf(componentId, answer) ?: return
        val node = find(componentId)
        val entries = stack.queue.entries
        val top = entries.lastOrNull() ?: return answer.reject(ErrorTexts.firstScreenOfStack(componentId))
        val entry = if (node === stack) top else node?.let(stack::entryOf) ?: top
        // The id of the command's screen; for the stack's id, the top's.
        val value = if (node === stack) stack.answerIdOf(top, StackQueue.AnswerKind.Back) else componentId
        if (entries.size == 1) {
            // The only screen of a column goes back to the column before: no queue.
            if (stack.returnsFromColumn { answer.resolve(value) }) return
            return answer.reject(ErrorTexts.firstScreenOfStack(componentId))
        }
        val index = entries.indexOf(entry)
        if (index <= 0) return answer.reject(ErrorTexts.firstScreenOfStack(componentId))
        stack.queue.cut(entries[index - 1], answer, componentId, what, value)
    }

    /** `popTo`: back to the screen [componentId], the target. */
    fun popTo(componentId: String, answer: CommandAnswer, what: String) {
        val stack = stackOf(componentId, answer, entriesOnly = false) ?: return
        val node = find(componentId)
        val entry = node?.takeIf { it !== stack }?.let(stack::entryOf)
        val entries = stack.queue.entries
        if (entry == null) return answer.reject(ErrorTexts.notInThisStack(componentId))
        // A screen of the stack an accepted cut or replace took away is gone for later commands.
        if (entry !in entries) return answer.reject(ErrorTexts.screenNotShown(componentId))
        if (entry === entries.last()) return stack.queue.none(componentId, entry, answer, componentId, what)
        stack.queue.cut(entry, answer, componentId, what, componentId)
    }

    /** `popToRoot`: back to the first screen of the stack of [componentId]. */
    fun popToRoot(componentId: String, answer: CommandAnswer, what: String) {
        val stack = stackOf(componentId, answer) ?: return
        val first = stack.queue.entries.firstOrNull() ?: return answer.reject(ErrorTexts.screenNotShown(componentId))
        val value = stack.answerIdOf(first, StackQueue.AnswerKind.Back)
        // One screen answers at once (C6).
        if (stack.queue.entries.size == 1) return answer.resolve(value)
        stack.queue.cut(first, answer, componentId, what, value)
    }

    /** `setStackRoot`: [layouts] replace every screen of the stack of [componentId], the last on top. */
    fun setStackRoot(componentId: String, layouts: List<LayoutNode>, answer: CommandAnswer, what: String) {
        if (layouts.isEmpty()) return answer.reject(ErrorTexts.noScreensForStack(componentId))
        val stack = stackOf(componentId, answer) ?: return
        // An id of an entry of this stack may come again: the new screen replaces it (`setStackRoot` step 3). The
        // stack's own id, and a screen an accepted cut or replace took away, show elsewhere or still go: as on iOS.
        val shown = LayoutChecks.firstShownId(layouts, ::isShown) { isInEntryOf(stack, it.id) }
        if (shown != null) return answer.reject(ErrorTexts.alreadyShown(shown))
        for (layout in layouts) LayoutChecks.checkStackChild(layout, stack.id)?.let { return answer.reject(it) }
        if (!layouts.all(factory::canMake)) return answer.reject(ErrorTexts.notDoneYet("setStackRoot"))
        val nodes = ArrayList<ViewController<*>>(layouts.size)
        try {
            layouts.forEach { nodes += make(it) }
        } catch (e: Throwable) {
            nodes.forEach(ViewController<*>::destroy)
            throw e
        }
        nodes.forEachIndexed { index, node -> stack.join(node, first = index == 0, key = keyOf(layouts[index])) }
        val animation = stack.resolvedOf(nodes.last()).animations.setStackRoot
        stack.queue.replace(
            nodes = nodes,
            animated = !animation.enabled.isFalse,
            asPush = animation.asPush.isTrue,
            answer = answer,
            listener = null,
            componentId = componentId,
            what = what,
        )
    }

    /**
     * `preparePush`: a slot screen made and laid out hidden in the stack of [componentId] as if it were its top, and
     * its frame for JavaScript; none — and nothing prepared — in the cases of [PreparedPushes.refusal], or when it
     * can't be laid out (no size, no insets) or anything fails (logged).
     */
    fun preparePush(componentId: String, layout: LayoutNode, hasActivity: Boolean): PreparedFrame? = try {
        prepare(componentId, layout, hasActivity)
    } catch (e: Throwable) {
        val reason = ErrorTexts.couldNot("prepare the screen '${layout.id}'", ErrorTexts.reasonOf(e))
        Log.e(NavigationLog.TAG, "[ApexNavigation] preparePush: $reason")
        prepared.discard(layout.id)
        null
    }

    /** `cancelPreparedPush`: the node prepared as [componentId] goes; an unknown or pushed one stays. */
    fun cancelPreparedPush(componentId: String) {
        val node = find(componentId) ?: return
        if (prepared.isPrepared(node)) prepared.discard(componentId)
    }

    private fun prepare(componentId: String, layout: LayoutNode, hasActivity: Boolean): PreparedFrame? {
        val from = find(componentId)?.takeIf { !prepared.isPrepared(it) }
        val stack = from?.let { it as? StackController ?: it.findParentStack() }
        val stackView = stack?.peekView()
        val push = (stack?.resolveCurrentOptions()?.mergeWith(layout.options) ?: layout.options.copy())
            .withDefaultOptions(DefaultOptions.current).animations.push
        val key = keyOf(layout)
        val refusal = PreparedPushes.refusal(
            hasActivity = hasActivity,
            node = layout,
            hasStack = stack != null,
            idShown = isShown(layout.id),
            stackHasView = stackView != null,
            stackShown = stack?.isViewShown == true,
            stackWidth = stackView?.width ?: 0,
            stackHeight = stackView?.height ?: 0,
            keyInStack = key != null && stack?.queue?.entries?.any { stack.keyOf(it) == key } == true,
            push = push,
        )
        if (refusal != null || stack == null || stackView == null) return null
        // A second preparePush with the same id replaces the first.
        prepared.discard(layout.id)
        val node = factory.create(layout) as? ComponentViewController ?: return null
        node.parentController = stack
        prepared.add(node, stack)
        val screenView = node.view
        (node.content as? SlotScreenView)?.isPreparedPush = true
        // Under the bar, hidden, without touches and unseen by accessibility; laid out now with the stack's last
        // insets, its room and its size, as the top it will be.
        screenView.visibility = View.INVISIBLE
        screenView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        stackView.giveInsets(screenView, node.frame.windowInsets)
        stackView.placeUnderBar(screenView)
        node.applyTopInset()
        node.applyBottomInset()
        layoutNow(stackView, screenView)
        node.updateSafeArea()
        val content = node.content?.asView()
        val padding = ScreenEnvironment.padding(node.id)
        val noInsets = content == null || ViewCompat.getRootWindowInsets(content) == null
        if (noInsets || padding == null || content.width <= 0 || content.height <= 0) {
            prepared.discard(node.id)
            return null
        }
        return PreparedFrame.of(content, padding)
    }

    // A measure and a layout of [view] at once, at the container's size less its margins.
    private fun layoutNow(container: ViewGroup, view: View) {
        val margins = view.layoutParams as? ViewGroup.MarginLayoutParams
        val left = margins?.leftMargin ?: 0
        val top = margins?.topMargin ?: 0
        val width = (container.width - left - (margins?.rightMargin ?: 0)).coerceAtLeast(0)
        val height = (container.height - top - (margins?.bottomMargin ?: 0)).coerceAtLeast(0)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(left, top, left + width, top + height)
    }

    // The stack of the screen [componentId] names, after the shared lookups; none when the command rejected.
    private fun stackOf(componentId: String, answer: CommandAnswer, entriesOnly: Boolean = true): StackController? {
        val node = find(componentId)?.takeIf { !prepared.isPrepared(it) }
        if (node == null) {
            answer.reject(ErrorTexts.screenNotShown(componentId))
            return null
        }
        val stack = node as? StackController ?: node.findParentStack()
        if (stack == null) {
            answer.reject(ErrorTexts.notInStack(componentId))
            return null
        }
        // A screen an accepted cut or replace took away is gone for later commands.
        if (entriesOnly && node !== stack && stack.entryOf(node)?.let { it in stack.queue.entries } != true) {
            answer.reject(ErrorTexts.screenNotShown(componentId))
            return null
        }
        return stack
    }

    // The node a command names: the registry's, unless commands don't find it ([isGone]).
    private fun find(id: String): ViewController<*>? = registry.find(id)?.takeUnless(isGone)

    // A node of the registry has [id], and it isn't only prepared for a push (`push` step 1, `preparePush`).
    private fun isShown(id: String): Boolean = registry.find(id)?.let { !prepared.isPrepared(it) } == true

    // The node with [id] is in an entry of [stack] as JavaScript sees it (`StackQueue.entries`), not the stack itself.
    private fun isInEntryOf(stack: StackController, id: String): Boolean {
        val node = find(id)?.takeIf { it !== stack } ?: return false
        return stack.entryOf(node)?.let { it in stack.queue.entries } == true
    }

    // A node of [layout], with its view made now when it has a native screen: its creator's failure rejects the
    // command, and nothing joins the stack. React and slot screens make their views as their operation runs.
    private fun make(layout: LayoutNode): ViewController<*> {
        val node = factory.create(layout)
        if (layout.allNodes().any { it.type == LayoutType.ExternalComponent }) {
            try {
                node.view
            } catch (e: Throwable) {
                node.destroy()
                throw e
            }
        }
        return node
    }

    private companion object {
        // The key of an entry: a pair's is its primary's.
        fun keyOf(layout: LayoutNode): String? = layout.key ?: layout.children.firstOrNull()?.key?.takeIf {
            layout.type == LayoutType.Arrangement
        }
    }
}

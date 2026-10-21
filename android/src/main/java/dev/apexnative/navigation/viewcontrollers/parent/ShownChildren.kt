package dev.apexnative.navigation.viewcontrollers.parent

import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The children a container told they appeared, for a container whose children come into view and leave it without a
 * command (the split view's panes, the pair's screens): its moves go through the dispatcher's order of a transition:
 * `ComponentWillAppear` of what comes at the start of the move ([moveStarted]), and at its end ([moved])
 * `ComponentDidDisappear` of what left, then `ComponentDidAppear` of what came. [shownNow] is what the container shows
 * now (`visibleChildren`). The container's own appearance tells the children it shows (`ParentController`) and reports
 * here. Main thread only.
 */
internal class ShownChildren(private val shownNow: () -> List<ViewController<*>>) {
    /** The container appeared (its show ended), until it disappears: only then do its moves tell its children. */
    var isShown = false
        private set

    // The children told they appeared, until they leave, in the order they came.
    private val appeared = LinkedHashSet<ViewController<*>>()
    // The children whose WillAppear the move on its way sent.
    private val coming = LinkedHashSet<ViewController<*>>()

    /** The container starts to appear: the children it shows are told by its own appearance. */
    fun containerWillAppear() {
        appeared += shownNow()
    }

    /** The container has appeared: from now on its moves tell its children. */
    fun containerDidAppear() {
        isShown = true
        appeared += shownNow()
    }

    /** The container disappeared, with every child (`ParentController.onViewDisappear`), or goes. */
    fun containerDisappeared() {
        isShown = false
        appeared.clear()
        coming.clear()
    }

    /**
     * A move starts (a pane starts to slide in): those of [candidates] that aren't shown start to appear now. A move
     * that ends with them out of view ([moved]) makes them disappear.
     */
    fun moveStarted(candidates: List<ViewController<*>>) {
        if (!isShown) return
        val came = candidates.filter { it !in appeared && it !in coming }
        if (came.isEmpty()) return
        coming += came
        AppearanceDispatcher.beginTransition(came)
    }

    /**
     * The move ended, or what the container shows changed at once (a rotation, a resize): what came starts to appear
     * if it hadn't, what left disappears, then what came appears, and one update of the navigation state is asked for
     * this pass of the main loop; nothing when nothing changed.
     */
    fun moved() {
        if (!isShown) return
        val now = shownNow()
        val left = (appeared + coming).filter { it !in now }
        val came = now.filter { it !in appeared }
        val starting = came.filter { it !in coming }
        appeared.clear()
        appeared += now
        coming.clear()
        if (left.isEmpty() && came.isEmpty()) return
        AppearanceDispatcher.beginTransition(starting)
        // The state stays one posted update of this pass, which a `setRoot`'s hold holds and which comes after a tab
        // switch's `ANN.BottomTabSelected`.
        AppearanceDispatcher.endTransition(
            hiding = left,
            showing = came,
            events = null,
            answer = null,
            value = null,
            reportsState = false,
        )
        AppearanceDispatcher.state.setNeedsUpdate()
    }
}

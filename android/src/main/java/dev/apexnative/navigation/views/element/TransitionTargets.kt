package dev.apexnative.navigation.views.element

import android.os.SystemClock
import android.view.ViewGroup
import dev.apexnative.navigation.options.LayoutAnimation
import dev.apexnative.navigation.utils.DRAW_WAIT_LIMIT
import dev.apexnative.navigation.viewcontrollers.component.ContentReadiness
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import kotlinx.coroutines.android.awaitFrame

/**
 * Waits, a frame at a time and half a second at most, while [waiting] holds, until the screen a transition brings in
 * is ready for it, as on iOS: its content is there, and so is every view the transition moves into it (the targets of
 * the shared elements, and the element transitions found in neither screen yet). That the screen appeared is not
 * enough: a screen of the new API mounts its safe area view first and its content after, and in one tree it appears
 * with that first view. Then nothing was found to move and only the fade played.
 */
suspend fun awaitTransitionTargets(
    animation: LayoutAnimation,
    from: ViewController<*>,
    to: ViewController<*>,
    waiting: () -> Boolean
) {
    val deadline = SystemClock.uptimeMillis() + DRAW_WAIT_LIMIT
    var waited = false
    while (waiting() && !to.isDestroyed && SystemClock.uptimeMillis() < deadline && !areReady(animation, from, to)) {
        awaitFrame()
        waited = true
    }
    // Found on a frame they mounted on, the views are laid out but not drawn yet: a background gets its bounds as it
    // draws, and a shared view that moved into an empty one shrank to a point. One frame more, and they are drawn.
    if (waited && waiting() && !to.isDestroyed) awaitFrame()
}

private fun areReady(animation: LayoutAnimation, from: ViewController<*>, to: ViewController<*>): Boolean {
    val screen = to.shownScreen()
    val content = screen.view as? ViewGroup
    if (content != null && !ContentReadiness.isReady(screen)) return false
    // Each screen walked once for all the ids ([TransitionViews.findViews]); an element is looked for in the screen
    // that goes only when the one that comes doesn't have it.
    val targets = animation.sharedElements.get().map { it.toId.get("") }.filter { it.isNotEmpty() }
    val elements = animation.elementTransitions.transitions.map { it.id }.filter { it.isNotEmpty() }
    if (targets.isEmpty() && elements.isEmpty()) return true
    val inTo = TransitionViews.findViews(to.view, (targets + elements).toSet())
    if (!targets.all { it in inTo }) return false
    val missing = elements.filterNot { it in inTo }.toSet()
    return missing.isEmpty() || TransitionViews.findViews(from.view, missing).size == missing.size
}

// The screen a layout shows: a modal of a stack shows the screen on its top.
private fun ViewController<*>.shownScreen(): ViewController<*> {
    var controller: ViewController<*> = this
    while (controller is ParentController<*>) controller = controller.currentChild ?: break
    return controller
}

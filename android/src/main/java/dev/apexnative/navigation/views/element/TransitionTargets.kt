package dev.apexnative.navigation.views.element

import android.os.SystemClock
import android.view.ViewGroup
import com.facebook.react.uimanager.util.ReactFindViewUtil
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
    val sharedFound = animation.sharedElements.get().all { isIn(to, it.toId.get("")) }
    return sharedFound && animation.elementTransitions.transitions.all { isIn(to, it.id) || isIn(from, it.id) }
}

private fun isIn(screen: ViewController<*>, nativeId: String) =
    nativeId.isEmpty() || ReactFindViewUtil.findView(screen.view, nativeId) != null

// The screen a layout shows: a modal of a stack shows the screen on its top.
private fun ViewController<*>.shownScreen(): ViewController<*> {
    var controller: ViewController<*> = this
    while (controller is ParentController<*>) controller = controller.currentChild ?: break
    return controller
}

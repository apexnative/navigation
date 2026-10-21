package dev.apexnative.navigation.utils

import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import androidx.core.view.OneShotPreDrawListener
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.stack.StackController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.component.ComponentLayout
import dev.apexnative.navigation.views.component.Renderable

/**
 * How long a modal, a root or a stack's new root waits for its screen and the components of its bar to draw, unless
 * waitForRender is set; how long the push of a prepared screen waits for its slot's content to mount.
 */
const val DRAW_WAIT_LIMIT = 500L

/**
 * Runs [start] on a frame once the components of the bar over this screen (title, buttons, background) have drawn, or
 * at [deadline] (uptime millis), while [waiting] holds. A stack waits for those of its screen on top.
 */
fun ViewController<*>.awaitBarComponents(deadline: Long, waiting: () -> Boolean, start: Runnable) {
    // A root of tabs or of a side menu waits for the bar of the stack it shows, as a stack for that of its top screen.
    val parentStack = findParentStack()
    val stack = parentStack ?: shownStack()
    val screen: ViewController<*>? = when {
        this is StackController -> currentChild
        parentStack == null -> stack?.currentChild
        else -> this
    }
    Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!waiting() || isDestroyed) return
            if (stack == null || screen == null || stack.areBarComponentsDrawn(screen) ||
                    SystemClock.uptimeMillis() >= deadline) {
                start.run()
            } else {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    })
}
private fun ViewController<*>.shownStack(): StackController? {
    var controller: ViewController<*>? = this
    while (controller is ParentController<*> && controller !is StackController) controller = controller.currentChild
    return controller as? StackController
}

// Whether where it goes shows: a stack in a tab that is not selected, or a root under a modal, does not. There it would
// never appear and the wait would never end: it starts at once, and is drawn by the time it comes on screen.
private fun ViewController<*>.hostIsShown(): Boolean {
    val host = parentController?.view ?: view.parent as? View ?: return true
    return host.isShown
}

/**
 * Runs [mounted] as the content of this screen's slot mounts into its container, and [start] on the pre-draw of that
 * frame: after React Native applied the whole mount, before the frame is drawn. At once if the content has mounted,
 * after [DRAW_WAIT_LIMIT] if it never does; while [waiting] holds. A screen prepared for its push was rendered at its
 * size and insets before: in that frame it is whole.
 */
fun ViewController<*>.whenContentMounted(waiting: () -> Boolean, mounted: () -> Unit, start: () -> Unit) {
    var done = false
    val run = Runnable {
        if (done || !waiting() || isDestroyed) return@Runnable
        done = true
        mounted()
        // Out of the window no frame comes to draw it. The listener goes with the view's window: a screen destroyed
        // before that frame leaves nothing in it.
        if (!view.isAttachedToWindow) return@Runnable start()
        OneShotPreDrawListener.add(view) { if (waiting() && !isDestroyed) start() }
    }
    val container = (view as? ComponentLayout)?.reactView ?: return run.run()
    UiThread.postDelayed(run, DRAW_WAIT_LIMIT)
    container.whenSlotContentMounted(run)
}

/**
 * waitForRender unset: the screen (or a root) waits, hidden, for its content to draw, then for the components of its
 * bar, half a second at most in all: it comes in drawn, not filled in after. true waits for the content as long as it
 * takes, false starts at once. It appears (its options go to the bars) once its content is drawn.
 */
fun ViewController<*>.whenDrawn(waitForRender: Bool, waiting: () -> Boolean, start: Runnable) {
    if (waitForRender.isFalse || !hostIsShown()) return start.run()
    val limited = !waitForRender.hasValue()
    val deadline = SystemClock.uptimeMillis() + DRAW_WAIT_LIMIT
    view.alpha = 0f
    if (limited) {
        setWaitForRender(Bool(true))
        UiThread.postDelayed({
            if (!waiting() || isDestroyed) return@postDelayed
            // Not drawn in time: it appears as it is.
            setWaitForRender(Bool(false))
            view.requestLayout()
        }, DRAW_WAIT_LIMIT)
    }
    addOnAppearedListener {
        // A pop may have completed it while it waited.
        if (!waiting()) return@addOnAppearedListener
        if (limited) setWaitForRender(waitForRender)
        val barDeadline = if (limited) deadline else SystemClock.uptimeMillis() + DRAW_WAIT_LIMIT
        awaitBarComponents(barDeadline, waiting) {
            view.alpha = 1f
            start.run()
        }
    }
}

/**
 * Whether what this root shows has drawn: the content of its screen (a native screen has none to wait for) and the
 * components of the bar over it, which [whenDrawn] waits for.
 */
fun ViewController<*>.isContentDrawn(): Boolean {
    var screen: ViewController<*> = this
    while (screen is ParentController<*>) {
        if (!screen.isViewCreated) return false
        screen = screen.currentChild ?: return false
    }
    if (screen.isDestroyed || !screen.isViewCreated) return false
    if ((screen.view as? Renderable)?.isRendered == false) return false
    return screen.findParentStack()?.areBarComponentsDrawn(screen) ?: true
}

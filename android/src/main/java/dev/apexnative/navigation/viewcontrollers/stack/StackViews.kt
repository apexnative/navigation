package dev.apexnative.navigation.viewcontrollers.stack

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import dev.apexnative.navigation.options.LayoutAnimation
import dev.apexnative.navigation.utils.UiThread
import dev.apexnative.navigation.utils.whenContentMounted
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.externalcomponent.ExternalComponentViewController
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.Motion
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.AnimationLayer
import dev.apexnative.navigation.views.animations.AnimationRules
import dev.apexnative.navigation.views.animations.SideAnimator
import dev.apexnative.navigation.views.animations.SystemTransition
import dev.apexnative.navigation.views.element.ElementTransition
import dev.apexnative.navigation.views.element.TargetsWait
import dev.apexnative.navigation.views.stack.StackLayout

/**
 * What shows a stack ([StackDisplay]): the views of its screens in its [StackLayout] and their animators — the default
 * transitions and the animations of their own with the transition's elements in a layer over the screens (one
 * animator per screen, which owns them). A screen that waits lies under the current top, transparent and hidden from
 * accessibility (the top keeps the touches); it moves under the bar as its transition starts, without leaving the
 * container (no insets pass). Main thread only.
 */
internal class StackViews(private val stack: StackController) : StackDisplay {
    private val animators = HashMap<ViewController<*>, Animator>()
    // The preview of the back gesture that runs or ends now, and its two screens.
    private var preview: BackPreview? = null
    private var previewScreens: Pair<ViewController<*>, ViewController<*>>? = null
    // The ends of the two screens of a released gesture whose preview plays its end.
    private var committing: Pair<() -> Unit, () -> Unit>? = null
    // What goes back when a screen's animator ends or stops: the elements its transition carries.
    private val cleanups = HashMap<ViewController<*>, () -> Unit>()

    private val layout: StackLayout?
        get() = stack.peekView()?.takeIf { !stack.isDestroyed }

    override val hasView: Boolean
        get() = layout != null

    // A root under a full-screen modal is out of the window (the modal took its view out), so not shown; a stack in a
    // modal or a root that waits to show is in a waiting layer.
    override val isOnScreen: Boolean
        get() = stack.isViewShown && !stack.isInWaitingLayer

    override val canAnimate: Boolean
        get() = isActivitySeen()

    override fun isPlaced(screen: ViewController<*>): Boolean {
        val view = screen.peekView() ?: return false
        return layout?.holds(view) == true && view.visibility == View.VISIBLE
    }

    override fun placeWaiting(screen: ViewController<*>) {
        val layout = layout ?: return
        val view = screen.view
        layout.giveInsets(view, insetsOf(screen))
        layout.placeUnder(view, stack.currentChild?.peekView())
        hide(view)
    }

    override fun placeLower(screen: ViewController<*>) {
        val layout = layout ?: return
        val view = screen.view
        layout.giveInsets(view, insetsOf(screen))
        layout.placeAtBottom(view)
        hide(view)
    }

    override fun raise(screen: ViewController<*>) {
        val layout = layout ?: return
        val view = screen.view
        layout.giveInsets(view, insetsOf(screen))
        layout.placeUnderBar(view)
        seen(view)
    }

    override fun reveal(screen: ViewController<*>, over: ViewController<*>) {
        val layout = layout ?: return
        val view = screen.view
        layout.giveInsets(view, insetsOf(screen))
        layout.placeUnder(view, over.peekView())
        seen(view)
    }

    override fun hide(screen: ViewController<*>) {
        screen.peekView()?.visibility = View.INVISIBLE
    }

    override fun detach(screen: ViewController<*>) {
        stop(screen)
        val view = screen.peekView() ?: return
        SideAnimator.rest(view)
        seen(view)
        layout?.take(view)
    }

    override fun remove(screen: ViewController<*>) {
        stop(screen)
        val view = screen.peekView() ?: return
        if (view.parent === layout) (view.parent as? ViewGroup)?.removeView(view)
    }

    override fun rest(screen: ViewController<*>) {
        val view = screen.peekView() ?: return
        SideAnimator.rest(view)
        seen(view)
    }

    override fun animate(screen: ViewController<*>, motion: Motion, fromCurrent: Boolean, done: () -> Unit) {
        stop(screen)
        val view = screen.peekView() ?: return done()
        val layout = layout
        val animator = StackTransitions.animator(view, motion, fromCurrent) { layout?.edgesMoved() }
        cleanups[screen] = dress(view, motion, layout)
        animator.addListener(object : AnimatorListenerAdapter() {
            // Ended or cancelled by anything else than [stop]: the screen's part is done.
            override fun onAnimationEnd(animation: Animator) {
                if (animators[screen] === animator) {
                    animators.remove(screen)
                    cleanups.remove(screen)?.invoke()
                }
                done()
            }
        })
        animators[screen] = animator
        animator.start()
    }

    // For the default transition [view] moves as the system's windows do: its edge extended by the distance it moves,
    // and its fade drawn through a hardware layer, so the whole screen isn't drawn again at every frame of it. What
    // undoes both.
    private fun dress(view: View, motion: Motion, layout: StackLayout?): () -> Unit {
        StackTransitions.extendsStart(motion)?.let { start ->
            layout?.extendEdge(view, start, (SystemTransition.DISTANCE_DP * view.resources.displayMetrics.density).toInt())
        }
        val layered = StackTransitions.fades(motion) && view.layerType == View.LAYER_TYPE_NONE
        if (layered) view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        return {
            layout?.dropEdge(view)
            if (layered && view.layerType == View.LAYER_TYPE_HARDWARE) view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    // The elements play in their screens and the pairs' copies fly in a layer over them, all done as the screen's
    // animator ends or stops. A shared element without a duration takes the screen's fade: its own single `alpha`,
    // else 300 ms.
    override fun animateCustom(screen: ViewController<*>, custom: StackDisplay.Custom, done: () -> Unit) {
        stop(screen)
        val view = screen.peekView() ?: return done()
        custom.alpha?.let { view.alpha = it }
        val own = custom.side?.let { SideAnimator.build(view, it, SideAnimator.Base(alpha = custom.alpha)) }
        val fade = if (own == null) StackTransitions.fade(view, custom.fade) else null
        // A fade of the whole screen draws it through a hardware layer, as the default transition does ([dress]).
        for (animator in listOfNotNull(own, fade)) AnimationLayer.during(view, animator)
        val elements = custom.elements?.let { elements ->
            val layout = layout ?: return@let null
            val fadeLength = custom.side?.takeIf(AnimationRules::isSingleAlpha)?.let(AnimationRules::lengthOf)
            ElementTransition.startOrLog(
                elements.animation, elements.from, elements.to, layout, null,
                fadeLength ?: AnimationRules.SCREEN_FADE,
            )
        }
        val parts = listOfNotNull(own, fade) + elements?.animators.orEmpty()
        if (parts.isEmpty()) {
            elements?.finish()
            return done()
        }
        val set = AnimatorSet().apply { playTogether(parts) }
        elements?.let { cleanups[screen] = it::finish }
        set.addListener(object : AnimatorListenerAdapter() {
            // Ended or cancelled by anything else than [stop]: the elements back, then the screen's part is done.
            override fun onAnimationEnd(animation: Animator) {
                if (animators[screen] === set) {
                    animators.remove(screen)
                    cleanups.remove(screen)?.invoke()
                }
                done()
            }
        })
        animators[screen] = set
        set.start()
    }

    override fun awaitTargets(
        animation: LayoutAnimation,
        from: ViewController<*>?,
        to: ViewController<*>,
        waiting: () -> Boolean,
        done: () -> Unit,
    ): () -> Unit = TargetsWait.start(animation, from, to, waiting, done)

    /**
     * Starts the preview of a back gesture over [top], [target] under it (`BackPreview`): none without the stack's view
     * or a window. [onReveal] shows [target] with its bar and its FAB as the preview's picture is there.
     */
    fun startBackPreview(
        top: ViewController<*>,
        target: ViewController<*>,
        fromLeft: Boolean,
        touchY: Float,
        onReveal: () -> Unit,
    ): BackPreview? {
        val bottomRoom = top.getBottomInset()
        endPreview()
        val layout = layout ?: return null
        val window = stack.activity.window ?: return null
        val topView = top.peekView() ?: return null
        val targetView = target.peekView() ?: return null
        val started = BackPreview(layout, topView, targetView, fromLeft, touchY, bottomRoom, onReveal) {
            stack.fab.shownView
        }
        preview = started
        previewScreens = top to target
        started.start(window)
        return started
    }

    /** The preview of the back gesture went: cancelled and put back, or never shown. */
    fun previewEnded(ended: BackPreview) {
        if (preview !== ended) return
        preview = null
        previewScreens = null
    }

    override fun hasBackPreview(top: ViewController<*>, target: ViewController<*>): Boolean =
        preview?.isReady == true && previewScreens == top to target

    override fun finishBackGesture(
        top: ViewController<*>,
        target: ViewController<*>,
        topDone: () -> Unit,
        targetDone: () -> Unit,
    ) {
        val running = preview?.takeIf { hasBackPreview(top, target) } ?: run {
            topDone()
            targetDone()
            return
        }
        committing = topDone to targetDone
        running.commit {
            committing = null
            previewEnded(running)
            topDone()
            targetDone()
        }
    }

    private fun endPreview() {
        val ended = preview ?: return
        preview = null
        previewScreens = null
        committing = null
        ended.endAtOnce()
    }

    // A later transition takes one screen of a released gesture's preview: the preview ends where it is, the popped
    // screen stays out of sight until the pop takes it away, and the other screen's part ends on the next turn.
    private fun stopPreview(screen: ViewController<*>) {
        val (top, target) = previewScreens ?: return
        if (screen !== top && screen !== target) return
        val dones = committing
        endPreview()
        if (dones == null) return
        top.peekView()?.alpha = 0f
        val other = if (screen === top) dones.second else dones.first
        UiThread.post { other() }
    }

    override fun stop(screen: ViewController<*>) {
        stopPreview(screen)
        val animator = animators.remove(screen)
        if (animator != null) {
            animator.removeAllListeners()
            animator.cancel()
        }
        cleanups.remove(screen)?.invoke()
    }

    override fun awaitPrepared(
        screen: ViewController<*>,
        waiting: () -> Boolean,
        mounted: () -> Unit,
        start: () -> Unit,
    ) {
        screen.whenContentMounted(
            waiting = waiting,
            mounted = {
                // The frame of the mount: the screen shows transparent and lays out. The pre-draw of that frame hands
                // the start to the queue, whose transition begins behind the calls JavaScript made before.
                screen.peekView()?.let {
                    it.alpha = 0f
                    it.visibility = View.VISIBLE
                }
                mounted()
            },
            start = start,
        )
    }

    // A view that waits or renders under the others: transparent, without touches, unseen by accessibility.
    private fun hide(view: View) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private fun seen(view: View) {
        view.visibility = View.VISIBLE
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }

    // The window insets a screen got last.
    private fun insetsOf(screen: ViewController<*>): WindowInsetsCompat? = when (screen) {
        is ComponentViewController -> screen.frame.windowInsets
        is ExternalComponentViewController -> screen.frame.windowInsets
        else -> null
    }

    // The frames of an animation come only while the activity can be seen (the display on, the app in front).
    private fun isActivitySeen(): Boolean =
        (stack.activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != false
}

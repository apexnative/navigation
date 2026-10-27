package dev.apexnative.navigation.viewcontrollers.modal

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.apexnative.navigation.options.ModalAnimationOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.dp
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.AnimationLayer
import dev.apexnative.navigation.views.animations.AnimationRules
import dev.apexnative.navigation.views.animations.SideAnimator
import dev.apexnative.navigation.views.element.ElementTransition
import dev.apexnative.navigation.views.element.TargetsWait
import java.util.WeakHashMap

/**
 * The views of the modals of one activity ([ModalCoordinator.Views]): a [ModalContainer] per modal in the modals
 * layer, newer above older; the default slide ([ModalAnimations]) or the options' own animation with its elements; the
 * Material sheet of a modal shown as one (`ModalSheet`); the layer under a modal out of its layer and back; the modals
 * of an old root carried into the root layer for its transition. The modals layer is visible only while it holds a
 * modal. Main thread only.
 */
internal class ModalViews(
    // The activity's context: the containers' views are made with it.
    private val context: Context,
    private val modalsLayer: FrameLayout,
    private val rootLayer: FrameLayout,
) : ModalCoordinator.Views {
    // The views of one modal; what goes back when its animation of its own ends or stops (its elements).
    private class Parts(val container: ModalContainer) {
        var sheet: ModalSheet? = null
        var animator: Animator? = null
        var cleanup: (() -> Unit)? = null
    }

    private val parts = HashMap<ModalRecord, Parts>()
    private val carried = mutableListOf<ModalRecord>()
    // The views under a modal that its show's own `exit` moved, and the modal: they go back to rest as they leave the
    // layout, when the show stops, and when that modal goes unless its dismissal's `enter` took them.
    private val moved: MutableMap<View, ModalRecord> = WeakHashMap()

    /** The containers of the old root's modals in the root layer: they leave with the old root's transition. */
    val carriedViews: List<View>
        get() = carried.mapNotNull { parts[it]?.container }

    override fun place(record: ModalRecord) {
        val container = ModalContainer(context)
        container.isWaiting = true
        container.alpha = 0f
        container.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        try {
            val view = record.root.view
            (view.parent as? ViewGroup)?.removeView(view)
            // A sheet's content waits at the sheet's full height, under the status bar, and at its width.
            val params = CoordinatorLayout.LayoutParams(MATCH, MATCH)
            if (record.isSheet) {
                params.topMargin = statusBarTop()
                params.gravity = Gravity.CENTER_HORIZONTAL
                // The modals layer is hidden while it has no modal: the window's content gives the width.
                val maxWidth = ModalSheet.MAX_WIDTH_DP.dp
                if (((modalsLayer.parent as? View)?.width ?: 0) > maxWidth) params.width = maxWidth
            }
            container.addView(view, params)
            modalsLayer.addView(container, FrameLayout.LayoutParams(MATCH, MATCH))
        } catch (e: Throwable) {
            (container.parent as? ViewGroup)?.removeView(container)
            throw e
        }
        parts[record] = Parts(container)
        modalsLayer.visibility = View.VISIBLE
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
        val parts = parts[record] ?: return none(onEnd)
        val container = parts.container
        container.isWaiting = false
        container.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        if (record.isSheet) return presentSheet(record, parts, options, animated, seen, events, onEnd)
        applyTestIds(record, parts, options)
        if (!animated || !seen) {
            SideAnimator.rest(container)
            return none(onEnd)
        }
        if (motion != null) {
            // The modal shows with its alpha at rest; its side writes its own start values.
            container.alpha = 1f
            container.translationY = 0f
            return playOwn(record, parts, motion, showing = true, onEnd)
        }
        // The system's slide over the window below: the modal comes up whole, without a fade.
        val distance = distanceOf(container)
        container.alpha = 1f
        container.translationY = distance
        val animator = ObjectAnimator.ofFloat(container, View.TRANSLATION_Y, distance, 0f)
        animator.interpolator = TimeInterpolator { ModalAnimations.linearOutSlowIn(it) }
        return play(parts, animator, ModalAnimations.SHOW_DURATION, onEnd)
    }

    // The root's view leaves the container and goes into the sheet's, in one turn; the sheet slides to its first
    // detent, or stands there at once when not [animated]. Nobody sees the activity: the show counts as done now, the sheet's later end does nothing.
    private fun presentSheet(
        record: ModalRecord,
        parts: Parts,
        options: Options,
        animated: Boolean,
        seen: Boolean,
        events: ModalSheet.Events,
        onEnd: () -> Unit,
    ): () -> Unit {
        val container = parts.container
        val view = record.root.view
        container.removeView(view)
        val sheet = ModalSheet(container, view, events) { (shownScreen(record.root) as? ComponentViewController)?.content }
        parts.sheet = sheet
        container.alpha = 1f
        applyTestIds(record, parts, options)
        var told = false
        val shown = {
            if (!told) {
                told = true
                onEnd()
            }
        }
        sheet.show(options.modal.sheet, swipeToDismissOf(options), animated) { shown() }
        if (!seen) shown()
        return { told = true }
    }

    // The screen a layout shows now: down through the child each parent shows.
    private fun shownScreen(root: ViewController<*>): ViewController<*>? {
        var node = root
        while (node is ParentController<*>) node = node.currentChild ?: return null
        return node
    }

    override fun dismiss(
        record: ModalRecord,
        animated: Boolean,
        seen: Boolean,
        onEnd: () -> Unit,
        motion: ModalCoordinator.Motion?,
    ): () -> Unit {
        val parts = parts[record] ?: return none(onEnd)
        stopAnimation(parts)
        val sheet = parts.sheet
        if (sheet != null) {
            if (!seen) return none(onEnd)
            var told = false
            sheet.close(animated) {
                if (!told) {
                    told = true
                    onEnd()
                }
            }
            return { told = true }
        }
        if (!animated || !seen) return none(onEnd)
        if (motion != null) return playOwn(record, parts, motion, showing = false, onEnd)
        val container = parts.container
        val animator = ObjectAnimator.ofFloat(
            container, View.TRANSLATION_Y, container.translationY, distanceOf(container),
        )
        animator.interpolator = TimeInterpolator { ModalAnimations.accelerate(it) }
        return play(parts, animator, ModalAnimations.DISMISS_DURATION, onEnd)
    }

    override fun awaitTargets(
        record: ModalRecord,
        animation: ModalAnimationOptions,
        under: ViewController<*>?,
        waiting: () -> Boolean,
        done: () -> Unit,
    ): () -> Unit = TargetsWait.start(animation, under, record.root, waiting, done)

    override fun remove(record: ModalRecord) {
        carried -= record
        val parts = parts.remove(record) ?: return
        stopAnimation(parts)
        // What its show's `exit` moved and no dismissal's `enter` took goes back to rest with it.
        moved.entries.filter { it.value === record }.forEach {
            moved.remove(it.key)
            SideAnimator.rest(it.key)
        }
        val container = parts.container
        parts.sheet?.let { sheet ->
            // The sheet lets go of the window's layout listener through its container's observer: a container out of
            // the window (under a covering modal) joins the layer for it, unseen, so the observer is the window's.
            if (container.parent == null) {
                container.visibility = View.GONE
                modalsLayer.addView(container, FrameLayout.LayoutParams(MATCH, MATCH))
            }
            sheet.remove()
        }
        (container.parent as? ViewGroup)?.removeView(container)
        hideEmptyLayer()
    }

    override fun settle(record: ModalRecord) {
        val container = parts[record]?.container ?: return
        SideAnimator.rest(container)
    }

    override fun takeOut(record: ModalRecord) {
        val container = parts[record]?.container ?: return
        if (moved.remove(container) != null) SideAnimator.rest(container)
        if (container.parent === modalsLayer) modalsLayer.removeView(container)
    }

    override fun putBack(record: ModalRecord, at: ModalRecord?) {
        val container = parts[record]?.container ?: return
        if (container.parent != null) return
        val index = at?.let { parts[it]?.container }?.let(modalsLayer::indexOfChild)?.takeIf { it >= 0 }
        modalsLayer.addView(container, index ?: modalsLayer.childCount, FrameLayout.LayoutParams(MATCH, MATCH))
        modalsLayer.visibility = View.VISIBLE
    }

    override fun takeOutRoot(root: ViewController<*>) {
        val view = root.peekView() ?: return
        if (moved.remove(view) != null) SideAnimator.rest(view)
        if (view.parent === rootLayer) rootLayer.removeView(view)
    }

    override fun putBackRoot(root: ViewController<*>) {
        val view = root.peekView() ?: return
        if (view.parent != null) return
        rootLayer.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    override fun carry(record: ModalRecord, oldRoot: ViewController<*>?) {
        val container = parts[record]?.container ?: return
        (container.parent as? ViewGroup)?.removeView(container)
        // Right above the old root's view and the modals carried before, bottom to top.
        val below = carriedViews.lastOrNull() ?: oldRoot?.peekView()
        val index = below?.let(rootLayer::indexOfChild)?.takeIf { it >= 0 }?.plus(1) ?: rootLayer.childCount
        rootLayer.addView(container, index, FrameLayout.LayoutParams(MATCH, MATCH))
        carried += record
        hideEmptyLayer()
    }

    override fun update(record: ModalRecord, options: Options, merge: Options?) {
        val parts = parts[record] ?: return
        applyTestIds(record, parts, options)
        parts.sheet?.update(options.modal.sheet, swipeToDismissOf(options), merge?.modal?.sheet)
    }

    override fun repaint(record: ModalRecord) {
        (record.root.peekView()?.parent as? SheetContainer)?.applyBackground()
    }

    override fun requestLayout(record: ModalRecord) {
        parts[record]?.container?.requestLayout()
    }

    override fun sheetBack(record: ModalRecord): SheetBack? = parts[record]?.sheet

    // `modal.testID` on the modal's view; a sheet's on the sheet's container, `modal.sheet.testID` first, and its
    // grabber and scrim after it.
    private fun applyTestIds(record: ModalRecord, parts: Parts, options: Options) {
        val modalId = options.modal.testID.value?.ifEmpty { null }
        if (!record.isSheet) return TestIds.apply(parts.container, modalId)
        TestIds.apply(parts.container, null)
        parts.sheet?.setTestIds(options.modal.sheet.testID.value?.ifEmpty { null } ?: modalId)
    }

    // The modal's own show or dismissal: its side or its fade on its container, the side of the layer under it on that
    // layer's view, and the elements in a layer between the modals and the overlays. They end together; a stop puts the
    // elements back and the layer under at rest.
    private fun playOwn(
        record: ModalRecord,
        parts: Parts,
        motion: ModalCoordinator.Motion,
        showing: Boolean,
        onEnd: () -> Unit,
    ): () -> Unit {
        val container = parts.container
        val side = motion.side?.let {
            SideAnimator.build(container, it, SideAnimator.Base(alpha = if (showing) 1f else null))
        }
        val fade = if (side == null && motion.fades) fade(container, showing) else null
        val underView = motion.under?.let(::viewOf)
        // The layer a dismissal reveals comes back to its alpha at rest, whatever the show's `exit` left.
        val underBase = SideAnimator.Base(alpha = if (showing) null else 1f)
        val under = motion.underSide?.let { side -> underView?.let { SideAnimator.build(it, side, underBase) } }
        if (under != null && underView != null) {
            if (showing) moved[underView] = record else moved.remove(underView)
            AnimationLayer.during(underView, under)
        }
        // A fade of the modal or of the layer under it draws it through a hardware layer, given back at its end and at
        // any stop (the set's cancel cancels it).
        for (animator in listOfNotNull(side, fade)) AnimationLayer.during(container, animator)
        val fadeLength = motion.side?.takeIf(AnimationRules::isSingleAlpha)?.let(AnimationRules::lengthOf)
        val elements = motion.elements?.let { animation ->
            val content = modalsLayer.parent as? ViewGroup ?: return@let null
            val to = motion.to ?: return@let null
            ElementTransition.startOrLog(
                animation, motion.from, to, content, content.indexOfChild(modalsLayer) + 1,
                fadeLength ?: AnimationRules.SCREEN_FADE,
            )
        }
        val animators = listOfNotNull(side, fade, under) + elements?.animators.orEmpty()
        if (animators.isEmpty()) {
            elements?.finish()
            return none(onEnd)
        }
        val set = AnimatorSet().apply { playTogether(animators) }
        parts.cleanup = {
            elements?.finish()
            if (showing && underView != null && moved[underView] === record) {
                moved.remove(underView)
                SideAnimator.rest(underView)
            }
        }
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (parts.animator === animation) {
                    parts.animator = null
                    // Ended: the elements go back; the layer under stays where its side took it until it leaves.
                    elements?.finish()
                    parts.cleanup = null
                }
                onEnd()
            }
        })
        parts.animator = set
        set.start()
        return {
            if (parts.animator === set) stopAnimation(parts)
        }
    }

    // A modal's animator stops where it is, without its end; what its own animation moved goes back.
    private fun stopAnimation(parts: Parts) {
        parts.animator?.let {
            parts.animator = null
            it.removeAllListeners()
            it.cancel()
        }
        parts.cleanup?.invoke()
        parts.cleanup = null
    }

    // The fade of a modal around elements: in from 0, out from where it is, 300 ms, linearly.
    private fun fade(view: View, showing: Boolean): Animator {
        val from = if (showing) 0f else view.alpha
        view.alpha = from
        return ObjectAnimator.ofFloat(view, View.ALPHA, from, if (showing) 1f else 0f).apply {
            duration = AnimationRules.SCREEN_FADE
            interpolator = LinearInterpolator()
        }
    }

    // The view a layer under a modal shows: a modal's container, or the root's view.
    private fun viewOf(node: ViewController<*>): View? =
        parts.entries.firstOrNull { it.key.root === node }?.value?.container ?: node.peekView()

    private fun play(parts: Parts, animator: Animator, duration: Long, onEnd: () -> Unit): () -> Unit {
        animator.duration = duration
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (parts.animator === animation) parts.animator = null
                onEnd()
            }
        })
        parts.animator = animator
        animator.start()
        return {
            if (parts.animator === animator) parts.animator = null
            animator.removeAllListeners()
            animator.cancel()
        }
    }

    // An end at once, and nothing to stop.
    private fun none(onEnd: () -> Unit): () -> Unit {
        onEnd()
        return {}
    }

    private fun hideEmptyLayer() {
        if (modalsLayer.childCount == 0) modalsLayer.visibility = View.GONE
    }

    // The window's height at the start, the display's while the window has none.
    private fun distanceOf(view: View): Float =
        ModalAnimations.distance(view.rootView?.height ?: 0, view.resources.displayMetrics.heightPixels)

    private fun statusBarTop(): Int =
        ViewCompat.getRootWindowInsets(modalsLayer)?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        // `modal.swipeToDismiss`, `true` by default.
        fun swipeToDismissOf(options: Options): Boolean = !options.modal.swipeToDismiss.isFalse
    }
}

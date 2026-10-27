package dev.apexnative.navigation.views.element

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import dev.apexnative.navigation.options.ElementTransitionOptions
import dev.apexnative.navigation.options.LayoutAnimation
import dev.apexnative.navigation.options.SharedElementTransitionOptions
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.utils.DevelopmentLog
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.utils.SystemAnimations
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.AnimationLayer
import dev.apexnative.navigation.views.animations.SideAnimator
import dev.apexnative.navigation.views.element.animators.SharedAnimatorFactory
import java.util.Collections
import java.util.WeakHashMap

/**
 * The elements of one transition: the element transitions and the shared pairs found by their `nativeID` (depth first,
 * the first match) in the whole view of the screen each side shows — an element in the screen that goes first, then in
 * the one that comes; a pair's `fromId` in the screen that goes, its `toId` in the one that comes; each screen is
 * walked once for all of them ([TransitionViews]). What isn't found, what another transition plays now and an image
 * that isn't loaded is skipped; so is an element whose start fails ([elementStep]): it gets its values back at once and
 * the rest plays. No view leaves its parent, as on iOS. An element plays where it is, in its screen: its properties
 * animate about its center and it moves with its screen. A pair flies as a copy drawn in a [TransitionLayer] over the
 * screens, the copies in the order their targets are drawn; its `to` is hidden from the start, its `from` from the
 * start of its flight. [finish] gives every element the values of its properties read before, shows the pairs' views
 * again and takes the layer away. Main thread only.
 */
internal class ElementTransition private constructor(private val layer: TransitionLayer?) {
    // An element playing where it is: what it was.
    private class InPlace(val view: View, val values: FloatArray, val pivotSet: Boolean)

    private val inPlace = ArrayList<InPlace>()
    private val taken = ArrayList<View>()
    // The views of the pairs hidden while their copies fly, with the alpha each had.
    private val hidden = LinkedHashMap<View, Float>()
    private var finished = false

    /** The animators of the elements and of the shared elements, each with its own time. */
    val animators = ArrayList<Animator>()

    /**
     * Every element's values back, the pairs' views seen, the layer gone; at the end of the transition and at any stop.
     * Once.
     */
    fun finish() {
        if (finished) return
        finished = true
        for ((view, alpha) in hidden) show(view, alpha)
        hidden.clear()
        // Each element on its own: one that can't get its values back doesn't keep the others from theirs.
        for (item in inPlace.asReversed()) {
            try {
                restore(item.view, item.values, item.pivotSet)
            } catch (e: RuntimeException) {
                Log.w(NavigationLog.TAG, "[ApexNavigation] An element of a transition couldn't get its values back", e)
            }
        }
        inPlace.clear()
        playing -= taken.toSet()
        taken.clear()
        layer?.detach()
    }

    private fun restore(view: View, values: FloatArray, pivotSet: Boolean) {
        view.alpha = values[ALPHA]
        view.translationX = values[TRANSLATION_X]
        view.translationY = values[TRANSLATION_Y]
        view.scaleX = values[SCALE_X]
        view.scaleY = values[SCALE_Y]
        view.rotation = values[ROTATION]
        view.rotationX = values[ROTATION_X]
        view.rotationY = values[ROTATION_Y]
        if (!pivotSet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.resetPivot()
        } else {
            view.pivotX = values[PIVOT_X]
            view.pivotY = values[PIVOT_Y]
        }
    }

    // An element with views in it fades through a hardware layer only where its alpha would draw it into an offscreen
    // buffer at every frame (AnimationLayer: its rendering overlaps). React's views overlap only with
    // `needsOffscreenAlphaCompositing`, so most elements fade without one and nothing they draw outside their bounds (a
    // box shadow) is clipped. A view without children fades its own drawing at no extra cost. Scale and rotations turn
    // about the element's center; `x` and `y` are its place in its own parent.
    private fun addElement(options: ElementTransitionOptions, view: View) {
        inPlace += InPlace(view, valuesOf(view), pivotSetOf(view))
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        val animator = SideAnimator.build(view, options, SideAnimator.Base()) ?: return
        if (view is ViewGroup) AnimationLayer.during(view, animator)
        animators += animator
    }

    // An element whose start failed gets its values back now.
    private fun giveValuesBack(view: View) {
        val item = inPlace.lastOrNull { it.view === view } ?: return
        inPlace -= item
        try {
            restore(view, item.values, item.pivotSet)
        } catch (e: RuntimeException) {
            Log.w(NavigationLog.TAG, "[ApexNavigation] An element of a transition couldn't get its values back", e)
        }
    }

    // The pair's flight: `to` hidden from now, `from` from its start, both seen again when it stops or its copy can't
    // be drawn. Nothing changes when it doesn't fly.
    private fun addPair(
        options: SharedElementTransitionOptions,
        from: View,
        to: View,
        name: String,
        fallbackDuration: Long,
    ) {
        val layer = layer ?: return
        val flight = SharedAnimatorFactory.create(
            from, to, layer, options, fallbackDuration, DevelopmentLog::warn,
        ) ?: return
        val fromAlpha = from.alpha
        val seenAgain = {
            flight.picture.shown = false
            hidden.remove(from)?.let { show(from, it) }
            hidden.remove(to)?.let { show(to, it) }
            layer.invalidate()
        }
        flight.picture.onFailed = { e ->
            elementSkipped(name, e.toString())
            seenAgain()
        }
        flight.animator.addListener(object : AnimatorListenerAdapter() {
            private var stopped = false

            override fun onAnimationStart(animation: Animator) {
                if (stopped || finished) return
                hidden[from] = fromAlpha
                from.alpha = 0f
            }

            // A flight whose start or frame failed stops (`MoveAnimator`), maybe inside its own start.
            override fun onAnimationCancel(animation: Animator) {
                stopped = true
                seenAgain()
            }
        })
        layer.add(flight.picture)
        hidden[to] = to.alpha
        to.alpha = 0f
        animators += flight.animator
    }

    companion object {
        private const val ALPHA = 0
        private const val TRANSLATION_X = 1
        private const val TRANSLATION_Y = 2
        private const val SCALE_X = 3
        private const val SCALE_Y = 4
        private const val ROTATION = 5
        private const val ROTATION_X = 6
        private const val ROTATION_Y = 7
        private const val PIVOT_X = 8
        private const val PIVOT_Y = 9

        // The views the transitions that run now play: another transition leaves them alone.
        private val playing: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())

        private fun valuesOf(view: View) = floatArrayOf(
            view.alpha, view.translationX, view.translationY, view.scaleX, view.scaleY, view.rotation,
            view.rotationX, view.rotationY, view.pivotX, view.pivotY,
        )

        private fun pivotSetOf(view: View) = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && view.isPivotSet

        // A view hidden for a flight shows again with its alpha, unless React gave it another one meanwhile. React
        // writes the same alpha: an opacity it commits during the flight shows the view beside its copy until the end.
        private fun show(view: View, alpha: Float) {
            if (view.alpha == 0f) view.alpha = alpha
        }

        /**
         * The elements of [animation] from [from] to [to]; the copies of the pairs in a layer that joins [host] at
         * [layerIndex] (its top without one). None when none of them is found or the system's animations are off. A
         * shared element without a duration takes [fallbackDuration], the screen's fade.
         */
        fun start(
            animation: LayoutAnimation,
            from: ViewController<*>?,
            to: ViewController<*>,
            host: ViewGroup,
            layerIndex: Int?,
            fallbackDuration: Long,
        ): ElementTransition? {
            if (SystemAnimations.areOff(host.context)) return null
            val fromView = from?.let { AppearanceDispatcher.visibleNode(it).peekView() }
            val toView = AppearanceDispatcher.visibleNode(to).peekView()
            val transitions = animation.elementTransitions.transitions
            val shared = animation.sharedElements.get()
            val views = TransitionViews(shared, transitions, fromView, toView)
            val taken = HashSet<View>()
            val elements = ArrayList<Pair<ElementTransitionOptions, View>>()
            for (element in transitions) {
                if (element.id.isEmpty()) continue
                val view = views.leaving(element.id) ?: views.arriving(element.id) ?: continue
                if (view in playing || !taken.add(view)) continue
                elements += element to view
            }
            val pairs = ArrayList<Triple<SharedElementTransitionOptions, View, View>>()
            for (pair in shared) {
                val fromId = pair.fromId.get(null) ?: continue
                val toId = pair.toId.get(null) ?: continue
                val source = views.leaving(fromId) ?: continue
                val target = views.arriving(toId) ?: continue
                if (source === target || source in playing || target in playing) continue
                if (target in taken || source in taken) continue
                if (!loaded(source, fromId) || !loaded(target, toId)) continue
                taken += source
                taken += target
                pairs += Triple(pair, source, target)
            }
            if (elements.isEmpty() && pairs.isEmpty()) return null
            val layer = if (pairs.isEmpty()) null else TransitionLayer(host.context)
            val transition = ElementTransition(layer)
            try {
                layer?.attachTo(host, layerIndex ?: host.childCount)
                transition.fill(host, elements, pairs, fallbackDuration)
            } catch (e: RuntimeException) {
                transition.finish()
                throw e
            }
            return transition
        }

        /**
         * [start], and when that fails, a line in the log and none: the screens play without their elements; every
         * element has its values back already.
         */
        fun startOrLog(
            animation: LayoutAnimation,
            from: ViewController<*>?,
            to: ViewController<*>,
            host: ViewGroup,
            layerIndex: Int?,
            fallbackDuration: Long,
        ): ElementTransition? = try {
            start(animation, from, to, host, layerIndex, fallbackDuration)
        } catch (e: RuntimeException) {
            Log.w(NavigationLog.TAG, "[ApexNavigation] The transition's elements failed: they don't play", e)
            null
        }

        // An image not loaded drops out of the transition; its wait logged it already, else it is logged now.
        private fun loaded(view: View, id: String): Boolean {
            if (ImageLoads.isLoaded(view)) return true
            ImageLoads.failed(view, id, DevelopmentLog::warn)
            return false
        }
    }

    // The elements' animators where they are, then the pairs' flights, their copies in the order their targets are
    // drawn. Each element's steps run on their own ([elementStep]): one that fails is left as it was, the others play.
    private fun fill(
        host: ViewGroup,
        elements: List<Pair<ElementTransitionOptions, View>>,
        pairs: List<Triple<SharedElementTransitionOptions, View, View>>,
        fallbackDuration: Long,
    ) {
        for ((_, view) in elements) taken += view
        for ((_, source, target) in pairs) taken += listOf(source, target)
        playing += taken
        for ((options, view) in elements) {
            if (!elementStep(elementName(options.id)) { addElement(options, view) }) giveValuesBack(view)
        }
        for ((options, source, target) in DrawOrder.sorted(pairs, host) { it.third }) {
            val name = pairName(options.fromId.get(""), options.toId.get(""))
            elementStep(name) { addPair(options, source, target, name, fallbackDuration) }
        }
    }
}

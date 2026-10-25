package dev.apexnative.navigation.views.element

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.facebook.react.views.view.ReactViewGroup
import dev.apexnative.navigation.options.ElementTransitionOptions
import dev.apexnative.navigation.options.LayoutAnimation
import dev.apexnative.navigation.options.SharedElementTransitionOptions
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.utils.DevelopmentLog
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.AnimationLayer
import dev.apexnative.navigation.views.animations.SideAnimator
import dev.apexnative.navigation.views.element.animators.SharedAnimatorFactory

/**
 * The elements of one transition: the element transitions and the targets of the shared elements found by their
 * `nativeID` (depth first, the first match) in the whole view of the screen each side shows — an element in the screen
 * that goes first, then in the one that comes; a pair's `fromId` in the screen that goes, its `toId` in the one that
 * comes; each screen is walked once for all of them ([TransitionViews]). What isn't found, what is in another
 * transition's layer and an image that isn't loaded is skipped; so is an element whose start fails ([elementStep]):
 * it goes back at once and the rest plays. Each view found moves into a [TransitionLayer] at its place on the screen,
 * in the order its hierarchy draws it, and an [ElementStub] takes its index in its parent. A pair's `from` stays where
 * it is, hidden while its copy flies. [finish] puts every view back at its index with its layout, its pivot and the
 * values of its properties read before — unless React removed its stub meanwhile: then it is gone for good —, shows
 * the `from`s again and takes the layer away. Main thread only.
 */
internal class ElementTransition private constructor(private val layer: TransitionLayer) {
    // A view in the layer: where it came from and what it was.
    private class Moved(
        val view: View,
        val stub: ElementStub,
        val params: ViewGroup.LayoutParams,
        val frame: Rect,
        val values: FloatArray,
        val pivotSet: Boolean,
        // Where its parent's origin is in the layer: what `x` and `y` are counted from.
        val originX: Int,
        val originY: Int,
    )

    private val moved = ArrayList<Moved>()
    private val hidden = ArrayList<Pair<View, Float>>()
    private var finished = false

    /** The animators of the elements and of the shared elements, each with its own time. */
    val animators = ArrayList<Animator>()

    /** Every view back, the `from`s seen, the layer gone; at the end of the transition and at any stop. Once. */
    fun finish() {
        if (finished) return
        finished = true
        for ((view, alpha) in hidden) view.alpha = alpha
        hidden.clear()
        // Each view goes back on its own: one that can't (its parent gone wrong) doesn't keep the others in the layer.
        for (item in moved.asReversed()) {
            try {
                giveBack(item)
            } catch (e: RuntimeException) {
                Log.w(NavigationLog.TAG, "[ApexNavigation] An element of a transition couldn't go back", e)
            }
        }
        moved.clear()
        layer.detach()
    }

    private fun moveIntoLayer(view: View): Moved? {
        val parent = view.parent as? ViewGroup ?: return null
        // A parent that clips its subviews keeps its own list of children, which a stub swapped in would leave
        // behind: its element stays where it is.
        if ((parent as? ReactViewGroup)?.removeClippedSubviews == true) return null
        val index = parent.indexOfChild(view)
        if (index < 0) return null
        val onScreen = IntArray(2)
        parent.getLocationOnScreen(onScreen)
        val origin = layer.originOnScreen
        val originX = onScreen[0] - parent.scrollX - origin.x
        val originY = onScreen[1] - parent.scrollY - origin.y
        val frame = Rect(view.left, view.top, view.right, view.bottom)
        val params = view.layoutParams ?: ViewGroup.LayoutParams(view.width, view.height)
        val pivotSet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && view.isPivotSet
        val values = floatArrayOf(
            view.alpha, view.translationX, view.translationY, view.scaleX, view.scaleY, view.rotation,
            view.rotationX, view.rotationY, view.pivotX, view.pivotY,
        )
        val stub = ElementStub(view.context, view)
        try {
            parent.removeViewAt(index)
            parent.addView(stub, index, params)
            stub.layout(frame.left, frame.top, frame.right, frame.bottom)
            val left = originX + frame.left
            val top = originY + frame.top
            layer.put(view, left, top, frame.width(), frame.height())
        } catch (e: RuntimeException) {
            putBack(view, stub, parent, index, params, frame)
            throw e
        }
        // Scale and rotations turn about the center.
        view.pivotX = frame.width() / 2f
        view.pivotY = frame.height() / 2f
        return Moved(view, stub, params, frame, values, pivotSet, originX, originY).also { moved += it }
    }

    // A move that failed half way undone: the view back at its index in its parent, the stub gone.
    private fun putBack(
        view: View,
        stub: ElementStub,
        parent: ViewGroup,
        index: Int,
        params: ViewGroup.LayoutParams,
        frame: Rect,
    ) {
        if (view.parent === layer) layer.removeView(view)
        val stubIndex = parent.indexOfChild(stub)
        if (stubIndex >= 0) parent.removeViewAt(stubIndex)
        if (view.parent == null) {
            parent.addView(view, index.coerceAtMost(parent.childCount), params)
            view.layout(frame.left, frame.top, frame.right, frame.bottom)
        }
    }

    private fun giveBack(item: Moved) {
        val view = item.view
        // A commit of Fabric meanwhile laid it out in its parent's coordinates: the newest such frame holds.
        val reactFrame = if (view.parent === layer) layer.frameFromReact(view) else null
        if (view.parent === layer) layer.removeView(view)
        restore(view, item)
        val parent = item.stub.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(item.stub)
        if (index < 0) return
        parent.removeViewAt(index)
        parent.addView(view, index, item.params)
        val frame = reactFrame ?: item.frame
        view.measure(
            View.MeasureSpec.makeMeasureSpec(frame.width(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(frame.height(), View.MeasureSpec.EXACTLY),
        )
        view.layout(frame.left, frame.top, frame.right, frame.bottom)
    }

    private fun restore(view: View, item: Moved) {
        val values = item.values
        view.alpha = values[ALPHA]
        view.translationX = values[TRANSLATION_X]
        view.translationY = values[TRANSLATION_Y]
        view.scaleX = values[SCALE_X]
        view.scaleY = values[SCALE_Y]
        view.rotation = values[ROTATION]
        view.rotationX = values[ROTATION_X]
        view.rotationY = values[ROTATION_Y]
        if (!item.pivotSet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.resetPivot()
        } else {
            view.pivotX = values[PIVOT_X]
            view.pivotY = values[PIVOT_Y]
        }
    }

    // An element with views in it fades through a hardware layer only where its alpha would draw it into an offscreen
    // buffer at every frame (AnimationLayer: its rendering overlaps). React's views overlap only with
    // `needsOffscreenAlphaCompositing`, so most elements fade without one and nothing they draw outside their bounds (a
    // box shadow) is clipped. A view without children fades its own drawing at no extra cost.
    private fun addElement(options: ElementTransitionOptions, item: Moved) {
        val base = SideAnimator.Base(xOffset = item.originX.toFloat(), yOffset = item.originY.toFloat())
        val animator = SideAnimator.build(item.view, options, base) ?: return
        if (item.view is ViewGroup) AnimationLayer.during(item.view, animator)
        animators += animator
    }

    // The pair's flight: `to` hidden until it starts, `from` hidden from its start; false when it doesn't fly.
    private fun addPair(
        options: SharedElementTransitionOptions,
        from: View,
        item: Moved,
        toParent: View?,
        fallbackDuration: Long,
    ): Boolean {
        val to = item.view
        val flight = SharedAnimatorFactory.create(
            from, to, toParent, item.stub, layer, options, fallbackDuration, DevelopmentLog::warn,
        ) ?: return false
        val alpha = item.values[ALPHA]
        val fromAlpha = from.alpha
        to.alpha = 0f
        hidden += from to fromAlpha
        flight.animator.addListener(object : AnimatorListenerAdapter() {
            private var stopped = false

            override fun onAnimationStart(animation: Animator) {
                if (stopped) return
                from.alpha = 0f
                if (!flight.drawsPictures) to.alpha = alpha
            }

            // A flight whose start or frame failed stops (`MoveAnimator`), maybe inside its own start: `from` shows
            // again in its screen, and a `to` that moves itself in its place, as at any stop (a flight of pictures
            // shows its `to` at its end).
            override fun onAnimationCancel(animation: Animator) {
                stopped = true
                from.alpha = fromAlpha
                if (!flight.drawsPictures) to.alpha = alpha
            }
        })
        animators += flight.animator
        return true
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

        /**
         * The elements of [animation] from [from] to [to], moved into a layer that joins [host] at [layerIndex] (its
         * top without one); none when none of them is found. A shared element without a duration takes
         * [fallbackDuration], the screen's fade.
         */
        fun start(
            animation: LayoutAnimation,
            from: ViewController<*>?,
            to: ViewController<*>,
            host: ViewGroup,
            layerIndex: Int?,
            fallbackDuration: Long,
        ): ElementTransition? {
            val fromView = from?.let { AppearanceDispatcher.visibleNode(it).peekView() }
            val toView = AppearanceDispatcher.visibleNode(to).peekView()
            val transitions = animation.elementTransitions.transitions
            val shared = animation.sharedElements.get()
            val views = TransitionViews(shared, transitions, fromView, toView)
            val taken = HashSet<View>()
            val elements = ArrayList<Pair<ElementTransitionOptions, View>>()
            for (element in transitions) {
                if (element.id.isEmpty()) continue
                val view = found(views.leaving(element.id)) ?: found(views.arriving(element.id)) ?: continue
                if (TransitionLayer.holds(view) || !taken.add(view)) continue
                elements += element to view
            }
            val pairs = ArrayList<Triple<SharedElementTransitionOptions, View, View>>()
            for (pair in shared) {
                val fromId = pair.fromId.get(null) ?: continue
                val toId = pair.toId.get(null) ?: continue
                val source = found(views.leaving(fromId)) ?: continue
                val target = found(views.arriving(toId)) ?: continue
                if (source === target || TransitionLayer.holds(source) || TransitionLayer.holds(target)) continue
                if (target in taken || source in taken) continue
                if (!loaded(source, fromId) || !loaded(target, toId)) continue
                taken += target
                pairs += Triple(pair, source, target)
            }
            if (elements.isEmpty() && pairs.isEmpty()) return null
            val layer = TransitionLayer(host.context)
            val transition = ElementTransition(layer)
            try {
                layer.attachTo(host, layerIndex ?: host.childCount)
                transition.fill(host, elements, pairs, fallbackDuration)
            } catch (e: RuntimeException) {
                transition.finish()
                throw e
            }
            return transition
        }

        /**
         * [start], and when that fails, a line in the log and none: the screens play without their elements; every view
         * moved is back already.
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

        private fun found(view: View?): View? = view?.takeUnless { it is ElementStub }

        // An image not loaded drops out of the transition; its wait logged it already, else it is logged now.
        private fun loaded(view: View, id: String): Boolean {
            if (ImageLoads.isLoaded(view)) return true
            ImageLoads.failed(view, id, DevelopmentLog::warn)
            return false
        }
    }

    // The views into the layer in the order they are drawn, then their animators; a pair that doesn't fly goes back at
    // once. Each element's steps run on their own ([elementStep]): one that fails goes back, the others play.
    private fun fill(
        host: ViewGroup,
        elements: List<Pair<ElementTransitionOptions, View>>,
        pairs: List<Triple<SharedElementTransitionOptions, View, View>>,
        fallbackDuration: Long,
    ) {
        val names = HashMap<View, String>()
        for ((options, view) in elements) names[view] = elementName(options.id)
        for ((options, _, target) in pairs) names[target] = pairName(options.fromId.get(""), options.toId.get(""))
        val views = elements.map { it.second } + pairs.map { it.third }
        val parents = views.associateWith { it.parent as? View }
        val placed = HashMap<View, Moved>()
        for (view in DrawOrder.sorted(views, host) { it }) {
            var item: Moved? = null
            if (elementStep(names.getValue(view)) { item = moveIntoLayer(view) }) item?.let { placed[view] = it }
        }
        for ((options, view) in elements) {
            val item = placed[view] ?: continue
            if (!elementStep(names.getValue(view)) { addElement(options, item) }) goBack(item)
        }
        for ((options, source, target) in pairs) {
            val item = placed[target] ?: continue
            var flies = false
            elementStep(names.getValue(target)) {
                flies = addPair(options, source, item, parents[target], fallbackDuration)
            }
            if (!flies) goBack(item)
        }
    }

    // An element that doesn't play leaves the layer now; one that can't go back is logged, the others go on.
    private fun goBack(item: Moved) {
        moved -= item
        try {
            giveBack(item)
        } catch (e: RuntimeException) {
            Log.w(NavigationLog.TAG, "[ApexNavigation] An element of a transition couldn't go back", e)
        }
    }
}

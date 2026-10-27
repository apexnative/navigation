package dev.apexnative.navigation.viewcontrollers.stack

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import dev.apexnative.navigation.views.animations.SystemTransition
import dev.apexnative.navigation.views.stack.StackLayout

/**
 * The preview of a pop while the user's back gesture runs (predictive back), as the system shows the activity under
 * the one that closes: the screen on top with its bar, a picture taken as the gesture starts, shrinks toward the
 * gesture's side with rounded corners and follows the finger up and down; the screen under it shows behind it with
 * its own bar and FAB, smaller and dimmed. Released, the pop plays its end ([commit]); cancelled, everything goes back
 * ([cancel]). The values are those of the system's cross-activity back animation (WMShell
 * `DefaultCrossActivityBackAnimation` of Android 15 and 16, the smallest scale as Android 17 shows it), also on Android
 * 14, whose own preview differs. Main thread only.
 */
internal class BackPreview(
    private val layout: StackLayout,
    private val top: View,
    private val target: View,
    /** The gesture started at the left edge: the screen moves right; from the right edge it stays centered. */
    private val fromLeft: Boolean,
    private val startY: Float,
    /**
     * The room the tabs take at the bottom of the top screen: the card is the stack above them, rounded on all four
     * corners, while the tab bar stays, as Gmail shows it.
     */
    private val bottomRoom: Int,
    /**
     * The picture is there: the stack shows the screen under the top, with its bar and its FAB, before the preview
     * draws.
     */
    private val onReveal: () -> Unit,
    /** The view of the FAB the stack shows now, which goes with the screen under the top; none. */
    private val fab: () -> View? = { null },
) {
    private val density = layout.resources.displayMetrics.density
    private val bar = layout.topBar
    private val scrim = View(layout.context)
    private var card: ImageView? = null
    private var progress = 0f
    private var touchY = startY
    private var animator: Animator? = null
    private var ended = false
    private var topAlpha = 1f
    // The FAB that shows with the screen under the top, drawn flat under the dim and the picture, its own Z and the
    // animator of its Z by its state.
    private var flatFab: View? = null
    private var fabZ = 0f
    private var fabZByState: StateListAnimator? = null
    // What puts the stack as it was after a cancel: once, as the preview ends.
    private var onCancelled: (() -> Unit)? = null
    // Forward is to the end of the line: right to left, the screens move the other way, as the stack's pop does.
    private val forward = if (layout.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f

    /** The picture of the top is there and the screen under it shows: the preview draws. */
    val isReady: Boolean
        get() = card != null && !ended

    /**
     * Takes the picture of the stack as it shows now, drawn from its views (what lies over the stack, an overlay or a
     * tab bar drawn over it, stays out of it); with a hardware bitmap in it, copied from the window a frame later. The
     * preview draws once it is there.
     */
    fun start(window: Window) {
        val width = layout.width
        val height = areaHeight
        if (width <= 0 || height <= 0) return
        val picture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            layout.draw(Canvas(picture))
            show(picture)
            return
        } catch (e: IllegalArgumentException) {
            picture.eraseColor(Color.TRANSPARENT)
        }
        val place = IntArray(2)
        layout.getLocationInWindow(place)
        val area = Rect(place[0], place[1], place[0] + width, place[1] + height)
        PixelCopy.request(window, area, picture, { result ->
            if (result == PixelCopy.SUCCESS && !ended) show(picture)
        }, Handler(Looper.getMainLooper()))
    }

    /** The gesture moved: [value] is its progress, 0 … 1, and [y] the finger's height in the window. */
    fun progress(value: Float, y: Float) {
        progress = value
        touchY = y
        if (isReady) drawGesture(GESTURE_CURVE.getInterpolation(value))
    }

    /**
     * The gesture was cancelled: the preview springs back, then [restore] puts the stack as it was; at once when the
     * preview ends earlier (a new gesture, a command).
     */
    fun cancel(restore: () -> Unit) {
        onCancelled = restore
        if (!isReady) return endAtOnce()
        val from = progress
        animator?.cancel()
        animator = ValueAnimator.ofFloat(from, 0f).apply {
            duration = CANCEL_MILLIS
            interpolator = DecelerateInterpolator()
            addUpdateListener { drawGesture(GESTURE_CURVE.getInterpolation(it.animatedValue as Float)) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator === animation) endAtOnce()
                }
            })
            start()
        }
    }

    /**
     * The gesture was released and the pop runs: the top goes back to its size while it moves away by 96 dp and fades
     * in the first fifth, the screen under it grows to its place, the dim fades; [done] once at the end, after the
     * preview took its views away.
     */
    fun commit(done: () -> Unit) {
        val card = card ?: return done()
        val distance = SystemTransition.DISTANCE_DP * density
        val q = GESTURE_CURVE.getInterpolation(progress)
        val scale = scaleAt(q)
        val cardX = card.translationX
        val cardY = card.translationY
        val dim = scrim.alpha
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SystemTransition.DURATION
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                val e = SystemTransition.CURVE.getInterpolation(t)
                card.scaleX = lerp(scale, 1f, e)
                card.scaleY = card.scaleX
                card.translationX = lerp(cardX, distance * forward, e)
                card.translationY = lerp(cardY, 0f, e)
                card.alpha = (1f - 5f * t).coerceAtLeast(0f)
                entering(lerp(scale, 1f, e), lerp(-distance * forward, 0f, e))
                scrim.alpha = lerp(dim, 0f, t)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    endAtOnce()
                    done()
                }
            })
            start()
        }
    }

    /**
     * The preview ends where it is: its views go, the screens and the bar take their own values again, and a cancel's
     * restore runs.
     */
    fun endAtOnce() {
        if (ended) return
        ended = true
        animator?.let {
            animator = null
            it.cancel()
        }
        card?.let { (it.parent as? ViewGroup)?.removeView(it) }
        card = null
        (scrim.parent as? ViewGroup)?.removeView(scrim)
        top.alpha = topAlpha
        flatFab?.let {
            flatFab = null
            it.translationZ = fabZ
            it.stateListAnimator = fabZByState
            fabZByState = null
        }
        entering(1f, 0f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            target.resetPivot()
            bar.resetPivot()
            fab()?.resetPivot()
        }
        onCancelled?.let {
            onCancelled = null
            it()
        }
    }

    // The picture came: the screen under the top shows with its bar, dimmed, and the picture lies over everything in
    // the top's place, cut to rounded corners.
    private fun show(picture: Bitmap) {
        onReveal()
        // A child with a Z draws over those without, whatever their order: the FAB, raised by its elevation, would lie
        // over the dim and the picture. Its state's animator, which Material's FAB sets on the frame after it joins the
        // window (and on a press, focus or hover), would raise it again: it stays off while the preview draws.
        fab()?.takeIf { it.z > 0f }?.let {
            flatFab = it
            fabZ = it.translationZ
            fabZByState = it.stateListAnimator
            it.stateListAnimator = null
            it.translationZ = -it.elevation
        }
        val dark = layout.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        scrim.setBackgroundColor(Color.BLACK)
        scrim.alpha = if (dark) SCRIM_DARK else SCRIM_LIGHT
        scrim.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layout.addOverAll(scrim, fill())
        val radius = CORNER_DP * density
        val area = CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, areaHeight)
        card = ImageView(layout.context).apply {
            setImageBitmap(picture)
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
            clipToOutline = true
            layout.addOverAll(this, area)
        }
        // Transparent, not invisible: a surface in it (a video, a map) stays, and so does the focus.
        topAlpha = top.alpha
        top.alpha = 0f
        drawGesture(GESTURE_CURVE.getInterpolation(progress))
    }

    // The values of the gesture at [q], its progress on the system's curve.
    private fun drawGesture(q: Float) {
        val card = card ?: return
        val width = layout.width.toFloat()
        val height = areaHeight.toFloat()
        val margin = MARGIN_DP * density
        val scale = scaleAt(q)
        card.scaleX = scale
        card.scaleY = scale
        // From the left edge the screen's right side ends 8 dp from the window's right edge; from the right edge it stays
        // centered.
        card.translationX = if (fromLeft) (width - width * scale) / 2f - margin * q else 0f
        // Up and down with the finger, never closer than 8 dp to the window's edges.
        val moved = touchY - startY
        val ratio = FOLLOW_CURVE.getInterpolation((kotlin.math.abs(moved) / (height / 2f)).coerceAtMost(1f))
        card.translationY = ((height - height * scale) / 2f - margin).coerceAtLeast(0f) * ratio *
            Math.signum(moved)
        entering(scale, -SystemTransition.DISTANCE_DP * density * forward)
    }

    // The screen under the top, its bar and its FAB at [scale] around the stack's center, [x] along the line.
    private fun entering(scale: Float, x: Float) {
        for (view in listOfNotNull(target, bar, fab())) {
            view.pivotX = layout.width / 2f - view.left
            view.pivotY = areaHeight / 2f - view.top
            view.scaleX = scale
            view.scaleY = scale
            view.translationX = x
        }
    }

    // The stack above the tabs' room: what the card shows and what the screens shrink around.
    private val areaHeight: Int
        get() = (layout.height - bottomRoom).coerceAtLeast(0)

    private fun scaleAt(q: Float): Float = 1f - (1f - minScale()) * q

    private fun fill() = CoordinatorLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )

    private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

    private companion object {
        /** The system's curve of the back gesture's progress (`BackGestureInterpolator`). */
        val GESTURE_CURVE = PathInterpolator(0.1f, 0.1f, 0f, 1f)

        /** How the screen follows the finger up and down. */
        val FOLLOW_CURVE = DecelerateInterpolator()

        /** The room the shrunk screen keeps from the window's edges. */
        const val MARGIN_DP = 8f

        /** The corners of the shrunk screen, as Android 17 draws them on a Pixel (6.5 dp at its smallest). */
        const val CORNER_DP = 7.5f

        const val SCRIM_LIGHT = 0.2f
        const val SCRIM_DARK = 0.8f

        /** The cancel springs back about as fast as the system's (`BackProgressAnimator`'s spring, no bounce). */
        const val CANCEL_MILLIS = 350L

        /**
         * The smallest scale of the screens: 0.9 in WMShell of Android 15 and 16 (and here on Android 14); Android 17
         * shrinks the window further (0.86, measured on a Pixel 6).
         */
        fun minScale(): Float = if (android.os.Build.VERSION.SDK_INT >= 37) 0.86f else 0.9f
    }
}

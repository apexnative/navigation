package dev.apexnative.navigation.views.stack.topbar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.appbar.MaterialToolbar
import dev.apexnative.navigation.R
import dev.apexnative.navigation.utils.MaterialTheme
import dev.apexnative.navigation.utils.UiUtils

/**
 * The top bar of a stack: one per stack, showing what its screen on top sets. Top to bottom: the strip under the status
 * bar ([topPadding]), the row's area (`topBar.height`) with the row of the title and the buttons ([titleBar],
 * `topBar.title.height`, moved down by `topBar.title.topMargin`), the large title (88 dp), the search area under the
 * row or in its place ([setSearchArea]), and the line under the bar; the color and the React background
 * ([setBackgroundComponent]) go from edge to edge, under the strip too. It has a theme of its own over the app's
 * Material 3 theme or the navigation's neutral one, never the activity's, which follows a light/dark switch
 * ([MaterialTheme.rebase]).
 *
 * The core moves it only by its translation (hiding, the hide on scroll, the large title's collapse); it is a plain
 * view, so the stack's `CoordinatorLayout` gives it no behavior of its own. Main thread only.
 */
@SuppressLint("ViewConstructor")
class TopBar(context: Context) : FrameLayout(themed(context)) {
    private val backgroundHolder = FrameLayout(this.context)
    private val content = LinearLayout(this.context).apply { orientation = LinearLayout.VERTICAL }
    private val rowArea = RowArea(this.context)

    /** The row of the title and the buttons. */
    val titleBar = TitleBar(this.context)

    /** The large title under the row (`topBar.largeTitle`), shown by [setLargeTitleShown]. */
    val largeTitle = LargeTitleView(this.context)
    private val largeTitleHolder = FrameLayout(this.context)
    private val searchHolder = FrameLayout(this.context)
    private val border = View(this.context)
    private val snapshot = ImageView(this.context)
    private var searchArea: View? = null
    private var barHeight = UiUtils.dpToPx(this.context, DEFAULT_HEIGHT_DP)
    private var searchTakesBar = false
    // A part that sets the bar's height changed since [layoutNow] last laid it out.
    private var heightChanged = false
    // How far the large title is collapsed, in pixels.
    private var largeTitleShift = 0
    // `topBar.elevation`, in pixels: the bar's elevation unless it is flat.
    private var shadow = 0f

    /** The toolbar of the right buttons: its menu holds them, the overflow items and the search's magnifier. */
    val rightButtonBar: MaterialToolbar
        get() = titleBar.rightButtonBar

    /** The toolbar of the back button and the left buttons. */
    val leftButtonBar: MaterialToolbar
        get() = titleBar.leftButtonBar

    /** The strip under the status bar at the top of the bar, in pixels: the bar is under the status bar. */
    var topPadding = 0
        set(value) {
            if (field == value) return
            field = value
            content.setPadding(0, value, 0, 0)
            heightChanged = true
        }

    /** The large title shows under the row. */
    val isLargeTitleShown: Boolean
        get() = largeTitleHolder.visibility == VISIBLE

    /** The height of the search area under the row, in pixels; 0 without one or when it takes the row. */
    val searchAreaHeight: Int
        get() = if (searchArea != null && !searchTakesBar) searchHolder.layoutParams.height.coerceAtLeast(0) else 0

    init {
        MaterialTheme.follow(this.context, R.style.ThemeOverlay_ApexNavigation_TopBar)
        addView(backgroundHolder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val row = UiUtils.dpToPx(this.context, DEFAULT_HEIGHT_DP)
        rowArea.addView(titleBar, LayoutParams(LayoutParams.MATCH_PARENT, row))
        content.addView(rowArea, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, row))
        largeTitleHolder.addView(largeTitle, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        largeTitleHolder.visibility = GONE
        largeTitleHolder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> clipLargeTitle() }
        content.addView(largeTitleHolder, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            UiUtils.dpToPx(this.context, LargeTitleView.HEIGHT).toInt(),
        ))
        searchHolder.visibility = GONE
        content.addView(searchHolder, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0))
        border.visibility = GONE
        border.setBackgroundColor(Color.BLACK)
        content.addView(border, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0))
        // The picture of the row takes the touches while it lies over it, and accessibility doesn't see it: it is only
        // a picture of what was there. It stays at the left edge, so a layout right to left doesn't move it.
        snapshot.visibility = GONE
        snapshot.isClickable = true
        snapshot.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        snapshot.scaleType = ImageView.ScaleType.MATRIX
        addView(snapshot, LayoutParams(0, 0, Gravity.TOP or Gravity.LEFT))
        setBackgroundColor(TopBarDefaults.backgroundColor(this.context))
    }

    /** The opacity of the row of the title and the buttons, which fades as the bar hides on scroll. */
    fun setTitleRowAlpha(alpha: Float) {
        titleBar.alpha = alpha
    }

    /**
     * What the bar shows of the search of the screen on top ([area], [height] pixels tall), under the row or, when
     * [takesBar], in its place; none removes it.
     */
    fun setSearchArea(area: View?, height: Int, takesBar: Boolean) {
        heightChanged = true
        if (searchArea !== area) {
            searchArea?.let { (it.parent as? ViewGroup)?.removeView(it) }
            searchArea = area
        }
        searchTakesBar = area != null && takesBar
        if (area == null) {
            searchHolder.visibility = GONE
            titleBar.visibility = VISIBLE
            setRowAreaHeight(barHeight)
            return
        }
        val holder = if (takesBar) rowArea else searchHolder
        if (area.parent !== holder) {
            (area.parent as? ViewGroup)?.removeView(area)
            holder.addView(area, LayoutParams(LayoutParams.MATCH_PARENT, height))
        } else {
            area.layoutParams = area.layoutParams.also { it.height = height }
        }
        if (takesBar) {
            searchHolder.visibility = GONE
            titleBar.visibility = INVISIBLE
            setRowAreaHeight(height)
        } else {
            titleBar.visibility = VISIBLE
            setRowAreaHeight(barHeight)
            searchHolder.visibility = VISIBLE
            searchHolder.layoutParams = searchHolder.layoutParams.also { it.height = height }
        }
    }

    /** The search takes the row: the large title doesn't show. */
    val searchTakesRow: Boolean
        get() = searchTakesBar

    /** `topBar.height`: the area of the row, in pixels; [layoutNow] applies it in the same frame. */
    fun setBarHeight(height: Int) {
        if (barHeight == height) return
        barHeight = height
        if (!searchTakesBar) setRowAreaHeight(height)
    }

    /** `topBar.title.height` and `topBar.title.topMargin`: the row's height and its shift, in pixels. */
    fun setRow(height: Int, topMargin: Int) {
        val params = titleBar.layoutParams as LayoutParams
        if (params.height == height && params.topMargin == topMargin) return
        params.height = height
        params.topMargin = topMargin
        titleBar.layoutParams = params
        heightChanged = true
    }

    /** The bar's color, under the strip and the React background too. */
    fun setBarColor(color: Int) {
        val current = (background as? ColorDrawable)?.color
        if (current == color) return
        setBackgroundColor(color)
    }

    /** The bar's color now. */
    val barColor: Int
        get() = (background as? ColorDrawable)?.color ?: Color.TRANSPARENT

    /**
     * The shadow of `topBar.elevation`, in pixels: its colors are black (Android 9 and later read them), none at 0.
     */
    fun setShadow(elevation: Float) {
        if (shadow == elevation) return
        shadow = elevation
        if (!isFlat) this.elevation = elevation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outlineAmbientShadowColor = Color.BLACK
            outlineSpotShadowColor = Color.BLACK
        }
    }

    /**
     * The bar draws without its shadow, at the level of the screens, in a transition that draws it with a screen: a
     * screen drawn after it covers it, and an alpha layer around it fades it (with an elevation the renderer would draw
     * the bar over every screen and out of the layer).
     */
    var isFlat = false
        set(value) {
            if (field == value) return
            field = value
            elevation = if (value) 0f else shadow
        }

    /** The line under the bar: [height] pixels of [color]. */
    fun setBorder(color: Int, height: Int) {
        border.setBackgroundColor(color)
        val params = border.layoutParams
        if (params.height != height) {
            params.height = height
            border.layoutParams = params
            heightChanged = true
        }
        val visibility = if (height > 0) VISIBLE else GONE
        if (border.visibility != visibility) {
            border.visibility = visibility
            heightChanged = true
        }
    }

    /** The React background of the bar, the bar's whole height with the strip; none takes it away. */
    fun setBackgroundComponent(view: View?) {
        val current = if (backgroundHolder.childCount > 0) backgroundHolder.getChildAt(0) else null
        if (current === view) return
        backgroundHolder.removeAllViews()
        if (view == null) return
        (view.parent as? ViewGroup)?.removeView(view)
        backgroundHolder.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** The background the bar shows now; none. */
    val backgroundComponent: View?
        get() = if (backgroundHolder.childCount > 0) backgroundHolder.getChildAt(0) else null

    /** Shows the large title under the row, or not (88 dp more; [layoutNow] applies it in the same frame). */
    fun setLargeTitleShown(shown: Boolean) {
        val visibility = if (shown) VISIBLE else GONE
        if (largeTitleHolder.visibility == visibility) return
        largeTitleHolder.visibility = visibility
        heightChanged = true
        if (!shown) setLargeTitleCollapse(0)
    }

    /**
     * The large title collapsed by [shift] pixels: the bar moves up, the row stays where it was, and the large title is
     * cut under the row. The row's picture in a transition moves with the row.
     */
    fun setLargeTitleCollapse(shift: Int) {
        largeTitleShift = shift
        translationY = -shift.toFloat()
        rowArea.translationY = shift.toFloat()
        snapshot.translationY = shift.toFloat()
        clipLargeTitle()
    }

    // The large title cut under the row by its collapse, at the holder's size: set again when the holder is laid out,
    // since a collapse set before the holder's first layout (or while it was hidden) has no size yet.
    private fun clipLargeTitle() {
        val shift = largeTitleShift
        val holder = largeTitleHolder
        holder.clipBounds = if (shift > 0) Rect(0, shift, holder.width, holder.height) else null
    }

    /**
     * Measures and lays out the bar at once at the size its parts give now. A height set through layout params lands a
     * layout pass later, while translations and visibility apply at once: the frame between would show an empty band or
     * a hole. Only when a part that sets the height changed, for a bar that is laid out, outside a layout pass; the
     * parent's next pass follows as usual.
     */
    fun layoutNow() {
        if (!heightChanged) return
        heightChanged = false
        val parent = parent as? View
        val params = layoutParams as? MarginLayoutParams
        if (parent != null && params != null && isLaidOut && !isInLayout) {
            val room = parent.height - parent.paddingTop - parent.paddingBottom - params.topMargin - params.bottomMargin
            measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(room.coerceAtLeast(0), MeasureSpec.AT_MOST),
            )
            val top = parent.paddingTop + params.topMargin
            layout(left, top, left + measuredWidth, top + measuredHeight)
        }
        requestLayout()
    }

    /** How far up the bar goes to hide: its height and the room over it. */
    val hiddenOffset: Float
        get() = (measuredHeight + ((layoutParams as? MarginLayoutParams)?.topMargin ?: 0)).toFloat()

    /** `topBar.topMargin` and the status bar over a bar below it: the room over the bar, in pixels. */
    fun setMargin(margin: Int) {
        val params = layoutParams as? MarginLayoutParams ?: return
        if (params.topMargin == margin) return
        params.topMargin = margin
        heightChanged = true
        layoutParams = params
    }

    // The row's picture in a transition.

    /**
     * Lays a picture of the row and the large title over them, laid out at once; false when the bar can't be drawn in
     * software: no picture then.
     */
    fun takeRowSnapshot(withLargeTitle: Boolean = true): Boolean {
        val width = rowArea.width
        val large = withLargeTitle && isLargeTitleShown
        val height = rowArea.height + if (large) largeTitleHolder.height else 0
        if (width <= 0 || height <= 0) return false
        val picture = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                rowArea.draw(canvas)
                if (large) {
                    canvas.translate(0f, rowArea.height.toFloat())
                    largeTitleHolder.draw(canvas)
                }
            }
        } catch (e: IllegalArgumentException) {
            // A HARDWARE bitmap in the bar: it can't be drawn on a software canvas.
            return false
        }
        val top = content.top + rowArea.top
        snapshot.setImageBitmap(picture)
        snapshot.alpha = 1f
        snapshot.translationX = 0f
        snapshot.translationY = rowArea.translationY
        snapshot.visibility = VISIBLE
        snapshot.layoutParams = (snapshot.layoutParams as LayoutParams).also {
            it.width = width
            it.height = height
            it.topMargin = top
        }
        snapshot.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        snapshot.layout(0, top, width, top + height)
        return true
    }

    /**
     * A picture of the bar under its row — the background, the large title, the search, the line — and where it starts
     * in the bar, for a push to a screen without a large title; none when the bar can't be drawn in software.
     */
    fun pictureUnderRow(): Pair<Bitmap, Int>? = pictureFrom(content.top + rowArea.bottom)

    /**
     * A picture of the whole bar, for the screen it leaves with in a transition of the stack's own; none when the bar
     * can't be drawn in software.
     */
    fun picture(): Bitmap? = pictureFrom(0)?.first

    private fun pictureFrom(top: Int): Pair<Bitmap, Int>? {
        val width = width
        val height = height - top
        if (width <= 0 || height <= 0) return null
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(0f, -top.toFloat())
            draw(canvas)
            bitmap to top
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** The row's picture goes. */
    fun dropRowSnapshot() {
        snapshot.setImageDrawable(null)
        snapshot.translationY = 0f
        snapshot.visibility = GONE
    }

    /**
     * The row and the large title at [rowAlpha] and [rowShift] pixels along the line, the picture at [snapshotAlpha]
     * and [snapshotShift]; the row takes no touches while it is transparent.
     */
    fun showRowFrame(snapshotAlpha: Float, snapshotShift: Float, rowAlpha: Float, rowShift: Float) {
        if (snapshot.visibility == VISIBLE) {
            snapshot.alpha = snapshotAlpha
            snapshot.translationX = snapshotShift
        }
        rowArea.alpha = rowAlpha
        rowArea.translationX = rowShift
        largeTitleHolder.alpha = rowAlpha
        largeTitleHolder.translationX = rowShift
        rowArea.blocksTouches = rowAlpha <= 0f
    }

    /** The row and the large title at rest, the picture gone. */
    fun restRow() {
        dropRowSnapshot()
        showRowFrame(1f, 0f, 1f, 0f)
    }

    private fun setRowAreaHeight(height: Int) {
        val params = rowArea.layoutParams
        if (params.height == height) return
        params.height = height
        rowArea.layoutParams = params
        heightChanged = true
    }

    // The area of the row: it takes the touches without passing them on while the row is transparent.
    private class RowArea(context: Context) : FrameLayout(context) {
        var blocksTouches = false

        override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = blocksTouches || super.onInterceptTouchEvent(ev)

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent?): Boolean = blocksTouches || super.onTouchEvent(event)
    }

    private companion object {
        const val DEFAULT_HEIGHT_DP = 64

        fun themed(context: Context): Context =
            ContextThemeWrapper(MaterialTheme.context(context), R.style.ThemeOverlay_ApexNavigation_TopBar)
    }
}

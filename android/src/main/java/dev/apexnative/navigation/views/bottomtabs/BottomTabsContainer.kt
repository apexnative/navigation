package dev.apexnative.navigation.views.bottomtabs

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The container of the tab bar: it holds the module's [bar], whose own background is transparent, and draws what the
 * bar shows around it — the background, a line at the top, the shadow, the elevation and the rounded clip. Its bottom
 * padding keeps the bar's items above the navigation bar of a stretched bar. Main thread only.
 */
@SuppressLint("ViewConstructor")
internal class BottomTabsContainer(context: Context, val bar: BottomTabs) : FrameLayout(context) {
    /** What the container draws, in pixels and ARGB colors. */
    data class Look(
        val background: Int,
        /** The top line's color; none without a line. */
        val borderColor: Int?,
        val borderWidth: Float,
        /** None without `bottomTabs.shadow`. */
        val shadow: Shadow?,
        val elevation: Float,
        val cornerRadius: Float,
    )

    /** `bottomTabs.shadow`: its color with the alpha of its opacity, and its blur radius. */
    data class Shadow(val color: Int, val radius: Float)

    private val backgroundShape = GradientDrawable()
    private val borderPaint = Paint()
    private var look: Look? = null
    // The picture of the shadow, made for the container's size and dropped as the size changes or the view leaves
    // the window: its bitmap is released, not kept for a size it won't have again.
    private var shadowPicture: Bitmap? = null

    init {
        setWillNotDraw(false)
        background = backgroundShape
        outlineProvider = ViewOutlineProvider.BACKGROUND
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))
    }

    /** Draws [look]; nothing changes when it is the one drawn. */
    fun apply(look: Look) {
        if (look == this.look) return
        val shadowChanged = look.shadow != this.look?.shadow || look.cornerRadius != this.look?.cornerRadius
        this.look = look
        backgroundShape.setColor(look.background)
        backgroundShape.cornerRadius = look.cornerRadius
        elevation = look.elevation
        clipToOutline = look.cornerRadius > 0f
        borderPaint.color = look.borderColor ?: Color.TRANSPARENT
        if (shadowChanged) releaseShadow()
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        drawShadow(canvas)
        super.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val look = look ?: return
        if (look.borderColor == null || look.borderWidth <= 0f) return
        canvas.drawRect(0f, 0f, width.toFloat(), look.borderWidth, borderPaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        releaseShadow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseShadow()
    }

    // The shadow lies under the container, cast up from it: angle 270°, offset 15, blur 10 and `shadow.radius`.
    private fun drawShadow(canvas: Canvas) {
        val shadow = look?.shadow ?: return
        if (width <= 0 || height <= 0) return
        val pad = padOf(shadow)
        val picture = shadowPicture ?: makeShadow(shadow, pad)?.also { shadowPicture = it } ?: return
        canvas.drawBitmap(picture, -pad.toFloat(), -pad.toFloat(), null)
    }

    private fun makeShadow(shadow: Shadow, pad: Int): Bitmap? {
        val bitmap = runCatching {
            Bitmap.createBitmap(width + 2 * pad, height + 2 * pad, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return null
        val canvas = Canvas(bitmap)
        val left = pad.toFloat()
        val top = pad.toFloat()
        val right = left + width
        val bottom = top + height
        val radius = look?.cornerRadius ?: 0f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shadow.color
            setShadowLayer(BLUR + shadow.radius, OFFSET * cos(ANGLE), OFFSET * sin(ANGLE), shadow.color)
        }
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        // Only the shadow stays: the shape itself is the background's to draw.
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, clear)
        return bitmap
    }

    private fun releaseShadow() {
        shadowPicture?.recycle()
        shadowPicture = null
    }

    private fun padOf(shadow: Shadow): Int = ceil(BLUR + shadow.radius + OFFSET).roundToInt() + 1

    private companion object {
        const val BLUR = 10f
        const val OFFSET = 15f
        // 270°: straight up, over the screens above the bar.
        val ANGLE = Math.toRadians(270.0).toFloat()
    }
}

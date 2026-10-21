package dev.apexnative.navigation.views.stack.topbar.titlebar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.DrawableCompat

/**
 * An icon on its background (`iconBackground` of a button or of the back button, `topBar.backButton.iconBackground`): a
 * rectangle of [color] with corners cut by [cornerRadius], at least as large as the icon ([width] × [height] when
 * larger), the icon in its middle. The icon is drawn into a picture once and again only when its opacity, its filter or
 * its direction changes, and it mirrors with the direction it is given (an `autoMirrored` arrow). Pixels.
 */
internal class IconBackgroundDrawable(
    private val icon: Drawable,
    private val color: Int,
    private val cornerRadius: Float,
    private val width: Int,
    private val height: Int,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var picture: Bitmap? = null
    private var iconAlpha = FULL
    private var filter: ColorFilter? = null

    override fun getIntrinsicWidth(): Int = maxOf(width, icon.intrinsicWidth)

    override fun getIntrinsicHeight(): Int = maxOf(height, icon.intrinsicHeight)

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        rect.set(bounds)
        paint.color = color
        paint.alpha = Color.alpha(color) * iconAlpha / FULL
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        val image = picture ?: drawIcon()?.also { picture = it } ?: return
        val left = bounds.left + (bounds.width() - image.width) / 2f
        val top = bounds.top + (bounds.height() - image.height) / 2f
        canvas.drawBitmap(image, left, top, null)
    }

    override fun setAlpha(alpha: Int) {
        if (alpha == iconAlpha) return
        iconAlpha = alpha
        icon.alpha = alpha
        invalidatePicture()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        filter = colorFilter
        icon.colorFilter = colorFilter
        invalidatePicture()
    }

    override fun getColorFilter(): ColorFilter? = filter

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun onLayoutDirectionChanged(layoutDirection: Int): Boolean {
        DrawableCompat.setLayoutDirection(icon, layoutDirection)
        invalidatePicture()
        return true
    }

    override fun isAutoMirrored(): Boolean = icon.isAutoMirrored

    private fun invalidatePicture() {
        picture = null
        invalidateSelf()
    }

    // The icon at its size, once.
    private fun drawIcon(): Bitmap? {
        val w = icon.intrinsicWidth
        val h = icon.intrinsicHeight
        if (w <= 0 || h <= 0) return null
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, w, h)
        icon.draw(Canvas(bitmap))
        return bitmap
    }

    private companion object {
        const val FULL = 255
    }
}

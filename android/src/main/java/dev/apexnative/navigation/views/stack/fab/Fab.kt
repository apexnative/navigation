package dev.apexnative.navigation.views.stack.fab

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.utils.ImageLoader
import dev.apexnative.navigation.utils.ImageLoadingListenerAdapter
import dev.apexnative.navigation.utils.MaterialTheme
import dev.apexnative.navigation.utils.UiUtils
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

private const val NORMAL_SIZE_DP = 56
private const val MINI_SIZE_DP = 40

/**
 * A Material floating action button, with the M3 shape and colors of the app's Material 3 theme, or of the navigation's
 * neutral one when the app's isn't Material 3.
 */
class Fab(context: Context, val fabId: String) : FloatingActionButton(MaterialTheme.context(context)), FabAnimator {
    private val collapseBehaviour = FabCollapseBehaviour(this)
    private var normalColor: Int? = null
    private var pressedColor: Int? = null
    // The icon's color of the options: a container color merged later kept the theme's icon color instead.
    private var iconColor: Int? = null
    private var optionRippleColor: Int? = null

    init {
        // Material's theme overlay of the button is a copy of the theme, made once: it follows a light/dark switch too.
        // The view's context, not the constructor's.
        MaterialTheme.followOverlay(this.context)
    }

    fun applyIcon(icon: String, color: ThemeColour) {
        if (color.hasValue()) iconColor = color.get()
        ImageLoader().loadIcon(context, icon, object : ImageLoadingListenerAdapter() {
            override fun onComplete(drawable: Drawable) {
                setImageDrawable(drawable)
                iconColor?.let { imageTintList = ColorStateList.valueOf(it) }
            }
        })
    }

    /** iconColor alone, without a new icon: it did nothing in a merge. */
    fun setIconColor(color: Int) {
        iconColor = color
        imageTintList = ColorStateList.valueOf(color)
    }

    /** The container's color, and its color while pressed (clickColor); either keeps the other. */
    fun setColors(normal: Int?, pressed: Int?) {
        normal?.let { normalColor = it }
        pressed?.let { pressedColor = it }
        if (normalColor == null && pressedColor == null) return
        // The theme's enabled color: a theme's selector lists its disabled color first.
        val base = normalColor
            ?: backgroundTintList?.getColorForState(intArrayOf(android.R.attr.state_enabled), Color.TRANSPARENT)
            ?: return
        backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(pressedColor ?: base, base)
        )
    }

    /** The ripple's color of the options (rippleColor): it stays when the theme's colors are read again. */
    fun applyRippleColor(color: Int) {
        optionRippleColor = color
        setRippleColor(color)
    }

    /** The theme tints the icon for its own container color: on a color of the options the icon keeps its own. */
    fun keepIconColors() {
        imageTintList = iconColor?.let { ColorStateList.valueOf(it) }
    }

    /** 40dp or 56dp, with the app's Material 3 theme too (its FAB style sets a custom size). */
    fun setMini(mini: Boolean) {
        customSize = UiUtils.dpToPx(context, if (mini) MINI_SIZE_DP else NORMAL_SIZE_DP)
    }

    // After a light/dark switch (the activity isn't recreated) the theme's colors again: the button read them once,
    // when it was made. The colors of the options go over them.
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        reloadThemeColors()
    }

    private fun reloadThemeColors() {
        val style = context.obtainStyledAttributes(
            null, MaterialR.styleable.FloatingActionButton, MaterialR.attr.floatingActionButtonStyle,
            MaterialR.style.Widget_Design_FloatingActionButton,
        )
        val image = context.obtainStyledAttributes(
            null, AppCompatR.styleable.AppCompatImageView, MaterialR.attr.floatingActionButtonStyle,
            MaterialR.style.Widget_Design_FloatingActionButton,
        )
        try {
            style.getColorStateList(MaterialR.styleable.FloatingActionButton_backgroundTint)?.let { backgroundTintList = it }
            if (optionRippleColor == null) {
                style.getColorStateList(MaterialR.styleable.FloatingActionButton_rippleColor)?.let { setRippleColor(it) }
            }
            val themeIconTint = image.getColorStateList(AppCompatR.styleable.AppCompatImageView_tint)
            imageTintList = iconColor?.let { ColorStateList.valueOf(it) }
                // A container color of the options: the icon keeps its own colors (keepIconColors).
                ?: if (normalColor != null) null else themeIconTint ?: imageTintList
        } finally {
            style.recycle()
            image.recycle()
        }
        setColors(null, null)
    }

    override fun show() = super.show()

    override fun hide() = super.hide()

    fun enableCollapse(scrollEventListener: ScrollEventListener) = collapseBehaviour.enableCollapse(scrollEventListener)

    fun disableCollapse() = collapseBehaviour.disableCollapse()
}

package dev.apexnative.navigation.splash

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver

/**
 * The copy of the splash at the bottom of the activity's content, under the navigation until its first root shows: the
 * splash's background, and its logo at its own size centered in the whole window, as the system splash (Android 12+)
 * and the starting window (the splash theme's `windowBackground`) center it. The splash leaving shows the same
 * picture, and the first root comes in over it.
 */
@SuppressLint("ViewConstructor")
internal class SplashView(context: Context, private val splash: SplashTheme) : View(context) {
    private var logo: Drawable? = null
    private val location = IntArray(2)
    // The content can sit inset in the window (not edge-to-edge) and move with the insets: the logo is placed again
    // after each layout of the window.
    private val placeLogo = ViewTreeObserver.OnGlobalLayoutListener { invalidate() }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        loadResources()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(placeLogo)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(placeLogo)
        super.onDetachedFromWindow()
    }

    // A light/dark switch the activity takes itself: Android updates the resources before it tells the views, and the
    // same ids give the other mode's color and logo.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        loadResources()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val logo = logo ?: return
        val width = logo.intrinsicWidth
        val height = logo.intrinsicHeight
        if (width <= 0 || height <= 0) return
        val window = rootView
        getLocationInWindow(location)
        val left = (window.width - width) / 2 - location[0]
        val top = (window.height - height) / 2 - location[1]
        logo.setBounds(left, top, left + width, top + height)
        logo.draw(canvas)
    }

    private fun loadResources() {
        background = splash.background(context)?.let(::ColorDrawable)
        logo = splash.logo(context)
        invalidate()
    }

    companion object {
        /**
         * The view of `NavigationActivity.addDefaultSplashLayout`: the copy when [splash] has a background or a logo,
         * else an empty view.
         */
        @JvmStatic
        fun create(context: Context, splash: SplashTheme?): View =
            if (splash != null && splash.hasPicture) SplashView(context, splash) else View(context)
    }
}

package dev.apexnative.navigation.viewcontrollers.stack.topbar

import android.view.ViewGroup
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener
import dev.apexnative.navigation.views.stack.topbar.TopBar
import kotlin.math.abs

/**
 * `topBar.hideOnScroll`: the bar follows the rows while they are dragged and settles, shown or hidden, when they are let
 * go. It only moves: it stays laid out all along, as going GONE laid the screen out again and moved its content.
 * Under the status bar it moves by its row of the title and the buttons, which fades as it goes: its part under the
 * status bar stays, with its color, as Material's bars do. Below the status bar it goes above the screen.
 */
class TopBarCollapseBehavior(private val topBar: TopBar) :
    ScrollEventListener.OnScrollListener, ScrollEventListener.OnDragListener {
    private var scrollEventListener: ScrollEventListener? = null
    // The bar is where the rows took it, not where it rests.
    private var moved = false
    // The way the bar went last: it settles that way.
    private var hiding = false

    fun enableCollapse(scrollEventListener: ScrollEventListener?) {
        if (scrollEventListener !== this.scrollEventListener) {
            // The rows of another screen: the bar shows for it, and no longer follows the rows of the one before.
            this.scrollEventListener?.unregister(this)
            reset()
        }
        this.scrollEventListener = scrollEventListener
        requireNotNull(scrollEventListener) { "hideOnScroll follows the scroll listener of a React screen" }
            .register(topBar, this, this)
    }

    /**
     * The bar no longer follows the rows; back at rest unless [reset] is false: the bar hides by `topBar.visible: false`
     * and its slide goes from where the rows took it, and the next show resets it. The reset stops the bar's running
     * motion, which would be that slide.
     */
    fun disableCollapse(reset: Boolean = true) {
        scrollEventListener?.let {
            it.unregister(this)
            scrollEventListener = null
            if (reset) reset()
        }
    }

    override fun onScrollUp(nextTranslation: Float) = follow(nextTranslation)

    override fun onScrollDown(nextTranslation: Float) = follow(nextTranslation)

    // Let go, the bar settles the way it went last. Which of the two is called comes from the speed of the finger, about
    // nothing when it stopped before it was lifted.
    override fun onShow() = settle()

    override fun onHide() = settle()

    private fun follow(nextTranslation: Float) {
        topBar.animate().cancel()
        val translation = nextTranslation.coerceIn(-hiddenOffset(), 0f)
        if (translation != topBar.translationY) hiding = translation < topBar.translationY
        topBar.translationY = translation
        fadeTitleRow()
        moved = true
    }

    private fun fadeTitleRow() {
        val offset = hiddenOffset()
        topBar.setTitleRowAlpha(if (offset > 0) 1f + topBar.translationY / offset else 1f)
    }

    private fun settle() {
        if (!moved) return
        val target = if (hiding) -hiddenOffset() else 0f
        val distance = abs(target - topBar.translationY)
        if (distance == 0f) return
        topBar.animate()
                .translationY(target)
                .setDuration((SETTLE_DURATION * distance / hiddenOffset()).toLong())
                .setUpdateListener { fadeTitleRow() }
                .withEndAction { topBar.animate().setUpdateListener(null) }
                .start()
    }

    /** The bar where it rests, its title and buttons shown: where the rows took it no longer counts. */
    fun reset() {
        if (!moved) return
        moved = false
        topBar.animate().cancel()
        topBar.animate().setUpdateListener(null)
        topBar.translationY = 0f
        topBar.setTitleRowAlpha(1f)
    }

    // Under the status bar: up by its row, its part under the status bar stays. Below it: up by its height and its
    // margin over it, above the screen.
    private fun hiddenOffset(): Float {
        val underStatusBar = topBar.topPadding
        if (underStatusBar > 0) return (topBar.measuredHeight - underStatusBar).toFloat()
        return (topBar.measuredHeight + ((topBar.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0)).toFloat()
    }

    private companion object {
        const val SETTLE_DURATION = 200f
    }
}

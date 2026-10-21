package dev.apexnative.navigation.viewcontrollers.bottomtabs

import android.view.View
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener

/**
 * bottomTabs.hideOnScroll: the tabs hide after a drag that scrolls the screen's content down and come back after one
 * that scrolls it up. They only move down, out of the screen: going GONE laid the screen out again, and with the screen
 * under them (it is while they hide on scroll) its safe area changed and its content jumped.
 */
class BottomTabsCollapseBehavior(private val container: () -> View?) :
    ScrollEventListener.OnScrollListener, ScrollEventListener.OnDragListener {
    private var scrollEventListener: ScrollEventListener? = null
    private var hiddenByScroll = false

    /**
     * Follows the scroll of [listener]'s screen; another screen's replaces it, and the tabs show for it, as the top bar
     * does: hidden by the rows of the screen before, they stayed hidden until the new one scrolled up.
     */
    fun enableCollapse(listener: ScrollEventListener?) {
        if (listener === scrollEventListener) return
        scrollEventListener?.unregister(this)
        scrollEventListener = listener
        listener?.register(null, this, this)
        if (hiddenByScroll) onShow()
    }

    fun disableCollapse() {
        scrollEventListener?.unregister(this)
        scrollEventListener = null
        if (hiddenByScroll) onShow()
    }

    override fun onScrollUp(nextTranslation: Float) = Unit

    override fun onScrollDown(nextTranslation: Float) = Unit

    override fun onShow() {
        hiddenByScroll = false
        container()?.animate()?.translationY(0f)?.setDuration(DURATION)?.start()
    }

    override fun onHide() {
        val view = container() ?: return
        hiddenByScroll = true
        // Out of the screen with what is under it: a compact bar floats over its bottom margin and the navigation bar.
        val under = ((view.parent as? View)?.let { it.height - view.bottom } ?: 0).coerceAtLeast(0)
        view.animate().translationY((view.height + under).toFloat()).setDuration(DURATION).start()
    }

    private companion object {
        const val DURATION = 200L
    }
}

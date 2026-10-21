package dev.apexnative.navigation.views.stack.fab

import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener

/**
 * `fab.hideOnScroll`: the FAB hides after a drag that scrolls its screen's content down and shows after one that
 * scrolls it up.
 */
class FabCollapseBehaviour(private val fab: FabAnimator) :
    ScrollEventListener.OnScrollListener, ScrollEventListener.OnDragListener {
    private var scrollEventListener: ScrollEventListener? = null

    /** Follows the scroll of [scrollEventListener]'s screen; another screen's replaces it. */
    fun enableCollapse(scrollEventListener: ScrollEventListener) {
        if (scrollEventListener === this.scrollEventListener) return
        this.scrollEventListener?.unregister(this)
        this.scrollEventListener = scrollEventListener
        scrollEventListener.register(null, this, this)
    }

    fun disableCollapse() {
        scrollEventListener?.unregister(this)
        scrollEventListener = null
        fab.show()
    }

    override fun onScrollUp(nextTranslation: Float) = Unit

    override fun onScrollDown(nextTranslation: Float) = Unit

    override fun onShow() = fab.show()

    override fun onHide() = fab.hide()
}

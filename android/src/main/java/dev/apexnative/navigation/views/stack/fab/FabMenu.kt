package dev.apexnative.navigation.views.stack.fab

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import dev.apexnative.navigation.R
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener

private const val ANIMATION_DURATION = 150L
private const val ANIMATION_STAGGER = 30L
private const val CLOSED_SCALE = 0.6f

/** A main button with its actions above it: the main button opens and closes them. */
class FabMenu(context: Context, val fabId: String) : LinearLayout(context), FabAnimator {
    val main = Fab(context, fabId)
    val actions = mutableListOf<Fab>()
    var isOpen = false
        private set
    private val collapseBehaviour = FabCollapseBehaviour(this)
    private val spacing = context.resources.getDimensionPixelSize(R.dimen.margin)

    /** Room for the buttons' shadows inside the menu, which its parent clips: the menu's margins are this much smaller. */
    val shadowInset = spacing

    /** `fab.scrimColor`: the color over the screen while the actions show; null for none. */
    var scrimColor: Int? = null
    private var scrim: View? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(shadowInset, shadowInset, shadowInset, shadowInset)
        clipToPadding = false
        main.setImageResource(R.drawable.ic_fab_add_24dp)
        main.setOnClickListener { toggle() }
        // Its own size, centered by the menu. MATCH_PARENT, the default of a vertical LinearLayout, had the menu measure
        // it again to the menu's width: the button took that width and its touch target padding on top, larger than its
        // room, and its icon went to the bottom right corner (a mini button, or actions of another size).
        addView(main, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun setActions(fabs: List<Fab>) {
        actions.forEach { removeView(it) }
        actions.clear()
        fabs.forEach { fab ->
            fab.isVisible = isOpen
            addView(fab, childCount - 1, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = spacing
            })
            actions.add(fab)
        }
    }

    fun toggle() = if (isOpen) close() else open()

    fun open() {
        if (isOpen) return
        isOpen = true
        showScrim()
        // From the main button up.
        actions.asReversed().forEachIndexed { i, fab ->
            fab.animate().cancel()
            fab.isVisible = true
            fab.alpha = 0f
            fab.scaleX = CLOSED_SCALE
            fab.scaleY = CLOSED_SCALE
            fab.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(i * ANIMATION_STAGGER).setDuration(ANIMATION_DURATION).withEndAction(null).start()
        }
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        hideScrim()
        actions.forEachIndexed { i, fab ->
            fab.animate().cancel()
            fab.animate().alpha(0f).scaleX(CLOSED_SCALE).scaleY(CLOSED_SCALE)
                .setStartDelay(i * ANIMATION_STAGGER).setDuration(ANIMATION_DURATION)
                .withEndAction { if (!isOpen) fab.isVisible = false }.start()
        }
    }

    // Right under the menu, over the screen and its bars: it fades in and out with the actions, a press on it closes them.
    private fun showScrim() {
        val color = scrimColor ?: return
        val parent = parent as? ViewGroup ?: return
        val view = scrim ?: View(context).apply {
            setOnClickListener { close() }
            scrim = this
        }
        view.setBackgroundColor(color)
        view.animate().cancel()
        if (view.parent == null) {
            parent.addView(view, parent.indexOfChild(this), CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.MATCH_PARENT))
            view.alpha = 0f
        }
        view.animate().alpha(1f).setDuration(ANIMATION_DURATION).withEndAction(null).start()
    }

    private fun hideScrim() {
        val view = scrim ?: return
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(ANIMATION_DURATION)
                .withEndAction { if (!isOpen) (view.parent as? ViewGroup)?.removeView(view) }.start()
    }

    /**
     * The scrim out at once, before the menu leaves its parent: the parent may not lose another child while it removes
     * one (taken out from onDetachedFromWindow, it broke the parent's list of children and the app stopped).
     */
    fun removeScrim() {
        val view = scrim ?: return
        view.animate().cancel()
        (view.parent as? ViewGroup)?.removeView(view)
    }

    /**
     * The menu closes and leaves its parent next: a scrim that shows fades out by itself and goes from the parent at
     * the end of its fade, which the menu no longer owns; it takes no touches meanwhile, which belong to the new screen.
     */
    fun closeToLeave() {
        close()
        scrim?.apply {
            setOnClickListener(null)
            isClickable = false
        }
        scrim = null
    }

    override fun show() = main.show()

    override fun hide() {
        close()
        main.hide()
    }

    fun enableCollapse(scrollEventListener: ScrollEventListener) = collapseBehaviour.enableCollapse(scrollEventListener)

    fun disableCollapse() = collapseBehaviour.disableCollapse()
}

package dev.apexnative.navigation.views.bottomtabs

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import dev.apexnative.navigation.react.ReactView
import dev.apexnative.navigation.react.events.ComponentType
import dev.apexnative.navigation.viewcontrollers.bottomtabs.TabCellState
import dev.apexnative.navigation.viewcontrollers.viewcontroller.IReactView

/**
 * The React component of a tab (`bottomTab.component`, the cells of `bottomTabs.customRow`) in place of the tab's own
 * content: a content view of its own — a React root by its name now, a component from the screens' tree later — that
 * fills the cell, renders at once, hears its state as props ([update]) and its appearance with its tabs
 * (`BottomTabItem`), and stops once with the tabs ([destroy]). Its place in the row takes the touches. Main thread
 * only.
 */
@SuppressLint("ViewConstructor")
open class CustomBottomTabItemView(context: Context) : FrameLayout(context) {
    private var content: IReactView? = null
    private var state: TabCellState? = null
    private var destroyed = false

    /** The content the cell shows, filling it, made with [state] as its first props. */
    internal fun show(view: IReactView, state: TabCellState) {
        content = view
        this.state = state
        isSelected = state.selected
        addView(view.asView(), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** The state the native side tells the component; nothing goes when it is the one told. */
    internal fun update(state: TabCellState) {
        if (destroyed || state == this.state) return
        this.state = state
        isSelected = state.selected
        (content as? ReactView)?.updateProps(bundleOf(state))
    }

    /** The tab is selected or not: the component hears it. */
    open fun setItemSelected(selected: Boolean) {
        isSelected = selected
        state?.let { update(it.copy(selected = selected)) }
    }

    /** The tabs start to appear: so does the cell's component. */
    internal fun willAppear() {
        if (!destroyed) content?.sendComponentWillStart(ComponentType.BottomTabItem)
    }

    /** The tabs appeared. */
    internal fun didAppear() {
        if (!destroyed) content?.sendComponentStart(ComponentType.BottomTabItem)
    }

    /** The tabs disappeared. */
    internal fun didDisappear() {
        if (!destroyed) content?.sendComponentStop(ComponentType.BottomTabItem)
    }

    /** Stops the component's React root; once. Its DidDisappear goes first when it appeared. */
    open fun destroy() {
        if (destroyed) return
        didDisappear()
        destroyed = true
        content?.destroy()
    }

    /** The props of [state] as React Native takes them. */
    internal fun bundleOf(state: TabCellState): Bundle = Bundle().apply {
        putInt(TAB_INDEX, state.tabIndex)
        putBoolean(SELECTED, state.selected)
        state.badge?.let { putString(BADGE, it) } ?: putString(BADGE, null)
        putBoolean(DOT, state.dot)
        state.dotColor?.let { putInt(DOT_COLOR, it) }
    }

    private companion object {
        const val TAB_INDEX = "tabIndex"
        const val SELECTED = "selected"
        const val BADGE = "badge"
        const val DOT = "dot"
        const val DOT_COLOR = "dotColor"
    }
}

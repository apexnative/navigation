package dev.apexnative.navigation.views.bottomtabs

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationBarItemView
import com.google.android.material.navigation.NavigationBarView
import dev.apexnative.navigation.R
import dev.apexnative.navigation.customrow.BottomTabsCustomRowOptions
import dev.apexnative.navigation.options.params.LayoutDirection
import dev.apexnative.navigation.utils.MaterialTheme
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.utils.UiUtils
import com.google.android.material.R as MaterialR

/**
 * The bottom tabs: a Material navigation bar. With the app's Material theme it has that theme's style and colors;
 * without one (an AppCompat theme), Material 3's. The controller owns the selection: a tap reaches
 * [OnTabSelectedListener], which selects the tab with [setCurrentItem].
 */
private const val COMPACT_TAB_WIDTH_DP = 80f

@SuppressLint("ViewConstructor")
class BottomTabs(context: Context) : BottomNavigationView(MaterialTheme.context(context)) {
    /**
     * `layoutStyle: compact`: as wide as its tabs need, 80 dp each, for a bar that floats. Material shares out all the
     * width the bar gets among its tabs, so without it a compact bar was as wide as a stretched one.
     */
    var compact = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            ViewCompat.requestApplyInsets(this)
        }

    /** When the tabs show or hide: the container around them goes with them. */
    var onVisibilityChanged: ((Int) -> Unit)? = null

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        onVisibilityChanged?.invoke(visibility)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!compact || menu.size() == 0) return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = (UiUtils.dpToPx(context, COMPACT_TAB_WIDTH_DP) * menu.size()).toInt() + paddingLeft + paddingRight
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val exact = if (available > 0) minOf(width, available) else width
        super.onMeasure(MeasureSpec.makeMeasureSpec(exact, MeasureSpec.EXACTLY), heightMeasureSpec)
    }

    enum class TitleState {
        ALWAYS_SHOW, SHOW_WHEN_ACTIVE, SHOW_WHEN_ACTIVE_FORCE, ALWAYS_HIDE,

        /** Every title up to three tabs, the selected tab's from four. */
        AUTO
    }

    fun interface OnTabSelectedListener {
        /** [wasSelected]: a tap on the selected tab. Returns whether the bar should select the tab itself. */
        fun onTabSelected(index: Int, wasSelected: Boolean): Boolean
    }

    fun interface OnTabLongPressedListener {
        /** The tab is not selected and gets no [OnTabSelectedListener.onTabSelected]. */
        fun onTabLongPressed(index: Int)
    }

    private class ItemStyle(var testId: String?) {
        var iconActiveColor: Int? = null
        var iconInactiveColor: Int? = null
        var titleActiveColor: Int? = null
        var titleInactiveColor: Int? = null
        var iconWidth: Double? = null
        var iconHeight: Double? = null
        var typeface: Typeface? = null
        var activeTextSize: Float? = null
        var inactiveTextSize: Float? = null
        var keepsIconColors = false
        var keepsSelectedIconColors = false
    }

    private val items = mutableListOf<BottomTabItem>()
    private val itemIds = mutableListOf<Int>()
    private val styles = mutableListOf<ItemStyle>()
    private val customItemViews = mutableListOf<CustomBottomTabItemView>()
    private var tabSelectedListener: OnTabSelectedListener? = null
    private var tabLongPressedListener: OnTabLongPressedListener? = null
    // The long presses set again after a tap's update of the menu, before Android 8.
    private val longPressesAgain = Runnable { applyLongPressesAgain() }
    private var externalCustomItemViewHost = false

    var titleState = TitleState.AUTO
        set(value) {
            field = value
            labelVisibilityMode = when (value) {
                TitleState.ALWAYS_SHOW -> NavigationBarView.LABEL_VISIBILITY_LABELED
                TitleState.SHOW_WHEN_ACTIVE, TitleState.SHOW_WHEN_ACTIVE_FORCE -> NavigationBarView.LABEL_VISIBILITY_SELECTED
                TitleState.ALWAYS_HIDE -> NavigationBarView.LABEL_VISIBILITY_UNLABELED
                TitleState.AUTO -> NavigationBarView.LABEL_VISIBILITY_AUTO
            }
            applyItemStyles()
        }

    val itemsCount: Int
        get() = items.size

    val currentItem: Int
        get() = itemIds.indexOf(selectedItemId).coerceAtLeast(0)

    val isHidden: Boolean
        get() = visibility == GONE

    init {
        id = R.id.bottomTabs
        // Material's theme overlay of the bar is a copy of the theme, made once: it follows a light/dark switch too.
        // The view's context, not the constructor's.
        MaterialTheme.followOverlay(this.context)
        // The container draws the background, the outline and the shadow.
        setBackgroundColor(Color.TRANSPARENT)
        elevation = 0f
        // The tabs' layout keeps clear of the navigation bar below: the bar itself only keeps its sides clear
        // (landscape), its own bottom padding would double it.
        // A compact bar floats in the middle, clear of the sides: their insets made it wider and off center.
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val sides = if (compact) Insets.NONE
                else insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(sides.left, 0, sides.right, 0)
            insets
        }
        setOnItemSelectedListener { item ->
            // The tap's update of the menu comes after this listener: the long presses are set again after it.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) post(longPressesAgain)
            tabSelectedListener?.onTabSelected(indexOf(item), false) ?: true
        }
        setOnItemReselectedListener { item -> tabSelectedListener?.onTabSelected(indexOf(item), true) }
    }

    fun setOnTabSelectedListener(listener: OnTabSelectedListener?) {
        tabSelectedListener = listener
    }

    fun setOnTabLongPressedListener(listener: OnTabLongPressedListener?) {
        tabLongPressedListener = listener
        applyItemStyles()
    }

    fun addItems(newItems: List<BottomTabItem>) {
        for (item in newItems) {
            val itemId = View.generateViewId()
            itemIds.add(itemId)
            items.add(item)
            styles.add(ItemStyle(item.testId))
            menu.add(Menu.NONE, itemId, items.size - 1, item.title).icon = iconOf(item, styles.last())
        }
        applyItemStyles()
    }

    fun getItem(index: Int): BottomTabItem? = items.getOrNull(index)

    fun setCurrentItem(index: Int) = setCurrentItem(index, true)

    /** [useCallback]: as a tap, through the listener; without it the tab is selected silently. */
    fun setCurrentItem(index: Int, useCallback: Boolean) {
        if (index !in itemIds.indices) {
            Log.w(NavigationLog.TAG, "BottomTabs: there is no tab at index $index: there are $itemsCount tabs")
            return
        }
        if (useCallback) {
            val listener = tabSelectedListener
            if (index == currentItem) {
                listener?.onTabSelected(index, true)
                return
            }
            if (listener != null && !listener.onTabSelected(index, false)) return
        }
        menu.findItem(itemIds[index])?.isChecked = true
        applyLongPressesAgain()
    }

    fun hideBottomNavigation(withAnimation: Boolean) {
        if (withAnimation) {
            animate().translationY(height.toFloat()).setDuration(ANIMATION_DURATION).withEndAction { visibility = GONE }.start()
        } else {
            translationY = height.toFloat()
            visibility = GONE
        }
    }

    fun restoreBottomNavigation(withAnimation: Boolean) {
        visibility = VISIBLE
        if (withAnimation) animate().translationY(0f).setDuration(ANIMATION_DURATION).start() else translationY = 0f
    }

    fun setText(index: Int, text: String) {
        val item = items.getOrNull(index) ?: return
        if (item.title == text) return
        items[index] = item.copy(title = text)
        menu.findItem(itemIds[index])?.title = text
        applyItemStyle(index)
        applyLongPressesAgain()
    }

    fun setIcon(index: Int, icon: Drawable) {
        val item = items.getOrNull(index) ?: return
        if (item.icon == icon) return
        updateIcon(index, item.copy(icon = icon))
    }

    fun setSelectedIcon(index: Int, icon: Drawable) {
        val item = items.getOrNull(index) ?: return
        if (item.selectedIcon == icon) return
        updateIcon(index, item.copy(selectedIcon = icon))
    }

    fun setLayoutDirection(direction: LayoutDirection) {
        layoutDirection = direction.get()
    }

    fun setTabTestId(index: Int, testId: String?) = updateStyle(index) { it.testId = testId }

    fun setIconWidth(index: Int, width: Double?) = updateStyle(index) { it.iconWidth = width }

    fun setIconHeight(index: Int, height: Double?) = updateStyle(index) { it.iconHeight = height }

    fun setTitleTypeface(index: Int, typeface: Typeface?) = updateStyle(index) { it.typeface = typeface }

    fun setTitleActiveTextSizeInSp(index: Int, size: Float?) = updateStyle(index) { it.activeTextSize = size }

    fun setTitleInactiveTextSizeInSp(index: Int, size: Float?) = updateStyle(index) { it.inactiveTextSize = size }

    /** Null: the theme's color. */
    fun setIconActiveColor(index: Int, color: Int?) = updateStyle(index) { it.iconActiveColor = color }

    fun setIconInactiveColor(index: Int, color: Int?) = updateStyle(index) { it.iconInactiveColor = color }

    fun setTitleActiveColor(index: Int, color: Int?) = updateStyle(index) { it.titleActiveColor = color }

    fun setTitleInactiveColor(index: Int, color: Int?) = updateStyle(index) { it.titleInactiveColor = color }

    /**
     * `disableIconTint`, `disableSelectedIconTint`: the icon of the tab keeps its own colors in that state, tinted
     * neither by the tab's colors nor by the bar's style.
     */
    fun setIconTintDisabled(index: Int, unselected: Boolean, selected: Boolean) {
        val style = styles.getOrNull(index) ?: return
        if (style.keepsIconColors == unselected && style.keepsSelectedIconColors == selected) return
        style.keepsIconColors = unselected
        style.keepsSelectedIconColors = selected
        updateIcon(index, items[index])
    }

    /** An empty text removes the badge. Null color: the theme's. */
    fun setBadge(index: Int, text: String, color: Int?) {
        val itemId = itemIds.getOrNull(index) ?: return
        if (text.isEmpty()) {
            removeBadge(itemId)
            return
        }
        getOrCreateBadge(itemId).apply {
            setText(text)
            applyBadgeColors(this, color)
            isVisible = true
        }
    }

    /** A badge without text. */
    fun setDot(index: Int, visible: Boolean, color: Int?) {
        val itemId = itemIds.getOrNull(index) ?: return
        if (!visible) {
            removeBadge(itemId)
            return
        }
        getOrCreateBadge(itemId).apply {
            clearText()
            clearNumber()
            applyBadgeColors(this, color)
            isVisible = true
        }
    }

    fun getViewAtPosition(index: Int): View? = itemView(index)

    // The theme's, when the options give none: read each time, so that they follow a light/dark switch.
    private fun applyBadgeColors(badge: BadgeDrawable, color: Int?) {
        badge.backgroundColor = color ?: MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError, badge.backgroundColor)
        badge.badgeTextColor = MaterialColors.getColor(this, MaterialR.attr.colorOnError, badge.badgeTextColor)
    }

    // The bar reads its style's colors once: after a light/dark switch (the activity isn't recreated) it reads them
    // again, items included.
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        reloadStyleColors()
    }

    private fun reloadStyleColors() {
        val values = context.obtainStyledAttributes(
            null, MaterialR.styleable.NavigationBarView, MaterialR.attr.bottomNavigationStyle, MaterialR.style.Widget_Design_BottomNavigationView
        )
        try {
            values.getColorStateList(MaterialR.styleable.NavigationBarView_itemIconTint)?.let { itemIconTintList = it }
            values.getColorStateList(MaterialR.styleable.NavigationBarView_itemTextColor)?.let { itemTextColor = it }
            values.getColorStateList(MaterialR.styleable.NavigationBarView_itemRippleColor)?.let { itemRippleColor = it }
            val indicatorStyle = values.getResourceId(MaterialR.styleable.NavigationBarView_itemActiveIndicatorStyle, 0)
            if (indicatorStyle != 0) {
                val indicator = context.obtainStyledAttributes(indicatorStyle, MaterialR.styleable.NavigationBarActiveIndicator)
                try {
                    indicator.getColorStateList(MaterialR.styleable.NavigationBarActiveIndicator_android_color)?.let { itemActiveIndicatorColor = it }
                } finally {
                    indicator.recycle()
                }
            }
        } finally {
            values.recycle()
        }
        applyItemStyles()
    }

    /**
     * When enabled, this view stops re-parenting custom React tab item views into its cells on every layout pass —
     * the caller (the customRow floating row) owns where those item views live — and stays fully transparent, since
     * the caller draws the tabs.
     */
    fun setExternalCustomItemViewHost(enabled: Boolean) {
        externalCustomItemViewHost = enabled
        alpha = if (enabled) 0f else 1f
    }

    /** `bottomTabs.customRow`, resolved: the look of the row that hosts the tabs' React components. */
    var customRowOptions = BottomTabsCustomRowOptions()
        set(value) {
            field = value
            customRowOptionsListener?.invoke(value)
        }

    /** The row hosting the custom item views follows [customRowOptions]. */
    var customRowOptionsListener: ((BottomTabsCustomRowOptions) -> Unit)? = null

    fun mergeCustomRowOptions(options: BottomTabsCustomRowOptions) {
        customRowOptions = customRowOptions.copy().mergeWith(options)
    }

    /** Show and hide animations reset view properties, alpha included: the bar stays invisible under an external host. */
    override fun setAlpha(alpha: Float) {
        super.setAlpha(if (externalCustomItemViewHost) 0f else alpha)
    }

    /** Under an external host the invisible tabs take no taps: the row is narrower than the bar, its edges select nothing. */
    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        !externalCustomItemViewHost && super.dispatchTouchEvent(event)

    /**
     * Replaces the content of every tab cell with the given views, attached in the cell so that taps still reach it.
     * An empty list removes them.
     */
    fun setCustomItemViews(customViews: List<CustomBottomTabItemView>?) {
        clearCustomItemViews()
        if (customViews.isNullOrEmpty()) return
        customItemViews.addAll(customViews)
        attachCustomItemViews()
    }

    fun onCustomItemViewSelectionChanged(selectedIndex: Int) {
        customItemViews.forEachIndexed { i, view -> view.setItemSelected(i == selectedIndex) }
    }

    fun getCustomItemView(index: Int): CustomBottomTabItemView? = customItemViews.getOrNull(index)

    fun hasCustomItemViews() = customItemViews.isNotEmpty()

    /** With the tabs: their React components were never stopped. */
    fun destroyCustomItemViews() {
        customItemViews.forEach { it.destroy() }
        clearCustomItemViews()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed && customItemViews.isNotEmpty()) attachCustomItemViews()
    }

    private fun clearCustomItemViews() {
        customItemViews.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        customItemViews.clear()
    }

    private fun attachCustomItemViews() {
        if (externalCustomItemViewHost) return
        customItemViews.forEachIndexed { i, itemView ->
            val cell = getViewAtPosition(i) as? ViewGroup ?: return@forEachIndexed
            val parent = itemView.parent as? ViewGroup
            if (parent != null && parent !== cell) parent.removeView(itemView)
            if (itemView.parent == null) {
                cell.addView(itemView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        }
    }

    private fun indexOf(item: MenuItem) = itemIds.indexOf(item.itemId)

    private fun updateIcon(index: Int, item: BottomTabItem) {
        items[index] = item
        menu.findItem(itemIds[index])?.icon = iconOf(item, styles[index])
        applyItemStyle(index)
        applyLongPressesAgain()
    }

    // A state whose icon keeps its own colors gets a drawable that ignores the item's tint; one drawable that serves
    // both states with only one of them kept is copied, so the tinted state doesn't tint the kept one.
    private fun iconOf(item: BottomTabItem, style: ItemStyle): Drawable? {
        val keepsIcon = style.keepsIconColors
        val keepsSelected = style.keepsSelectedIconColors
        val selected = item.selectedIcon
        if (selected == null && !keepsIcon && !keepsSelected) return item.icon
        val icon = item.icon ?: selected ?: return null
        if (selected == null && keepsIcon && keepsSelected) return KeepsColors(icon)
        val checked = selected ?: if (keepsIcon != keepsSelected) copyOf(icon) else icon
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), if (keepsSelected) KeepsColors(checked) else checked)
            addState(intArrayOf(), if (keepsIcon) KeepsColors(icon) else icon)
        }
    }

    private fun copyOf(icon: Drawable): Drawable = icon.constantState?.newDrawable()?.mutate() ?: icon

    // An icon in its own colors: the tint the item view gives its icon doesn't reach it.
    private class KeepsColors(drawable: Drawable) : DrawableWrapper(drawable) {
        override fun setTintList(tint: ColorStateList?) = Unit

        override fun setTintMode(tintMode: PorterDuff.Mode?) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    }

    private inline fun updateStyle(index: Int, update: (ItemStyle) -> Unit) {
        val style = styles.getOrNull(index) ?: return
        update(style)
        applyItemStyle(index)
    }

    private fun applyItemStyles() = styles.indices.forEach(::applyItemStyle)

    private fun applyItemStyle(index: Int) {
        val view = itemView(index) ?: return
        val style = styles[index]
        view.setIconTintList(tint(style.iconActiveColor, style.iconInactiveColor, itemIconTintList))
        view.setTextColor(tint(style.titleActiveColor, style.titleInactiveColor, itemTextColor))
        val iconSize = style.iconWidth ?: style.iconHeight
        view.setIconSize(if (iconSize != null) UiUtils.dpToPixels(context, iconSize) else itemIconSize)
        view.findViewById<TextView>(MaterialR.id.navigation_bar_item_small_label_view)?.let {
            style.typeface?.let { typeface -> it.typeface = typeface }
            style.inactiveTextSize?.let { size -> it.setTextSize(TypedValue.COMPLEX_UNIT_SP, size) }
        }
        view.findViewById<TextView>(MaterialR.id.navigation_bar_item_large_label_view)?.let {
            style.typeface?.let { typeface -> it.typeface = typeface }
            style.activeTextSize?.let { size -> it.setTextSize(TypedValue.COMPLEX_UNIT_SP, size) }
        }
        view.tag = style.testId
        ViewCompat.setAccessibilityDelegate(view, TestIdAccessibilityDelegate)
        applyLongPress(index)
    }

    // Without a listener the long press keeps the item's tooltip.
    private fun applyLongPress(index: Int) {
        val view = itemView(index) ?: return
        val listener = tabLongPressedListener ?: return
        view.setOnLongClickListener {
            listener.onTabLongPressed(index)
            true
        }
    }

    // Before Android 8 the item's tooltip takes the long press over each time Material updates the menu (a selection,
    // a title, an icon: every item is set up again): the long presses are set again after each update.
    private fun applyLongPressesAgain() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return
        styles.indices.forEach(::applyLongPress)
    }

    /** The given colors over the bar's: checked, then the rest. */
    private fun tint(active: Int?, inactive: Int?, base: ColorStateList?): ColorStateList? {
        if (active == null && inactive == null) return base
        val checked = intArrayOf(android.R.attr.state_checked)
        return ColorStateList(
            arrayOf(checked, intArrayOf()),
            intArrayOf(
                active ?: base?.getColorForState(checked, base.defaultColor) ?: Color.BLACK,
                inactive ?: base?.defaultColor ?: Color.GRAY
            )
        )
    }

    private fun itemView(index: Int): NavigationBarItemView? {
        val itemId = itemIds.getOrNull(index) ?: return null
        return findItemView(menuViewGroup, itemId)
    }

    // Not findViewById: the React views of custom items have ids too.
    private fun findItemView(group: ViewGroup, itemId: Int): NavigationBarItemView? {
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is NavigationBarItemView -> if (child.itemData?.itemId == itemId) return child
                is ViewGroup -> findItemView(child, itemId)?.let { return it }
            }
        }
        return null
    }

    // The testID as the tab's resource id: UI tests that go through accessibility (UiAutomator, Appium, Maestro)
    // don't see view tags.
    private object TestIdAccessibilityDelegate : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            (host.tag as? String)?.takeIf { it.isNotEmpty() }?.let { info.viewIdResourceName = it }
        }
    }

    private companion object {
        const val ANIMATION_DURATION = 300L
    }
}

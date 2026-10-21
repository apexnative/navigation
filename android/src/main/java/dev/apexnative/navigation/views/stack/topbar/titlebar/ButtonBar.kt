package dev.apexnative.navigation.views.stack.topbar.titlebar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.menu.MenuView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.MenuItemCompat
import com.google.android.material.appbar.MaterialToolbar
import dev.apexnative.navigation.R
import dev.apexnative.navigation.options.ButtonMenuOptions
import dev.apexnative.navigation.options.ButtonOptions
import dev.apexnative.navigation.options.parsers.TypefaceLoader
import dev.apexnative.navigation.utils.MaterialTheme
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.UiUtils
import java.util.WeakHashMap

/**
 * The buttons of one side of the bar in its toolbar: each button a menu item — a text, an icon (on its background), or
 * a React button's cell as the item's action view —, in the bar or in "⋮" as [set] says, with the `overflowMenu` items
 * first in "⋮". A press sends the button's id; a button with a `menu` opens it under the button (in "⋮" or not laid
 * out: at the bar's end), or on a long press with `menuOnLongPress`. The views of the items get their `testID`s after
 * each layout of the toolbar, which makes them again. Main thread only.
 */
internal class ButtonBar(
    private val toolbar: MaterialToolbar,
    private val left: Boolean,
    private val loadIcon: (String) -> Drawable?,
    private val host: Host,
) {
    /** What the buttons tell the bar. */
    interface Host {
        /** The button [buttonId] was pressed. */
        fun pressed(buttonId: String)

        /** The item [itemId] of a menu was picked: of the button [buttonId], or of `overflowMenu` without one. */
        fun picked(buttonId: String?, itemId: String)

        /** The menu of the button [buttonId] as the screen's options give it now; none. */
        fun menuOf(buttonId: String): ButtonMenuOptions?

        /** The `testID` of an item the side holds besides its buttons (the search's magnifier); none. */
        fun testIdOf(item: MenuItem): String? = null
    }

    /** One button as the bar shows it: its options with the side's colors on them, and its React cell if it has one. */
    class Button(val options: ButtonOptions, val cell: BarButtonComponentCell?)

    private val context: Context = toolbar.context
    private val typefaces = TypefaceLoader(context)
    private val itemIds = HashMap<MenuItem, ButtonOptions>()
    private var overflowTestId: String? = null
    private var popup: PopupMenu? = null
    private var watched: ActionMenuView? = null
    // The item views that have the long press of a menu.
    private val hooked = WeakHashMap<View, Boolean>()

    init {
        toolbar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyViewIds() }
    }

    /**
     * The buttons of the side: [inBar] in the bar, [inOverflow] in "⋮" (the right side; the left one keeps all in
     * the bar), each in the list's order; [overflow], the items of `overflowMenu` (right side). [animated]: the change
     * goes with an `AutoTransition` (`animateLeftButtons` / `animateRightButtons`).
     */
    fun set(inBar: List<Button>, inOverflow: List<Button>, overflow: ButtonMenuOptions?, animated: Boolean) {
        if (animated && toolbar.isLaidOut) TransitionManager.beginDelayedTransition(toolbar, AutoTransition())
        val menu = toolbar.menu
        menu.clear()
        itemIds.clear()
        var order = 0
        overflow?.let {
            ButtonMenus.fill(context, menu, OVERFLOW_GROUP, 0, it, MenuItem.SHOW_AS_ACTION_NEVER, loadIcon) { id ->
                host.picked(null, id)
            }
            order = it.items.size
        }
        overflowTestId = overflow?.testID
        inOverflow.forEachIndexed { index, button -> add(menu, button, OVERFLOW_ORDER + order + index, false) }
        inBar.forEachIndexed { index, button ->
            // The first right button is the last of the line: the menu lays its items out in their order.
            val place = if (left) index else inBar.size - 1 - index
            add(menu, button, BAR_ORDER + place, true)
        }
        toolbar.post { applyViewIds() }
    }

    /** The color of "⋮" (`rightButtonColor`, else `colorOnSurfaceVariant`). */
    fun setOverflowColor(color: Int) {
        val icon = toolbar.overflowIcon ?: return
        val tinted = DrawableCompat.wrap(icon.mutate())
        DrawableCompat.setTint(tinted, color)
        toolbar.overflowIcon = tinted
    }

    /** An open menu of the side closes: the bar shows another screen. */
    fun closeMenus() {
        popup?.dismiss()
        popup = null
        toolbar.dismissPopupMenus()
    }

    private fun add(menu: Menu, button: Button, order: Int, inBar: Boolean) {
        val options = button.options
        val id = options.id.get(null) ?: return
        val item = menu.add(BUTTONS_GROUP, Menu.NONE, order, titleOf(options))
        itemIds[item] = options
        val enabled = !options.enabled.isFalse
        item.isEnabled = enabled
        if (inBar) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        } else {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        val cell = button.cell
        if (cell != null) {
            item.actionView = cell
            cell.contentDescription = options.accessibilityLabel.get(null)
        } else {
            iconOf(options, enabled)?.let { item.icon = it }
            options.accessibilityLabel.get(null)?.let { MenuItemCompat.setContentDescription(item, it) }
        }
        item.setOnMenuItemClickListener {
            val menuOptions = host.menuOf(id)
            if (cell == null && menuOptions != null && !options.menuOnLongPress.isTrue) {
                openMenu(id, menuOptions, viewOf(item))
            } else if (cell == null) {
                host.pressed(id)
            }
            true
        }
    }

    // The text of a button: its font (missing weight or italic made synthetically), its size in sp, its color, its
    // capitals (`allCaps`; Material 3's own text has none).
    private fun titleOf(options: ButtonOptions): CharSequence {
        val raw = options.text.get(null).orEmpty()
        val text = if (options.allCaps.isTrue) raw.uppercase() else raw
        if (text.isEmpty()) return text
        val spannable = SpannableString(text)
        val enabled = !options.enabled.isFalse
        val color = if (enabled) options.color.get(context, null) else options.disabledColor.get(context, DISABLED)
        color?.let { spannable.setSpan(ForegroundColorSpan(it), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        if (options.fontSize.hasValue()) {
            val size = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                options.fontSize.get(0.0).toFloat(),
                context.resources.displayMetrics,
            )
            spannable.setSpan(AbsoluteSizeSpan(size.toInt()), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (options.font.hasValue()) {
            val typeface = options.font.getTypeface(typefaces, Typeface.DEFAULT)
            val weight = options.font.fontWeight.get(null)
            val bold = weight == "bold" || (weight?.toIntOrNull() ?: 0) >= BOLD_WEIGHT
            val italic = options.font.fontStyle.get(null) == "italic"
            spannable.setSpan(FontSpan(typeface, bold, italic), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    /**
     * The icon of a button: its own, or the library's close icon for `systemItem: 'close'`; tinted with `color`
     * (disabled: `disabledColor`, light grey by default) unless `disableIconTint`, as it is without; on its background
     * with an `iconBackground` color. None when it doesn't load (the button keeps its text).
     */
    private fun iconOf(options: ButtonOptions, enabled: Boolean): Drawable? {
        val icon = when {
            options.icon.hasValue() -> options.icon.get(null)?.let(loadIcon)
            options.systemItem.get(null) == CLOSE -> AppCompatResources.getDrawable(context, R.drawable.apex_navigation_ic_close)
            else -> null
        }?.mutate() ?: return null
        if (!options.disableIconTint.isTrue) {
            val tint = if (enabled) options.color.get(context, null) else options.disabledColor.get(context, DISABLED)
            tint?.let { DrawableCompat.setTint(icon, it) }
        }
        val background = options.iconBackground
        if (!background.color.hasValue()) return icon
        val color = (if (enabled) null else background.disabledColor.get(context, null))
            ?: background.color.get(context, null)
            ?: return icon
        return IconBackgroundDrawable(
            icon,
            color,
            UiUtils.dpToPx(context, background.cornerRadius.get(0.0).toFloat()),
            UiUtils.dpToPx(context, background.width.get(0.0).toFloat()).toInt(),
            UiUtils.dpToPx(context, background.height.get(0.0).toFloat()).toInt(),
        )
    }

    // The menu of a button: Material's popup under the button, or at the bar's end.
    private fun openMenu(buttonId: String, options: ButtonMenuOptions, anchor: View?) {
        if (options.items.isEmpty()) return
        popup?.dismiss()
        val themed = MaterialTheme.context(context)
        val menu = if (anchor != null) {
            PopupMenu(themed, anchor)
        } else {
            PopupMenu(themed, toolbar, Gravity.END)
        }
        ButtonMenus.fill(themed, menu.menu, Menu.NONE, 0, options, null, loadIcon) { id -> host.picked(buttonId, id) }
        menu.setOnDismissListener { if (popup === menu) popup = null }
        popup = menu
        menu.show()
    }

    // The view of [item] in the bar; none in "⋮" or before the bar laid it out.
    private fun viewOf(item: MenuItem): View? {
        val menuView = actionMenuView() ?: return null
        for (index in 0 until menuView.childCount) {
            val child = menuView.getChildAt(index)
            if (itemOf(child) === item || item.actionView === child) return child
        }
        return null
    }

    private fun actionMenuView(): ActionMenuView? {
        for (index in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(index)
            if (child is ActionMenuView) return child
        }
        return null
    }

    // AppCompat gives an item's view no public way back to its item: the item view's own interface, which only
    // AppCompat's own views implement, is read and never changed.
    @SuppressLint("RestrictedApi")
    private fun itemOf(view: View): MenuItem? = (view as? MenuView.ItemView)?.itemData

    // The `testID`s of the item views: the bar makes its views again, so after every layout; a view used again without
    // one loses the one it had. "⋮" by AppCompat's description of it. A long press opens the menu of a button with
    // `menuOnLongPress`: the handler finds the item by the view pressed.
    private fun applyViewIds() {
        val menuView = actionMenuView() ?: return
        if (watched !== menuView) {
            watched = menuView
            menuView.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    toolbar.post { applyViewIds() }
                }

                override fun onChildViewRemoved(parent: View?, child: View?) = Unit
            })
        }
        val overflowDescription = context.getString(AppCompatR.string.abc_action_menu_overflow_description)
        for (index in 0 until menuView.childCount) {
            val child = menuView.getChildAt(index)
            val item = itemOf(child) ?: itemIds.keys.firstOrNull { it.actionView === child }
            val options = item?.let(itemIds::get)
            when {
                options != null -> {
                    TestIds.apply(child, options.testID.get(null))
                    if (child !is BarButtonComponentCell) longPressOf(child, options)
                }
                child.contentDescription == overflowDescription -> TestIds.apply(child, overflowTestId)
                item != null -> TestIds.apply(child, host.testIdOf(item))
                else -> Unit
            }
        }
    }

    // The toolbar uses its item views again for other items: the handler finds the button by the view pressed, at the
    // press, and a view that no longer needs it gets the toolbar's tooltip back.
    private fun longPressOf(view: View, options: ButtonOptions) {
        val id = options.id.get(null)
        if (id != null && options.menuOnLongPress.isTrue && host.menuOf(id) != null) {
            hooked[view] = true
            view.setOnLongClickListener { pressed -> openOnLongPress(pressed) }
            return
        }
        if (hooked.remove(view) == null) return
        view.setOnLongClickListener(null)
        itemOf(view)?.let { TooltipCompat.setTooltipText(view, MenuItemCompat.getTooltipText(it) ?: it.title) }
    }

    private fun openOnLongPress(pressed: View): Boolean {
        val options = itemOf(pressed)?.let(itemIds::get) ?: return false
        val id = options.id.get(null) ?: return false
        if (!options.menuOnLongPress.isTrue) return false
        val menu = host.menuOf(id) ?: return false
        openMenu(id, menu, pressed)
        return true
    }

    // A font of the options on a button's text, with the weight and the slant the typeface lacks drawn synthetically.
    private class FontSpan(private val typeface: Typeface?, private val bold: Boolean, private val italic: Boolean) :
        MetricAffectingSpan() {
        override fun updateDrawState(paint: TextPaint) = apply(paint)

        override fun updateMeasureState(paint: TextPaint) = apply(paint)

        private fun apply(paint: Paint) {
            val face = typeface ?: return
            paint.typeface = face
            if (bold && !face.isBold) paint.isFakeBoldText = true
            if (italic && !face.isItalic) paint.textSkewX = ITALIC_SKEW
        }
    }

    private companion object {
        const val BUTTONS_GROUP = 1
        const val OVERFLOW_GROUP = 2
        const val OVERFLOW_ORDER = 100
        const val BAR_ORDER = 1000
        const val CLOSE = "close"
        const val BOLD_WEIGHT = 600
        const val ITALIC_SKEW = -0.25f
        val DISABLED = Color.LTGRAY
    }
}

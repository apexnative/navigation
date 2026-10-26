package dev.apexnative.navigation.views.stack.topbar.titlebar

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
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.Menu
import android.view.View
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.MenuItemCompat
import dev.apexnative.navigation.R
import dev.apexnative.navigation.options.ButtonMenuOptions
import dev.apexnative.navigation.options.ButtonOptions
import dev.apexnative.navigation.options.parsers.TypefaceLoader
import dev.apexnative.navigation.utils.MaterialTheme
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.UiUtils
import kotlin.math.roundToInt

/**
 * The buttons of one side of the bar in its [ButtonRow]: each button a view of its own — a text or an icon (on its
 * background) as a toolbar draws its items ([BarButtonView]), or a React button's cell —, in the bar or in "⋮" as
 * [set] says, with the `overflowMenu` items first in "⋮". A press sends the button's id; a button with a `menu` opens
 * it under the button (in "⋮": at the bar's end), or on a long press with `menuOnLongPress`. A button keeps its view
 * while its id stays, and the view takes its `testID` as it is set. Main thread only.
 */
internal class ButtonBar(
    private val row: ButtonRow,
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
    }

    /** One button as the bar shows it: its options with the side's colors on them, and its React cell if it has one. */
    class Button(val options: ButtonOptions, val cell: BarButtonComponentCell?)

    private val context: Context = row.context
    private val typefaces = TypefaceLoader(context)
    // The views of the buttons in the bar by their ids, kept from one set to the next.
    private var views = HashMap<String, BarButtonView>()
    private var overflowMenu: ButtonMenuOptions? = null
    private var inOverflow: List<ButtonOptions> = emptyList()
    private var popup: PopupMenu? = null
    private var overflowPopup: PopupMenu? = null

    /**
     * The buttons of the side: [line] in the bar, in the line's order from its start; [inOverflow] in "⋮" (the right
     * side; the left one keeps all in the bar), in the list's order; [overflow], the items of `overflowMenu` (right
     * side). [animated]: the change goes with an `AutoTransition` (`animateLeftButtons` / `animateRightButtons`).
     */
    fun set(line: List<Button>, inOverflow: List<Button>, overflow: ButtonMenuOptions?, animated: Boolean) {
        if (animated && row.isLaidOut) TransitionManager.beginDelayedTransition(row, AutoTransition())
        val kept = this.views
        this.views = HashMap()
        val views = line.mapNotNull { button ->
            val id = button.options.id.get(null) ?: return@mapNotNull null
            button.cell?.let { return@mapNotNull cellOf(it, button.options) }
            val view = kept.remove(id)?.takeIf { id !in this.views } ?: BarButtonView(context)
            this.views[id] = view
            bind(view, id, button.options)
        }
        row.setButtons(views)
        overflowMenu = overflow
        this.inOverflow = inOverflow.map { it.options }.filter { it.id.get(null) != null }
        val showsOverflow = overflow != null || this.inOverflow.isNotEmpty()
        if (showsOverflow) {
            val button = row.overflowButton
            button.setOnClickListener { openOverflow(it) }
            TestIds.apply(button, overflow?.testID)
        }
        row.setOverflowShown(showsOverflow)
        // An open "⋮" shows what it holds now.
        overflowPopup?.let {
            if (showsOverflow) {
                it.menu.clear()
                fillOverflow(it.menu)
            } else {
                it.dismiss()
            }
        }
    }

    /** The color of "⋮" (`rightButtonColor`, else `colorOnSurfaceVariant`). */
    fun setOverflowColor(color: Int) = row.setOverflowColor(color)

    /** An open menu of the side closes: the bar shows another screen. */
    fun closeMenus() {
        popup?.dismiss()
        popup = null
        overflowPopup?.dismiss()
        overflowPopup = null
    }

    // A React button's cell: its label and its `testID` on the cell, the press its content's own.
    private fun cellOf(cell: BarButtonComponentCell, options: ButtonOptions): View {
        cell.contentDescription = options.accessibilityLabel.get(null)
        TestIds.apply(cell, options.testID.get(null))
        return cell
    }

    // A text or icon button: its look, its press and long press, its `testID`. The long press shows the tooltip, or
    // opens the menu of a button with `menuOnLongPress`.
    private fun bind(view: BarButtonView, id: String, options: ButtonOptions): View {
        val enabled = !options.enabled.isFalse
        view.isEnabled = enabled
        view.restyle()
        val longPressMenu = options.menuOnLongPress.isTrue && host.menuOf(id) != null
        view.setOnLongClickListener(null)
        // A listener, even none, makes the view long-clickable: TalkBack would offer a long press that does nothing.
        view.isLongClickable = false
        view.show(titleOf(options), iconOf(options, enabled), options.accessibilityLabel.get(null), !longPressMenu)
        if (longPressMenu) {
            view.setOnLongClickListener { pressed ->
                val menu = host.menuOf(id) ?: return@setOnLongClickListener false
                openMenu(id, menu, pressed)
                true
            }
        }
        view.setOnClickListener { pressed -> press(id, options, pressed) }
        TestIds.apply(view, options.testID.get(null))
        return view
    }

    // A press: the button's menu opens under [anchor] (at the bar's end without one), or the press goes to the screen.
    private fun press(id: String, options: ButtonOptions, anchor: View?) {
        val menu = host.menuOf(id)
        if (menu != null && !options.menuOnLongPress.isTrue) openMenu(id, menu, anchor) else host.pressed(id)
    }

    // "⋮" opens as a toolbar opens it: the theme's overflow popup from the end of the button, in the bar's popup theme.
    private fun openOverflow(anchor: View) {
        closeMenus()
        val popupTheme = MaterialTheme.popupTheme(context)
        val themed = if (popupTheme != 0) ContextThemeWrapper(context, popupTheme) else context
        val menu = PopupMenu(themed, anchor, Gravity.END, AppCompatR.attr.actionOverflowMenuStyle, 0)
        fillOverflow(menu.menu)
        menu.setOnDismissListener { if (overflowPopup === menu) overflowPopup = null }
        overflowPopup = menu
        menu.show()
    }

    // The `overflowMenu` items, then the buttons that don't fit in the bar, in the list's order.
    private fun fillOverflow(menu: Menu) {
        var order = 0
        overflowMenu?.let {
            ButtonMenus.fill(context, menu, OVERFLOW_GROUP, 0, it, null, loadIcon) { id -> host.picked(null, id) }
            order = it.items.size
        }
        inOverflow.forEachIndexed { index, options ->
            val id = options.id.get(null) ?: return@forEachIndexed
            val enabled = !options.enabled.isFalse
            val item = menu.add(BUTTONS_GROUP, Menu.NONE, order + index, titleOf(options))
            item.isEnabled = enabled
            iconOf(options, enabled)?.let { item.icon = it }
            options.accessibilityLabel.get(null)?.let { MenuItemCompat.setContentDescription(item, it) }
            item.setOnMenuItemClickListener {
                press(id, options, null)
                true
            }
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
            spannable.setSpan(AbsoluteSizeSpan(size.roundToInt()), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
            UiUtils.dpToPx(context, background.width.get(0.0).toFloat()).roundToInt(),
            UiUtils.dpToPx(context, background.height.get(0.0).toFloat()).roundToInt(),
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
            PopupMenu(themed, row, Gravity.END)
        }
        ButtonMenus.fill(themed, menu.menu, Menu.NONE, 0, options, null, loadIcon) { id -> host.picked(buttonId, id) }
        menu.setOnDismissListener { if (popup === menu) popup = null }
        popup = menu
        menu.show()
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
        const val CLOSE = "close"
        const val BOLD_WEIGHT = 600
        const val ITALIC_SKEW = -0.25f
        val DISABLED = Color.LTGRAY
    }
}

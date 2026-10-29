package dev.apexnative.navigation.views.stack.topbar.titlebar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import dev.apexnative.navigation.R
import dev.apexnative.navigation.utils.TestIds
import kotlin.math.max

/**
 * A menu of the top bar, the menu of a button or "⋮": a popup list under [anchor] as AppCompat shows a popup menu, with
 * the theme's style of [styleAttr] (`popupMenuStyle`, or `actionOverflowMenuStyle` for "⋮": its background, shadow,
 * offsets and place over the anchor) and [gravity] against the anchor. A submenu takes the menu's place, under its title.
 * Its [Colors] go over the theme's. Main thread only.
 */
internal class BarMenu(
    val context: Context,
    private val anchor: View,
    private val gravity: Int,
    private val styleAttr: Int,
    val colors: Colors,
) {
    /** The menu's colors over the theme's; none keeps the theme's. */
    class Colors(val background: Int?, val text: Int?)

    /** How an item shows its check: none, a check box, or a radio button of a single selection. */
    enum class Check { None, Box, Radio }

    /**
     * An item of the menu: [title], [icon] (tinted already), [enabled], its [check] and whether it is [checked], and
     * either [submenu] (its items, under its title) or [onPick]. [testId] goes on the item's view, [label] is its
     * accessibility label.
     */
    class Entry(
        val title: CharSequence,
        val icon: Drawable?,
        val enabled: Boolean = true,
        val check: Check = Check.None,
        val checked: Boolean = false,
        val submenu: List<Entry>? = null,
        val testId: String? = null,
        val label: CharSequence? = null,
        val onPick: () -> Unit = {},
    )

    private var popup: ListPopupWindow? = null
    // The level that shows is the top one, not a submenu.
    private var atTop = false
    // The text color of the options, dimmed as Material 3 dims a disabled item.
    private val itemTextColors = colors.text?.let {
        ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(ColorUtils.setAlphaComponent(it, DISABLED_ALPHA), it),
        )
    }
    // The menu goes from one level to the next: the level that closes doesn't close the menu.
    private var switching = false

    /** The menu closed: picked, dismissed, or taken away. */
    var onDismiss: (() -> Unit)? = null

    /** The menu shows. */
    val isShowing: Boolean
        get() = popup?.isShowing == true

    /** Shows [entries], the top level, with their icons when [icons]. */
    fun show(entries: List<Entry>, icons: Boolean = entries.any { it.icon != null }) =
        showLevel(entries, null, icons)

    /** The top level, while it shows, holds [entries] now; a submenu that shows stays. */
    fun update(entries: List<Entry>, icons: Boolean = entries.any { it.icon != null }) {
        val popup = popup?.takeIf { atTop && it.isShowing } ?: return
        val adapter = Adapter(entries, icons)
        popup.setAdapter(adapter)
        popup.setContentWidth(contentWidth(adapter))
        popup.show()
    }

    /** Closes the menu. */
    fun dismiss() {
        popup?.dismiss()
    }

    private fun showLevel(entries: List<Entry>, title: CharSequence?, icons: Boolean) {
        val adapter = Adapter(entries, icons)
        val popup = ListPopupWindow(context, null, styleAttr)
        popup.anchorView = anchor
        popup.setDropDownGravity(gravity)
        popup.isModal = true
        // As AppCompat's menus: the menu goes over an open keyboard, which stays.
        popup.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        popup.setAdapter(adapter)
        popup.setContentWidth(contentWidth(adapter))
        colors.background?.let { color ->
            val background = popup.background?.mutate()
            if (background != null) {
                background.setTint(color)
                popup.setBackgroundDrawable(background)
            } else {
                popup.setBackgroundDrawable(ColorDrawable(color))
            }
        }
        popup.setOnItemClickListener { parent, _, position, _ ->
            val entry = parent.adapter.getItem(position) as? Entry ?: return@setOnItemClickListener
            val submenu = entry.submenu
            if (submenu != null) {
                // The submenu shows where the menu was, as AppCompat shows it.
                switching = true
                popup.dismiss()
                switching = false
                showLevel(submenu, entry.title, submenu.any { it.icon != null })
            } else {
                popup.dismiss()
                entry.onPick()
            }
        }
        popup.setOnDismissListener {
            if (this.popup === popup) this.popup = null
            if (!switching) onDismiss?.invoke()
        }
        this.popup = popup
        atTop = title == null
        popup.show()
        if (title != null) {
            // The list exists once the popup shows; a header added then shows at once.
            popup.listView?.let { list ->
                list.addHeaderView(header(title, list), null, false)
                popup.show()
            }
        }
    }

    // As wide as the widest item, at most half the screen or 320 dp (AppCompat's rule for its menus).
    private fun contentWidth(adapter: Adapter): Int {
        val parent = FrameLayout(context)
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val metrics = context.resources.displayMetrics
        val most = max(metrics.widthPixels / 2, (MAX_WIDTH_DP * metrics.density).toInt())
        var width = 0
        for (index in 0 until adapter.count) {
            val view = adapter.getView(index, null, parent)
            view.measure(spec, spec)
            width = max(width, view.measuredWidth)
            if (width >= most) return most
        }
        return width
    }

    private fun header(title: CharSequence, parent: ViewGroup): View {
        val view = LayoutInflater.from(context).inflate(R.layout.apex_navigation_menu_header, parent, false)
        view.findViewById<TextView>(R.id.apex_navigation_menu_title).apply {
            text = title
            itemTextColors?.let(::setTextColor)
        }
        return view
    }

    // The icons show when [icons], as in AppCompat's menus with optional icons shown.
    private inner class Adapter(private val entries: List<Entry>, private val icons: Boolean) : BaseAdapter() {
        override fun getCount(): Int = entries.size

        override fun getItem(position: Int): Entry = entries[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun areAllItemsEnabled(): Boolean = false

        override fun isEnabled(position: Int): Boolean = entries[position].enabled

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(context).inflate(R.layout.apex_navigation_menu_item, parent, false)
            val entry = entries[position]
            view.isEnabled = entry.enabled
            view.findViewById<TextView>(R.id.apex_navigation_menu_title).apply {
                text = entry.title
                itemTextColors?.let(::setTextColor)
            }
            view.findViewById<ImageView>(R.id.apex_navigation_menu_icon).apply {
                setImageDrawable(entry.icon)
                visibility = if (icons && entry.icon != null) View.VISIBLE else View.GONE
            }
            view.findViewById<ImageView>(R.id.apex_navigation_menu_arrow).apply {
                visibility = if (entry.submenu != null) View.VISIBLE else View.GONE
                ImageViewCompat.setImageTintList(this, colors.text?.let(ColorStateList::valueOf))
            }
            check(view.findViewById(R.id.apex_navigation_menu_check), entry, Check.Box)
            check(view.findViewById(R.id.apex_navigation_menu_radio), entry, Check.Radio)
            view.contentDescription = entry.label
            TestIds.apply(view, entry.testId)
            return view
        }

        private fun check(button: CompoundButton, entry: Entry, kind: Check) {
            val shown = entry.check == kind
            button.visibility = if (shown) View.VISIBLE else View.GONE
            if (shown) button.isChecked = entry.checked
        }
    }

    private companion object {
        // AppCompat's `abc_config_prefDialogWidth`.
        const val MAX_WIDTH_DP = 320
        const val DISABLED_ALPHA = 97
    }
}

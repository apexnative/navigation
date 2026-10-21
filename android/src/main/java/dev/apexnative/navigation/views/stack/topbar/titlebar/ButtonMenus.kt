package dev.apexnative.navigation.views.stack.topbar.titlebar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.menu.MenuBuilder
import com.google.android.material.color.MaterialColors
import dev.apexnative.navigation.options.ButtonMenuOptions
import dev.apexnative.navigation.utils.MaterialTheme
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * Puts the items of a bar menu (the overflow menu, the menu of a button) into an Android menu: their titles and icons,
 * the destructive ones red, the checked ones with a checkmark. A pick of an item calls `onPick` with its id; with
 * `singleSelection` the checkmark moves to it in the open menu, and the caller writes it into the screen's options.
 */
object ButtonMenus {
    fun fill(
        context: Context,
        menu: Menu,
        group: Int,
        firstOrder: Int,
        options: ButtonMenuOptions,
        showAsAction: Int?,
        loadIcon: (String) -> Drawable?,
        onPick: (String) -> Unit,
    ) {
        addElements(context, menu, group, firstOrder, options.items, options.singleSelection, showAsAction, loadIcon, onPick)
    }

    private fun addElements(
        context: Context,
        menu: Menu,
        group: Int,
        firstOrder: Int,
        elements: List<ButtonMenuOptions.Element>,
        singleSelection: Boolean,
        showAsAction: Int?,
        loadIcon: (String) -> Drawable?,
        onPick: (String) -> Unit,
    ) {
        val submenus = mutableListOf<MenuItem>()
        elements.forEachIndexed { index, element ->
            val order = firstOrder + index
            val item = when (element) {
                is ButtonMenuOptions.Submenu -> menu.addSubMenu(group, Menu.NONE, order, element.title).also {
                    addElements(context, it, group, 0, element.items, element.singleSelection, null, loadIcon, onPick)
                }.item.also { submenus += it }
                is ButtonMenuOptions.Item -> menu.add(group, Menu.NONE, order, title(context, element)).apply {
                    isEnabled = !element.disabled
                    if (singleSelection || element.checked) {
                        isCheckable = true
                        isChecked = element.checked
                    }
                    setOnMenuItemClickListener { picked ->
                        if (singleSelection) picked.isChecked = true
                        onPick(element.id)
                        true
                    }
                }
            }
            val destructive = element is ButtonMenuOptions.Item && element.destructive
            val tint = if (destructive) errorColor(context) else iconColor(context)
            element.icon?.let { uri -> item.icon = loadIcon(uri)?.mutate()?.also { it.setTint(tint) } }
            if (showAsAction != null) item.setShowAsAction(showAsAction)
        }
        if (singleSelection) {
            menu.setGroupCheckable(group, true, true)
            // The group made the submenus checkable too: they open, and have no checkmark.
            submenus.forEach { it.isCheckable = false }
        }
        if (elements.any { it.icon != null }) showIcons(menu)
    }

    /** Some item of the menu, or of a submenu, has an icon. */
    fun hasIcons(options: ButtonMenuOptions): Boolean = hasIcons(options.items)

    private fun hasIcons(elements: List<ButtonMenuOptions.Element>): Boolean =
        elements.any { it.icon != null || (it is ButtonMenuOptions.Submenu && hasIcons(it.items)) }

    // Material's menus have their icons at the start of the items; the toolbar's menus leave them out by default.
    @SuppressLint("RestrictedApi")
    private fun showIcons(menu: Menu) {
        (menu as? MenuBuilder)?.setOptionalIconsVisible(true)
    }

    private fun title(context: Context, item: ButtonMenuOptions.Item): CharSequence {
        if (!item.destructive) return item.title
        return SpannableString(item.title).apply {
            setSpan(ForegroundColorSpan(errorColor(context)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun errorColor(context: Context) =
        MaterialColors.getColor(MaterialTheme.context(context), AppCompatR.attr.colorError, Color.RED)

    private fun iconColor(context: Context) =
        MaterialColors.getColor(MaterialTheme.context(context), MaterialR.attr.colorOnSurfaceVariant, Color.GRAY)
}

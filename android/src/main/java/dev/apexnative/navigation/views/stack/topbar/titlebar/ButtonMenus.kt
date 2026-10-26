package dev.apexnative.navigation.views.stack.topbar.titlebar

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.google.android.material.color.MaterialColors
import dev.apexnative.navigation.options.ButtonMenuOptions
import dev.apexnative.navigation.utils.MaterialTheme
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * Turns the items of a bar menu (the overflow menu, the menu of a button) into the entries of a [BarMenu]: their
 * titles and icons, the destructive ones red, the checked ones with a check mark, the items of a `singleSelection` menu
 * with radio buttons. Icons take `textColor` (`topBar.menuTextColor`), else the theme's on-surface-variant color. A
 * pick of an item calls `onPick` with its id; the caller writes a `singleSelection` choice into the screen's options.
 */
internal object ButtonMenus {
    fun entries(
        context: Context,
        options: ButtonMenuOptions,
        textColor: Int?,
        loadIcon: (String) -> Drawable?,
        onPick: (String) -> Unit,
    ): List<BarMenu.Entry> = entriesOf(context, options.items, options.singleSelection, textColor, loadIcon, onPick)

    private fun entriesOf(
        context: Context,
        elements: List<ButtonMenuOptions.Element>,
        singleSelection: Boolean,
        textColor: Int?,
        loadIcon: (String) -> Drawable?,
        onPick: (String) -> Unit,
    ): List<BarMenu.Entry> = elements.map { element ->
        val destructive = element is ButtonMenuOptions.Item && element.destructive
        val tint = if (destructive) errorColor(context) else textColor ?: iconColor(context)
        val icon = element.icon?.let(loadIcon)?.mutate()?.also { it.setTint(tint) }
        when (element) {
            is ButtonMenuOptions.Submenu -> BarMenu.Entry(
                element.title,
                icon,
                submenu = entriesOf(context, element.items, element.singleSelection, textColor, loadIcon, onPick),
                testId = element.testID,
            )
            is ButtonMenuOptions.Item -> BarMenu.Entry(
                title(context, element),
                icon,
                enabled = !element.disabled,
                check = when {
                    singleSelection -> BarMenu.Check.Radio
                    element.checked -> BarMenu.Check.Box
                    else -> BarMenu.Check.None
                },
                checked = element.checked,
                testId = element.testID,
                onPick = { onPick(element.id) },
            )
        }
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

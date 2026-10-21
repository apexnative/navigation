package dev.apexnative.navigation.viewcontrollers.bottomtabs

import dev.apexnative.navigation.options.BottomTabOptions
import dev.apexnative.navigation.options.DotIndicatorOptions
import dev.apexnative.navigation.options.FontOptions
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.views.bottomtabs.BottomTabs

/**
 * What the items of the tab bar show, from the resolved `bottomTab` of each tab (`bottomTab`): the calls to the
 * module's bar ([Call]), without views. At creation every field; at a merge only the fields it names, with their
 * resolved values (a `badgeColor`, `fontSize` or `selectedFontSize` alone works); at the repaint the colors again. The
 * badge text and the dot an item shows change only at creation or by a merge that carries them: a repaint, or a merge
 * of another field, keeps them. [color] reads a color of the current mode (`'NoColor'` and unset: none, the style's
 * color). Main thread only.
 */
internal class TabItems(private val color: (ThemeColour) -> Int?) {
    /** One call to the module's bar for the item at [index]. */
    sealed class Call {
        abstract val index: Int

        data class Title(override val index: Int, val text: String) : Call()

        data class Icon(override val index: Int, val uri: String) : Call()

        data class SelectedIcon(override val index: Int, val uri: String) : Call()

        /** The colors over the bar's style: none is the style's. */
        data class Colors(
            override val index: Int,
            val iconActive: Int?,
            val iconInactive: Int?,
            val titleActive: Int?,
            val titleInactive: Int?,
        ) : Call()

        /** The icon's size in dp, width over height. */
        data class IconSize(override val index: Int, val width: Int?, val height: Int?) : Call()

        data class Font(override val index: Int, val font: FontOptions) : Call()

        /** The titles' sizes in sp: selected ([active]) and the others. */
        data class FontSizes(override val index: Int, val active: Float?, val inactive: Float?) : Call()

        data class TestId(override val index: Int, val testId: String?) : Call()

        data class TintDisabled(override val index: Int, val unselected: Boolean, val selected: Boolean) : Call()

        /**
         * The badge with [text] in [color] (none: the theme's), which takes the place of the dot; without a text the
         * dot, when [dot], in [dotColor].
         */
        data class Badge(
            override val index: Int,
            val text: String,
            val color: Int?,
            val dot: Boolean,
            val dotColor: Int?,
        ) : Call()
    }

    // What each item shows of its badge and its dot.
    private val badges = HashMap<Int, String>()
    private val dots = HashMap<Int, DotIndicatorOptions>()

    /** The badge text the item at [index] shows; empty without one. */
    fun badgeOf(index: Int): String = badges[index].orEmpty()

    /** The dot of the item at [index] as it shows: visible, and its color. */
    fun dotOf(index: Int): DotIndicatorOptions = dots[index] ?: DotIndicatorOptions()

    /** Every field of the item at [index], at its creation. */
    fun create(index: Int, options: BottomTabOptions): List<Call> {
        badges[index] = badgeText(options.badge)
        dots[index] = options.dotIndicator.copy()
        return look(index, options) + badge(index, options)
    }

    /**
     * The fields of the item at [index] but its badge and dot, from [options]: as a screen of the tab appears, the
     * look follows its options.
     */
    fun look(index: Int, options: BottomTabOptions): List<Call> = listOfNotNull(
        Call.Title(index, options.text.get("")),
        options.icon.get(null)?.let { Call.Icon(index, it) },
        options.selectedIcon.get(null)?.let { Call.SelectedIcon(index, it) },
        colors(index, options),
        Call.IconSize(index, options.iconWidth.get(null)?.toInt(), options.iconHeight.get(null)?.toInt()),
        Call.Font(index, options.font.copy()),
        Call.FontSizes(index, options.selectedFontSize.get(null)?.toFloat(), options.fontSize.get(null)?.toFloat()),
        Call.TestId(index, options.testID.get(null)),
        Call.TintDisabled(index, options.disableIconTint.isTrue, options.disableSelectedIconTint.isTrue),
    )

    /**
     * A `mergeOptions` of [merge] into the tab at [index] or a screen in it: the fields it names, with the values of
     * [resolved] (the tab's resolved `bottomTab` with [merge] over it).
     */
    fun merge(index: Int, merge: BottomTabOptions, resolved: BottomTabOptions): List<Call> {
        val calls = ArrayList<Call>()
        if (merge.text.hasValue()) calls += Call.Title(index, resolved.text.get(""))
        if (merge.icon.hasValue()) resolved.icon.get(null)?.let { calls += Call.Icon(index, it) }
        if (merge.selectedIcon.hasValue()) {
            resolved.selectedIcon.get(null)?.let { calls += Call.SelectedIcon(index, it) }
        }
        if (merge.iconColor.hasValue() || merge.selectedIconColor.hasValue() || merge.textColor.hasValue() ||
            merge.selectedTextColor.hasValue()
        ) {
            calls += colors(index, resolved)
        }
        if (merge.iconWidth.hasValue() || merge.iconHeight.hasValue()) {
            calls += Call.IconSize(index, resolved.iconWidth.get(null)?.toInt(), resolved.iconHeight.get(null)?.toInt())
        }
        if (merge.font.hasValue()) calls += Call.Font(index, resolved.font.copy())
        if (merge.fontSize.hasValue() || merge.selectedFontSize.hasValue()) {
            val active = resolved.selectedFontSize.get(null)?.toFloat()
            calls += Call.FontSizes(index, active, resolved.fontSize.get(null)?.toFloat())
        }
        if (merge.testID.hasValue()) calls += Call.TestId(index, resolved.testID.get(null))
        if (merge.disableIconTint.hasValue() || merge.disableSelectedIconTint.hasValue()) {
            calls += Call.TintDisabled(index, resolved.disableIconTint.isTrue, resolved.disableSelectedIconTint.isTrue)
        }
        if (merge.badge.hasValue()) badges[index] = badgeText(resolved.badge)
        if (merge.dotIndicator.hasValue()) dots[index] = resolved.dotIndicator.copy()
        if (merge.badge.hasValue() || merge.badgeColor.hasValue() || merge.dotIndicator.hasValue()) {
            calls += badge(index, resolved)
        }
        return calls
    }

    /**
     * Light or dark changed, or new default options: the colors of the item at [index] from [options], its badge and
     * dot as they show, the badge's color following the options.
     */
    fun repaint(index: Int, options: BottomTabOptions): List<Call> =
        listOf(colors(index, options), badge(index, options))

    // `selectedIconColor` defaults to `iconColor` (`bottomTab.selectedIconColor`).
    private fun colors(index: Int, options: BottomTabOptions): Call.Colors {
        val icon = color(options.iconColor)
        return Call.Colors(
            index = index,
            iconActive = color(options.selectedIconColor) ?: icon,
            iconInactive = icon,
            titleActive = color(options.selectedTextColor),
            titleInactive = color(options.textColor),
        )
    }

    private fun badge(index: Int, options: BottomTabOptions): Call.Badge {
        val dot = dotOf(index)
        return Call.Badge(index, badgeOf(index), color(options.badgeColor), dot.visible.isTrue, color(dot.color))
    }

    companion object {
        /** The badge's text: `''` and `null` take it away. */
        fun badgeText(badge: Text): String = if (badge.isNone) "" else badge.get("")

        /**
         * The titles' mode of `bottomTabs.titleDisplayMode`: unset, Material's automatic mode when some tab has an icon
         * ([anyIcon]), else every title.
         */
        fun titleState(mode: Text, anyIcon: Boolean): BottomTabs.TitleState = when (mode.get(null)) {
            "alwaysShow" -> BottomTabs.TitleState.ALWAYS_SHOW
            "showWhenActive" -> BottomTabs.TitleState.SHOW_WHEN_ACTIVE
            "showWhenActiveForce" -> BottomTabs.TitleState.SHOW_WHEN_ACTIVE_FORCE
            "alwaysHide" -> BottomTabs.TitleState.ALWAYS_HIDE
            else -> if (anyIcon) BottomTabs.TitleState.AUTO else BottomTabs.TitleState.ALWAYS_SHOW
        }
    }
}

package dev.apexnative.navigation.options

import android.content.Context
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullFraction
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.NullThemeColour
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.FontParser
import dev.apexnative.navigation.options.parsers.FractionParser
import dev.apexnative.navigation.options.parsers.IconParser
import dev.apexnative.navigation.options.parsers.TextParser

/** `bottomTab.dotIndicator` (`DotIndicatorOptions`): the dot on a tab's icon. */
class DotIndicatorOptions {
    @JvmField var color: ThemeColour = NullThemeColour()
    @JvmField var size: Fraction = NullFraction()
    @JvmField var visible: Bool = NullBool()

    fun hasValue(): Boolean = color.hasValue() || size.hasValue() || visible.hasValue()

    fun mergeWith(other: DotIndicatorOptions) {
        color = color.mergeWith(other.color)
        if (other.size.hasValue()) size = other.size
        if (other.visible.hasValue()) visible = other.visible
    }

    fun mergeWithDefault(defaults: DotIndicatorOptions) {
        color = color.withDefault(defaults.color)
        if (!size.hasValue()) size = defaults.size
        if (!visible.hasValue()) visible = defaults.visible
    }

    fun copy() = DotIndicatorOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): DotIndicatorOptions {
            val options = DotIndicatorOptions()
            if (map == null) return options
            options.color = ThemeColour.parse(context, map, "color")
            options.size = FractionParser.parse(map, "size")
            options.visible = BoolParser.parse(map, "visible")
            return options
        }
    }
}

/**
 * `bottomTab.component` (`OptionsBottomTabComponent`): a React component in place of a tab's icon and text. Its props
 * stay in JavaScript; `componentId` is the id the engine made it with.
 */
class BottomTabComponentOptions {
    @JvmField var name: Text = NullText()
    @JvmField var componentId: Text = NullText()

    fun hasValue(): Boolean = name.hasValue() || componentId.hasValue()

    fun mergeWith(other: BottomTabComponentOptions) {
        if (other.name.hasValue()) name = other.name
        if (other.componentId.hasValue()) componentId = other.componentId
    }

    fun mergeWithDefault(defaults: BottomTabComponentOptions) {
        if (!name.hasValue()) name = defaults.name
        if (!componentId.hasValue()) componentId = defaults.componentId
    }

    fun copy() = BottomTabComponentOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): BottomTabComponentOptions {
            val options = BottomTabComponentOptions()
            if (map == null) return options
            options.name = TextParser.parse(map, "name")
            options.componentId = TextParser.parse(map, "componentId")
            return options
        }
    }
}

/**
 * `bottomTab` (`OptionsBottomTab`): one tab of a tabs layout, its text, icon, badge and look; the sizes are in dp,
 * `fontSize` and `selectedFontSize` in sp.
 */
class BottomTabOptions {
    @JvmField var dotIndicator = DotIndicatorOptions()
    @JvmField var component = BottomTabComponentOptions()
    @JvmField var text: Text = NullText()
    /** `string | null`; `null` takes the badge away ([Text.isNone]). */
    @JvmField var badge: Text = NullText()
    @JvmField var badgeColor: ThemeColour = NullThemeColour()
    @JvmField var animateBadge: Bool = NullBool()
    @JvmField var testID: Text = NullText()
    @JvmField var icon: Text = NullText()
    @JvmField var iconColor: ThemeColour = NullThemeColour()
    @JvmField var iconWidth: Fraction = NullFraction()
    @JvmField var iconHeight: Fraction = NullFraction()
    @JvmField var textColor: ThemeColour = NullThemeColour()
    @JvmField var selectedIconColor: ThemeColour = NullThemeColour()
    @JvmField var selectedTextColor: ThemeColour = NullThemeColour()
    @JvmField var font = FontOptions()
    @JvmField var fontSize: Fraction = NullFraction()
    @JvmField var selectedFontSize: Fraction = NullFraction()
    @JvmField var iconInsets = IconInsetsOptions()
    @JvmField var selectedIcon: Text = NullText()
    @JvmField var disableIconTint: Bool = NullBool()
    @JvmField var disableSelectedIconTint: Bool = NullBool()
    @JvmField var selectTabOnPress: Bool = NullBool()
    @JvmField var popToRootOnPress: Bool = NullBool()
    @JvmField var popToRootOnBlur: Bool = NullBool()
    @JvmField var role: Text = NullText()
    @JvmField var customization: Text = NullText()
    @JvmField var shownByDefault: Bool = NullBool()
    @JvmField var allowsHiding: Bool = NullBool()

    fun hasValue(): Boolean = dotIndicator.hasValue() || component.hasValue() || text.hasValue() || badge.hasValue() ||
        badgeColor.hasValue() || animateBadge.hasValue() || testID.hasValue() || icon.hasValue() ||
        iconColor.hasValue() || iconWidth.hasValue() || iconHeight.hasValue() || textColor.hasValue() ||
        selectedIconColor.hasValue() || selectedTextColor.hasValue() || font.hasValue() || fontSize.hasValue() ||
        selectedFontSize.hasValue() || iconInsets.hasValue() || selectedIcon.hasValue() || disableIconTint.hasValue() ||
        disableSelectedIconTint.hasValue() || selectTabOnPress.hasValue() || popToRootOnPress.hasValue() ||
        popToRootOnBlur.hasValue() || role.hasValue() || customization.hasValue() || shownByDefault.hasValue() ||
        allowsHiding.hasValue()

    fun mergeWith(other: BottomTabOptions) {
        dotIndicator.mergeWith(other.dotIndicator)
        component.mergeWith(other.component)
        if (other.text.hasValue()) text = other.text
        if (other.badge.hasValue()) badge = other.badge
        badgeColor = badgeColor.mergeWith(other.badgeColor)
        if (other.animateBadge.hasValue()) animateBadge = other.animateBadge
        if (other.testID.hasValue()) testID = other.testID
        if (other.icon.hasValue()) icon = other.icon
        iconColor = iconColor.mergeWith(other.iconColor)
        if (other.iconWidth.hasValue()) iconWidth = other.iconWidth
        if (other.iconHeight.hasValue()) iconHeight = other.iconHeight
        textColor = textColor.mergeWith(other.textColor)
        selectedIconColor = selectedIconColor.mergeWith(other.selectedIconColor)
        selectedTextColor = selectedTextColor.mergeWith(other.selectedTextColor)
        font.mergeWith(other.font)
        if (other.fontSize.hasValue()) fontSize = other.fontSize
        if (other.selectedFontSize.hasValue()) selectedFontSize = other.selectedFontSize
        iconInsets.mergeWith(other.iconInsets)
        if (other.selectedIcon.hasValue()) selectedIcon = other.selectedIcon
        if (other.disableIconTint.hasValue()) disableIconTint = other.disableIconTint
        if (other.disableSelectedIconTint.hasValue()) disableSelectedIconTint = other.disableSelectedIconTint
        if (other.selectTabOnPress.hasValue()) selectTabOnPress = other.selectTabOnPress
        if (other.popToRootOnPress.hasValue()) popToRootOnPress = other.popToRootOnPress
        if (other.popToRootOnBlur.hasValue()) popToRootOnBlur = other.popToRootOnBlur
        if (other.role.hasValue()) role = other.role
        if (other.customization.hasValue()) customization = other.customization
        if (other.shownByDefault.hasValue()) shownByDefault = other.shownByDefault
        if (other.allowsHiding.hasValue()) allowsHiding = other.allowsHiding
    }

    fun mergeWithDefault(defaults: BottomTabOptions) {
        dotIndicator.mergeWithDefault(defaults.dotIndicator)
        component.mergeWithDefault(defaults.component)
        if (!text.hasValue()) text = defaults.text
        if (!badge.hasValue()) badge = defaults.badge
        badgeColor = badgeColor.withDefault(defaults.badgeColor)
        if (!animateBadge.hasValue()) animateBadge = defaults.animateBadge
        if (!testID.hasValue()) testID = defaults.testID
        if (!icon.hasValue()) icon = defaults.icon
        iconColor = iconColor.withDefault(defaults.iconColor)
        if (!iconWidth.hasValue()) iconWidth = defaults.iconWidth
        if (!iconHeight.hasValue()) iconHeight = defaults.iconHeight
        textColor = textColor.withDefault(defaults.textColor)
        selectedIconColor = selectedIconColor.withDefault(defaults.selectedIconColor)
        selectedTextColor = selectedTextColor.withDefault(defaults.selectedTextColor)
        font.mergeWithDefault(defaults.font)
        if (!fontSize.hasValue()) fontSize = defaults.fontSize
        if (!selectedFontSize.hasValue()) selectedFontSize = defaults.selectedFontSize
        iconInsets.mergeWithDefault(defaults.iconInsets)
        if (!selectedIcon.hasValue()) selectedIcon = defaults.selectedIcon
        if (!disableIconTint.hasValue()) disableIconTint = defaults.disableIconTint
        if (!disableSelectedIconTint.hasValue()) disableSelectedIconTint = defaults.disableSelectedIconTint
        if (!selectTabOnPress.hasValue()) selectTabOnPress = defaults.selectTabOnPress
        if (!popToRootOnPress.hasValue()) popToRootOnPress = defaults.popToRootOnPress
        if (!popToRootOnBlur.hasValue()) popToRootOnBlur = defaults.popToRootOnBlur
        if (!role.hasValue()) role = defaults.role
        if (!customization.hasValue()) customization = defaults.customization
        if (!shownByDefault.hasValue()) shownByDefault = defaults.shownByDefault
        if (!allowsHiding.hasValue()) allowsHiding = defaults.allowsHiding
    }

    fun copy() = BottomTabOptions().also { it.mergeWith(this) }

    companion object {
        private val ROLES = setOf(
            "search", "bookmarks", "contacts", "downloads", "favorites", "featured", "history", "more", "mostRecent",
            "mostViewed", "recents", "topRated",
        )
        private val CUSTOMIZATIONS = setOf("fixed", "movable", "optional", "pinned", "sidebarOnly")

        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): BottomTabOptions {
            val options = BottomTabOptions()
            if (map == null) return options
            options.dotIndicator = map.group("dotIndicator") { DotIndicatorOptions.parse(context, it) }
            options.component = map.group("component") { BottomTabComponentOptions.parse(it) }
            options.text = TextParser.parse(map, "text")
            options.badge = TextParser.parse(map, "badge")
            options.badgeColor = ThemeColour.parse(context, map, "badgeColor")
            options.animateBadge = BoolParser.parse(map, "animateBadge")
            options.testID = TextParser.parse(map, "testID")
            options.icon = IconParser.parse(map, "icon")
            options.iconColor = ThemeColour.parse(context, map, "iconColor")
            options.iconWidth = FractionParser.parse(map, "iconWidth")
            options.iconHeight = FractionParser.parse(map, "iconHeight")
            options.textColor = ThemeColour.parse(context, map, "textColor")
            options.selectedIconColor = ThemeColour.parse(context, map, "selectedIconColor")
            options.selectedTextColor = ThemeColour.parse(context, map, "selectedTextColor")
            options.font = FontParser.parse(map)
            options.fontSize = FractionParser.parse(map, "fontSize")
            options.selectedFontSize = FractionParser.parse(map, "selectedFontSize")
            options.iconInsets = map.group("iconInsets") { IconInsetsOptions.parse(it) }
            options.selectedIcon = IconParser.parse(map, "selectedIcon")
            options.disableIconTint = BoolParser.parse(map, "disableIconTint")
            options.disableSelectedIconTint = BoolParser.parse(map, "disableSelectedIconTint")
            options.selectTabOnPress = BoolParser.parse(map, "selectTabOnPress")
            options.popToRootOnPress = BoolParser.parse(map, "popToRootOnPress")
            options.popToRootOnBlur = BoolParser.parse(map, "popToRootOnBlur")
            options.role = TextParser.parse(map, "role", ROLES)
            options.customization = TextParser.parse(map, "customization", CUSTOMIZATIONS)
            options.shownByDefault = BoolParser.parse(map, "shownByDefault")
            options.allowsHiding = BoolParser.parse(map, "allowsHiding")
            return options
        }
    }
}

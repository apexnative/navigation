package dev.apexnative.navigation.options

import android.content.Context
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullFraction
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.NullThemeColour
import dev.apexnative.navigation.options.params.NullVariant
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.options.params.Variant
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.FontParser
import dev.apexnative.navigation.options.parsers.FractionParser
import dev.apexnative.navigation.options.parsers.IconParser
import dev.apexnative.navigation.options.parsers.TextParser
import dev.apexnative.navigation.options.parsers.VariantParser

/**
 * A button of the top bar (`topBar.leftButtons[]`, `rightButtons[]`, `centerButtons[]`): its `id` names it in the
 * events. A list of buttons replaces the one below whole.
 */
class ButtonOptions(@JvmField var id: Text = NullText()) {
    @JvmField var allCaps: Bool = NullBool()
    @JvmField var icon: Text = NullText()
    @JvmField var iconInsets = IconInsetsOptions()
    @JvmField var component = ButtonComponentOptions()
    @JvmField var systemItem: Text = NullText()
    @JvmField var text: Text = NullText()
    @JvmField var accessibilityLabel: Text = NullText()
    @JvmField var font = FontOptions()
    @JvmField var fontSize: Fraction = NullFraction()
    @JvmField var enabled: Bool = NullBool()
    @JvmField var disableIconTint: Bool = NullBool()
    @JvmField var color: ThemeColour = NullThemeColour()
    @JvmField var disabledColor: ThemeColour = NullThemeColour()
    @JvmField var iconBackground = IconBackgroundOptions()
    @JvmField var style: Text = NullText()
    @JvmField var testID: Text = NullText()
    @JvmField var hideSharedBackground: Bool = NullBool()
    @JvmField var showText: Bool = NullBool()
    @JvmField var group: Text = NullText()
    /** `number | string | boolean`; `null` takes the badge away ([Variant.isNone]). */
    @JvmField var badge: Variant = NullVariant()
    @JvmField var badgeColor: ThemeColour = NullThemeColour()
    @JvmField var badgeTextColor: ThemeColour = NullThemeColour()
    @JvmField var toggle: Bool = NullBool()
    @JvmField var selected: Bool = NullBool()
    /** Shared between copies, as its module wants: the checkmark a pick moves stays. */
    @JvmField var menu: ButtonMenuOptions? = null
    @JvmField var menuOnLongPress: Bool = NullBool()
    @JvmField var customization: Text = NullText()
    @JvmField var shownByDefault: Bool = NullBool()
    @JvmField var pinned: Bool = NullBool()
    @JvmField var priority: Text = NullText()
    @JvmField var vertical: Bool = NullBool()

    fun hasValue(): Boolean = id.hasValue() || allCaps.hasValue() || icon.hasValue() || iconInsets.hasValue() ||
        component.hasValue() || systemItem.hasValue() || text.hasValue() || accessibilityLabel.hasValue() ||
        font.hasValue() || fontSize.hasValue() || enabled.hasValue() || disableIconTint.hasValue() ||
        color.hasValue() || disabledColor.hasValue() || iconBackground.hasValue() || style.hasValue() ||
        testID.hasValue() || hideSharedBackground.hasValue() || showText.hasValue() || group.hasValue() ||
        badge.hasValue() || badgeColor.hasValue() || badgeTextColor.hasValue() || toggle.hasValue() ||
        selected.hasValue() || menu != null || menuOnLongPress.hasValue() || customization.hasValue() ||
        shownByDefault.hasValue() || pinned.hasValue() || priority.hasValue() || vertical.hasValue()

    fun mergeWith(other: ButtonOptions) {
        if (other.id.hasValue()) id = other.id
        if (other.allCaps.hasValue()) allCaps = other.allCaps
        if (other.icon.hasValue()) icon = other.icon
        iconInsets.mergeWith(other.iconInsets)
        component.mergeWith(other.component)
        if (other.systemItem.hasValue()) systemItem = other.systemItem
        if (other.text.hasValue()) text = other.text
        if (other.accessibilityLabel.hasValue()) accessibilityLabel = other.accessibilityLabel
        font.mergeWith(other.font)
        if (other.fontSize.hasValue()) fontSize = other.fontSize
        if (other.enabled.hasValue()) enabled = other.enabled
        if (other.disableIconTint.hasValue()) disableIconTint = other.disableIconTint
        color = color.mergeWith(other.color)
        disabledColor = disabledColor.mergeWith(other.disabledColor)
        iconBackground.mergeWith(other.iconBackground)
        if (other.style.hasValue()) style = other.style
        if (other.testID.hasValue()) testID = other.testID
        if (other.hideSharedBackground.hasValue()) hideSharedBackground = other.hideSharedBackground
        if (other.showText.hasValue()) showText = other.showText
        if (other.group.hasValue()) group = other.group
        if (other.badge.hasValue()) badge = other.badge
        badgeColor = badgeColor.mergeWith(other.badgeColor)
        badgeTextColor = badgeTextColor.mergeWith(other.badgeTextColor)
        if (other.toggle.hasValue()) toggle = other.toggle
        if (other.selected.hasValue()) selected = other.selected
        other.menu?.let { menu = it }
        if (other.menuOnLongPress.hasValue()) menuOnLongPress = other.menuOnLongPress
        if (other.customization.hasValue()) customization = other.customization
        if (other.shownByDefault.hasValue()) shownByDefault = other.shownByDefault
        if (other.pinned.hasValue()) pinned = other.pinned
        if (other.priority.hasValue()) priority = other.priority
        if (other.vertical.hasValue()) vertical = other.vertical
    }

    fun mergeWithDefault(defaults: ButtonOptions) {
        if (!id.hasValue()) id = defaults.id
        if (!allCaps.hasValue()) allCaps = defaults.allCaps
        if (!icon.hasValue()) icon = defaults.icon
        iconInsets.mergeWithDefault(defaults.iconInsets)
        component.mergeWithDefault(defaults.component)
        if (!systemItem.hasValue()) systemItem = defaults.systemItem
        if (!text.hasValue()) text = defaults.text
        if (!accessibilityLabel.hasValue()) accessibilityLabel = defaults.accessibilityLabel
        font.mergeWithDefault(defaults.font)
        if (!fontSize.hasValue()) fontSize = defaults.fontSize
        if (!enabled.hasValue()) enabled = defaults.enabled
        if (!disableIconTint.hasValue()) disableIconTint = defaults.disableIconTint
        color = color.withDefault(defaults.color)
        disabledColor = disabledColor.withDefault(defaults.disabledColor)
        iconBackground.mergeWithDefault(defaults.iconBackground)
        if (!style.hasValue()) style = defaults.style
        if (!testID.hasValue()) testID = defaults.testID
        if (!hideSharedBackground.hasValue()) hideSharedBackground = defaults.hideSharedBackground
        if (!showText.hasValue()) showText = defaults.showText
        if (!group.hasValue()) group = defaults.group
        if (!badge.hasValue()) badge = defaults.badge
        badgeColor = badgeColor.withDefault(defaults.badgeColor)
        badgeTextColor = badgeTextColor.withDefault(defaults.badgeTextColor)
        if (!toggle.hasValue()) toggle = defaults.toggle
        if (!selected.hasValue()) selected = defaults.selected
        if (menu == null) menu = defaults.menu?.copyMenu()
        if (!menuOnLongPress.hasValue()) menuOnLongPress = defaults.menuOnLongPress
        if (!customization.hasValue()) customization = defaults.customization
        if (!shownByDefault.hasValue()) shownByDefault = defaults.shownByDefault
        if (!pinned.hasValue()) pinned = defaults.pinned
        if (!priority.hasValue()) priority = defaults.priority
        if (!vertical.hasValue()) vertical = defaults.vertical
    }

    fun copy() = ButtonOptions().also { it.mergeWith(this) }

    /** A copy that has a menu of its own too: for a button taken from the default options ([copyMenu]). */
    fun copyWithMenu() = copy().also { it.menu = it.menu?.copyMenu() }

    companion object {
        /** The values of `systemItem`. */
        val SYSTEM_ITEMS = setOf(
            "done", "cancel", "edit", "save", "add", "flexibleSpace", "fixedSpace", "compose", "reply", "action",
            "organize", "bookmarks", "search", "refresh", "stop", "camera", "trash", "play", "pause", "rewind",
            "fastForward", "undo", "redo", "close",
        )
        private val STYLES = setOf("plain", "prominent")
        private val CUSTOMIZATIONS = setOf("fixed", "movable", "optional")
        private val PRIORITIES = setOf("high", "standard", "low")

        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): ButtonOptions {
            val options = ButtonOptions()
            if (map == null) return options
            options.id = TextParser.parse(map, "id")
            options.allCaps = BoolParser.parse(map, "allCaps")
            options.icon = IconParser.parse(map, "icon")
            options.iconInsets = map.group("iconInsets") { IconInsetsOptions.parse(it) }
            options.component = map.group("component") { ButtonComponentOptions.parse(it) }
            options.systemItem = TextParser.parse(map, "systemItem", SYSTEM_ITEMS)
            options.text = TextParser.parse(map, "text")
            options.accessibilityLabel = TextParser.parse(map, "accessibilityLabel")
            options.font = FontParser.parse(map)
            options.fontSize = FractionParser.parse(map, "fontSize")
            options.enabled = BoolParser.parse(map, "enabled")
            options.disableIconTint = BoolParser.parse(map, "disableIconTint")
            options.color = ThemeColour.parse(context, map, "color")
            options.disabledColor = ThemeColour.parse(context, map, "disabledColor")
            options.iconBackground = map.group("iconBackground") { IconBackgroundOptions.parse(context, it) }
            options.style = TextParser.parse(map, "style", STYLES)
            options.testID = TextParser.parse(map, "testID")
            options.hideSharedBackground = BoolParser.parse(map, "hideSharedBackground")
            options.showText = BoolParser.parse(map, "showText")
            options.group = TextParser.parse(map, "group")
            options.badge =
                VariantParser.parse(map, "badge", booleans = true, numbers = true, texts = null, noneOnNull = true)
            options.badgeColor = ThemeColour.parse(context, map, "badgeColor")
            options.badgeTextColor = ThemeColour.parse(context, map, "badgeTextColor")
            options.toggle = BoolParser.parse(map, "toggle")
            options.selected = BoolParser.parse(map, "selected")
            options.menu = map.group("menu") { ButtonMenuOptions.parse(it) }
            options.menuOnLongPress = BoolParser.parse(map, "menuOnLongPress")
            options.customization = TextParser.parse(map, "customization", CUSTOMIZATIONS)
            options.shownByDefault = BoolParser.parse(map, "shownByDefault")
            options.pinned = BoolParser.parse(map, "pinned")
            options.priority = TextParser.parse(map, "priority", PRIORITIES)
            options.vertical = BoolParser.parse(map, "vertical")
            return options
        }
    }
}

/**
 * A copy of the menu with new items and submenus and the same values. A menu taken from the default options is copied
 * first, so that nothing written into a resolved result reaches [DefaultOptions.current]; the user layer writes its
 * checks into a copy too (`UserChoices`), and copies between a node's own and resolved options share the menu.
 */
internal fun ButtonMenuOptions.copyMenu(): ButtonMenuOptions =
    ButtonMenuOptions(singleSelection, items.map(::copyElement), testID)

private fun copyElement(element: ButtonMenuOptions.Element): ButtonMenuOptions.Element = when (element) {
    is ButtonMenuOptions.Item -> ButtonMenuOptions.Item(
        element.id, element.title, element.icon, element.destructive, element.disabled, element.checked, element.testID,
    )
    is ButtonMenuOptions.Submenu -> ButtonMenuOptions.Submenu(
        element.title, element.icon, element.singleSelection, element.items.map(::copyElement), element.testID,
    )
}

/** `iconInsets`: the room around an icon, in dp; each side on its own. */
class IconInsetsOptions {
    @JvmField var top: Fraction = NullFraction()
    @JvmField var left: Fraction = NullFraction()
    @JvmField var bottom: Fraction = NullFraction()
    @JvmField var right: Fraction = NullFraction()

    fun hasValue(): Boolean = top.hasValue() || left.hasValue() || bottom.hasValue() || right.hasValue()

    fun mergeWith(other: IconInsetsOptions) {
        if (other.top.hasValue()) top = other.top
        if (other.left.hasValue()) left = other.left
        if (other.bottom.hasValue()) bottom = other.bottom
        if (other.right.hasValue()) right = other.right
    }

    fun mergeWithDefault(defaults: IconInsetsOptions) {
        if (!top.hasValue()) top = defaults.top
        if (!left.hasValue()) left = defaults.left
        if (!bottom.hasValue()) bottom = defaults.bottom
        if (!right.hasValue()) right = defaults.right
    }

    fun copy() = IconInsetsOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): IconInsetsOptions {
            val options = IconInsetsOptions()
            if (map == null) return options
            options.top = FractionParser.parse(map, "top")
            options.left = FractionParser.parse(map, "left")
            options.bottom = FractionParser.parse(map, "bottom")
            options.right = FractionParser.parse(map, "right")
            return options
        }
    }
}

/** `iconBackground`: a shape behind a button's icon, its colors and its size in dp. */
class IconBackgroundOptions {
    @JvmField var color: ThemeColour = NullThemeColour()
    @JvmField var disabledColor: ThemeColour = NullThemeColour()
    @JvmField var cornerRadius: Fraction = NullFraction()
    @JvmField var width: Fraction = NullFraction()
    @JvmField var height: Fraction = NullFraction()

    fun hasValue(): Boolean = color.hasValue() || disabledColor.hasValue() || cornerRadius.hasValue() ||
        width.hasValue() || height.hasValue()

    fun mergeWith(other: IconBackgroundOptions) {
        color = color.mergeWith(other.color)
        disabledColor = disabledColor.mergeWith(other.disabledColor)
        if (other.cornerRadius.hasValue()) cornerRadius = other.cornerRadius
        if (other.width.hasValue()) width = other.width
        if (other.height.hasValue()) height = other.height
    }

    fun mergeWithDefault(defaults: IconBackgroundOptions) {
        color = color.withDefault(defaults.color)
        disabledColor = disabledColor.withDefault(defaults.disabledColor)
        if (!cornerRadius.hasValue()) cornerRadius = defaults.cornerRadius
        if (!width.hasValue()) width = defaults.width
        if (!height.hasValue()) height = defaults.height
    }

    fun copy() = IconBackgroundOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): IconBackgroundOptions {
            val options = IconBackgroundOptions()
            if (map == null) return options
            options.color = ThemeColour.parse(context, map, "color")
            options.disabledColor = ThemeColour.parse(context, map, "disabledColor")
            options.cornerRadius = FractionParser.parse(map, "cornerRadius")
            options.width = FractionParser.parse(map, "width")
            options.height = FractionParser.parse(map, "height")
            return options
        }
    }
}

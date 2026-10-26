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

/**
 * `topBar`: the bar over the screens of a stack. The search (`searchBar`), the large title and the menus are the groups
 * of their modules, which keep their own reading and merging.
 */
class TopBarOptions {
    @JvmField var visible: Bool = NullBool()
    @JvmField var animate: Bool = NullBool()
    @JvmField var hideOnScroll: Bool = NullBool()
    @JvmField var hideOnTap: Bool = NullBool()
    @JvmField var hideOnKeyboard: Bool = NullBool()
    @JvmField var leftButtonColor: ThemeColour = NullThemeColour()
    @JvmField var rightButtonColor: ThemeColour = NullThemeColour()
    @JvmField var leftButtonBackgroundColor: ThemeColour = NullThemeColour()
    @JvmField var rightButtonBackgroundColor: ThemeColour = NullThemeColour()
    @JvmField var leftButtonDisabledColor: ThemeColour = NullThemeColour()
    @JvmField var rightButtonDisabledColor: ThemeColour = NullThemeColour()
    @JvmField var centerButtonColor: ThemeColour = NullThemeColour()
    @JvmField var centerButtonDisabledColor: ThemeColour = NullThemeColour()
    @JvmField var menuBackgroundColor: ThemeColour = NullThemeColour()
    @JvmField var menuTextColor: ThemeColour = NullThemeColour()
    @JvmField var drawBehind: Bool = NullBool()
    @JvmField var testID: Text = NullText()
    @JvmField var title = TitleOptions()
    @JvmField var subtitle = SubtitleOptions()
    /** `backButton`, `leftButtons`, `rightButtons`, `centerButtons`. */
    @JvmField var buttons = TopBarButtons()
    @JvmField var customizationId: Text = NullText()
    /** Shared between copies, as its module wants. */
    @JvmField var overflowMenu: ButtonMenuOptions? = null
    @JvmField var verticalBarKeeps: Text = NullText()
    @JvmField var background = TopBarBackgroundOptions()
    @JvmField var scrollEdgeAppearance = TopBarScrollEdgeAppearanceOptions()
    @JvmField var barStyle: Text = NullText()
    @JvmField var style: Text = NullText()
    @JvmField var noBorder: Bool = NullBool()
    @JvmField var searchBar = SearchBarOptions()
    @JvmField var largeTitle = LargeTitleOptions()
    @JvmField var height: Fraction = NullFraction()
    @JvmField var borderColor: ThemeColour = NullThemeColour()
    @JvmField var borderHeight: Fraction = NullFraction()
    @JvmField var elevation: Fraction = NullFraction()
    @JvmField var topMargin: Fraction = NullFraction()
    @JvmField var animateLeftButtons: Bool = NullBool()
    @JvmField var animateRightButtons: Bool = NullBool()

    fun hasValue(): Boolean = visible.hasValue() || animate.hasValue() || hideOnScroll.hasValue() ||
        hideOnTap.hasValue() || hideOnKeyboard.hasValue() || leftButtonColor.hasValue() ||
        rightButtonColor.hasValue() || leftButtonBackgroundColor.hasValue() || rightButtonBackgroundColor.hasValue() ||
        leftButtonDisabledColor.hasValue() || rightButtonDisabledColor.hasValue() || centerButtonColor.hasValue() ||
        centerButtonDisabledColor.hasValue() || menuBackgroundColor.hasValue() || menuTextColor.hasValue() ||
        drawBehind.hasValue() || testID.hasValue() || title.hasValue() ||
        subtitle.hasValue() || buttons.hasValue() || customizationId.hasValue() || overflowMenu != null ||
        verticalBarKeeps.hasValue() || background.hasValue() || scrollEdgeAppearance.hasValue() ||
        barStyle.hasValue() || style.hasValue() || noBorder.hasValue() || searchBar.hasAnyValue() ||
        largeTitle.hasValue() || height.hasValue() || borderColor.hasValue() || borderHeight.hasValue() ||
        elevation.hasValue() || topMargin.hasValue() || animateLeftButtons.hasValue() ||
        animateRightButtons.hasValue()

    fun mergeWith(other: TopBarOptions) {
        if (other.visible.hasValue()) visible = other.visible
        if (other.animate.hasValue()) animate = other.animate
        if (other.hideOnScroll.hasValue()) hideOnScroll = other.hideOnScroll
        if (other.hideOnTap.hasValue()) hideOnTap = other.hideOnTap
        if (other.hideOnKeyboard.hasValue()) hideOnKeyboard = other.hideOnKeyboard
        leftButtonColor = leftButtonColor.mergeWith(other.leftButtonColor)
        rightButtonColor = rightButtonColor.mergeWith(other.rightButtonColor)
        leftButtonBackgroundColor = leftButtonBackgroundColor.mergeWith(other.leftButtonBackgroundColor)
        rightButtonBackgroundColor = rightButtonBackgroundColor.mergeWith(other.rightButtonBackgroundColor)
        leftButtonDisabledColor = leftButtonDisabledColor.mergeWith(other.leftButtonDisabledColor)
        rightButtonDisabledColor = rightButtonDisabledColor.mergeWith(other.rightButtonDisabledColor)
        centerButtonColor = centerButtonColor.mergeWith(other.centerButtonColor)
        centerButtonDisabledColor = centerButtonDisabledColor.mergeWith(other.centerButtonDisabledColor)
        menuBackgroundColor = menuBackgroundColor.mergeWith(other.menuBackgroundColor)
        menuTextColor = menuTextColor.mergeWith(other.menuTextColor)
        if (other.drawBehind.hasValue()) drawBehind = other.drawBehind
        if (other.testID.hasValue()) testID = other.testID
        title.mergeWith(other.title)
        subtitle.mergeWith(other.subtitle)
        buttons.mergeWith(other.buttons)
        if (other.customizationId.hasValue()) customizationId = other.customizationId
        other.overflowMenu?.let { overflowMenu = it }
        if (other.verticalBarKeeps.hasValue()) verticalBarKeeps = other.verticalBarKeeps
        background.mergeWith(other.background)
        scrollEdgeAppearance.mergeWith(other.scrollEdgeAppearance)
        if (other.barStyle.hasValue()) barStyle = other.barStyle
        if (other.style.hasValue()) style = other.style
        if (other.noBorder.hasValue()) noBorder = other.noBorder
        searchBar.mergeWith(other.searchBar)
        // The search module takes the results component's object as it is; these options keep one of their own.
        if (other.searchBar.resultsComponent.hasValue()) {
            searchBar.resultsComponent = other.searchBar.resultsComponent.copy()
        }
        largeTitle.mergeWith(other.largeTitle)
        if (other.height.hasValue()) height = other.height
        borderColor = borderColor.mergeWith(other.borderColor)
        if (other.borderHeight.hasValue()) borderHeight = other.borderHeight
        if (other.elevation.hasValue()) elevation = other.elevation
        if (other.topMargin.hasValue()) topMargin = other.topMargin
        if (other.animateLeftButtons.hasValue()) animateLeftButtons = other.animateLeftButtons
        if (other.animateRightButtons.hasValue()) animateRightButtons = other.animateRightButtons
    }

    fun mergeWithDefault(defaults: TopBarOptions) {
        if (!visible.hasValue()) visible = defaults.visible
        if (!animate.hasValue()) animate = defaults.animate
        if (!hideOnScroll.hasValue()) hideOnScroll = defaults.hideOnScroll
        if (!hideOnTap.hasValue()) hideOnTap = defaults.hideOnTap
        if (!hideOnKeyboard.hasValue()) hideOnKeyboard = defaults.hideOnKeyboard
        leftButtonColor = leftButtonColor.withDefault(defaults.leftButtonColor)
        rightButtonColor = rightButtonColor.withDefault(defaults.rightButtonColor)
        leftButtonBackgroundColor = leftButtonBackgroundColor.withDefault(defaults.leftButtonBackgroundColor)
        rightButtonBackgroundColor = rightButtonBackgroundColor.withDefault(defaults.rightButtonBackgroundColor)
        leftButtonDisabledColor = leftButtonDisabledColor.withDefault(defaults.leftButtonDisabledColor)
        rightButtonDisabledColor = rightButtonDisabledColor.withDefault(defaults.rightButtonDisabledColor)
        centerButtonColor = centerButtonColor.withDefault(defaults.centerButtonColor)
        centerButtonDisabledColor = centerButtonDisabledColor.withDefault(defaults.centerButtonDisabledColor)
        menuBackgroundColor = menuBackgroundColor.withDefault(defaults.menuBackgroundColor)
        menuTextColor = menuTextColor.withDefault(defaults.menuTextColor)
        if (!drawBehind.hasValue()) drawBehind = defaults.drawBehind
        if (!testID.hasValue()) testID = defaults.testID
        title.mergeWithDefault(defaults.title)
        subtitle.mergeWithDefault(defaults.subtitle)
        buttons.mergeWithDefault(defaults.buttons)
        if (!customizationId.hasValue()) customizationId = defaults.customizationId
        if (overflowMenu == null) overflowMenu = defaults.overflowMenu?.copyMenu()
        if (!verticalBarKeeps.hasValue()) verticalBarKeeps = defaults.verticalBarKeeps
        background.mergeWithDefault(defaults.background)
        scrollEdgeAppearance.mergeWithDefault(defaults.scrollEdgeAppearance)
        if (!barStyle.hasValue()) barStyle = defaults.barStyle
        if (!style.hasValue()) style = defaults.style
        if (!noBorder.hasValue()) noBorder = defaults.noBorder
        val ownResults = searchBar.resultsComponent.hasValue()
        searchBar.mergeWithDefault(defaults.searchBar)
        // As in mergeWith: the results component of the defaults is never shared with these options.
        if (!ownResults) searchBar.resultsComponent = defaults.searchBar.resultsComponent.copy()
        largeTitle.mergeWithDefault(defaults.largeTitle)
        if (!height.hasValue()) height = defaults.height
        borderColor = borderColor.withDefault(defaults.borderColor)
        if (!borderHeight.hasValue()) borderHeight = defaults.borderHeight
        if (!elevation.hasValue()) elevation = defaults.elevation
        if (!topMargin.hasValue()) topMargin = defaults.topMargin
        if (!animateLeftButtons.hasValue()) animateLeftButtons = defaults.animateLeftButtons
        if (!animateRightButtons.hasValue()) animateRightButtons = defaults.animateRightButtons
    }

    fun copy() = TopBarOptions().also { it.mergeWith(this) }

    companion object {
        private val VERTICAL_BAR_KEEPS = setOf("tabs", "buttons")
        private val BAR_STYLES = setOf("default", "black")
        private val STYLES = setOf("navigator", "browser", "editor")

        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): TopBarOptions {
            val options = TopBarOptions()
            if (map == null) return options
            options.visible = BoolParser.parse(map, "visible")
            options.animate = BoolParser.parse(map, "animate")
            options.hideOnScroll = BoolParser.parse(map, "hideOnScroll")
            options.hideOnTap = BoolParser.parse(map, "hideOnTap")
            options.hideOnKeyboard = BoolParser.parse(map, "hideOnKeyboard")
            options.leftButtonColor = ThemeColour.parse(context, map, "leftButtonColor")
            options.rightButtonColor = ThemeColour.parse(context, map, "rightButtonColor")
            options.leftButtonBackgroundColor = ThemeColour.parse(context, map, "leftButtonBackgroundColor")
            options.rightButtonBackgroundColor = ThemeColour.parse(context, map, "rightButtonBackgroundColor")
            options.leftButtonDisabledColor = ThemeColour.parse(context, map, "leftButtonDisabledColor")
            options.rightButtonDisabledColor = ThemeColour.parse(context, map, "rightButtonDisabledColor")
            options.centerButtonColor = ThemeColour.parse(context, map, "centerButtonColor")
            options.centerButtonDisabledColor = ThemeColour.parse(context, map, "centerButtonDisabledColor")
            options.menuBackgroundColor = ThemeColour.parse(context, map, "menuBackgroundColor")
            options.menuTextColor = ThemeColour.parse(context, map, "menuTextColor")
            options.drawBehind = BoolParser.parse(map, "drawBehind")
            options.testID = TextParser.parse(map, "testID")
            options.title = map.group("title") { TitleOptions.parse(context, it) }
            options.subtitle = map.group("subtitle") { SubtitleOptions.parse(context, it) }
            options.buttons = TopBarButtons.parse(context, map)
            options.customizationId = TextParser.parse(map, "customizationId")
            options.overflowMenu = map.group("overflowMenu") { ButtonMenuOptions.parse(it) }
            options.verticalBarKeeps = TextParser.parse(map, "verticalBarKeeps", VERTICAL_BAR_KEEPS)
            options.background = map.group("background") { TopBarBackgroundOptions.parse(context, it) }
            options.scrollEdgeAppearance =
                map.group("scrollEdgeAppearance") { TopBarScrollEdgeAppearanceOptions.parse(context, it) }
            options.barStyle = TextParser.parse(map, "barStyle", BAR_STYLES)
            options.style = TextParser.parse(map, "style", STYLES)
            options.noBorder = BoolParser.parse(map, "noBorder")
            options.searchBar = map.group("searchBar") { SearchBarOptions.parse(context, it) }
            options.largeTitle = map.group("largeTitle") { LargeTitleOptions.parse(context, it) }
            options.height = FractionParser.parse(map, "height")
            options.borderColor = ThemeColour.parse(context, map, "borderColor")
            options.borderHeight = FractionParser.parse(map, "borderHeight")
            options.elevation = FractionParser.parse(map, "elevation")
            options.topMargin = FractionParser.parse(map, "topMargin")
            options.animateLeftButtons = BoolParser.parse(map, "animateLeftButtons")
            options.animateRightButtons = BoolParser.parse(map, "animateRightButtons")
            return options
        }
    }
}

/**
 * The buttons of the top bar, read from `topBar` itself: the back button (`backButton`), and the buttons at the start
 * (`leftButtons`), at the end (`rightButtons`) and in the middle (`centerButtons`). A list is null when the options
 * don't give it; `[]` takes the buttons of the layers below away.
 */
class TopBarButtons {
    @JvmField var back = BackButtonOptions()
    /** `topBar.leftButtons`; null when the options don't give them. */
    @JvmField var left: List<ButtonOptions>? = null
    /** `topBar.rightButtons`; null when the options don't give them. */
    @JvmField var right: List<ButtonOptions>? = null
    /** `topBar.centerButtons`; null when the options don't give them. */
    @JvmField var center: List<ButtonOptions>? = null

    fun hasValue(): Boolean = back.hasValue() || left != null || right != null || center != null

    fun mergeWith(other: TopBarButtons) {
        back.mergeWith(other.back)
        other.left?.let { left = it.map(ButtonOptions::copy) }
        other.right?.let { right = it.map(ButtonOptions::copy) }
        other.center?.let { center = it.map(ButtonOptions::copy) }
    }

    fun mergeWithDefault(defaultOptions: TopBarButtons) {
        back.mergeWithDefault(defaultOptions.back)
        if (left == null) left = defaultOptions.left?.map(ButtonOptions::copyWithMenu)
        if (right == null) right = defaultOptions.right?.map(ButtonOptions::copyWithMenu)
        if (center == null) center = defaultOptions.center?.map(ButtonOptions::copyWithMenu)
    }

    fun copy() = TopBarButtons().also { it.mergeWith(this) }

    companion object {
        /** The buttons of the object of `topBar`. */
        @JvmStatic
        fun parse(context: Context?, topBar: ReadableMap?): TopBarButtons {
            val buttons = TopBarButtons()
            if (topBar == null) return buttons
            buttons.back = topBar.group("backButton") { BackButtonOptions.parse(context, it) }
            buttons.left = topBar.groupList("leftButtons") { ButtonOptions.parse(context, it) }
            buttons.right = topBar.groupList("rightButtons") { ButtonOptions.parse(context, it) }
            buttons.center = topBar.groupList("centerButtons") { ButtonOptions.parse(context, it) }
            return buttons
        }
    }
}

/** `topBar.backButton`: the arrow at the start of the bar over a screen that isn't the first of its stack. */
class BackButtonOptions {
    @JvmField var accessibilityLabel: Text = NullText()
    @JvmField var id: Text = NullText()
    @JvmField var icon: Text = NullText()
    @JvmField var visible: Bool = NullBool()
    @JvmField var title: Text = NullText()
    @JvmField var showTitle: Bool = NullBool()
    @JvmField var color: ThemeColour = NullThemeColour()
    @JvmField var fontSize: Fraction = NullFraction()
    @JvmField var font = FontOptions()
    @JvmField var iconBackground = IconBackgroundOptions()
    @JvmField var testID: Text = NullText()
    @JvmField var enableMenu: Bool = NullBool()
    @JvmField var displayMode: Text = NullText()
    @JvmField var hideSharedBackground: Bool = NullBool()
    /** Internal: whether a press pops the stack. */
    @JvmField var popStackOnPress: Bool = NullBool()

    /** Any of its options is set. */
    fun hasValue(): Boolean = accessibilityLabel.hasValue() || id.hasValue() || icon.hasValue() ||
        visible.hasValue() || title.hasValue() || showTitle.hasValue() || color.hasValue() || fontSize.hasValue() ||
        font.hasValue() || iconBackground.hasValue() || testID.hasValue() || enableMenu.hasValue() ||
        displayMode.hasValue() || hideSharedBackground.hasValue() || popStackOnPress.hasValue()

    fun setVisible() {
        visible = Bool(true)
    }

    fun setHidden() {
        visible = Bool(false)
    }

    // Three exceptions to the merge (the back button of Android): the default `id` and `accessibilityLabel`
    // (`ApexNavigation.back`, `Navigate Up`) from a stronger layer don't replace a value set below it, and an
    // `iconBackground` is one value: with a `color` it replaces the whole group, without one it is no layer.
    fun mergeWith(other: BackButtonOptions) {
        if (other.accessibilityLabel.hasValue() && !(isDefault(other.accessibilityLabel, DEFAULT_LABEL) &&
                accessibilityLabel.hasValue())
        ) {
            accessibilityLabel = other.accessibilityLabel
        }
        if (other.id.hasValue() && !(isDefault(other.id, DEFAULT_ID) && id.hasValue())) id = other.id
        if (other.icon.hasValue()) icon = other.icon
        if (other.visible.hasValue()) visible = other.visible
        if (other.title.hasValue()) title = other.title
        if (other.showTitle.hasValue()) showTitle = other.showTitle
        color = color.mergeWith(other.color)
        if (other.fontSize.hasValue()) fontSize = other.fontSize
        font.mergeWith(other.font)
        if (other.iconBackground.color.hasValue()) iconBackground = other.iconBackground.copy()
        if (other.testID.hasValue()) testID = other.testID
        if (other.enableMenu.hasValue()) enableMenu = other.enableMenu
        if (other.displayMode.hasValue()) displayMode = other.displayMode
        if (other.hideSharedBackground.hasValue()) hideSharedBackground = other.hideSharedBackground
        if (other.popStackOnPress.hasValue()) popStackOnPress = other.popStackOnPress
    }

    // The defaults lie below: the same exceptions as [mergeWith], seen from the stronger side.
    fun mergeWithDefault(defaultOptions: BackButtonOptions) {
        if (!accessibilityLabel.hasValue() ||
            (isDefault(accessibilityLabel, DEFAULT_LABEL) && defaultOptions.accessibilityLabel.hasValue())
        ) {
            accessibilityLabel = defaultOptions.accessibilityLabel
        }
        if (!id.hasValue() || (isDefault(id, DEFAULT_ID) && defaultOptions.id.hasValue())) id = defaultOptions.id
        if (!icon.hasValue()) icon = defaultOptions.icon
        if (!visible.hasValue()) visible = defaultOptions.visible
        if (!title.hasValue()) title = defaultOptions.title
        if (!showTitle.hasValue()) showTitle = defaultOptions.showTitle
        color = color.withDefault(defaultOptions.color)
        if (!fontSize.hasValue()) fontSize = defaultOptions.fontSize
        font.mergeWithDefault(defaultOptions.font)
        if (!iconBackground.color.hasValue() && defaultOptions.iconBackground.color.hasValue()) {
            iconBackground = defaultOptions.iconBackground.copy()
        }
        if (!testID.hasValue()) testID = defaultOptions.testID
        if (!enableMenu.hasValue()) enableMenu = defaultOptions.enableMenu
        if (!displayMode.hasValue()) displayMode = defaultOptions.displayMode
        if (!hideSharedBackground.hasValue()) hideSharedBackground = defaultOptions.hideSharedBackground
        if (!popStackOnPress.hasValue()) popStackOnPress = defaultOptions.popStackOnPress
    }

    fun copy() = BackButtonOptions().also { it.mergeWith(this) }

    companion object {
        private val DISPLAY_MODES = setOf("default", "generic", "minimal")

        /** The `id` of the back button's event without one of its own (`StackController.BACK_BUTTON_ID`). */
        const val DEFAULT_ID = "ApexNavigation.back"

        /** The back button's description for the screen reader without one of its own. */
        const val DEFAULT_LABEL = "Navigate Up"

        private fun isDefault(text: Text, value: String): Boolean = text.hasValue() && text.get(null) == value

        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): BackButtonOptions {
            val options = BackButtonOptions()
            if (map == null) return options
            options.accessibilityLabel = TextParser.parse(map, "accessibilityLabel")
            options.id = TextParser.parse(map, "id")
            options.icon = IconParser.parse(map, "icon")
            options.visible = BoolParser.parse(map, "visible")
            options.title = TextParser.parse(map, "title")
            options.showTitle = BoolParser.parse(map, "showTitle")
            options.color = ThemeColour.parse(context, map, "color")
            options.fontSize = FractionParser.parse(map, "fontSize")
            options.font = FontParser.parse(map)
            options.iconBackground = map.group("iconBackground") { IconBackgroundOptions.parse(context, it) }
            options.testID = TextParser.parse(map, "testID")
            options.enableMenu = BoolParser.parse(map, "enableMenu")
            options.displayMode = TextParser.parse(map, "displayMode", DISPLAY_MODES)
            options.hideSharedBackground = BoolParser.parse(map, "hideSharedBackground")
            options.popStackOnPress = BoolParser.parse(map, "popStackOnPress")
            return options
        }
    }
}

// The search module's group has no check of its own: any of its fields set.
private fun SearchBarOptions.hasAnyValue(): Boolean = visible.hasValue() || focus.hasValue() ||
    hideOnScroll.hasValue() || hideTopBarOnFocus.hasValue() || obscuresBackgroundDuringPresentation.hasValue() ||
    backgroundColor.hasValue() || tintColor.hasValue() || placeholder.hasValue() || text.hasValue() ||
    placement.hasValue() || scopes != null || selectedScope.hasValue() || scopeBarActivation.hasValue() ||
    suggestions != null || resultsComponent.hasValue() || resultsActivation.hasValue()

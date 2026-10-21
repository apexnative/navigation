package dev.apexnative.navigation.options

import android.content.Context
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.customrow.BottomTabsCustomRowOptions
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullFraction
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.NullThemeColour
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.FractionParser
import dev.apexnative.navigation.options.parsers.TextParser

/** A shadow (`ShadowOptions`): `bottomTabs.shadow`. */
class ShadowOptions {
    @JvmField var opacity: Fraction = NullFraction()
    @JvmField var color: ThemeColour = NullThemeColour()
    @JvmField var radius: Fraction = NullFraction()

    fun hasValue(): Boolean = opacity.hasValue() || color.hasValue() || radius.hasValue()

    fun mergeWith(other: ShadowOptions) {
        if (other.opacity.hasValue()) opacity = other.opacity
        color = color.mergeWith(other.color)
        if (other.radius.hasValue()) radius = other.radius
    }

    fun mergeWithDefault(defaults: ShadowOptions) {
        if (!opacity.hasValue()) opacity = defaults.opacity
        color = color.withDefault(defaults.color)
        if (!radius.hasValue()) radius = defaults.radius
    }

    fun copy() = ShadowOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): ShadowOptions {
            val options = ShadowOptions()
            if (map == null) return options
            options.opacity = FractionParser.parse(map, "opacity")
            options.color = ThemeColour.parse(context, map, "color")
            options.radius = FractionParser.parse(map, "radius")
            return options
        }
    }
}

/**
 * `bottomTabs.bottomAccessory` (`OptionsBottomTabsAccessory`): a React component over the tab bar. Its props stay in
 * JavaScript.
 */
class BottomTabsAccessoryOptions {
    @JvmField var component = ComponentOptions()
    @JvmField var visible: Bool = NullBool()
    @JvmField var testID: Text = NullText()

    fun hasValue(): Boolean = component.hasValue() || visible.hasValue() || testID.hasValue()

    fun mergeWith(other: BottomTabsAccessoryOptions) {
        component.mergeWith(other.component)
        if (other.visible.hasValue()) visible = other.visible
        if (other.testID.hasValue()) testID = other.testID
    }

    fun mergeWithDefault(defaults: BottomTabsAccessoryOptions) {
        component.mergeWithDefault(defaults.component)
        if (!visible.hasValue()) visible = defaults.visible
        if (!testID.hasValue()) testID = defaults.testID
    }

    fun copy() = BottomTabsAccessoryOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): BottomTabsAccessoryOptions {
            val options = BottomTabsAccessoryOptions()
            if (map == null) return options
            options.component = map.group("component") { ComponentOptions.parse(it) }
            options.visible = BoolParser.parse(map, "visible")
            options.testID = TextParser.parse(map, "testID")
            return options
        }
    }
}

/**
 * `bottomTabs`: the tabs of a tabs layout; `customRow` is the group of the custom row module, which keeps its own
 * reading and merging. `currentTabIndex` and `currentTabId` act once and are never kept in a node's own options.
 */
class BottomTabsOptions {
    @JvmField var visible: Bool = NullBool()
    @JvmField var animate: Bool = NullBool()
    /** Selects a tab once; a whole number, truncated where it is read. */
    @JvmField var currentTabIndex: Fraction = NullFraction()
    /** Selects a tab once. */
    @JvmField var currentTabId: Text = NullText()
    @JvmField var testID: Text = NullText()
    @JvmField var accessibilityLabel: Text = NullText()
    @JvmField var moreTestID: Text = NullText()
    @JvmField var drawBehind: Bool = NullBool()
    @JvmField var backgroundColor: ThemeColour = NullThemeColour()
    @JvmField var tabsAttachMode: Text = NullText()
    @JvmField var barStyle: Text = NullText()
    @JvmField var layoutStyle: Text = NullText()
    @JvmField var cornerRadius: Fraction = NullFraction()
    @JvmField var bottomMargin: Fraction = NullFraction()
    @JvmField var translucent: Bool = NullBool()
    @JvmField var hideShadow: Bool = NullBool()
    @JvmField var titleDisplayMode: Text = NullText()
    @JvmField var elevation: Fraction = NullFraction()
    @JvmField var hideOnScroll: Bool = NullBool()
    @JvmField var borderColor: ThemeColour = NullThemeColour()
    @JvmField var borderWidth: Fraction = NullFraction()
    @JvmField var shadow = ShadowOptions()
    @JvmField var customRow = BottomTabsCustomRowOptions()
    @JvmField var sidebar: Text = NullText()
    @JvmField var sidebarLayout: Text = NullText()
    @JvmField var customizationId: Text = NullText()
    @JvmField var minimizeBehavior: Text = NullText()
    @JvmField var bottomAccessory = BottomTabsAccessoryOptions()

    fun hasValue(): Boolean = visible.hasValue() || animate.hasValue() || currentTabIndex.hasValue() ||
        currentTabId.hasValue() || testID.hasValue() || accessibilityLabel.hasValue() || moreTestID.hasValue() ||
        drawBehind.hasValue() || backgroundColor.hasValue() || tabsAttachMode.hasValue() || barStyle.hasValue() ||
        layoutStyle.hasValue() || cornerRadius.hasValue() || bottomMargin.hasValue() || translucent.hasValue() ||
        hideShadow.hasValue() || titleDisplayMode.hasValue() || elevation.hasValue() || hideOnScroll.hasValue() ||
        borderColor.hasValue() || borderWidth.hasValue() || shadow.hasValue() || customRow.hasValue() ||
        sidebar.hasValue() || sidebarLayout.hasValue() || customizationId.hasValue() || minimizeBehavior.hasValue() ||
        bottomAccessory.hasValue()

    fun mergeWith(other: BottomTabsOptions) {
        if (other.visible.hasValue()) visible = other.visible
        if (other.animate.hasValue()) animate = other.animate
        if (other.currentTabIndex.hasValue()) currentTabIndex = other.currentTabIndex
        if (other.currentTabId.hasValue()) currentTabId = other.currentTabId
        if (other.testID.hasValue()) testID = other.testID
        if (other.accessibilityLabel.hasValue()) accessibilityLabel = other.accessibilityLabel
        if (other.moreTestID.hasValue()) moreTestID = other.moreTestID
        if (other.drawBehind.hasValue()) drawBehind = other.drawBehind
        backgroundColor = backgroundColor.mergeWith(other.backgroundColor)
        if (other.tabsAttachMode.hasValue()) tabsAttachMode = other.tabsAttachMode
        if (other.barStyle.hasValue()) barStyle = other.barStyle
        if (other.layoutStyle.hasValue()) layoutStyle = other.layoutStyle
        if (other.cornerRadius.hasValue()) cornerRadius = other.cornerRadius
        if (other.bottomMargin.hasValue()) bottomMargin = other.bottomMargin
        if (other.translucent.hasValue()) translucent = other.translucent
        if (other.hideShadow.hasValue()) hideShadow = other.hideShadow
        if (other.titleDisplayMode.hasValue()) titleDisplayMode = other.titleDisplayMode
        if (other.elevation.hasValue()) elevation = other.elevation
        if (other.hideOnScroll.hasValue()) hideOnScroll = other.hideOnScroll
        borderColor = borderColor.mergeWith(other.borderColor)
        if (other.borderWidth.hasValue()) borderWidth = other.borderWidth
        shadow.mergeWith(other.shadow)
        customRow.mergeWith(other.customRow)
        if (other.sidebar.hasValue()) sidebar = other.sidebar
        if (other.sidebarLayout.hasValue()) sidebarLayout = other.sidebarLayout
        if (other.customizationId.hasValue()) customizationId = other.customizationId
        if (other.minimizeBehavior.hasValue()) minimizeBehavior = other.minimizeBehavior
        bottomAccessory.mergeWith(other.bottomAccessory)
    }

    fun mergeWithDefault(defaults: BottomTabsOptions) {
        if (!visible.hasValue()) visible = defaults.visible
        if (!animate.hasValue()) animate = defaults.animate
        if (!currentTabIndex.hasValue()) currentTabIndex = defaults.currentTabIndex
        if (!currentTabId.hasValue()) currentTabId = defaults.currentTabId
        if (!testID.hasValue()) testID = defaults.testID
        if (!accessibilityLabel.hasValue()) accessibilityLabel = defaults.accessibilityLabel
        if (!moreTestID.hasValue()) moreTestID = defaults.moreTestID
        if (!drawBehind.hasValue()) drawBehind = defaults.drawBehind
        backgroundColor = backgroundColor.withDefault(defaults.backgroundColor)
        if (!tabsAttachMode.hasValue()) tabsAttachMode = defaults.tabsAttachMode
        if (!barStyle.hasValue()) barStyle = defaults.barStyle
        if (!layoutStyle.hasValue()) layoutStyle = defaults.layoutStyle
        if (!cornerRadius.hasValue()) cornerRadius = defaults.cornerRadius
        if (!bottomMargin.hasValue()) bottomMargin = defaults.bottomMargin
        if (!translucent.hasValue()) translucent = defaults.translucent
        if (!hideShadow.hasValue()) hideShadow = defaults.hideShadow
        if (!titleDisplayMode.hasValue()) titleDisplayMode = defaults.titleDisplayMode
        if (!elevation.hasValue()) elevation = defaults.elevation
        if (!hideOnScroll.hasValue()) hideOnScroll = defaults.hideOnScroll
        borderColor = borderColor.withDefault(defaults.borderColor)
        if (!borderWidth.hasValue()) borderWidth = defaults.borderWidth
        shadow.mergeWithDefault(defaults.shadow)
        customRow.mergeWithDefault(defaults.customRow)
        if (!sidebar.hasValue()) sidebar = defaults.sidebar
        if (!sidebarLayout.hasValue()) sidebarLayout = defaults.sidebarLayout
        if (!customizationId.hasValue()) customizationId = defaults.customizationId
        if (!minimizeBehavior.hasValue()) minimizeBehavior = defaults.minimizeBehavior
        bottomAccessory.mergeWithDefault(defaults.bottomAccessory)
    }

    fun copy() = BottomTabsOptions().also { it.mergeWith(this) }

    companion object {
        private val TABS_ATTACH_MODES = setOf("together", "afterInitialTab", "onSwitchToTab")
        private val BAR_STYLES = setOf("default", "black")
        private val LAYOUT_STYLES = setOf("stretch", "compact")
        private val TITLE_DISPLAY_MODES = setOf("alwaysShow", "showWhenActive", "alwaysHide", "showWhenActiveForce")
        private val SIDEBARS = setOf("automatic", "tabBar", "sidebar")
        private val SIDEBAR_LAYOUTS = setOf("tile", "overlap")
        private val MINIMIZE_BEHAVIORS = setOf("automatic", "never", "onScrollDown", "onScrollUp")

        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): BottomTabsOptions {
            val options = BottomTabsOptions()
            if (map == null) return options
            options.visible = BoolParser.parse(map, "visible")
            options.animate = BoolParser.parse(map, "animate")
            options.currentTabIndex = FractionParser.parse(map, "currentTabIndex")
            options.currentTabId = TextParser.parse(map, "currentTabId")
            options.testID = TextParser.parse(map, "testID")
            options.accessibilityLabel = TextParser.parse(map, "accessibilityLabel")
            options.moreTestID = TextParser.parse(map, "moreTestID")
            options.drawBehind = BoolParser.parse(map, "drawBehind")
            options.backgroundColor = ThemeColour.parse(context, map, "backgroundColor")
            options.tabsAttachMode = TextParser.parse(map, "tabsAttachMode", TABS_ATTACH_MODES)
            options.barStyle = TextParser.parse(map, "barStyle", BAR_STYLES)
            options.layoutStyle = TextParser.parse(map, "layoutStyle", LAYOUT_STYLES)
            options.cornerRadius = FractionParser.parse(map, "cornerRadius")
            options.bottomMargin = FractionParser.parse(map, "bottomMargin")
            options.translucent = BoolParser.parse(map, "translucent")
            options.hideShadow = BoolParser.parse(map, "hideShadow")
            options.titleDisplayMode = TextParser.parse(map, "titleDisplayMode", TITLE_DISPLAY_MODES)
            options.elevation = FractionParser.parse(map, "elevation")
            options.hideOnScroll = BoolParser.parse(map, "hideOnScroll")
            options.borderColor = ThemeColour.parse(context, map, "borderColor")
            options.borderWidth = FractionParser.parse(map, "borderWidth")
            options.shadow = map.group("shadow") { ShadowOptions.parse(context, it) }
            options.customRow = map.group("customRow") { BottomTabsCustomRowOptions.parse(context, it) }
            options.sidebar = TextParser.parse(map, "sidebar", SIDEBARS)
            options.sidebarLayout = TextParser.parse(map, "sidebarLayout", SIDEBAR_LAYOUTS)
            options.customizationId = TextParser.parse(map, "customizationId")
            options.minimizeBehavior = TextParser.parse(map, "minimizeBehavior", MINIMIZE_BEHAVIORS)
            options.bottomAccessory = map.group("bottomAccessory") { BottomTabsAccessoryOptions.parse(it) }
            return options
        }
    }
}

package dev.apexnative.navigation.options

import android.content.Context
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.NullThemeColour
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.IconParser
import dev.apexnative.navigation.options.parsers.TextParser

/**
 * `fab` (`OptionsFab`): a floating action button of a screen, and `actions` the buttons of its menu, each described
 * the same way.
 */
class FabOptions {
    @JvmField var id: Text = NullText()
    @JvmField var testID: Text = NullText()
    @JvmField var backgroundColor: ThemeColour = NullThemeColour()
    @JvmField var clickColor: ThemeColour = NullThemeColour()
    @JvmField var rippleColor: ThemeColour = NullThemeColour()
    @JvmField var visible: Bool = NullBool()
    @JvmField var icon: Text = NullText()
    @JvmField var iconColor: ThemeColour = NullThemeColour()
    @JvmField var alignHorizontally: Text = NullText()
    @JvmField var hideOnScroll: Bool = NullBool()
    @JvmField var size: Text = NullText()
    @JvmField var actions: List<FabOptions>? = null
    @JvmField var scrimColor: ThemeColour = NullThemeColour()

    fun hasValue(): Boolean = id.hasValue() || testID.hasValue() || backgroundColor.hasValue() ||
        clickColor.hasValue() || rippleColor.hasValue() || visible.hasValue() || icon.hasValue() ||
        iconColor.hasValue() || alignHorizontally.hasValue() || hideOnScroll.hasValue() || size.hasValue() ||
        actions != null || scrimColor.hasValue()

    fun mergeWith(other: FabOptions) {
        if (other.id.hasValue()) id = other.id
        if (other.testID.hasValue()) testID = other.testID
        backgroundColor = backgroundColor.mergeWith(other.backgroundColor)
        clickColor = clickColor.mergeWith(other.clickColor)
        rippleColor = rippleColor.mergeWith(other.rippleColor)
        if (other.visible.hasValue()) visible = other.visible
        if (other.icon.hasValue()) icon = other.icon
        iconColor = iconColor.mergeWith(other.iconColor)
        if (other.alignHorizontally.hasValue()) alignHorizontally = other.alignHorizontally
        if (other.hideOnScroll.hasValue()) hideOnScroll = other.hideOnScroll
        if (other.size.hasValue()) size = other.size
        other.actions?.let { actions = it.map(FabOptions::copy) }
        scrimColor = scrimColor.mergeWith(other.scrimColor)
    }

    fun mergeWithDefault(defaults: FabOptions) {
        if (!id.hasValue()) id = defaults.id
        if (!testID.hasValue()) testID = defaults.testID
        backgroundColor = backgroundColor.withDefault(defaults.backgroundColor)
        clickColor = clickColor.withDefault(defaults.clickColor)
        rippleColor = rippleColor.withDefault(defaults.rippleColor)
        if (!visible.hasValue()) visible = defaults.visible
        if (!icon.hasValue()) icon = defaults.icon
        iconColor = iconColor.withDefault(defaults.iconColor)
        if (!alignHorizontally.hasValue()) alignHorizontally = defaults.alignHorizontally
        if (!hideOnScroll.hasValue()) hideOnScroll = defaults.hideOnScroll
        if (!size.hasValue()) size = defaults.size
        if (actions == null) actions = defaults.actions?.map(FabOptions::copy)
        scrimColor = scrimColor.withDefault(defaults.scrimColor)
    }

    fun copy() = FabOptions().also { it.mergeWith(this) }

    companion object {
        private val ALIGNMENTS = setOf("left", "right")
        private val SIZES = setOf("mini", "regular")

        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): FabOptions {
            val options = FabOptions()
            if (map == null) return options
            options.id = TextParser.parse(map, "id")
            options.testID = TextParser.parse(map, "testID")
            options.backgroundColor = ThemeColour.parse(context, map, "backgroundColor")
            options.clickColor = ThemeColour.parse(context, map, "clickColor")
            options.rippleColor = ThemeColour.parse(context, map, "rippleColor")
            options.visible = BoolParser.parse(map, "visible")
            options.icon = IconParser.parse(map, "icon")
            options.iconColor = ThemeColour.parse(context, map, "iconColor")
            options.alignHorizontally = TextParser.parse(map, "alignHorizontally", ALIGNMENTS)
            options.hideOnScroll = BoolParser.parse(map, "hideOnScroll")
            options.size = TextParser.parse(map, "size", SIZES)
            options.actions = map.groupList("actions") { FabOptions.parse(context, it) }
            options.scrimColor = ThemeColour.parse(context, map, "scrimColor")
            return options
        }
    }
}

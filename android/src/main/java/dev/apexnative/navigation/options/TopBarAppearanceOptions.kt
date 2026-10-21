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
import dev.apexnative.navigation.options.parsers.TextParser

/**
 * `topBar.background.component` (`OptionsTopBarBackgroundComponent`): a React component behind the bar. Its props
 * stay in JavaScript; `componentId` is the id the engine made it with.
 */
class TopBarBackgroundComponentOptions {
    @JvmField var id: Text = NullText()
    @JvmField var name: Text = NullText()
    @JvmField var componentId: Text = NullText()
    @JvmField var waitForRender: Bool = NullBool()

    fun hasValue(): Boolean = id.hasValue() || name.hasValue() || componentId.hasValue() || waitForRender.hasValue()

    fun mergeWith(other: TopBarBackgroundComponentOptions) {
        if (other.id.hasValue()) id = other.id
        if (other.name.hasValue()) name = other.name
        if (other.componentId.hasValue()) componentId = other.componentId
        if (other.waitForRender.hasValue()) waitForRender = other.waitForRender
    }

    fun mergeWithDefault(defaults: TopBarBackgroundComponentOptions) {
        if (!id.hasValue()) id = defaults.id
        if (!name.hasValue()) name = defaults.name
        if (!componentId.hasValue()) componentId = defaults.componentId
        if (!waitForRender.hasValue()) waitForRender = defaults.waitForRender
    }

    fun copy() = TopBarBackgroundComponentOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): TopBarBackgroundComponentOptions {
            val options = TopBarBackgroundComponentOptions()
            if (map == null) return options
            options.id = TextParser.parse(map, "id")
            options.name = TextParser.parse(map, "name")
            options.componentId = TextParser.parse(map, "componentId")
            options.waitForRender = BoolParser.parse(map, "waitForRender")
            return options
        }
    }
}

/**
 * `topBar.background` (`OptionsTopBarBackground`): what the bar draws behind its content.
 */
class TopBarBackgroundOptions {
    @JvmField var color: ThemeColour = NullThemeColour()
    @JvmField var clipToBounds: Bool = NullBool()
    @JvmField var component = TopBarBackgroundComponentOptions()
    @JvmField var translucent: Bool = NullBool()
    @JvmField var blur: Bool = NullBool()

    fun hasValue(): Boolean = color.hasValue() || clipToBounds.hasValue() || component.hasValue() ||
        translucent.hasValue() || blur.hasValue()

    fun mergeWith(other: TopBarBackgroundOptions) {
        color = color.mergeWith(other.color)
        if (other.clipToBounds.hasValue()) clipToBounds = other.clipToBounds
        component.mergeWith(other.component)
        if (other.translucent.hasValue()) translucent = other.translucent
        if (other.blur.hasValue()) blur = other.blur
    }

    fun mergeWithDefault(defaults: TopBarBackgroundOptions) {
        color = color.withDefault(defaults.color)
        if (!clipToBounds.hasValue()) clipToBounds = defaults.clipToBounds
        component.mergeWithDefault(defaults.component)
        if (!translucent.hasValue()) translucent = defaults.translucent
        if (!blur.hasValue()) blur = defaults.blur
    }

    fun copy() = TopBarBackgroundOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): TopBarBackgroundOptions {
            val options = TopBarBackgroundOptions()
            if (map == null) return options
            options.color = ThemeColour.parse(context, map, "color")
            options.clipToBounds = BoolParser.parse(map, "clipToBounds")
            options.component = map.group("component") { TopBarBackgroundComponentOptions.parse(it) }
            options.translucent = BoolParser.parse(map, "translucent")
            options.blur = BoolParser.parse(map, "blur")
            return options
        }
    }
}

/**
 * `topBar.scrollEdgeAppearance.title` (`OptionsTopBarScrollEdgeAppearanceTitle`): the title of the bar while the
 * content is at its edge; its font size is in sp.
 */
class ScrollEdgeAppearanceTitleOptions {
    @JvmField var color: ThemeColour = NullThemeColour()
    @JvmField var font = FontOptions()
    @JvmField var fontSize: Fraction = NullFraction()

    fun hasValue(): Boolean = color.hasValue() || font.hasValue() || fontSize.hasValue()

    fun mergeWith(other: ScrollEdgeAppearanceTitleOptions) {
        color = color.mergeWith(other.color)
        font.mergeWith(other.font)
        if (other.fontSize.hasValue()) fontSize = other.fontSize
    }

    fun mergeWithDefault(defaults: ScrollEdgeAppearanceTitleOptions) {
        color = color.withDefault(defaults.color)
        font.mergeWithDefault(defaults.font)
        if (!fontSize.hasValue()) fontSize = defaults.fontSize
    }

    fun copy() = ScrollEdgeAppearanceTitleOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): ScrollEdgeAppearanceTitleOptions {
            val options = ScrollEdgeAppearanceTitleOptions()
            if (map == null) return options
            options.color = ThemeColour.parse(context, map, "color")
            options.font = FontParser.parse(map)
            options.fontSize = FractionParser.parse(map, "fontSize")
            return options
        }
    }
}

/**
 * `topBar.scrollEdgeAppearance.background` (`OptionsTopBarScrollEdgeAppearanceBackground`): the bar's background
 * while the content is at its edge.
 */
class ScrollEdgeAppearanceBackgroundOptions {
    @JvmField var color: ThemeColour = NullThemeColour()
    @JvmField var translucent: Bool = NullBool()

    fun hasValue(): Boolean = color.hasValue() || translucent.hasValue()

    fun mergeWith(other: ScrollEdgeAppearanceBackgroundOptions) {
        color = color.mergeWith(other.color)
        if (other.translucent.hasValue()) translucent = other.translucent
    }

    fun mergeWithDefault(defaults: ScrollEdgeAppearanceBackgroundOptions) {
        color = color.withDefault(defaults.color)
        if (!translucent.hasValue()) translucent = defaults.translucent
    }

    fun copy() = ScrollEdgeAppearanceBackgroundOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): ScrollEdgeAppearanceBackgroundOptions {
            val options = ScrollEdgeAppearanceBackgroundOptions()
            if (map == null) return options
            options.color = ThemeColour.parse(context, map, "color")
            options.translucent = BoolParser.parse(map, "translucent")
            return options
        }
    }
}

/**
 * `topBar.scrollEdgeAppearance` (`OptionsTopBarScrollEdgeAppearance`): how the bar looks while the content is at its
 * edge.
 */
class TopBarScrollEdgeAppearanceOptions {
    @JvmField var title = ScrollEdgeAppearanceTitleOptions()
    @JvmField var background = ScrollEdgeAppearanceBackgroundOptions()
    @JvmField var active: Bool = NullBool()
    @JvmField var noBorder: Bool = NullBool()
    @JvmField var borderColor: ThemeColour = NullThemeColour()

    fun hasValue(): Boolean = title.hasValue() || background.hasValue() || active.hasValue() || noBorder.hasValue() ||
        borderColor.hasValue()

    fun mergeWith(other: TopBarScrollEdgeAppearanceOptions) {
        title.mergeWith(other.title)
        background.mergeWith(other.background)
        if (other.active.hasValue()) active = other.active
        if (other.noBorder.hasValue()) noBorder = other.noBorder
        borderColor = borderColor.mergeWith(other.borderColor)
    }

    fun mergeWithDefault(defaults: TopBarScrollEdgeAppearanceOptions) {
        title.mergeWithDefault(defaults.title)
        background.mergeWithDefault(defaults.background)
        if (!active.hasValue()) active = defaults.active
        if (!noBorder.hasValue()) noBorder = defaults.noBorder
        borderColor = borderColor.withDefault(defaults.borderColor)
    }

    fun copy() = TopBarScrollEdgeAppearanceOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): TopBarScrollEdgeAppearanceOptions {
            val options = TopBarScrollEdgeAppearanceOptions()
            if (map == null) return options
            options.title = map.group("title") { ScrollEdgeAppearanceTitleOptions.parse(context, it) }
            options.background = map.group("background") { ScrollEdgeAppearanceBackgroundOptions.parse(context, it) }
            options.active = BoolParser.parse(map, "active")
            options.noBorder = BoolParser.parse(map, "noBorder")
            options.borderColor = ThemeColour.parse(context, map, "borderColor")
            return options
        }
    }
}

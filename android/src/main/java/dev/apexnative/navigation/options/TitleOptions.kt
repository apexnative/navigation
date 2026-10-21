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
 * `topBar.title`: the title of the bar, a text with its font (`fontSize` in sp), or a React component in its place.
 */
class TitleOptions {
    @JvmField var text: Text = NullText()
    @JvmField var fontSize: Fraction = NullFraction()
    @JvmField var color: ThemeColour = NullThemeColour()
    @JvmField var font = FontOptions()
    @JvmField var component = TitleComponentOptions()
    /** Shared between copies, as its module wants. */
    @JvmField var menu: ButtonMenuOptions? = null
    @JvmField var rename: Bool = NullBool()
    @JvmField var document = TitleDocumentOptions()
    @JvmField var glass: Text = NullText()
    @JvmField var height: Fraction = NullFraction()
    @JvmField var topMargin: Fraction = NullFraction()
    @JvmField var alignment: Text = NullText()

    fun hasValue(): Boolean = text.hasValue() || fontSize.hasValue() || color.hasValue() || font.hasValue() ||
        component.hasValue() || menu != null || rename.hasValue() || document.hasValue() || glass.hasValue() ||
        height.hasValue() || topMargin.hasValue() || alignment.hasValue()

    fun mergeWith(other: TitleOptions) {
        if (other.text.hasValue()) text = other.text
        if (other.fontSize.hasValue()) fontSize = other.fontSize
        color = color.mergeWith(other.color)
        font.mergeWith(other.font)
        component.mergeWith(other.component)
        other.menu?.let { menu = it }
        if (other.rename.hasValue()) rename = other.rename
        document.mergeWith(other.document)
        if (other.glass.hasValue()) glass = other.glass
        if (other.height.hasValue()) height = other.height
        if (other.topMargin.hasValue()) topMargin = other.topMargin
        if (other.alignment.hasValue()) alignment = other.alignment
    }

    fun mergeWithDefault(defaults: TitleOptions) {
        if (!text.hasValue()) text = defaults.text
        if (!fontSize.hasValue()) fontSize = defaults.fontSize
        color = color.withDefault(defaults.color)
        font.mergeWithDefault(defaults.font)
        component.mergeWithDefault(defaults.component)
        if (menu == null) menu = defaults.menu?.copyMenu()
        if (!rename.hasValue()) rename = defaults.rename
        document.mergeWithDefault(defaults.document)
        if (!glass.hasValue()) glass = defaults.glass
        if (!height.hasValue()) height = defaults.height
        if (!topMargin.hasValue()) topMargin = defaults.topMargin
        if (!alignment.hasValue()) alignment = defaults.alignment
    }

    fun copy() = TitleOptions().also { it.mergeWith(this) }

    companion object {
        private val GLASS = setOf("regular", "clear", "none")

        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): TitleOptions {
            val options = TitleOptions()
            if (map == null) return options
            options.text = TextParser.parse(map, "text")
            options.fontSize = FractionParser.parse(map, "fontSize")
            options.color = ThemeColour.parse(context, map, "color")
            options.font = FontParser.parse(map)
            options.component = map.group("component") { TitleComponentOptions.parse(it) }
            options.menu = map.group("menu") { ButtonMenuOptions.parse(it) }
            options.rename = BoolParser.parse(map, "rename")
            options.document = map.group("document") { TitleDocumentOptions.parse(it) }
            options.glass = TextParser.parse(map, "glass", GLASS)
            options.height = FractionParser.parse(map, "height")
            options.topMargin = FractionParser.parse(map, "topMargin")
            options.alignment = TextParser.parse(map, "alignment", TitleComponentOptions.ALIGNMENTS)
            return options
        }
    }
}

/** `topBar.subtitle`: the line under the title, a text with its font. */
class SubtitleOptions {
    @JvmField var text: Text = NullText()
    @JvmField var fontSize: Fraction = NullFraction()
    @JvmField var color: ThemeColour = NullThemeColour()
    @JvmField var font = FontOptions()
    @JvmField var alignment: Text = NullText()

    fun hasValue(): Boolean =
        text.hasValue() || fontSize.hasValue() || color.hasValue() || font.hasValue() || alignment.hasValue()

    fun mergeWith(other: SubtitleOptions) {
        if (other.text.hasValue()) text = other.text
        if (other.fontSize.hasValue()) fontSize = other.fontSize
        color = color.mergeWith(other.color)
        font.mergeWith(other.font)
        if (other.alignment.hasValue()) alignment = other.alignment
    }

    fun mergeWithDefault(defaults: SubtitleOptions) {
        if (!text.hasValue()) text = defaults.text
        if (!fontSize.hasValue()) fontSize = defaults.fontSize
        color = color.withDefault(defaults.color)
        font.mergeWithDefault(defaults.font)
        if (!alignment.hasValue()) alignment = defaults.alignment
    }

    fun copy() = SubtitleOptions().also { it.mergeWith(this) }

    companion object {
        private val ALIGNMENTS = setOf("center")

        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): SubtitleOptions {
            val options = SubtitleOptions()
            if (map == null) return options
            options.text = TextParser.parse(map, "text")
            options.fontSize = FractionParser.parse(map, "fontSize")
            options.color = ThemeColour.parse(context, map, "color")
            options.font = FontParser.parse(map)
            options.alignment = TextParser.parse(map, "alignment", ALIGNMENTS)
            return options
        }
    }
}

/** `topBar.title.document`: the document the title stands for (`url`). */
class TitleDocumentOptions {
    @JvmField var url: Text = NullText()

    fun hasValue(): Boolean = url.hasValue()

    fun mergeWith(other: TitleDocumentOptions) {
        if (other.url.hasValue()) url = other.url
    }

    fun mergeWithDefault(defaults: TitleDocumentOptions) {
        if (!url.hasValue()) url = defaults.url
    }

    fun copy() = TitleDocumentOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): TitleDocumentOptions {
            val options = TitleDocumentOptions()
            if (map == null) return options
            options.url = TextParser.parse(map, "url")
            return options
        }
    }
}

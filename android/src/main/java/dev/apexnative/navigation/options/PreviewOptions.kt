package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullFraction
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.FractionParser
import dev.apexnative.navigation.options.parsers.TextParser

/** An item of the menu of a preview (`PreviewMenuItem`); `actions` makes it a submenu. */
class PreviewMenuItemOptions {
    @JvmField var id: Text = NullText()
    @JvmField var title: Text = NullText()
    @JvmField var style: Text = NullText()
    @JvmField var actions: List<PreviewMenuItemOptions>? = null

    fun hasValue(): Boolean = id.hasValue() || title.hasValue() || style.hasValue() || actions != null

    fun mergeWith(other: PreviewMenuItemOptions) {
        if (other.id.hasValue()) id = other.id
        if (other.title.hasValue()) title = other.title
        if (other.style.hasValue()) style = other.style
        other.actions?.let { actions = it.map(PreviewMenuItemOptions::copy) }
    }

    fun mergeWithDefault(defaults: PreviewMenuItemOptions) {
        if (!id.hasValue()) id = defaults.id
        if (!title.hasValue()) title = defaults.title
        if (!style.hasValue()) style = defaults.style
        if (actions == null) actions = defaults.actions?.map(PreviewMenuItemOptions::copy)
    }

    fun copy() = PreviewMenuItemOptions().also { it.mergeWith(this) }

    companion object {
        private val STYLES = setOf("default", "selected", "destructive")

        @JvmStatic
        fun parse(map: ReadableMap?): PreviewMenuItemOptions {
            val options = PreviewMenuItemOptions()
            if (map == null) return options
            options.id = TextParser.parse(map, "id")
            options.title = TextParser.parse(map, "title")
            options.style = TextParser.parse(map, "style", STYLES)
            options.actions = map.groupList("actions") { PreviewMenuItemOptions.parse(it) }
            return options
        }
    }
}

/** `preview` (`PreviewOptions`): the preview of a screen that a long press on the view with `nativeID` shows. */
class PreviewOptions {
    @JvmField var nativeID: Text = NullText()
    @JvmField var commit: Bool = NullBool()
    @JvmField var actions: List<PreviewMenuItemOptions>? = null
    @JvmField var width: Fraction = NullFraction()
    @JvmField var height: Fraction = NullFraction()

    fun hasValue(): Boolean = nativeID.hasValue() || commit.hasValue() || actions != null || width.hasValue() ||
        height.hasValue()

    fun mergeWith(other: PreviewOptions) {
        if (other.nativeID.hasValue()) nativeID = other.nativeID
        if (other.commit.hasValue()) commit = other.commit
        other.actions?.let { actions = it.map(PreviewMenuItemOptions::copy) }
        if (other.width.hasValue()) width = other.width
        if (other.height.hasValue()) height = other.height
    }

    fun mergeWithDefault(defaults: PreviewOptions) {
        if (!nativeID.hasValue()) nativeID = defaults.nativeID
        if (!commit.hasValue()) commit = defaults.commit
        if (actions == null) actions = defaults.actions?.map(PreviewMenuItemOptions::copy)
        if (!width.hasValue()) width = defaults.width
        if (!height.hasValue()) height = defaults.height
    }

    fun copy() = PreviewOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): PreviewOptions {
            val options = PreviewOptions()
            if (map == null) return options
            options.nativeID = TextParser.parse(map, "nativeID")
            options.commit = BoolParser.parse(map, "commit")
            options.actions = map.groupList("actions") { PreviewMenuItemOptions.parse(it) }
            options.width = FractionParser.parse(map, "width")
            options.height = FractionParser.parse(map, "height")
            return options
        }
    }
}

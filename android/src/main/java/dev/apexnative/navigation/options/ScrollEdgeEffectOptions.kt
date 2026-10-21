package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.TextParser

/**
 * `scrollEdgeEffect.top`, `bottom`, `left`, `right` (`OptionsScrollEdge`): one edge of a scrolling view, whether its
 * effect is hidden and how it looks.
 */
class ScrollEdgeOptions {
    @JvmField var hidden: Bool = NullBool()
    @JvmField var style: Text = NullText()

    fun hasValue(): Boolean = hidden.hasValue() || style.hasValue()

    fun mergeWith(other: ScrollEdgeOptions) {
        if (other.hidden.hasValue()) hidden = other.hidden
        if (other.style.hasValue()) style = other.style
    }

    fun mergeWithDefault(defaults: ScrollEdgeOptions) {
        if (!hidden.hasValue()) hidden = defaults.hidden
        if (!style.hasValue()) style = defaults.style
    }

    fun copy() = ScrollEdgeOptions().also { it.mergeWith(this) }

    companion object {
        private val STYLES = setOf("automatic", "soft", "hard")

        @JvmStatic
        fun parse(map: ReadableMap?): ScrollEdgeOptions {
            val options = ScrollEdgeOptions()
            if (map == null) return options
            options.hidden = BoolParser.parse(map, "hidden")
            options.style = TextParser.parse(map, "style", STYLES)
            return options
        }
    }
}

/**
 * `scrollEdgeEffect` (`OptionsScrollEdgeEffect`): the effect at the edges of the scrolling views of a screen, for
 * all four edges and for each on its own.
 */
class ScrollEdgeEffectOptions {
    @JvmField var hidden: Bool = NullBool()
    @JvmField var style: Text = NullText()
    @JvmField var top = ScrollEdgeOptions()
    @JvmField var bottom = ScrollEdgeOptions()
    @JvmField var left = ScrollEdgeOptions()
    @JvmField var right = ScrollEdgeOptions()

    fun hasValue(): Boolean = hidden.hasValue() || style.hasValue() || top.hasValue() || bottom.hasValue() ||
        left.hasValue() || right.hasValue()

    fun mergeWith(other: ScrollEdgeEffectOptions) {
        if (other.hidden.hasValue()) hidden = other.hidden
        if (other.style.hasValue()) style = other.style
        top.mergeWith(other.top)
        bottom.mergeWith(other.bottom)
        left.mergeWith(other.left)
        right.mergeWith(other.right)
    }

    fun mergeWithDefault(defaults: ScrollEdgeEffectOptions) {
        if (!hidden.hasValue()) hidden = defaults.hidden
        if (!style.hasValue()) style = defaults.style
        top.mergeWithDefault(defaults.top)
        bottom.mergeWithDefault(defaults.bottom)
        left.mergeWithDefault(defaults.left)
        right.mergeWithDefault(defaults.right)
    }

    fun copy() = ScrollEdgeEffectOptions().also { it.mergeWith(this) }

    companion object {
        private val STYLES = setOf("automatic", "soft", "hard")

        @JvmStatic
        fun parse(map: ReadableMap?): ScrollEdgeEffectOptions {
            val options = ScrollEdgeEffectOptions()
            if (map == null) return options
            options.hidden = BoolParser.parse(map, "hidden")
            options.style = TextParser.parse(map, "style", STYLES)
            options.top = map.group("top") { ScrollEdgeOptions.parse(it) }
            options.bottom = map.group("bottom") { ScrollEdgeOptions.parse(it) }
            options.left = map.group("left") { ScrollEdgeOptions.parse(it) }
            options.right = map.group("right") { ScrollEdgeOptions.parse(it) }
            return options
        }
    }
}

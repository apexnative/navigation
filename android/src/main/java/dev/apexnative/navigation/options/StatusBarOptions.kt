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
import dev.apexnative.navigation.options.parsers.TextParser

/** `statusBar`: the status bar over a screen. */
class StatusBarOptions {
    @JvmField var visible: Bool = NullBool()
    @JvmField var style: Text = NullText()
    @JvmField var backgroundColor: ThemeColour = NullThemeColour()
    @JvmField var drawBehind: Bool = NullBool()
    @JvmField var translucent: Bool = NullBool()
    @JvmField var animate: Bool = NullBool()
    @JvmField var hideWithTopBar: Bool = NullBool()
    @JvmField var blur: Bool = NullBool()

    /**
     * The status bar is hidden, or the screen is drawn under it: the screen keeps no room for it. Without `drawBehind`, a
     * screen or a layout in an overlay ([inOverlay]) is drawn under it: an overlay covers the whole window, its dimmed
     * backdrop the status bar too.
     */
    fun isHiddenOrDrawBehind(inOverlay: Boolean): Boolean =
        visible.isFalse || if (drawBehind.hasValue()) drawBehind.isTrue else inOverlay

    fun hasValue(): Boolean = visible.hasValue() || style.hasValue() || backgroundColor.hasValue() ||
        drawBehind.hasValue() || translucent.hasValue() || animate.hasValue() || hideWithTopBar.hasValue() ||
        blur.hasValue()

    fun mergeWith(other: StatusBarOptions) {
        if (other.visible.hasValue()) visible = other.visible
        if (other.style.hasValue()) style = other.style
        backgroundColor = backgroundColor.mergeWith(other.backgroundColor)
        if (other.drawBehind.hasValue()) drawBehind = other.drawBehind
        if (other.translucent.hasValue()) translucent = other.translucent
        if (other.animate.hasValue()) animate = other.animate
        if (other.hideWithTopBar.hasValue()) hideWithTopBar = other.hideWithTopBar
        if (other.blur.hasValue()) blur = other.blur
    }

    fun mergeWithDefault(defaultOptions: StatusBarOptions) {
        if (!visible.hasValue()) visible = defaultOptions.visible
        if (!style.hasValue()) style = defaultOptions.style
        backgroundColor = backgroundColor.withDefault(defaultOptions.backgroundColor)
        if (!drawBehind.hasValue()) drawBehind = defaultOptions.drawBehind
        if (!translucent.hasValue()) translucent = defaultOptions.translucent
        if (!animate.hasValue()) animate = defaultOptions.animate
        if (!hideWithTopBar.hasValue()) hideWithTopBar = defaultOptions.hideWithTopBar
        if (!blur.hasValue()) blur = defaultOptions.blur
    }

    fun copy() = StatusBarOptions().also { it.mergeWith(this) }

    companion object {
        private val STYLES = setOf("light", "dark")

        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): StatusBarOptions {
            val options = StatusBarOptions()
            if (map == null) return options
            options.visible = BoolParser.parse(map, "visible")
            options.style = TextParser.parse(map, "style", STYLES)
            options.backgroundColor = ThemeColour.parse(context, map, "backgroundColor")
            options.drawBehind = BoolParser.parse(map, "drawBehind")
            options.translucent = BoolParser.parse(map, "translucent")
            options.animate = BoolParser.parse(map, "animate")
            options.hideWithTopBar = BoolParser.parse(map, "hideWithTopBar")
            options.blur = BoolParser.parse(map, "blur")
            return options
        }
    }
}

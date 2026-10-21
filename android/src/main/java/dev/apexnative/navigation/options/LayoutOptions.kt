package dev.apexnative.navigation.options

import android.content.Context
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.LayoutDirection
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.NullThemeColour
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.StringListParser
import dev.apexnative.navigation.options.parsers.TextParser

/**
 * `layout`: the look of a layout's own view and what it asks of the system, and the direction of the app (`direction`,
 * only in the default options).
 */
class LayoutOptions {
    @JvmField var safeAreaEdges: List<String>? = null
    @JvmField var backgroundColor: ThemeColour = NullThemeColour()
    @JvmField var componentBackgroundColor: ThemeColour = NullThemeColour()
    @JvmField var orientation: List<String>? = null
    @JvmField var verticalBars: Bool = NullBool()
    @JvmField var drawBehindVerticalBar: Bool = NullBool()
    @JvmField var autoHideHomeIndicator: Bool = NullBool()
    @JvmField var deferSystemGestures: List<String>? = null
    @JvmField var adjustResize: Bool = NullBool()
    /** `ltr`, `rtl` or `locale`: the direction of the app; see [layoutDirection]. */
    @JvmField var direction: Text = NullText()

    /** [direction] as a view takes it; [LayoutDirection.DEFAULT] when unset. */
    val layoutDirection: LayoutDirection
        get() = LayoutDirection.fromString(direction.get(null))

    fun hasValue(): Boolean = safeAreaEdges != null || backgroundColor.hasValue() ||
        componentBackgroundColor.hasValue() || orientation != null || verticalBars.hasValue() ||
        drawBehindVerticalBar.hasValue() || autoHideHomeIndicator.hasValue() || deferSystemGestures != null ||
        adjustResize.hasValue() || direction.hasValue()

    fun mergeWith(other: LayoutOptions) {
        other.safeAreaEdges?.let { safeAreaEdges = it }
        backgroundColor = backgroundColor.mergeWith(other.backgroundColor)
        componentBackgroundColor = componentBackgroundColor.mergeWith(other.componentBackgroundColor)
        other.orientation?.let { orientation = it }
        if (other.verticalBars.hasValue()) verticalBars = other.verticalBars
        if (other.drawBehindVerticalBar.hasValue()) drawBehindVerticalBar = other.drawBehindVerticalBar
        if (other.autoHideHomeIndicator.hasValue()) autoHideHomeIndicator = other.autoHideHomeIndicator
        other.deferSystemGestures?.let { deferSystemGestures = it }
        if (other.adjustResize.hasValue()) adjustResize = other.adjustResize
        if (other.direction.hasValue()) direction = other.direction
    }

    fun mergeWithDefault(defaultOptions: LayoutOptions) {
        if (safeAreaEdges == null) safeAreaEdges = defaultOptions.safeAreaEdges
        backgroundColor = backgroundColor.withDefault(defaultOptions.backgroundColor)
        componentBackgroundColor = componentBackgroundColor.withDefault(defaultOptions.componentBackgroundColor)
        if (orientation == null) orientation = defaultOptions.orientation
        if (!verticalBars.hasValue()) verticalBars = defaultOptions.verticalBars
        if (!drawBehindVerticalBar.hasValue()) drawBehindVerticalBar = defaultOptions.drawBehindVerticalBar
        if (!autoHideHomeIndicator.hasValue()) autoHideHomeIndicator = defaultOptions.autoHideHomeIndicator
        if (deferSystemGestures == null) deferSystemGestures = defaultOptions.deferSystemGestures
        if (!adjustResize.hasValue()) adjustResize = defaultOptions.adjustResize
        if (!direction.hasValue()) direction = defaultOptions.direction
    }

    fun copy() = LayoutOptions().also { it.mergeWith(this) }

    companion object {
        private val EDGES = setOf("top", "right", "bottom", "left")
        private val ORIENTATIONS = setOf(
            "all", "default", "portrait", "landscape", "upsideDown", "sensor", "sensorLandscape", "sensorPortrait",
        )
        private val DIRECTIONS = setOf("ltr", "rtl", "locale")

        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): LayoutOptions {
            val options = LayoutOptions()
            if (map == null) return options
            options.safeAreaEdges = StringListParser.parse(map, "safeAreaEdges", EDGES)
            options.backgroundColor = ThemeColour.parse(context, map, "backgroundColor")
            options.componentBackgroundColor = ThemeColour.parse(context, map, "componentBackgroundColor")
            options.orientation = StringListParser.parse(map, "orientation", ORIENTATIONS)
            options.verticalBars = BoolParser.parse(map, "verticalBars")
            options.drawBehindVerticalBar = BoolParser.parse(map, "drawBehindVerticalBar")
            options.autoHideHomeIndicator = BoolParser.parse(map, "autoHideHomeIndicator")
            options.deferSystemGestures = StringListParser.parse(map, "deferSystemGestures", EDGES)
            options.adjustResize = BoolParser.parse(map, "adjustResize")
            options.direction = TextParser.parse(map, "direction", DIRECTIONS)
            return options
        }
    }
}

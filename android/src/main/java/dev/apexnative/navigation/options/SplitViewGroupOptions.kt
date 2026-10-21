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

/**
 * `splitView` (`OptionsSplitView`) as the options tree holds it: every field, merged as the other groups. The split
 * view module takes what Android reads as its own [SplitViewOptions] ([toSplitViewOptions]); the name of that class
 * is the module's, so this group has another.
 */
class SplitViewGroupOptions {
    @JvmField var displayMode: Text = NullText()
    @JvmField var primaryEdge: Text = NullText()
    @JvmField var width: Fraction = NullFraction()
    @JvmField var minWidth: Fraction = NullFraction()
    @JvmField var maxWidth: Fraction = NullFraction()
    @JvmField var primaryBackgroundStyle: Text = NullText()
    @JvmField var supplementary = SplitViewColumnOptions()
    @JvmField var inspector = SplitViewInspectorOptions()
    @JvmField var splitBehavior: Text = NullText()
    @JvmField var displayModeButton: Text = NullText()
    @JvmField var presentsWithGesture: Bool = NullBool()
    @JvmField var showsSecondaryOnlyButton: Bool = NullBool()
    @JvmField var compactTopColumn: Text = NullText()
    @JvmField var testID: Text = NullText()

    /** What the split view module reads, with its defaults for what these options don't give. */
    fun toSplitViewOptions() = SplitViewOptions(
        width.get(SplitViewOptions.PANE_WIDTH),
        presentsWithGesture.get(true),
        compactTopColumn.get(null),
    )

    fun hasValue(): Boolean = displayMode.hasValue() || primaryEdge.hasValue() || width.hasValue() ||
        minWidth.hasValue() || maxWidth.hasValue() || primaryBackgroundStyle.hasValue() || supplementary.hasValue() ||
        inspector.hasValue() || splitBehavior.hasValue() || displayModeButton.hasValue() ||
        presentsWithGesture.hasValue() || showsSecondaryOnlyButton.hasValue() || compactTopColumn.hasValue() ||
        testID.hasValue()

    fun mergeWith(other: SplitViewGroupOptions) {
        if (other.displayMode.hasValue()) displayMode = other.displayMode
        if (other.primaryEdge.hasValue()) primaryEdge = other.primaryEdge
        if (other.width.hasValue()) width = other.width
        if (other.minWidth.hasValue()) minWidth = other.minWidth
        if (other.maxWidth.hasValue()) maxWidth = other.maxWidth
        if (other.primaryBackgroundStyle.hasValue()) primaryBackgroundStyle = other.primaryBackgroundStyle
        supplementary.mergeWith(other.supplementary)
        inspector.mergeWith(other.inspector)
        if (other.splitBehavior.hasValue()) splitBehavior = other.splitBehavior
        if (other.displayModeButton.hasValue()) displayModeButton = other.displayModeButton
        if (other.presentsWithGesture.hasValue()) presentsWithGesture = other.presentsWithGesture
        if (other.showsSecondaryOnlyButton.hasValue()) showsSecondaryOnlyButton = other.showsSecondaryOnlyButton
        if (other.compactTopColumn.hasValue()) compactTopColumn = other.compactTopColumn
        if (other.testID.hasValue()) testID = other.testID
    }

    fun mergeWithDefault(defaults: SplitViewGroupOptions) {
        if (!displayMode.hasValue()) displayMode = defaults.displayMode
        if (!primaryEdge.hasValue()) primaryEdge = defaults.primaryEdge
        if (!width.hasValue()) width = defaults.width
        if (!minWidth.hasValue()) minWidth = defaults.minWidth
        if (!maxWidth.hasValue()) maxWidth = defaults.maxWidth
        if (!primaryBackgroundStyle.hasValue()) primaryBackgroundStyle = defaults.primaryBackgroundStyle
        supplementary.mergeWithDefault(defaults.supplementary)
        inspector.mergeWithDefault(defaults.inspector)
        if (!splitBehavior.hasValue()) splitBehavior = defaults.splitBehavior
        if (!displayModeButton.hasValue()) displayModeButton = defaults.displayModeButton
        if (!presentsWithGesture.hasValue()) presentsWithGesture = defaults.presentsWithGesture
        if (!showsSecondaryOnlyButton.hasValue()) showsSecondaryOnlyButton = defaults.showsSecondaryOnlyButton
        if (!compactTopColumn.hasValue()) compactTopColumn = defaults.compactTopColumn
        if (!testID.hasValue()) testID = defaults.testID
    }

    fun copy() = SplitViewGroupOptions().also { it.mergeWith(this) }

    companion object {
        private val DISPLAY_MODES = setOf(
            "automatic", "secondaryOnly", "oneBesideSecondary", "oneOverSecondary", "twoBesideSecondary",
            "twoOverSecondary", "twoDisplaceSecondary",
        )
        private val PRIMARY_EDGES = setOf("leading", "trailing")
        private val BACKGROUND_STYLES = setOf("sidebar", "none")
        private val SPLIT_BEHAVIORS = setOf("automatic", "tile", "overlay", "displace")
        private val DISPLAY_MODE_BUTTONS = setOf("automatic", "never", "always")
        private val COLUMNS = setOf("primary", "secondary")

        @JvmStatic
        fun parse(map: ReadableMap?): SplitViewGroupOptions {
            val options = SplitViewGroupOptions()
            if (map == null) return options
            options.displayMode = TextParser.parse(map, "displayMode", DISPLAY_MODES)
            options.primaryEdge = TextParser.parse(map, "primaryEdge", PRIMARY_EDGES)
            options.width = FractionParser.parse(map, "width")
            options.minWidth = FractionParser.parse(map, "minWidth")
            options.maxWidth = FractionParser.parse(map, "maxWidth")
            options.primaryBackgroundStyle = TextParser.parse(map, "primaryBackgroundStyle", BACKGROUND_STYLES)
            options.supplementary = map.group("supplementary") { SplitViewColumnOptions.parse(it) }
            options.inspector = map.group("inspector") { SplitViewInspectorOptions.parse(it) }
            options.splitBehavior = TextParser.parse(map, "splitBehavior", SPLIT_BEHAVIORS)
            options.displayModeButton = TextParser.parse(map, "displayModeButton", DISPLAY_MODE_BUTTONS)
            options.presentsWithGesture = BoolParser.parse(map, "presentsWithGesture")
            options.showsSecondaryOnlyButton = BoolParser.parse(map, "showsSecondaryOnlyButton")
            options.compactTopColumn = TextParser.parse(map, "compactTopColumn", COLUMNS)
            options.testID = TextParser.parse(map, "testID")
            return options
        }
    }
}

/** `splitView.supplementary`: the width of a column, in points (iOS). */
class SplitViewColumnOptions {
    @JvmField var width: Fraction = NullFraction()
    @JvmField var minWidth: Fraction = NullFraction()
    @JvmField var maxWidth: Fraction = NullFraction()

    fun hasValue(): Boolean = width.hasValue() || minWidth.hasValue() || maxWidth.hasValue()

    fun mergeWith(other: SplitViewColumnOptions) {
        if (other.width.hasValue()) width = other.width
        if (other.minWidth.hasValue()) minWidth = other.minWidth
        if (other.maxWidth.hasValue()) maxWidth = other.maxWidth
    }

    fun mergeWithDefault(defaults: SplitViewColumnOptions) {
        if (!width.hasValue()) width = defaults.width
        if (!minWidth.hasValue()) minWidth = defaults.minWidth
        if (!maxWidth.hasValue()) maxWidth = defaults.maxWidth
    }

    fun copy() = SplitViewColumnOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): SplitViewColumnOptions {
            val options = SplitViewColumnOptions()
            if (map == null) return options
            options.width = FractionParser.parse(map, "width")
            options.minWidth = FractionParser.parse(map, "minWidth")
            options.maxWidth = FractionParser.parse(map, "maxWidth")
            return options
        }
    }
}

/** `splitView.inspector`: the inspector column, its width and whether it shows (iOS). */
class SplitViewInspectorOptions {
    @JvmField var width: Fraction = NullFraction()
    @JvmField var minWidth: Fraction = NullFraction()
    @JvmField var maxWidth: Fraction = NullFraction()
    @JvmField var visible: Bool = NullBool()

    fun hasValue(): Boolean = width.hasValue() || minWidth.hasValue() || maxWidth.hasValue() || visible.hasValue()

    fun mergeWith(other: SplitViewInspectorOptions) {
        if (other.width.hasValue()) width = other.width
        if (other.minWidth.hasValue()) minWidth = other.minWidth
        if (other.maxWidth.hasValue()) maxWidth = other.maxWidth
        if (other.visible.hasValue()) visible = other.visible
    }

    fun mergeWithDefault(defaults: SplitViewInspectorOptions) {
        if (!width.hasValue()) width = defaults.width
        if (!minWidth.hasValue()) minWidth = defaults.minWidth
        if (!maxWidth.hasValue()) maxWidth = defaults.maxWidth
        if (!visible.hasValue()) visible = defaults.visible
    }

    fun copy() = SplitViewInspectorOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): SplitViewInspectorOptions {
            val options = SplitViewInspectorOptions()
            if (map == null) return options
            options.width = FractionParser.parse(map, "width")
            options.minWidth = FractionParser.parse(map, "minWidth")
            options.maxWidth = FractionParser.parse(map, "maxWidth")
            options.visible = BoolParser.parse(map, "visible")
            return options
        }
    }
}

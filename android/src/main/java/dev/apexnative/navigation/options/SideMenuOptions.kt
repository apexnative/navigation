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
 * `sideMenu`: the sides of a side menu and how they open. `left.visible` and `right.visible` act once and are never
 * kept in a node's own options.
 */
class SideMenuOptions {
    @JvmField var left = SideMenuSideOptions()
    @JvmField var right = SideMenuSideOptions()
    @JvmField var openGestureMode: Text = NullText()
    @JvmField var animationType: Text = NullText()
    @JvmField var testID: Text = NullText()

    fun hasValue(): Boolean = left.hasValue() || right.hasValue() || openGestureMode.hasValue() ||
        animationType.hasValue() || testID.hasValue()

    fun mergeWith(other: SideMenuOptions) {
        left.mergeWith(other.left)
        right.mergeWith(other.right)
        if (other.openGestureMode.hasValue()) openGestureMode = other.openGestureMode
        if (other.animationType.hasValue()) animationType = other.animationType
        if (other.testID.hasValue()) testID = other.testID
    }

    fun mergeWithDefault(defaults: SideMenuOptions) {
        left.mergeWithDefault(defaults.left)
        right.mergeWithDefault(defaults.right)
        if (!openGestureMode.hasValue()) openGestureMode = defaults.openGestureMode
        if (!animationType.hasValue()) animationType = defaults.animationType
        if (!testID.hasValue()) testID = defaults.testID
    }

    fun copy() = SideMenuOptions().also { it.mergeWith(this) }

    companion object {
        private val OPEN_GESTURE_MODES = setOf("bezel", "entireScreen")
        private val ANIMATION_TYPES = setOf("parallax", "slide", "slide-and-scale", "door")

        @JvmStatic
        fun parse(map: ReadableMap?): SideMenuOptions {
            val options = SideMenuOptions()
            if (map == null) return options
            options.left = map.group("left") { SideMenuSideOptions.parse(it) }
            options.right = map.group("right") { SideMenuSideOptions.parse(it) }
            options.openGestureMode = TextParser.parse(map, "openGestureMode", OPEN_GESTURE_MODES)
            options.animationType = TextParser.parse(map, "animationType", ANIMATION_TYPES)
            options.testID = TextParser.parse(map, "testID")
            return options
        }
    }
}

/** `sideMenu.left`, `sideMenu.right`: one side of a side menu. */
class SideMenuSideOptions {
    /** Opens or closes the side once: never kept in a node's own options. */
    @JvmField var visible: Bool = NullBool()
    @JvmField var animate: Bool = NullBool()
    @JvmField var enabled: Bool = NullBool()
    @JvmField var width: Fraction = NullFraction()
    @JvmField var height: Fraction = NullFraction()
    @JvmField var shouldStretchDrawer: Bool = NullBool()
    @JvmField var animationVelocity: Fraction = NullFraction()
    @JvmField var openMode: Text = NullText()
    @JvmField var glass: Bool = NullBool()
    @JvmField var testID: Text = NullText()

    fun hasValue(): Boolean = visible.hasValue() || animate.hasValue() || enabled.hasValue() || width.hasValue() ||
        height.hasValue() || shouldStretchDrawer.hasValue() || animationVelocity.hasValue() || openMode.hasValue() ||
        glass.hasValue() || testID.hasValue()

    fun mergeWith(other: SideMenuSideOptions) {
        if (other.visible.hasValue()) visible = other.visible
        if (other.animate.hasValue()) animate = other.animate
        if (other.enabled.hasValue()) enabled = other.enabled
        if (other.width.hasValue()) width = other.width
        if (other.height.hasValue()) height = other.height
        if (other.shouldStretchDrawer.hasValue()) shouldStretchDrawer = other.shouldStretchDrawer
        if (other.animationVelocity.hasValue()) animationVelocity = other.animationVelocity
        if (other.openMode.hasValue()) openMode = other.openMode
        if (other.glass.hasValue()) glass = other.glass
        if (other.testID.hasValue()) testID = other.testID
    }

    fun mergeWithDefault(defaults: SideMenuSideOptions) {
        if (!visible.hasValue()) visible = defaults.visible
        if (!animate.hasValue()) animate = defaults.animate
        if (!enabled.hasValue()) enabled = defaults.enabled
        if (!width.hasValue()) width = defaults.width
        if (!height.hasValue()) height = defaults.height
        if (!shouldStretchDrawer.hasValue()) shouldStretchDrawer = defaults.shouldStretchDrawer
        if (!animationVelocity.hasValue()) animationVelocity = defaults.animationVelocity
        if (!openMode.hasValue()) openMode = defaults.openMode
        if (!glass.hasValue()) glass = defaults.glass
        if (!testID.hasValue()) testID = defaults.testID
    }

    fun copy() = SideMenuSideOptions().also { it.mergeWith(this) }

    companion object {
        private val OPEN_MODES = setOf("aboveContent", "pushContent")

        @JvmStatic
        fun parse(map: ReadableMap?): SideMenuSideOptions {
            val options = SideMenuSideOptions()
            if (map == null) return options
            options.visible = BoolParser.parse(map, "visible")
            options.animate = BoolParser.parse(map, "animate")
            options.enabled = BoolParser.parse(map, "enabled")
            options.width = FractionParser.parse(map, "width")
            options.height = FractionParser.parse(map, "height")
            options.shouldStretchDrawer = BoolParser.parse(map, "shouldStretchDrawer")
            options.animationVelocity = FractionParser.parse(map, "animationVelocity")
            options.openMode = TextParser.parse(map, "openMode", OPEN_MODES)
            options.glass = BoolParser.parse(map, "glass")
            options.testID = TextParser.parse(map, "testID")
            return options
        }
    }
}

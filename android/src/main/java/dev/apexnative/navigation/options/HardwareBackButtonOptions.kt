package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.TextParser

/** `hardwareBackButton`: what the system's back does in the navigation. */
class HardwareBackButtonOptions {
    @JvmField var bottomTabsOnPress: Text = NullText()
    /** Internal. */
    @JvmField var popStackOnPress: Bool = NullBool()
    /** Internal. */
    @JvmField var dismissModalOnPress: Bool = NullBool()
    /**
     * `systemAnimation`: `false` keeps back in the app on the first screen too, so the system doesn't play its
     * animation of going home; the press then does the activity's own back.
     */
    @JvmField var systemAnimation: Bool = NullBool()

    fun hasValue(): Boolean = bottomTabsOnPress.hasValue() || popStackOnPress.hasValue() ||
        dismissModalOnPress.hasValue() || systemAnimation.hasValue()

    fun mergeWith(other: HardwareBackButtonOptions) {
        if (other.bottomTabsOnPress.hasValue()) bottomTabsOnPress = other.bottomTabsOnPress
        if (other.popStackOnPress.hasValue()) popStackOnPress = other.popStackOnPress
        if (other.dismissModalOnPress.hasValue()) dismissModalOnPress = other.dismissModalOnPress
        if (other.systemAnimation.hasValue()) systemAnimation = other.systemAnimation
    }

    fun mergeWithDefault(defaults: HardwareBackButtonOptions) {
        if (!bottomTabsOnPress.hasValue()) bottomTabsOnPress = defaults.bottomTabsOnPress
        if (!popStackOnPress.hasValue()) popStackOnPress = defaults.popStackOnPress
        if (!dismissModalOnPress.hasValue()) dismissModalOnPress = defaults.dismissModalOnPress
        if (!systemAnimation.hasValue()) systemAnimation = defaults.systemAnimation
    }

    fun copy() = HardwareBackButtonOptions().also { it.mergeWith(this) }

    companion object {
        private val BOTTOM_TABS_ON_PRESS = setOf("exit", "first", "previous")

        @JvmStatic
        fun parse(map: ReadableMap?): HardwareBackButtonOptions {
            val options = HardwareBackButtonOptions()
            if (map == null) return options
            options.bottomTabsOnPress = TextParser.parse(map, "bottomTabsOnPress", BOTTOM_TABS_ON_PRESS)
            options.popStackOnPress = BoolParser.parse(map, "popStackOnPress")
            options.dismissModalOnPress = BoolParser.parse(map, "dismissModalOnPress")
            options.systemAnimation = BoolParser.parse(map, "systemAnimation")
            return options
        }
    }
}

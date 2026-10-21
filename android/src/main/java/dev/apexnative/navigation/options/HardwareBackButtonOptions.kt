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

    fun hasValue(): Boolean = bottomTabsOnPress.hasValue() || popStackOnPress.hasValue() ||
        dismissModalOnPress.hasValue()

    fun mergeWith(other: HardwareBackButtonOptions) {
        if (other.bottomTabsOnPress.hasValue()) bottomTabsOnPress = other.bottomTabsOnPress
        if (other.popStackOnPress.hasValue()) popStackOnPress = other.popStackOnPress
        if (other.dismissModalOnPress.hasValue()) dismissModalOnPress = other.dismissModalOnPress
    }

    fun mergeWithDefault(defaults: HardwareBackButtonOptions) {
        if (!bottomTabsOnPress.hasValue()) bottomTabsOnPress = defaults.bottomTabsOnPress
        if (!popStackOnPress.hasValue()) popStackOnPress = defaults.popStackOnPress
        if (!dismissModalOnPress.hasValue()) dismissModalOnPress = defaults.dismissModalOnPress
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
            return options
        }
    }
}

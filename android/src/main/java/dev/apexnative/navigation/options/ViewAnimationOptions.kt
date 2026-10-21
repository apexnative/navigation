package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.utils.DevelopmentLog

/**
 * `ViewAnimationOptions`: the properties of a view an animation moves. Open: the animations of the screen, the top bar
 * and the bottom tabs are its subclasses (a TS `extends`), each with a `mergeWith` of its own class that calls this
 * one's first and then merges its flags by key. The properties layer as one set.
 */
open class ViewAnimationOptions {
    @JvmField var x = PropertyAnimationOptions()
    @JvmField var y = PropertyAnimationOptions()
    @JvmField var translationX = PropertyAnimationOptions()
    @JvmField var translationY = PropertyAnimationOptions()
    @JvmField var alpha = PropertyAnimationOptions()
    @JvmField var scaleX = PropertyAnimationOptions()
    @JvmField var scaleY = PropertyAnimationOptions()
    @JvmField var rotationX = PropertyAnimationOptions()
    @JvmField var rotationY = PropertyAnimationOptions()
    @JvmField var rotation = PropertyAnimationOptions()

    open fun hasValue(): Boolean = hasProperty()

    /**
     * Whether any of the ten properties has a value. Not virtual: the sides' flags (`enabled`, `waitForRender`) never
     * count here, so a side of only flags keeps the properties of the layer under it.
     */
    fun hasProperty(): Boolean = x.hasValue() || y.hasValue() || translationX.hasValue() ||
        translationY.hasValue() || alpha.hasValue() || scaleX.hasValue() || scaleY.hasValue() ||
        rotationX.hasValue() || rotationY.hasValue() || rotation.hasValue()

    /**
     * The properties of [other] over these: the set of properties is replaced whole, by copies, when [other] has any
     * property; else these stay. The sides' flags merge by key in the subclasses.
     */
    fun mergeWith(other: ViewAnimationOptions) {
        if (other.hasProperty()) takeProperties(other)
    }

    /** The properties of [defaults] under these: taken whole, by copies, only when this side has none. */
    fun mergeWithDefault(defaults: ViewAnimationOptions) {
        if (!hasProperty()) takeProperties(defaults)
    }

    // Copies, never the groups of [other]: a property's curve merges in place, and resolved options are shared.
    private fun takeProperties(other: ViewAnimationOptions) {
        x = other.x.copy()
        y = other.y.copy()
        translationX = other.translationX.copy()
        translationY = other.translationY.copy()
        alpha = other.alpha.copy()
        scaleX = other.scaleX.copy()
        scaleY = other.scaleY.copy()
        rotationX = other.rotationX.copy()
        rotationY = other.rotationY.copy()
        rotation = other.rotation.copy()
    }

    open fun copy(): ViewAnimationOptions = ViewAnimationOptions().also { it.mergeWith(this) }

    // Reads the fields of this class from [map]. A key of the side that isn't one of [known] is skipped, and so is a
    // property without `from` and `to`, each with a warning.
    protected fun readViewAnimationOptions(map: ReadableMap, known: Set<String>) {
        x = readProperty(map, "x")
        y = readProperty(map, "y")
        translationX = readProperty(map, "translationX")
        translationY = readProperty(map, "translationY")
        alpha = readProperty(map, "alpha")
        scaleX = readProperty(map, "scaleX")
        scaleY = readProperty(map, "scaleY")
        rotationX = readProperty(map, "rotationX")
        rotationY = readProperty(map, "rotationY")
        rotation = readProperty(map, "rotation")
        val keys = map.keySetIterator()
        while (keys.hasNextKey()) {
            val key = keys.nextKey()
            if (key !in known) DevelopmentLog.warn("Animation of '$key' is not supported: skipped")
        }
    }

    private fun readProperty(map: ReadableMap, key: String): PropertyAnimationOptions {
        if (!map.has(key)) return PropertyAnimationOptions()
        val property = map.group(key) { PropertyAnimationOptions.parse(it) }
        if (property.from.hasValue() || property.to.hasValue()) return property
        DevelopmentLog.warn("Animation of '$key' without 'from' and 'to': skipped")
        return PropertyAnimationOptions()
    }

    companion object {
        /** The keys of the ten properties of a side. */
        @JvmField
        val PROPERTIES = setOf(
            "x", "y", "translationX", "translationY", "alpha", "scaleX", "scaleY", "rotationX", "rotationY", "rotation",
        )

        @JvmStatic
        fun parse(map: ReadableMap?): ViewAnimationOptions {
            val options = ViewAnimationOptions()
            if (map == null) return options
            options.readViewAnimationOptions(map, PROPERTIES)
            return options
        }
    }
}

/** `ScreenAnimationOptions`: the animation of a screen's content coming or going. */
class ScreenAnimationOptions : ViewAnimationOptions() {
    @JvmField var enabled: Bool = NullBool()
    @JvmField var waitForRender: Bool = NullBool()

    override fun hasValue(): Boolean = super.hasValue() || enabled.hasValue() || waitForRender.hasValue()

    fun mergeWith(other: ScreenAnimationOptions) {
        super.mergeWith(other)
        if (other.enabled.hasValue()) enabled = other.enabled
        if (other.waitForRender.hasValue()) waitForRender = other.waitForRender
    }

    fun mergeWithDefault(defaults: ScreenAnimationOptions) {
        super.mergeWithDefault(defaults)
        if (!enabled.hasValue()) enabled = defaults.enabled
        if (!waitForRender.hasValue()) waitForRender = defaults.waitForRender
    }

    override fun copy(): ScreenAnimationOptions = ScreenAnimationOptions().also { it.mergeWith(this) }

    // Reads the fields of this class from [map], after those of its parent.
    private fun readScreenAnimationOptions(map: ReadableMap) {
        readViewAnimationOptions(map, KNOWN)
        enabled = BoolParser.parse(map, "enabled")
        waitForRender = BoolParser.parse(map, "waitForRender")
    }

    companion object {
        private val KNOWN = ViewAnimationOptions.PROPERTIES + setOf("enabled", "waitForRender")

        @JvmStatic
        fun parse(map: ReadableMap?): ScreenAnimationOptions {
            val options = ScreenAnimationOptions()
            if (map == null) return options
            options.readScreenAnimationOptions(map)
            return options
        }
    }
}

/** `TopBarAnimationOptions`: the animation of the top bar. */
class TopBarAnimationOptions : ViewAnimationOptions() {
    @JvmField var enabled: Bool = NullBool()

    override fun hasValue(): Boolean = super.hasValue() || enabled.hasValue()

    fun mergeWith(other: TopBarAnimationOptions) {
        super.mergeWith(other)
        if (other.enabled.hasValue()) enabled = other.enabled
    }

    fun mergeWithDefault(defaults: TopBarAnimationOptions) {
        super.mergeWithDefault(defaults)
        if (!enabled.hasValue()) enabled = defaults.enabled
    }

    override fun copy(): TopBarAnimationOptions = TopBarAnimationOptions().also { it.mergeWith(this) }

    // Reads the fields of this class from [map], after those of its parent.
    private fun readTopBarAnimationOptions(map: ReadableMap) {
        readViewAnimationOptions(map, KNOWN)
        enabled = BoolParser.parse(map, "enabled")
    }

    companion object {
        // A bar's side has no `waitForRender` (`animations.setStackRoot.topBar.waitForRender`).
        private val KNOWN = ViewAnimationOptions.PROPERTIES + "enabled"

        @JvmStatic
        fun parse(map: ReadableMap?): TopBarAnimationOptions {
            val options = TopBarAnimationOptions()
            if (map == null) return options
            options.readTopBarAnimationOptions(map)
            return options
        }
    }
}

/** `BottomTabsAnimationOptions`: the animation of the bottom tabs. */
class BottomTabsAnimationOptions : ViewAnimationOptions() {
    @JvmField var enabled: Bool = NullBool()

    override fun hasValue(): Boolean = super.hasValue() || enabled.hasValue()

    fun mergeWith(other: BottomTabsAnimationOptions) {
        super.mergeWith(other)
        if (other.enabled.hasValue()) enabled = other.enabled
    }

    fun mergeWithDefault(defaults: BottomTabsAnimationOptions) {
        super.mergeWithDefault(defaults)
        if (!enabled.hasValue()) enabled = defaults.enabled
    }

    override fun copy(): BottomTabsAnimationOptions = BottomTabsAnimationOptions().also { it.mergeWith(this) }

    // Reads the fields of this class from [map], after those of its parent.
    private fun readBottomTabsAnimationOptions(map: ReadableMap) {
        readViewAnimationOptions(map, KNOWN)
        enabled = BoolParser.parse(map, "enabled")
    }

    companion object {
        // A bar's side has no `waitForRender` (`animations.setStackRoot.topBar.waitForRender`).
        private val KNOWN = ViewAnimationOptions.PROPERTIES + "enabled"

        @JvmStatic
        fun parse(map: ReadableMap?): BottomTabsAnimationOptions {
            val options = BottomTabsAnimationOptions()
            if (map == null) return options
            options.readBottomTabsAnimationOptions(map)
            return options
        }
    }
}

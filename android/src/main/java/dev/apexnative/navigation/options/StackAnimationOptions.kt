package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.parsers.BoolParser

/** `EnterExitAnimationOptions`: the animation of what comes (`enter`) and what goes (`exit`). */
class EnterExitAnimationOptions {
    @JvmField var enter = ScreenAnimationOptions()
    @JvmField var exit = ScreenAnimationOptions()

    fun hasValue(): Boolean = enter.hasValue() || exit.hasValue()

    fun mergeWith(other: EnterExitAnimationOptions) {
        enter.mergeWith(other.enter)
        exit.mergeWith(other.exit)
    }

    fun mergeWithDefault(defaults: EnterExitAnimationOptions) {
        enter.mergeWithDefault(defaults.enter)
        exit.mergeWithDefault(defaults.exit)
    }

    fun copy() = EnterExitAnimationOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): EnterExitAnimationOptions {
            val options = EnterExitAnimationOptions()
            if (map == null) return options
            options.enter = map.group("enter") { ScreenAnimationOptions.parse(it) }
            options.exit = map.group("exit") { ScreenAnimationOptions.parse(it) }
            return options
        }
    }
}

/** `EnterExitAnimationOptions<TopBarAnimationOptions>`: the top bar's `enter` and `exit`. */
class TopBarEnterExitAnimationOptions {
    @JvmField var enter = TopBarAnimationOptions()
    @JvmField var exit = TopBarAnimationOptions()

    fun hasValue(): Boolean = enter.hasValue() || exit.hasValue()

    fun mergeWith(other: TopBarEnterExitAnimationOptions) {
        enter.mergeWith(other.enter)
        exit.mergeWith(other.exit)
    }

    fun mergeWithDefault(defaults: TopBarEnterExitAnimationOptions) {
        enter.mergeWithDefault(defaults.enter)
        exit.mergeWithDefault(defaults.exit)
    }

    fun copy() = TopBarEnterExitAnimationOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): TopBarEnterExitAnimationOptions {
            val options = TopBarEnterExitAnimationOptions()
            if (map == null) return options
            options.enter = map.group("enter") { TopBarAnimationOptions.parse(it) }
            options.exit = map.group("exit") { TopBarAnimationOptions.parse(it) }
            return options
        }
    }
}

/** `EnterExitAnimationOptions<BottomTabsAnimationOptions>`: the bottom tabs' `enter` and `exit`. */
class BottomTabsEnterExitAnimationOptions {
    @JvmField var enter = BottomTabsAnimationOptions()
    @JvmField var exit = BottomTabsAnimationOptions()

    fun hasValue(): Boolean = enter.hasValue() || exit.hasValue()

    fun mergeWith(other: BottomTabsEnterExitAnimationOptions) {
        enter.mergeWith(other.enter)
        exit.mergeWith(other.exit)
    }

    fun mergeWithDefault(defaults: BottomTabsEnterExitAnimationOptions) {
        enter.mergeWithDefault(defaults.enter)
        exit.mergeWithDefault(defaults.exit)
    }

    fun copy() = BottomTabsEnterExitAnimationOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): BottomTabsEnterExitAnimationOptions {
            val options = BottomTabsEnterExitAnimationOptions()
            if (map == null) return options
            options.enter = map.group("enter") { BottomTabsAnimationOptions.parse(it) }
            options.exit = map.group("exit") { BottomTabsAnimationOptions.parse(it) }
            return options
        }
    }
}

/**
 * `StackAnimationOptions`: the base of the animations of `push`, `pop` and `setStackRoot`; over the shared elements and
 * element transitions of [LayoutAnimation].
 */
open class StackAnimationOptions : LayoutAnimation() {
    @JvmField var enabled: Bool = NullBool()
    @JvmField var waitForRender: Bool = NullBool()
    @JvmField var content = EnterExitAnimationOptions()
    @JvmField var topBar = TopBarEnterExitAnimationOptions()
    @JvmField var bottomTabs = BottomTabsEnterExitAnimationOptions()

    override fun hasValue(): Boolean = super.hasValue() || enabled.hasValue() || waitForRender.hasValue() ||
        content.hasValue() || topBar.hasValue() || bottomTabs.hasValue()

    fun mergeWith(other: StackAnimationOptions) {
        super.mergeWith(other)
        if (other.enabled.hasValue()) enabled = other.enabled
        if (other.waitForRender.hasValue()) waitForRender = other.waitForRender
        content.mergeWith(other.content)
        topBar.mergeWith(other.topBar)
        bottomTabs.mergeWith(other.bottomTabs)
    }

    fun mergeWithDefault(defaults: StackAnimationOptions) {
        super.mergeWithDefault(defaults)
        if (!enabled.hasValue()) enabled = defaults.enabled
        if (!waitForRender.hasValue()) waitForRender = defaults.waitForRender
        content.mergeWithDefault(defaults.content)
        topBar.mergeWithDefault(defaults.topBar)
        bottomTabs.mergeWithDefault(defaults.bottomTabs)
    }

    override fun copy(): StackAnimationOptions = StackAnimationOptions().also { it.mergeWith(this) }

    // Reads the fields of this class from [map], after those of its parent.
    protected fun readStackAnimationOptions(map: ReadableMap) {
        readAnimation(map)
        enabled = BoolParser.parse(map, "enabled")
        waitForRender = BoolParser.parse(map, "waitForRender")
        content = map.group("content") { EnterExitAnimationOptions.parse(it) }
        topBar = map.group("topBar") { TopBarEnterExitAnimationOptions.parse(it) }
        bottomTabs = map.group("bottomTabs") { BottomTabsEnterExitAnimationOptions.parse(it) }
    }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): StackAnimationOptions {
            val options = StackAnimationOptions()
            if (map == null) return options
            options.readStackAnimationOptions(map)
            return options
        }
    }
}

/** `StackPushAnimationOptions`: the animation of `push`. */
class StackPushAnimationOptions : StackAnimationOptions() {
    @JvmField var zoom = ZoomTransitionOptions()

    override fun hasValue(): Boolean = super.hasValue() || zoom.hasValue()

    fun mergeWith(other: StackPushAnimationOptions) {
        super.mergeWith(other)
        zoom.mergeWith(other.zoom)
    }

    fun mergeWithDefault(defaults: StackPushAnimationOptions) {
        super.mergeWithDefault(defaults)
        zoom.mergeWithDefault(defaults.zoom)
    }

    override fun copy(): StackPushAnimationOptions = StackPushAnimationOptions().also { it.mergeWith(this) }

    // Reads the fields of this class from [map], after those of its parent.
    private fun readStackPushAnimationOptions(map: ReadableMap) {
        readStackAnimationOptions(map)
        zoom = map.group("zoom") { ZoomTransitionOptions.parse(it) }
    }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): StackPushAnimationOptions {
            val options = StackPushAnimationOptions()
            if (map == null) return options
            options.readStackPushAnimationOptions(map)
            return options
        }
    }
}

/**
 * `StackPopAnimationOptions`: the animation of `pop`, and `preview`: whether a back gesture over the stack previews the
 * pop of its top (Android 14+); `false` pops it when the gesture is released, as on Android 13.
 */
class StackPopAnimationOptions : StackAnimationOptions() {
    @JvmField var preview: Bool = NullBool()

    override fun hasValue(): Boolean = super.hasValue() || preview.hasValue()

    fun mergeWith(other: StackPopAnimationOptions) {
        super.mergeWith(other)
        if (other.preview.hasValue()) preview = other.preview
    }

    fun mergeWithDefault(defaults: StackPopAnimationOptions) {
        super.mergeWithDefault(defaults)
        if (!preview.hasValue()) preview = defaults.preview
    }

    override fun copy(): StackPopAnimationOptions = StackPopAnimationOptions().also { it.mergeWith(this) }

    // Reads the fields of this class from [map], after those of its parent.
    private fun readStackPopAnimationOptions(map: ReadableMap) {
        readStackAnimationOptions(map)
        preview = BoolParser.parse(map, "preview")
    }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): StackPopAnimationOptions {
            val options = StackPopAnimationOptions()
            if (map == null) return options
            options.readStackPopAnimationOptions(map)
            return options
        }
    }
}

/** `SetStackRootAnimationOptions`: the animation of `setStackRoot`. */
class SetStackRootAnimationOptions : StackAnimationOptions() {
    @JvmField var asPush: Bool = NullBool()

    override fun hasValue(): Boolean = super.hasValue() || asPush.hasValue()

    fun mergeWith(other: SetStackRootAnimationOptions) {
        super.mergeWith(other)
        if (other.asPush.hasValue()) asPush = other.asPush
    }

    fun mergeWithDefault(defaults: SetStackRootAnimationOptions) {
        super.mergeWithDefault(defaults)
        if (!asPush.hasValue()) asPush = defaults.asPush
    }

    override fun copy(): SetStackRootAnimationOptions = SetStackRootAnimationOptions().also { it.mergeWith(this) }

    // Reads the fields of this class from [map], after those of its parent.
    private fun readSetStackRootAnimationOptions(map: ReadableMap) {
        readStackAnimationOptions(map)
        asPush = BoolParser.parse(map, "asPush")
    }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): SetStackRootAnimationOptions {
            val options = SetStackRootAnimationOptions()
            if (map == null) return options
            options.readSetStackRootAnimationOptions(map)
            return options
        }
    }
}

/**
 * `ModalAnimationOptions`: the animation of `showModal` and `dismissModal`: what comes (`enter`) and what goes
 * (`exit`), the shared elements and element transitions of [LayoutAnimation], and the zoom.
 */
class ModalAnimationOptions : LayoutAnimation() {
    @JvmField var enter = ScreenAnimationOptions()
    @JvmField var exit = ScreenAnimationOptions()
    @JvmField var zoom = ZoomTransitionOptions()

    override fun hasValue(): Boolean = super.hasValue() || enter.hasValue() || exit.hasValue() || zoom.hasValue()

    fun mergeWith(other: ModalAnimationOptions) {
        super.mergeWith(other)
        enter.mergeWith(other.enter)
        exit.mergeWith(other.exit)
        zoom.mergeWith(other.zoom)
    }

    fun mergeWithDefault(defaults: ModalAnimationOptions) {
        super.mergeWithDefault(defaults)
        enter.mergeWithDefault(defaults.enter)
        exit.mergeWithDefault(defaults.exit)
        zoom.mergeWithDefault(defaults.zoom)
    }

    override fun copy(): ModalAnimationOptions = ModalAnimationOptions().also { it.mergeWith(this) }

    // Reads the fields of this class from [map], after those of its parent.
    private fun readModalAnimationOptions(map: ReadableMap) {
        readAnimation(map)
        enter = map.group("enter") { ScreenAnimationOptions.parse(it) }
        exit = map.group("exit") { ScreenAnimationOptions.parse(it) }
        zoom = map.group("zoom") { ZoomTransitionOptions.parse(it) }
    }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): ModalAnimationOptions {
            val options = ModalAnimationOptions()
            if (map == null) return options
            options.readModalAnimationOptions(map)
            return options
        }
    }
}

package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableMap

/**
 * `animations` (`AnimationOptions`): the animations of the commands that change the hierarchy, each for the next time
 * its command runs.
 */
class AnimationsOptions {
    @JvmField var push = StackPushAnimationOptions()
    @JvmField var pop = StackPopAnimationOptions()
    @JvmField var setStackRoot = SetStackRootAnimationOptions()
    @JvmField var setRoot = EnterExitAnimationOptions()
    @JvmField var showModal = ModalAnimationOptions()
    @JvmField var dismissModal = ModalAnimationOptions()

    fun hasValue(): Boolean = push.hasValue() || pop.hasValue() || setStackRoot.hasValue() || setRoot.hasValue() ||
        showModal.hasValue() || dismissModal.hasValue()

    fun mergeWith(other: AnimationsOptions) {
        push.mergeWith(other.push)
        pop.mergeWith(other.pop)
        setStackRoot.mergeWith(other.setStackRoot)
        setRoot.mergeWith(other.setRoot)
        showModal.mergeWith(other.showModal)
        dismissModal.mergeWith(other.dismissModal)
    }

    fun mergeWithDefault(defaults: AnimationsOptions) {
        push.mergeWithDefault(defaults.push)
        pop.mergeWithDefault(defaults.pop)
        setStackRoot.mergeWithDefault(defaults.setStackRoot)
        setRoot.mergeWithDefault(defaults.setRoot)
        showModal.mergeWithDefault(defaults.showModal)
        dismissModal.mergeWithDefault(defaults.dismissModal)
    }

    fun copy() = AnimationsOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): AnimationsOptions {
            val options = AnimationsOptions()
            if (map == null) return options
            options.push = map.group("push") { StackPushAnimationOptions.parse(it) }
            options.pop = map.group("pop") { StackPopAnimationOptions.parse(it) }
            options.setStackRoot = map.group("setStackRoot") { SetStackRootAnimationOptions.parse(it) }
            options.setRoot = map.group("setRoot") { EnterExitAnimationOptions.parse(it) }
            options.showModal = map.group("showModal") { ModalAnimationOptions.parse(it) }
            options.dismissModal = map.group("dismissModal") { ModalAnimationOptions.parse(it) }
            return options
        }
    }
}

package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableMap

/**
 * The animation of a layout coming in or going out (`animations.push`, `pop`, `setStackRoot`, `showModal`,
 * `dismissModal`): its shared elements (`sharedElementTransitions`) and its element transitions. Open: the animation of
 * each command is its own group over these fields (a subclass with `mergeWith` of its own class, which calls this one's
 * first).
 */
open class LayoutAnimation(
    @JvmField var sharedElements: SharedElements = SharedElements(),
    @JvmField var elementTransitions: ElementTransitions = ElementTransitions(),
) {
    open fun hasValue(): Boolean = sharedElements.hasValue() || elementTransitions.hasValue()

    fun mergeWith(other: LayoutAnimation) {
        sharedElements.mergeWith(other.sharedElements)
        elementTransitions.mergeWith(other.elementTransitions)
    }

    fun mergeWithDefault(defaults: LayoutAnimation) {
        sharedElements.mergeWithDefault(defaults.sharedElements)
        elementTransitions.mergeWithDefault(defaults.elementTransitions)
    }

    open fun copy(): LayoutAnimation = LayoutAnimation().also { it.mergeWith(this) }

    /** Reads the fields of this class from [map] into this animation; a subclass reads its own after it. */
    protected fun readAnimation(map: ReadableMap) {
        sharedElements =
            SharedElements(map.groupList("sharedElementTransitions") { SharedElementTransitionOptions.parse(it) })
        elementTransitions =
            ElementTransitions(map.groupList("elementTransitions") { ElementTransitionOptions.parse(it) })
    }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): LayoutAnimation {
            val animation = LayoutAnimation()
            if (map != null) animation.readAnimation(map)
            return animation
        }
    }
}

/** `sharedElementTransitions`: the shared elements of an animation; null when the options don't give them. */
class SharedElements(@JvmField var elements: List<SharedElementTransitionOptions>? = null) {
    /** The shared elements; none when the options don't give them. */
    fun get(): List<SharedElementTransitionOptions> = elements.orEmpty()

    /** The options give the list, `[]` included. */
    fun hasValue(): Boolean = elements != null

    fun mergeWith(other: SharedElements) {
        other.elements?.let { elements = it.map(SharedElementTransitionOptions::copy) }
    }

    fun mergeWithDefault(defaults: SharedElements) {
        if (elements == null) elements = defaults.elements?.map(SharedElementTransitionOptions::copy)
    }

    fun copy() = SharedElements().also { it.mergeWith(this) }
}

/**
 * `elementTransitions`: views of one screen that move, fade or scale on their own during an animation; null when the
 * options don't give them.
 */
class ElementTransitions(@JvmField var items: List<ElementTransitionOptions>? = null) {
    /** The element transitions; none when the options don't give them. */
    val transitions: List<ElementTransitionOptions>
        get() = items.orEmpty()

    /** The options give the list, `[]` included. */
    fun hasValue(): Boolean = items != null

    fun mergeWith(other: ElementTransitions) {
        other.items?.let { items = it.map(ElementTransitionOptions::copy) }
    }

    fun mergeWithDefault(defaults: ElementTransitions) {
        if (items == null) items = defaults.items?.map(ElementTransitionOptions::copy)
    }

    fun copy() = ElementTransitions().also { it.mergeWith(this) }
}

/**
 * An element transition: the view with `nativeID` [id], on the screen that comes or the one that goes, and the
 * animation of each of its properties (`ElementTransition`).
 */
class ElementTransitionOptions(@JvmField var id: String = "") {
    @JvmField var alpha = ElementAnimationOptions()
    @JvmField var x = ElementAnimationOptions()
    @JvmField var y = ElementAnimationOptions()
    @JvmField var translationX = ElementAnimationOptions()
    @JvmField var translationY = ElementAnimationOptions()
    @JvmField var scaleX = ElementAnimationOptions()
    @JvmField var scaleY = ElementAnimationOptions()
    @JvmField var rotationX = ElementAnimationOptions()
    @JvmField var rotationY = ElementAnimationOptions()
    @JvmField var rotation = ElementAnimationOptions()

    fun hasValue(): Boolean = id.isNotEmpty() || alpha.hasValue() || x.hasValue() || y.hasValue() ||
        translationX.hasValue() || translationY.hasValue() || scaleX.hasValue() || scaleY.hasValue() ||
        rotationX.hasValue() || rotationY.hasValue() || rotation.hasValue()

    fun mergeWith(other: ElementTransitionOptions) {
        if (other.id.isNotEmpty()) id = other.id
        alpha.mergeWith(other.alpha)
        x.mergeWith(other.x)
        y.mergeWith(other.y)
        translationX.mergeWith(other.translationX)
        translationY.mergeWith(other.translationY)
        scaleX.mergeWith(other.scaleX)
        scaleY.mergeWith(other.scaleY)
        rotationX.mergeWith(other.rotationX)
        rotationY.mergeWith(other.rotationY)
        rotation.mergeWith(other.rotation)
    }

    fun mergeWithDefault(defaults: ElementTransitionOptions) {
        if (id.isEmpty()) id = defaults.id
        alpha.mergeWithDefault(defaults.alpha)
        x.mergeWithDefault(defaults.x)
        y.mergeWithDefault(defaults.y)
        translationX.mergeWithDefault(defaults.translationX)
        translationY.mergeWithDefault(defaults.translationY)
        scaleX.mergeWithDefault(defaults.scaleX)
        scaleY.mergeWithDefault(defaults.scaleY)
        rotationX.mergeWithDefault(defaults.rotationX)
        rotationY.mergeWithDefault(defaults.rotationY)
        rotation.mergeWithDefault(defaults.rotation)
    }

    fun copy() = ElementTransitionOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): ElementTransitionOptions {
            val options = ElementTransitionOptions(map?.opt("id") as? String ?: "")
            if (map == null) return options
            options.alpha = map.group("alpha") { ElementAnimationOptions.parse(it) }
            options.x = map.group("x") { ElementAnimationOptions.parse(it) }
            options.y = map.group("y") { ElementAnimationOptions.parse(it) }
            options.translationX = map.group("translationX") { ElementAnimationOptions.parse(it) }
            options.translationY = map.group("translationY") { ElementAnimationOptions.parse(it) }
            options.scaleX = map.group("scaleX") { ElementAnimationOptions.parse(it) }
            options.scaleY = map.group("scaleY") { ElementAnimationOptions.parse(it) }
            options.rotationX = map.group("rotationX") { ElementAnimationOptions.parse(it) }
            options.rotationY = map.group("rotationY") { ElementAnimationOptions.parse(it) }
            options.rotation = map.group("rotation") { ElementAnimationOptions.parse(it) }
            return options
        }
    }
}

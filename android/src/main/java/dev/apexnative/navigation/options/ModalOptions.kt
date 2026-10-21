package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.TextParser

/**
 * `modal`: how a modal shows; `sheet` is the group of the sheet module, which keeps its own reading and merging.
 */
class ModalOptions {
    @JvmField var sheet = SheetOptions()
    @JvmField var contentSize = ModalContentSizeOptions()
    @JvmField var popover = ModalPopoverOptions()
    @JvmField var testID: Text = NullText()
    /** Internal: a swipe down dismisses the modal. */
    @JvmField var swipeToDismiss: Bool = NullBool()

    fun hasValue(): Boolean = sheet.hasValue || contentSize.hasValue() || popover.hasValue() || testID.hasValue() ||
        swipeToDismiss.hasValue()

    fun mergeWith(other: ModalOptions) {
        sheet.mergeWith(other.sheet)
        contentSize.mergeWith(other.contentSize)
        popover.mergeWith(other.popover)
        if (other.testID.hasValue()) testID = other.testID
        if (other.swipeToDismiss.hasValue()) swipeToDismiss = other.swipeToDismiss
    }

    fun mergeWithDefault(defaults: ModalOptions) {
        sheet.mergeWithDefault(defaults.sheet)
        contentSize.mergeWithDefault(defaults.contentSize)
        popover.mergeWithDefault(defaults.popover)
        if (!testID.hasValue()) testID = defaults.testID
        if (!swipeToDismiss.hasValue()) swipeToDismiss = defaults.swipeToDismiss
    }

    fun copy() = ModalOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): ModalOptions {
            val options = ModalOptions()
            if (map == null) return options
            options.sheet = map.group("sheet") { SheetOptions.parse(it) }
            options.contentSize = map.group("contentSize") { ModalContentSizeOptions.parse(it) }
            options.popover = map.group("popover") { ModalPopoverOptions.parse(it) }
            options.testID = TextParser.parse(map, "testID")
            options.swipeToDismiss = BoolParser.parse(map, "swipeToDismiss")
            return options
        }
    }
}

/** `overlay`: how an overlay takes touches. */
class OverlayOptions {
    @JvmField var interceptTouchOutside: Bool = NullBool()
    @JvmField var testID: Text = NullText()

    fun hasValue(): Boolean = interceptTouchOutside.hasValue() || testID.hasValue()

    fun mergeWith(other: OverlayOptions) {
        if (other.interceptTouchOutside.hasValue()) interceptTouchOutside = other.interceptTouchOutside
        if (other.testID.hasValue()) testID = other.testID
    }

    fun mergeWithDefault(defaults: OverlayOptions) {
        if (!interceptTouchOutside.hasValue()) interceptTouchOutside = defaults.interceptTouchOutside
        if (!testID.hasValue()) testID = defaults.testID
    }

    fun copy() = OverlayOptions().also { it.mergeWith(this) }

    companion object {
        @JvmStatic
        fun parse(map: ReadableMap?): OverlayOptions {
            val options = OverlayOptions()
            if (map == null) return options
            options.interceptTouchOutside = BoolParser.parse(map, "interceptTouchOutside")
            options.testID = TextParser.parse(map, "testID")
            return options
        }
    }
}

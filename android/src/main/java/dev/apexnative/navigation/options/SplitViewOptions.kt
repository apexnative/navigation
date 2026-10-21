package dev.apexnative.navigation.options

import com.facebook.react.bridge.ReadableMap

/** The `splitView` options Android reads. */
class SplitViewOptions(
    /** The width of the primary pane, in dp. */
    @JvmField val width: Double,
    /** A swipe moves the secondary pane over the primary one and back, when they don't fit side by side. */
    @JvmField val presentsWithGesture: Boolean,
    /** `secondary`: on a narrow screen without the compact layout the split view starts with the secondary pane. */
    @JvmField val compactTopColumn: String?,
) {
    companion object {
        /** The width of the primary pane by default, and the least width of the secondary one beside it. */
        const val PANE_WIDTH = 360.0

        @JvmStatic
        fun parse(options: ReadableMap?): SplitViewOptions {
            val split = options?.optMap("splitView")
            return SplitViewOptions(
                (split?.opt("width") as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: PANE_WIDTH,
                split?.optBoolean("presentsWithGesture", true) ?: true,
                split?.optString("compactTopColumn")?.takeIf { it.isNotEmpty() },
            )
        }
    }
}

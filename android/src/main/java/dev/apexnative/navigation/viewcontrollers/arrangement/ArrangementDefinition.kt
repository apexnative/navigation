package dev.apexnative.navigation.viewcontrollers.arrangement

import org.json.JSONObject

/**
 * How a pair of screens lies (`defineArrangement`), from the `data` of its layout node. JavaScript sends every value
 * with its default filled in; a value it doesn't know takes the default too.
 */
data class ArrangementDefinition(
    val style: Style,
    val axes: Axes,
    /** `split`: the primary's share along the split, 0–1, where a fold doesn't decide. */
    val fraction: Float,
    /**
     * The edge the primary takes. `split`: leading or top puts it first, trailing or bottom second; `overlay`: its edge
     * when the pair goes side by side at a fold.
     */
    val primaryEdge: Edge,
    /** What a window too small for a pair shows. */
    val compact: Compact,
) {
    /** `split`: side by side or one above the other; `overlay`: the primary over the secondary. */
    enum class Style { SPLIT, OVERLAY }

    /** `horizontal`: side by side only; `vertical`: one above the other only. */
    enum class Axes { HORIZONTAL, VERTICAL, BOTH }

    enum class Edge { LEADING, TRAILING, TOP, BOTTOM }

    /** `primary`: the primary alone; `pair`: the pair, laid out as in a large window. */
    enum class Compact { PRIMARY, PAIR }

    /** The pair may lie side by side ([sideBySide]) or one above the other (not). */
    fun allows(sideBySide: Boolean): Boolean = axes == Axes.BOTH || (axes == Axes.HORIZONTAL) == sideBySide

    companion object {
        private const val DEFAULT_FRACTION = 0.5

        @JvmStatic
        fun parse(data: JSONObject): ArrangementDefinition {
            val fraction = data.optDouble("fraction", DEFAULT_FRACTION).takeUnless { it.isNaN() } ?: DEFAULT_FRACTION
            val style = if (data.optString("style") == "overlay") Style.OVERLAY else Style.SPLIT
            return ArrangementDefinition(
                style = style,
                axes = when (data.optString("axes")) {
                    "horizontal" -> Axes.HORIZONTAL
                    "vertical" -> Axes.VERTICAL
                    else -> Axes.BOTH
                },
                fraction = fraction.coerceIn(0.0, 1.0).toFloat(),
                primaryEdge = when (data.optString("primaryEdge")) {
                    "leading" -> Edge.LEADING
                    "top" -> Edge.TOP
                    "bottom" -> Edge.BOTTOM
                    "trailing" -> Edge.TRAILING
                    // The default of each style: the primary first in a split, at the trailing side of an overlay.
                    else -> if (style == Style.SPLIT) Edge.LEADING else Edge.TRAILING
                },
                compact = if (data.optString("compact") == "pair") Compact.PAIR else Compact.PRIMARY,
            )
        }
    }
}

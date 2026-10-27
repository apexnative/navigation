package dev.apexnative.navigation.viewcontrollers.modal

import dev.apexnative.navigation.options.SheetDetent

/**
 * Where a sheet rests: the stops it keeps of its detents, how the Material behavior stands at them, and where a shown
 * sheet goes at new options, as on iOS: the sheet keeps the stop it stands at (the user's last drag included) until a
 * `selectedDetent` arrives in a `mergeOptions`, or until its stop is no longer among the detents.
 */
internal object SheetStops {
    /** The Material behavior has three resting states: collapsed, half expanded and expanded. */
    const val MAX_STOPS = 3

    /**
     * The stops of the sheet, from the lowest: [detents] by their height ([heightOf]), one of each height (the first
     * the options give), and at most [MAX_STOPS] of them: the lowest, the highest, and between them [selected] when
     * it stands there, else the lowest of the rest.
     */
    fun stops(detents: List<SheetDetent>, heightOf: (SheetDetent) -> Int, selected: SheetDetent?): List<SheetDetent> {
        val sorted = detents.sortedBy(heightOf).distinctBy(heightOf)
        if (sorted.size <= MAX_STOPS) return sorted
        val between = sorted.subList(1, sorted.size - 1)
        val middle = selected?.takeIf { it in between } ?: between.first()
        return listOf(sorted.first(), middle, sorted.last())
    }

    /**
     * How the Material behavior stands at the stops: [fitToContents] with one or two (collapsed and expanded, the
     * sheet as tall as the highest, [maxHeight]), else half expanded between them at [halfExpandedRatio] of the
     * layout, and expanded [expandedOffset] below its top. [skipCollapsed] with one stop: the sheet has only that.
     * The heights are in pixels, as is the rest.
     */
    data class Material(
        val fitToContents: Boolean,
        val skipCollapsed: Boolean,
        val peekHeight: Int,
        val halfExpandedRatio: Float?,
        val expandedOffset: Int,
        val maxHeight: Int,
    )

    /**
     * The behavior's settings for stops [heights] tall (from the lowest, each higher than the one before) in a layout
     * [parentHeight] tall.
     */
    fun material(heights: List<Int>, parentHeight: Int): Material {
        val lowest = heights.first()
        val highest = heights.last()
        val three = heights.size == MAX_STOPS && parentHeight > 0
        return Material(
            fitToContents = !three,
            skipCollapsed = heights.size == 1,
            peekHeight = lowest,
            halfExpandedRatio = if (three) heights[1].toFloat() / parentHeight else null,
            expandedOffset = (parentHeight - highest).coerceAtLeast(0),
            maxHeight = highest,
        )
    }

    /**
     * The stop to move to, or null to stay: [arrived] (the `selectedDetent` the merge names) when it is among
     * [detents]; else, when [settled] (the stop the sheet rests at) is no longer one of them, [selected] (the resolved
     * `selectedDetent`) when it is, or the lowest.
     */
    fun target(
        settled: SheetDetent?,
        detents: List<SheetDetent>,
        arrived: SheetDetent?,
        selected: SheetDetent?,
    ): SheetDetent? = when {
        arrived != null && arrived in detents -> arrived
        settled != null && settled !in detents -> selected?.takeIf { it in detents } ?: detents.first()
        else -> null
    }
}

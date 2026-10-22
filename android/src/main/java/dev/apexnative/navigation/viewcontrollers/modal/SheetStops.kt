package dev.apexnative.navigation.viewcontrollers.modal

import dev.apexnative.navigation.options.SheetDetent

/**
 * Where a shown sheet goes at new options, as on iOS: the sheet keeps the stop it stands at (the user's last drag
 * included) until a `selectedDetent` arrives in a `mergeOptions`, or until its stop is no longer among the detents.
 */
internal object SheetStops {
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

package dev.apexnative.navigation.views.element

import dev.apexnative.navigation.utils.DevelopmentLog

/**
 * The isolation of every element of the transitions: [work], a step of the element named [element] (its `nativeID` in
 * quotes, or a pair's two), runs; an exception in it skips that element only — one line in the log
 * ([elementSkipped]), and false — while the rest of the transition plays and its command completes.
 */
internal inline fun elementStep(
    element: String,
    noinline log: (String) -> Unit = DevelopmentLog::warn,
    work: () -> Unit,
): Boolean = try {
    work()
    true
} catch (e: Exception) {
    elementSkipped(element, e.toString(), log)
    false
}

/** The line in the log for the element named [element], skipped for [reason]: it shows and hides with its screen. */
internal fun elementSkipped(element: String, reason: String, log: (String) -> Unit = DevelopmentLog::warn) =
    log("[ApexNavigation] the transition's element $element is skipped, it shows and hides with its screen while the " +
        "rest plays: $reason")

/** The name of an element in the log. */
internal fun elementName(nativeId: String) = "'$nativeId'"

/** The name of a shared pair in the log. */
internal fun pairName(fromId: String, toId: String) = "'$fromId' -> '$toId'"

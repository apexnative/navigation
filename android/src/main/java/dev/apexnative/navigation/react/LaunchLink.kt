package dev.apexnative.navigation.react

import android.content.Intent
import androidx.annotation.RestrictTo

/**
 * The link that launched an activity: the data of its `ACTION_VIEW` intent, given to JS once, through
 * `getLaunchLink`. A link that arrives while React Native runs reaches JS as a `url` event instead; one that arrives
 * before it runs becomes the activity's intent (`NavigationActivity.onNewIntent`), so it is the launch link.
 *
 * Main thread only: the activity reads and replaces its intent there.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
object LaunchLink {
    // The mark lives in the intent itself: a JS reload or a second call finds it, a new activity or a newer intent
    // comes without it, and an activity started again with this intent (`startActivity(intent)`) keeps it.
    private const val TAKEN = "dev.apexnative.navigation.linkTaken"

    /** The link of [intent], once: null when the intent isn't a link, was given before, or is an old one. */
    @JvmStatic
    fun take(intent: Intent?): String? {
        if (intent == null || !isLink(intent) || intent.getBooleanExtra(TAKEN, false)) return null
        // Opened from the recent apps, an activity gets the intent its task was first started with: an old link.
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return null
        markTaken(intent)
        return intent.data?.toString()
    }

    /** Marks the link of [intent] as given: [take] returns null for it from now on. */
    @JvmStatic
    fun markTaken(intent: Intent?) {
        if (intent != null && isLink(intent)) intent.putExtra(TAKEN, true)
    }

    // The extras, which another app filled, are read only for a link.
    private fun isLink(intent: Intent) = intent.action == Intent.ACTION_VIEW && intent.data != null
}

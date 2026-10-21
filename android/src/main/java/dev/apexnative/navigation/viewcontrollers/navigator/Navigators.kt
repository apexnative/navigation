package dev.apexnative.navigation.viewcontrollers.navigator

import dev.apexnative.navigation.NavigationActivity
import java.lang.ref.WeakReference

/**
 * The navigation activities of the app, from their `onCreate` to their `onDestroy`, held weakly: the core never keeps
 * a destroyed activity alive. Commands run in [current], even before its first resume and while an activity of another
 * app or SDK is over it; without one, the navigation has nowhere to show screens. Main thread only.
 */
internal object Navigators {
    private val tracker = ActivityTracker<NavigationActivity>(
        isFinishing = { it.isFinishing },
        isDestroyed = { it.isDestroyed },
    )

    /** [activity] was created: it is the newest. */
    fun add(activity: NavigationActivity) = tracker.add(activity)

    /** [activity] is destroyed. */
    fun remove(activity: NavigationActivity) = tracker.remove(activity)

    /** The activity commands run in: the newest that is neither finishing nor destroyed; none when there is none. */
    fun current(): NavigationActivity? = tracker.current()

    /** Every navigation activity not destroyed yet, oldest first. */
    fun all(): List<NavigationActivity> = tracker.all()
}

/** The activities of [Navigators], held weakly, newest last; what "finishing" and "destroyed" are is given. */
internal class ActivityTracker<A : Any>(
    private val isFinishing: (A) -> Boolean,
    private val isDestroyed: (A) -> Boolean,
) {
    private val activities = mutableListOf<WeakReference<A>>()

    /** [activity] was created: it is the newest. */
    fun add(activity: A) {
        remove(activity)
        activities += WeakReference(activity)
    }

    /** [activity] is destroyed; activities already collected go too. */
    fun remove(activity: A) {
        activities.removeAll { it.get().let { held -> held == null || held === activity } }
    }

    /** The newest activity that is neither finishing nor destroyed; none when there is none. */
    fun current(): A? = all().lastOrNull { !isFinishing(it) }

    /** Every activity neither collected nor destroyed, oldest first. */
    fun all(): List<A> = activities.mapNotNull { it.get() }.filterNot(isDestroyed)
}

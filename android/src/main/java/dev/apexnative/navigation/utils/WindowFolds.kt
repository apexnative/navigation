package dev.apexnative.navigation.utils

import android.app.Activity
import android.graphics.Rect
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.WeakHashMap

/**
 * The folds of a foldable in the activity's window (androidx.window `WindowLayoutInfo`), collected once for the
 * activity while it is started. None on a device without a fold, and before the first value, which comes after the
 * activity starts. On a change the listeners hear it and the window lays out again, so each screen measures its
 * reserved regions. Main thread only.
 */
class WindowFolds private constructor(private val activity: Activity) : DefaultLifecycleObserver {
    /** A fold of the window: its bounds in window pixels, and how it lies. */
    data class Fold(
        val bounds: Rect,
        /** It runs from top to bottom, between a left and a right part. */
        val vertical: Boolean,
        /** It divides the window into two parts the user sees apart (`isSeparating`). */
        val separating: Boolean,
        /** The device is partly folded at it (`HALF_OPENED`); flat otherwise. */
        val halfOpened: Boolean,
        /** It hides what is under it (`occlusionType FULL`), as the hinge between two displays. */
        val occludes: Boolean,
    )

    /** The folds of the window now. */
    var folds: List<Fold> = emptyList()
        private set

    /** The first value came: [folds] tells the window as it is, also when it has none. */
    var hasInfo = false
        private set

    private val listeners = LinkedHashSet<Runnable>()
    private val scope = MainScope()
    private var collecting: Job? = null

    /** Calls [listener] on every change of [folds]. */
    fun addListener(listener: Runnable) {
        listeners += listener
    }

    fun removeListener(listener: Runnable) {
        listeners -= listener
    }

    override fun onStart(owner: LifecycleOwner) {
        collecting = scope.launch {
            WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { onInfo(it) }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        collecting?.cancel()
        collecting = null
    }

    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
        scope.cancel()
        instances.remove(activity)
    }

    private fun onInfo(info: WindowLayoutInfo) {
        val next = info.displayFeatures.filterIsInstance<FoldingFeature>().map {
            Fold(
                bounds = Rect(it.bounds),
                vertical = it.orientation == FoldingFeature.Orientation.VERTICAL,
                separating = it.isSeparating,
                halfOpened = it.state == FoldingFeature.State.HALF_OPENED,
                occludes = it.occlusionType == FoldingFeature.OcclusionType.FULL,
            )
        }
        val first = !hasInfo
        hasInfo = true
        if (next == folds && !first) return
        folds = next
        listeners.toList().forEach { it.run() }
        // A fold that opens halfway changes no size: nothing would lay out, and the screens keep their old regions.
        activity.window?.peekDecorView()?.requestLayout()
    }

    companion object {
        private val instances = WeakHashMap<Activity, WindowFolds>()

        /** The folds of the window of [activity], collected from now on; null for an activity without a lifecycle. */
        @JvmStatic
        fun of(activity: Activity?): WindowFolds? {
            val owner = activity as? LifecycleOwner ?: return null
            if (activity.isDestroyed) return null
            return instances.getOrPut(activity) {
                // An observer added to a started activity hears onStart at once: the collection starts now.
                WindowFolds(activity).also { owner.lifecycle.addObserver(it) }
            }
        }
    }
}

package dev.apexnative.navigation.react

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import dev.apexnative.navigation.react.events.EventContract
import dev.apexnative.navigation.react.events.EventEmitter
import java.util.concurrent.ConcurrentHashMap

/**
 * What JavaScript knows of each screen's place, by the screen's id, in one record: its safe area (the insets JavaScript
 * gets and the padding of its safe area view, `useSafeAreaInsets`), its reserved regions (`useReservedRegions`) and,
 * in a pair, its arrangement (`useArrangement`). The core measures them (`ScreenSafeArea`, [ScreenRegions], the pair's
 * plan) on the main thread and keeps them here; JavaScript reads them from its own thread for a screen's first render
 * and gets every change as an event. The record is immutable and replaced at once, so a reader on another thread sees
 * one whole record. When the screen goes, [remove] clears all of it. As on iOS (`ANNScreenEnvironment`).
 */
internal object ScreenEnvironment {
    /** How much covers each edge of a screen's content, in dp. */
    data class Insets(val top: Double, val right: Double, val bottom: Double, val left: Double)

    /** A reserved region, in dp from the top left of the screen's content. */
    data class Region(val x: Double, val y: Double, val width: Double, val height: Double)

    /** Where a screen divides (`division`) and where hardware covers it (`occlusion`). */
    data class Regions(val division: List<Region>, val occlusion: List<Region>) {
        val isEmpty: Boolean get() = division.isEmpty() && occlusion.isEmpty()
    }

    /** Which screen of its pair a screen is, how the pair lies, whether it lies over the other one and is hidden. */
    data class Arrangement(val placement: String, val layout: String, val front: Boolean, val hidden: Boolean)

    /** Where the changes JavaScript hears go: the events of the screen's JavaScript, or a test's record. */
    interface Changes {
        fun insetsChanged(screenId: String, insets: Insets)

        fun regionsChanged(screenId: String, regions: Regions)

        fun arrangementChanged(screenId: String, arrangement: Arrangement)
    }

    // One screen's record; null for a fact not known yet (not laid out, outside a pair).
    private data class Record(
        val insets: Insets? = null,
        val padding: Insets? = null,
        val regions: Regions? = null,
        val arrangement: Arrangement? = null,
    )

    private val NO_REGIONS = Regions(emptyList(), emptyList())
    private val records = ConcurrentHashMap<String, Record>()
    // Main thread only.
    private val paddingListeners = HashMap<String, MutableSet<() -> Unit>>()

    /**
     * Keeps the screen's [insets] for JavaScript (what still covers its content) and the [padding] of its safe area
     * view. Calls the screen's padding listeners when the padding changed, then tells JavaScript when the insets did.
     * Main thread only.
     */
    fun setInsets(screenId: String, insets: Insets, padding: Insets, changes: Changes) {
        val before = update(screenId) { it.copy(insets = insets, padding = padding) }
        if (before?.padding != padding) paddingListeners[screenId]?.toList()?.forEach { it() }
        if (before?.insets != insets) changes.insetsChanged(screenId, insets)
    }

    /**
     * Keeps the screen's [regions], and tells JavaScript when they changed; none at first is what JavaScript reads
     * before the first layout, so it isn't told. Main thread only.
     */
    fun setRegions(screenId: String, regions: Regions, changes: Changes) {
        val before = update(screenId) { it.copy(regions = regions) }?.regions
        if (before == regions || (before == null && regions.isEmpty)) return
        changes.regionsChanged(screenId, regions)
    }

    /** Keeps the screen's [arrangement], and tells JavaScript when it changed and [notify] holds. Main thread only. */
    fun setArrangement(screenId: String, arrangement: Arrangement, notify: Boolean, changes: Changes) {
        val before = update(screenId) { it.copy(arrangement = arrangement) }?.arrangement
        if (before == arrangement || !notify) return
        changes.arrangementChanged(screenId, arrangement)
    }

    /** The padding of the screen's safe area view; none before the screen was laid out. Any thread. */
    fun padding(screenId: String): Insets? = records[screenId]?.padding

    /** The insets JavaScript gets; none before the screen was laid out. Any thread. */
    fun insets(screenId: String): Insets? = records[screenId]?.insets

    /** The screen's reserved regions; none before its first layout. Any thread. */
    fun regions(screenId: String): Regions? = records[screenId]?.regions

    /** The screen's arrangement; none outside a pair. Any thread. */
    fun arrangement(screenId: String): Arrangement? = records[screenId]?.arrangement

    /**
     * Calls `listener` when the padding of the screen changes; returns the function that stops it. Main thread only.
     */
    fun listenToPadding(screenId: String, listener: () -> Unit): () -> Unit {
        paddingListeners.getOrPut(screenId) { LinkedHashSet() }.add(listener)
        return {
            paddingListeners[screenId]?.let {
                it.remove(listener)
                if (it.isEmpty()) paddingListeners.remove(screenId)
            }
        }
    }

    /**
     * Forgets everything kept for the screen. Its padding listeners stay: a live safe area view keeps listening while
     * it keeps the id, and stops itself.
     */
    fun remove(screenId: String) {
        records.remove(screenId)
    }

    /** `getScreenInsets`: `{ top, right, bottom, left }`, empty before the screen was laid out. Any thread. */
    fun getScreenInsets(screenId: String): WritableMap = insets(screenId)?.let(::toMap) ?: Arguments.createMap()

    /** `getScreenRegions`: `{ division, occlusion }`, empty lists before the first layout. Any thread. */
    fun getScreenRegions(screenId: String): WritableMap = toMap(regions(screenId) ?: NO_REGIONS)

    /** `getArrangementState`: `{ placement, layout, front, hidden }`, empty outside a pair. Any thread. */
    fun getArrangementState(screenId: String): WritableMap =
        arrangement(screenId)?.let(::toMap) ?: Arguments.createMap()

    /** The changes as events through [emitter], the one of the JavaScript the screen was made for. */
    fun changesOf(emitter: EventEmitter): Changes = object : Changes {
        override fun insetsChanged(screenId: String, insets: Insets) =
            emitter.emitScreenInsetsChanged(screenId, toMap(insets))

        override fun regionsChanged(screenId: String, regions: Regions) =
            emitter.emitScreenRegionsChanged(screenId, toMap(regions))

        override fun arrangementChanged(screenId: String, arrangement: Arrangement) =
            emitter.emitArrangementChanged(screenId, toMap(arrangement))
    }

    // Replaces the screen's record at once with [change] of it; returns the record before, null for a new screen.
    private fun update(screenId: String, change: (Record) -> Record): Record? {
        var before: Record? = null
        records.compute(screenId) { _, old ->
            before = old
            change(old ?: Record())
        }
        return before
    }

    private fun toMap(value: Insets): WritableMap = Arguments.createMap().apply {
        putDouble(EventContract.ScreenInsetsChanged.TOP, value.top)
        putDouble(EventContract.ScreenInsetsChanged.RIGHT, value.right)
        putDouble(EventContract.ScreenInsetsChanged.BOTTOM, value.bottom)
        putDouble(EventContract.ScreenInsetsChanged.LEFT, value.left)
    }

    private fun toMap(value: Regions): WritableMap = Arguments.createMap().apply {
        putArray(EventContract.ScreenRegionsChanged.DIVISION, toArray(value.division))
        putArray(EventContract.ScreenRegionsChanged.OCCLUSION, toArray(value.occlusion))
    }

    private fun toArray(regions: List<Region>): WritableArray = Arguments.createArray().apply {
        for (region in regions) {
            pushMap(Arguments.createMap().apply {
                putDouble(EventContract.ScreenRegionRect.X, region.x)
                putDouble(EventContract.ScreenRegionRect.Y, region.y)
                putDouble(EventContract.ScreenRegionRect.WIDTH, region.width)
                putDouble(EventContract.ScreenRegionRect.HEIGHT, region.height)
            })
        }
    }

    private fun toMap(value: Arrangement): WritableMap = Arguments.createMap().apply {
        putString(EventContract.ArrangementChanged.PLACEMENT, value.placement)
        putString(EventContract.ArrangementChanged.LAYOUT, value.layout)
        putBoolean(EventContract.ArrangementChanged.FRONT, value.front)
        putBoolean(EventContract.ArrangementChanged.HIDDEN, value.hidden)
    }
}

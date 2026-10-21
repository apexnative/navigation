package dev.apexnative.navigation.system

import android.app.Activity
import android.view.ViewGroup
import dev.apexnative.navigation.NavigationActivity
import dev.apexnative.navigation.options.Options

/**
 * The one owner of the system UI of an activity: the status bar, the navigation bar, the requested orientation and
 * (without edge to edge) the window's resize for the keyboard follow the screen on top of the layer that decides each
 * group ([SystemUiLayers]). Every change — a screen appears or disappears, a side menu comes to rest, a merge, new
 * default options, a light/dark switch, a configuration change, a new mode of the navigation bar — only marks it
 * ([invalidate]); once before the next frame it resolves the deciding screens, computes the state ([SystemUiRules]) and
 * writes to the window only the fields that differ from the state it applied last, the first time all of them; a group
 * no layer decides (only sheets, or overlays that don't name it) stays as the window has it. Nothing is applied inside
 * the caller's call, and nothing waits.
 *
 * Each activity's navigator has its own, made with it; it does nothing before [start] and after [release], so an old
 * activity's owner never touches a new one's window, and nothing process-wide holds it. It also applies the app's
 * direction to its activity ([applyDirection]). [layers] gives the layers that show, bottom to top. Main thread only.
 */
internal class SystemUiOwner(private val layers: () -> List<SystemUiLayers.Layer>) {
    private var window: SystemUiWindow? = null
    private var remembered: SystemUiWindow.Remembered? = null
    private var released = false
    private var applied: SystemUiState? = null
    // The groups [applied] holds written values of; a group no layer decided yet was never written.
    private val appliedGroups = HashSet<SystemUiLayers.Group>()
    private var pending = false
    // A change other than new default options asked in this frame: the orientation applies.
    private var orientationAsked = false
    private var mode = SystemUiRules.initialMode(0)
    private var appliedDirection: String? = null
    // The window was told the keyboard covers the screen and wasn't told back yet.
    private var keyboardCovered = false
    // One instance, so the post can be taken back.
    private val applyTask = Runnable { applyNow() }

    /**
     * The activity was created ([window]): what its theme and manifest give is remembered before anything changes
     * them. Once.
     */
    fun start(window: SystemUiWindow) {
        if (released || this.window != null) return
        this.window = window
        remembered = window.start()
        mode = SystemUiRules.initialMode(window.sdk)
    }

    /** The layers joined [content]: the bars' views go over them, and the owner watches the window's insets. */
    fun attach(content: ViewGroup) {
        window?.attach(content, ::barsChanged)
    }

    /** Something the system UI depends on changed: it applies once before the next frame. */
    fun invalidate() {
        if (window == null) return
        orientationAsked = true
        schedule()
    }

    /**
     * New default options (`Navigator.repaint`): the bars apply again, the orientation as it is applied unless another
     * change of the same frame asks for it.
     */
    fun invalidateKeepingOrientation() {
        if (window == null) return
        schedule()
    }

    /** The configuration changed: the next apply writes every field again, once. */
    fun configurationChanged() {
        if (window == null) return
        applied = null
        appliedGroups.clear()
        invalidate()
    }

    /**
     * The app's [direction] for this activity (`setRoot` step 1, `setDefaultOptions`): the activity's views and React
     * Native take it, unless it is the one applied already. None: nothing changes.
     */
    fun applyDirection(direction: String?) {
        val window = window ?: return
        if (direction == null || direction == appliedDirection) return
        val layoutDirection = AppDirection.viewDirectionOf(direction) ?: return
        appliedDirection = direction
        window.setDirection(layoutDirection, AppDirection.rtlNow(direction, window.isLocaleRtl()))
    }

    /** The activity is destroyed: the pending apply, the views and the listeners go; the owner does nothing after. */
    fun release() {
        val window = window ?: return
        window.cancel(applyTask)
        window.release()
        this.window = null
        released = true
        pending = false
        applied = null
        appliedGroups.clear()
        remembered = null
    }

    private fun schedule() {
        val window = window ?: return
        if (pending) return
        pending = true
        window.post(applyTask)
    }

    // The window's insets changed: a new mode or side of the navigation bar applies again.
    private fun barsChanged() {
        val window = window ?: return
        val now = SystemUiRules.navigationMode(window.sdk, window.bars(), mode)
        if (now == mode) return
        mode = now
        invalidate()
    }

    private fun applyNow() {
        pending = false
        val window = window ?: return
        val remembered = remembered ?: return
        val orientation = orientationAsked
        orientationAsked = false
        // With no layer showing (before the first root, after a reload) the window keeps what it has.
        val picked = SystemUiLayers.pick(layers()) ?: return
        mode = SystemUiRules.navigationMode(window.sdk, window.bars(), mode)
        val environment = SystemUiRules.Environment(
            edgeToEdge = window.isEdgeToEdge,
            night = window.isNight(),
            threeButtons = mode.threeButtons,
            themeStatusBarColor = remembered.statusBarColor,
            themeNavigationBarColor = remembered.navigationBarColor,
            manifestOrientation = remembered.orientation,
            windowBackground = window.windowBackground(),
            topBarDefaultColor = window.topBarDefaultColor(),
            tabsDefaultColor = window.tabsDefaultColor(),
            colour = window::colourOf,
        )
        val deciders = mapOf(
            SystemUiLayers.Group.StatusBar to picked.statusBar,
            SystemUiLayers.Group.NavigationBar to picked.navigationBar,
            SystemUiLayers.Group.Orientation to picked.orientation,
            SystemUiLayers.Group.Keyboard to picked.keyboard,
        )
        // A group no layer decides (only sheets, or overlays that don't name it) stays as the window has it.
        val decided = deciders.filterValues { it != null }.keys
        if (decided.isEmpty()) return
        var state = SystemUiRules.state(
            statusBar = picked.statusBar?.screen ?: UNDECIDED,
            navigationBar = picked.navigationBar?.screen ?: UNDECIDED,
            orientation = picked.orientation?.screen ?: UNDECIDED,
            keyboard = picked.keyboard?.screen ?: UNDECIDED,
            environment = environment,
        )
        val last = applied
        if (last != null) {
            for (group in SystemUiLayers.Group.entries) {
                val kept = group !in decided ||
                    (group == SystemUiLayers.Group.Orientation && !orientation && group in appliedGroups)
                if (kept) state = state.keeping(last, group)
            }
        }
        val fields = state.changedFrom(last).toMutableSet()
        // A group decided for the first time is written whole.
        for (group in decided - appliedGroups) fields += SystemUiState.Field.entries.filter { groupOf(it) == group }
        // The window's soft input mode stays the app's unless a screen asks the keyboard to cover it: never with edge
        // to edge, where the window doesn't resize; and "resize again" only after "cover".
        if (window.isEdgeToEdge || state.keyboardCovers == keyboardCovered) fields -= SystemUiState.Field.KeyboardCovers
        for (field in SystemUiState.Field.entries) {
            if (field in fields && groupOf(field) in decided) write(window, state, field)
        }
        applied = state
        appliedGroups += decided
    }

    private fun write(window: SystemUiWindow, state: SystemUiState, field: SystemUiState.Field) = when (field) {
        SystemUiState.Field.StatusBarShown -> window.setStatusBarShown(state.statusBarShown)
        SystemUiState.Field.StatusBarColor -> window.setStatusBarColor(state.statusBarColor)
        SystemUiState.Field.StatusBarIcons -> window.setStatusBarLightIcons(state.statusBarLightIcons)
        SystemUiState.Field.NavigationBarShown -> window.setNavigationBarShown(state.navigationBarShown)
        SystemUiState.Field.NavigationBarColor -> window.setNavigationBarColor(state.navigationBarColor)
        SystemUiState.Field.NavigationBarButtons -> window.setNavigationBarLightButtons(state.navigationBarLightButtons)
        SystemUiState.Field.Orientation -> window.setOrientation(state.orientation)
        SystemUiState.Field.KeyboardCovers -> {
            keyboardCovered = state.keyboardCovers
            window.setKeyboardCovers(state.keyboardCovers)
        }
    }

    companion object {
        // Stands for a group no layer decides; its values are never written.
        private val UNDECIDED = SystemUiRules.Screen(Options())

        private fun groupOf(field: SystemUiState.Field): SystemUiLayers.Group = when (field) {
            SystemUiState.Field.StatusBarShown,
            SystemUiState.Field.StatusBarColor,
            SystemUiState.Field.StatusBarIcons,
            -> SystemUiLayers.Group.StatusBar
            SystemUiState.Field.NavigationBarShown,
            SystemUiState.Field.NavigationBarColor,
            SystemUiState.Field.NavigationBarButtons,
            -> SystemUiLayers.Group.NavigationBar
            SystemUiState.Field.Orientation -> SystemUiLayers.Group.Orientation
            SystemUiState.Field.KeyboardCovers -> SystemUiLayers.Group.Keyboard
        }

        // [this] with the values of [group] from [last].
        private fun SystemUiState.keeping(last: SystemUiState, group: SystemUiLayers.Group): SystemUiState =
            when (group) {
                SystemUiLayers.Group.StatusBar -> copy(
                    statusBarShown = last.statusBarShown,
                    statusBarColor = last.statusBarColor,
                    statusBarLightIcons = last.statusBarLightIcons,
                )
                SystemUiLayers.Group.NavigationBar -> copy(
                    navigationBarShown = last.navigationBarShown,
                    navigationBarColor = last.navigationBarColor,
                    navigationBarLightButtons = last.navigationBarLightButtons,
                )
                SystemUiLayers.Group.Orientation -> copy(orientation = last.orientation)
                SystemUiLayers.Group.Keyboard -> copy(keyboardCovers = last.keyboardCovers)
            }

        /** The owner of [activity]'s system UI; none for an activity that isn't the navigation's (unit tests). */
        fun of(activity: Activity): SystemUiOwner? = (activity as? NavigationActivity)?.navigator?.systemUi
    }
}

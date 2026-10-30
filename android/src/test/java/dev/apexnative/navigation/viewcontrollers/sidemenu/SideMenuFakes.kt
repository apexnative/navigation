package dev.apexnative.navigation.viewcontrollers.sidemenu

import android.app.Activity
import android.widget.FrameLayout
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.react.events.ComponentType
import dev.apexnative.navigation.react.events.FakeContent
import dev.apexnative.navigation.react.events.ScreenAppearance
import dev.apexnative.navigation.viewcontrollers.child.ChildController
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.sidemenu.SideMenuMotion.DrawerState
import dev.apexnative.navigation.viewcontrollers.sidemenu.SideMenuMotion.Side
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * A drawer that behaves as `DrawerLayout` 1.1.1 does for the motion, without views: a jump
 * calls back at once (a slide, then opened or closed), an animation reports settling at once and moves only when the
 * test says ([frame], [settle]), a drag is [drag] then [release]; `onDrawerClosed` only for a drawer that had opened,
 * `onDrawerOpened` only for one that hadn't; a drawer not laid out ([callsBack] false) writes its state silently.
 * What the motion asks goes into [calls]: `open:left:true`, `close:right:false`, `lock:left:true`.
 */
internal class FakeDrawer(
    private val calls: MutableList<String>,
    private val sides: Set<Side> = setOf(Side.Left, Side.Right),
) : SideMenuMotion.Drawer {
    var motion: SideMenuMotion? = null
    override var callsBack = true
    val hidden = mutableListOf<Side>()

    private val offsets = HashMap<Side, Float>()
    private val opened = HashSet<Side>()
    private val settling = HashMap<Side, Float>()
    private val locks = HashMap<Side, Boolean>()
    private var dragging: Side? = null
    private var state = DrawerState.Idle

    fun offsetOf(side: Side): Float = offsets[side] ?: 0f

    fun isLocked(side: Side): Boolean = locks[side] == true

    override fun has(side: Side): Boolean = side in sides

    override fun open(side: Side, animate: Boolean) {
        calls += "open:${side.jsName}:$animate"
        move(side, 1f, animate)
    }

    override fun close(side: Side, animate: Boolean) {
        calls += "close:${side.jsName}:$animate"
        move(side, 0f, animate)
    }

    override fun lock(side: Side, locked: Boolean) {
        if (locks[side] == locked) return
        locks[side] = locked
        calls += "lock:${side.jsName}:$locked"
    }

    override fun isVisible(side: Side): Boolean = offsetOf(side) > 0f

    override fun hide(side: Side) {
        hidden += side
    }

    /** A frame of a settle or a drag: the menu on [side] is at [offset]. */
    fun frame(side: Side, offset: Float) {
        if (offsetOf(side) == offset) return
        offsets[side] = offset
        motion?.slid(side, offset)
    }

    /** The settle of [side] ends at its target: its end comes, then the rest of the whole drawer. */
    fun settle(side: Side) {
        val target = settling.remove(side) ?: return
        frame(side, target)
        dispatchEnd(side)
        if (settling.isEmpty() && dragging == null) setState(DrawerState.Idle)
    }

    /** The user starts to drag [side]. */
    fun drag(side: Side) {
        dragging = side
        setState(DrawerState.Dragging)
    }

    /** The user lets go of [side]: it settles to [target]. */
    fun release(side: Side, target: Float) {
        dragging = null
        settling[side] = target
        setState(DrawerState.Settling)
    }

    private fun move(side: Side, target: Float, animate: Boolean) {
        if (!callsBack) {
            offsets[side] = target
            if (target == 1f) opened += side else opened -= side
            return
        }
        if (animate) {
            // Already there: the side's dragger stops at once (`smoothSlideViewTo` with no distance).
            if (offsetOf(side) == target) {
                settling.remove(side)
                if (settling.isEmpty() && dragging == null) setState(DrawerState.Idle)
                return
            }
            settling[side] = target
            setState(DrawerState.Settling)
            return
        }
        frame(side, target)
        dispatchEnd(side)
    }

    private fun dispatchEnd(side: Side) {
        val offset = offsetOf(side)
        if (offset == 0f && side in opened) {
            opened -= side
            motion?.closed(side)
        } else if (offset == 1f && side !in opened) {
            opened += side
            motion?.opened(side)
        }
    }

    private fun setState(new: DrawerState) {
        if (new == state) return
        state = new
        motion?.stateChanged(new)
    }
}

/** The host of a motion: menus of [ReportingMenuScreen]s, `enabled` as the test sets it; its calls in [journal]. */
internal class FakeMotionHost(
    private val journal: MutableList<String>,
    private val left: ViewController<*>?,
    private val right: ViewController<*>?,
) : SideMenuMotion.Host {
    override var appeared = true
    val enabled = HashMap<Side, Boolean>()
    /** The sides that started to show, in order. */
    val started = mutableListOf<Side>()

    override fun child(side: Side): ViewController<*>? = if (side == Side.Left) left else right

    override fun starts(side: Side) {
        started += side
    }

    override fun isEnabled(side: Side): Boolean = enabled[side] != false

    override fun missingSide(side: Side) {
        journal += "missing:${side.jsName}"
    }

    override fun stateChanged() {
        journal += "state"
    }

    override fun backChanged() {
        journal += "back"
    }

    override fun shownChanged() {
        journal += "shown"
    }

    override fun centerCurrentAgain() {
        journal += "center"
    }
}

/**
 * A screen that reports its appearance through its own record, its content mounted when the test says; as it starts
 * to appear it shows with its options, which the layout around takes its part of (as a React screen does).
 */
internal class ReportingMenuScreen(
    activity: Activity,
    registry: ChildControllersRegistry,
    id: String,
    options: Options = Options(),
    val content: FakeContent = FakeContent(ready = true),
) : ChildController<FrameLayout>(activity, registry, id, Presenter(activity, Options()), options),
    AppearanceDispatcher.RecordOwner {
    val appearance = ScreenAppearance(id, id, ComponentType.Component, content)
    var shown = false

    override val appearanceRecord: ScreenAppearance
        get() = appearance

    override val currentComponentName: String = id

    override val isViewShown: Boolean
        get() = shown

    override fun createView(): FrameLayout = FrameLayout(activity)

    override fun sendOnNavigationButtonPressed(buttonId: String) =
        AppearanceDispatcher.navigationButtonPressed(this, buttonId)

    override fun applyOptions(options: Options) {
        performOnParentController { it.applyChildOptions(options, this) }
    }

    override fun onViewWillAppear() {
        if (isDestroyed) return
        applyOptions(resolveCurrentOptions(DefaultOptions.current))
        AppearanceDispatcher.willAppear(appearance)
    }

    override fun onViewDidAppear() {
        if (isDestroyed) return
        AppearanceDispatcher.didAppear(appearance)
        super.onViewDidAppear()
    }

    override fun onViewDisappear() {
        if (!isDestroyed) AppearanceDispatcher.didDisappear(appearance)
    }
}

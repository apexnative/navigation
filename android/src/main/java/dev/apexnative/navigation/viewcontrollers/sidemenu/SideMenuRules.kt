package dev.apexnative.navigation.viewcontrollers.sidemenu

import android.view.KeyEvent
import android.view.ViewGroup
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.SideMenuOptions
import dev.apexnative.navigation.options.SideMenuSideOptions
import dev.apexnative.navigation.viewcontrollers.sidemenu.SideMenuMotion.Side
import kotlin.math.max
import kotlin.math.min

/**
 * What the options of a side menu do on Android (`sideMenu.*`), without views: the values of each moment, `visible` as
 * a command, the sizes, the `testID`s, when the menus join the drawer. Android reads `enabled`, `width`, `height`,
 * `visible`, `animate`, `menusAttachMode` and the `testID`s of `sideMenu`; `openGestureMode`, `animationType`,
 * `openMode`, `shouldStretchDrawer`, `animationVelocity` and `glass` are iOS's.
 */
internal object SideMenuRules {
    /** When the views of the menus' nodes join the drawer (`sideMenu.menusAttachMode`): their content renders then. */
    enum class AttachMode {
        /** With the side menu's view. */
        Together,

        /** Once the side menu first appeared, with its center. */
        AfterCenter,

        /** Each as it first starts to show: the drawer's first movement, or a command that opens it. */
        OnOpen,
    }

    /** A side's `visible` of one merge: open or close it, with its `animate`. */
    data class Command(val side: Side, val visible: Boolean, val animate: Boolean)

    /** The `testID`s of the side menu's views; none where unset. */
    data class TestIdSet(val menu: String?, val center: String?, val left: String?, val right: String?)

    /** The options of [side] in [options]. */
    fun sideOf(options: SideMenuOptions, side: Side): SideMenuSideOptions =
        if (side == Side.Left) options.left else options.right

    /**
     * The commands of a merge's own `sideMenu`: its `visible`, left first, then right, each with `animate` of the same
     * side, true unless `false`. Only a merge's options and the layout's give them: never resolved options, nor the
     * defaults.
     */
    fun commands(merge: SideMenuOptions): List<Command> = Side.entries.mapNotNull { side ->
        val options = sideOf(merge, side)
        if (!options.visible.hasValue()) return@mapNotNull null
        Command(side, options.visible.isTrue, !options.animate.isFalse)
    }

    /**
     * The sides the layout's own options open as the side menu first shows: those with `visible: true`, left first;
     * without animation whatever their `animate`.
     */
    fun firstShow(layout: SideMenuOptions): List<Side> = Side.entries.filter { sideOf(layout, it).visible.isTrue }

    /**
     * The mode of `sideMenu.menusAttachMode`: the side menu's [own] options, else the [defaults]; `together` without
     * either. Read once, as the side menu is made.
     */
    fun attachModeOf(own: SideMenuOptions, defaults: SideMenuOptions): AttachMode {
        val value = own.menusAttachMode.takeIf { it.hasValue() } ?: defaults.menusAttachMode
        return when (value.get(null)) {
            "afterCenter" -> AttachMode.AfterCenter
            "onOpen" -> AttachMode.OnOpen
            else -> AttachMode.Together
        }
    }

    /** The menus that join as the side menu is made: both with `together`. */
    fun attachedAtCreation(mode: AttachMode): List<Side> = if (mode == AttachMode.Together) Side.entries else emptyList()

    /** The menus that join once the side menu first appeared: both with `afterCenter`. */
    fun attachedAfterAppearance(mode: AttachMode): List<Side> =
        if (mode == AttachMode.AfterCenter) Side.entries else emptyList()

    /** `enabled` of [side]: true unless `false`, from the side menu's resolved options. */
    fun isEnabled(options: SideMenuOptions, side: Side): Boolean = !sideOf(options, side).enabled.isFalse

    /** A merge names `enabled` of a side: the locks follow. */
    fun touchesEnabled(merge: SideMenuOptions): Boolean =
        merge.left.enabled.hasValue() || merge.right.enabled.hasValue()

    /** A merge names a `testID` of the side menu or of a side. */
    fun touchesTestIds(merge: SideMenuOptions): Boolean =
        merge.testID.hasValue() || merge.left.testID.hasValue() || merge.right.testID.hasValue()

    /** A merge names `width` of a side: the menus take their widths again. */
    fun touchesWidths(merge: SideMenuOptions): Boolean = merge.left.width.hasValue() || merge.right.width.hasValue()

    /** A merge names `height` of a side: the menus take their heights again. */
    fun touchesHeights(merge: SideMenuOptions): Boolean =
        merge.left.height.hasValue() || merge.right.height.hasValue()

    /** A merge names the background of the side menu (`layout.backgroundColor`). */
    fun touchesBackground(merge: Options): Boolean = merge.layout.backgroundColor.hasValue()

    /**
     * The width a side wants, in pixels: `width` (dp) by [toPixels]; none without it, which is the drawer's own
     * (`MATCH_PARENT`: the side menu's width less 64 dp).
     */
    fun wantedWidth(options: SideMenuSideOptions, toPixels: (Double) -> Int): Int? =
        options.width.get(null)?.let { max(0, toPixels(it)) }

    /** The height of a side, in pixels: `height` (dp) by [toPixels]; none: the whole height. */
    fun height(options: SideMenuSideOptions, toPixels: (Double) -> Int): Int? =
        options.height.get(null)?.let { max(0, toPixels(it)) }

    /**
     * The width of a drawer's layout params in a side menu [sideMenuWidth] wide: the [wanted] width, at most the side
     * menu's width less [minMargin] (`DrawerLayout` measures an exact width as it is, and clamps only `MATCH_PARENT`);
     * `MATCH_PARENT` without one.
     */
    fun drawerWidth(wanted: Int?, sideMenuWidth: Int, minMargin: Int): Int {
        wanted ?: return ViewGroup.LayoutParams.MATCH_PARENT
        return min(wanted, max(0, sideMenuWidth - minMargin))
    }

    /**
     * How far a drawer is from where it rests open, along the width (`ChildOffsetFromRest`): the left one rests at 0,
     * the right one with its right edge at the side menu's.
     */
    fun offsetFromRest(side: Side, sideMenuWidth: Int, drawerLeft: Int, drawerRight: Int): Int =
        if (side == Side.Left) -drawerLeft else sideMenuWidth - drawerRight

    /**
     * The `testID`s: the side menu `<sideMenu.testID>`, its center `<id>.center`, a side its own
     * `sideMenu.<side>.testID`, else `<id>.left` / `<id>.right`. Android draws the dimming without a view of its own:
     * no `.scrim`.
     */
    fun testIds(options: SideMenuOptions): TestIdSet {
        val id = options.testID.get(null)?.ifEmpty { null }
        fun part(side: Side): String? = sideOf(options, side).testID.get(null)?.ifEmpty { null }
            ?: id?.let { "$it.${side.jsName}" }
        return TestIdSet(id, id?.let { "$it.center" }, part(Side.Left), part(Side.Right))
    }

    /**
     * A key the drawer leaves to the activity: back, which the navigation decides, not the drawer's own key handling.
     */
    fun leavesKeyToActivity(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_BACK

    /** What a side menu gives the layouts around: everything but its own group. */
    fun withoutSideMenu(options: Options): Options = options.copy().apply { sideMenu = SideMenuOptions() }
}

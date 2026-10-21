package dev.apexnative.navigation.viewcontrollers.sidemenu

import android.app.Activity
import android.graphics.Color
import android.view.View
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.SideMenuOptions
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.UiUtils
import dev.apexnative.navigation.viewcontrollers.sidemenu.SideMenuMotion.Side
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.sidemenu.SideMenuView

/**
 * Applies a side menu's options to its view on Android: the widths and heights of the menus, the background, the
 * `testID`s. Each moment applies its part, from the side menu's resolved options
 * (`SideMenuController.resolveShownOptions`: the current part's chain, the center's `sideMenu` over an open menu's) —
 * at creation everything with the heights, at every appearance of a screen inside everything but the heights, at a
 * merge the fields it names, at a repaint the background and the `testID`s. The locks of `enabled` and the command
 * `visible` are the node's ([SideMenuMotion]). Main thread only.
 */
internal class SideMenuPresenter(activity: Activity) : Presenter(activity, Options()) {
    /** The side menu's [view] is made, with [options], resolved: the heights, read only now. */
    fun applyOnCreation(view: SideMenuView, options: Options) {
        for (side in Side.entries) {
            view.setPartHeight(side, SideMenuRules.height(SideMenuRules.sideOf(options.sideMenu, side), ::toPixels))
        }
        applyWidths(view, options.sideMenu)
        applyBackground(view, options)
        applyTestIds(view, options.sideMenu)
    }

    /**
     * A screen in the side menu appears: the side menu shows with [options], resolved at that moment: a width a screen
     * of the center set acts while it is on top, the menu's own or the default after it.
     */
    override fun applyOptions(view: ViewController<*>, options: Options) {
        val layout = view.peekView() as? SideMenuView ?: return
        applyWidths(layout, options.sideMenu)
        applyBackground(layout, options)
        applyTestIds(layout, options.sideMenu)
    }

    /**
     * A merge reached the side menu, its own or through a screen inside (`mergeOptions` step 3): the fields [options]
     * name, with the values of the side menu's resolution now, so a screen off what shows changes nothing until it
     * appears. A width waits for the next appearance, a height for nothing.
     */
    override fun mergeOptions(viewController: ViewController<*>, options: Options) {
        val layout = viewController.peekView() as? SideMenuView ?: return
        val touchesBackground = SideMenuRules.touchesBackground(options)
        val touchesTestIds = SideMenuRules.touchesTestIds(options.sideMenu)
        if (!touchesBackground && !touchesTestIds) return
        val shown = viewController.resolveShownOptions(DefaultOptions.current)
        if (touchesBackground) applyBackground(layout, shown)
        if (touchesTestIds) applyTestIds(layout, shown.sideMenu)
    }

    /** Light or dark changed, or new default options: the background and the `testID`s. */
    override fun onConfigurationChanged(controller: ViewController<*>, options: Options) {
        val layout = controller.peekView() as? SideMenuView ?: return
        applyBackground(layout, options)
        applyTestIds(layout, options.sideMenu)
    }

    private fun applyWidths(view: SideMenuView, options: SideMenuOptions) {
        for (side in Side.entries) {
            view.setWantedWidth(side, SideMenuRules.wantedWidth(SideMenuRules.sideOf(options, side), ::toPixels))
        }
    }

    // `layout.backgroundColor` on the drawer, its whole height, under the status bar; unset leaves the view as it is,
    // `'NoColor'` clears it.
    private fun applyBackground(view: View, options: Options) {
        val color = options.layout.backgroundColor
        if (!color.hasValue()) return
        view.setBackgroundColor(color.get(activity, Color.TRANSPARENT) ?: Color.TRANSPARENT)
    }

    // The drawer has an accessibility delegate of its own, which stays; the parts take the id's own.
    private fun applyTestIds(view: SideMenuView, options: SideMenuOptions) {
        val ids = SideMenuRules.testIds(options)
        TestIds.applyOver(view, ids.menu)
        TestIds.apply(view.center, ids.center)
        view.partOf(Side.Left)?.let { TestIds.apply(it, ids.left) }
        view.partOf(Side.Right)?.let { TestIds.apply(it, ids.right) }
    }

    private fun toPixels(dp: Double): Int = UiUtils.dpToPixels(activity, dp)
}

package dev.apexnative.navigation.viewcontrollers.stack.topbar

import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.UserChoices
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The check of a single-selection menu the user picked: written into the screen's user layer
 * ([ViewController.changeChoices]), never into its own options nor into a menu other screens share (the stack's, the
 * defaults'). It goes over the menu as the screen's options resolve, by the button's id and the item's id, and stays
 * until the app changes that menu's checks or takes the item away. Main thread only.
 */
internal object MenuChecks {
    /**
     * [itemId] of the menu of the button [buttonId] (or of `overflowMenu` without one) was picked on [screen], whose
     * options resolve to [resolved]: nothing changes unless its level has `singleSelection`.
     */
    fun write(screen: ViewController<*>, resolved: Options, buttonId: String?, itemId: String) {
        val topBar = resolved.topBar
        val menu = if (buttonId == null) {
            topBar.overflowMenu
        } else {
            (topBar.buttons.left.orEmpty() + topBar.buttons.right.orEmpty()).firstOrNull { it.id.get(null) == buttonId }
                ?.menu
        } ?: return
        if (!UserChoices.checks(menu, itemId)) return
        screen.changeChoices { it.check(buttonId, menu, itemId) }
    }
}

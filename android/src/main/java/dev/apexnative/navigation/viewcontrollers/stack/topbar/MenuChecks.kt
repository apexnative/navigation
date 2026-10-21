package dev.apexnative.navigation.viewcontrollers.stack.topbar

import dev.apexnative.navigation.options.ButtonMenuOptions
import dev.apexnative.navigation.options.ButtonOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.copyMenu
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The check of a single-selection menu the user picked: written through [ViewController.changeOptions] into the
 * screen's own menu, never into a menu other screens share (the stack's, the defaults'). A menu that came from
 * elsewhere is copied into the screen's own options first — for a button, its whole side, as lists of buttons replace
 * whole. The check stays with the screen until new options give the menu. Main thread only.
 */
internal object MenuChecks {
    /**
     * [itemId] of the menu of the button [buttonId] (or of `overflowMenu` without one) was picked on [screen], whose
     * options resolve to [resolved]: nothing changes unless its level has `singleSelection`.
     */
    fun write(screen: ViewController<*>, resolved: Options, buttonId: String?, itemId: String) {
        val topBar = resolved.topBar
        if (buttonId == null) {
            val menu = topBar.overflowMenu ?: return
            if (!checks(menu, itemId)) return
            screen.changeOptions { own ->
                val copy = (own.topBar.overflowMenu ?: menu).copyMenu()
                check(copy, itemId)
                own.topBar.overflowMenu = copy
            }
            return
        }
        val left = topBar.buttons.left?.any { it.id.get(null) == buttonId } == true
        val list = (if (left) topBar.buttons.left else topBar.buttons.right) ?: return
        val menu = list.firstOrNull { it.id.get(null) == buttonId }?.menu ?: return
        if (!checks(menu, itemId)) return
        screen.changeOptions { own ->
            val ownList = (if (left) own.topBar.buttons.left else own.topBar.buttons.right)
                ?: list.map(ButtonOptions::copyWithMenu)
            val button = ownList.firstOrNull { it.id.get(null) == buttonId } ?: return@changeOptions
            val copy = (button.menu ?: menu).copyMenu()
            check(copy, itemId)
            button.menu = copy
            if (left) own.topBar.buttons.left = ownList else own.topBar.buttons.right = ownList
        }
    }

    /** Whether picking [itemId] moves a check: its level has `singleSelection`. */
    fun checks(menu: ButtonMenuOptions, itemId: String): Boolean = levelOf(menu.items, menu.singleSelection, itemId) == true

    /** Moves the check of [itemId]'s level to it, in [menu] itself. */
    fun check(menu: ButtonMenuOptions, itemId: String) {
        mark(menu.items, menu.singleSelection, itemId)
    }

    // Whether the level holding [itemId] has single selection; null when [itemId] isn't in [elements].
    private fun levelOf(elements: List<ButtonMenuOptions.Element>, single: Boolean, itemId: String): Boolean? {
        if (elements.any { it is ButtonMenuOptions.Item && it.id == itemId }) return single
        for (element in elements) {
            if (element !is ButtonMenuOptions.Submenu) continue
            levelOf(element.items, element.singleSelection, itemId)?.let { return it }
        }
        return null
    }

    private fun mark(elements: List<ButtonMenuOptions.Element>, single: Boolean, itemId: String): Boolean {
        if (elements.any { it is ButtonMenuOptions.Item && it.id == itemId }) {
            if (single) elements.forEach { if (it is ButtonMenuOptions.Item) it.checked = it.id == itemId }
            return true
        }
        return elements.any { it is ButtonMenuOptions.Submenu && mark(it.items, it.singleSelection, itemId) }
    }
}

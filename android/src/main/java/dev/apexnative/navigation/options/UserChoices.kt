package dev.apexnative.navigation.options

import dev.apexnative.navigation.options.params.Text

/**
 * The user layer of a node: what the user chose in the native UI, kept apart from the node's own options so that the
 * app's next `mergeOptions` or `setDefaultOptions` doesn't wipe it. The choices are the check of a single-selection
 * menu (a bar button's `menu`, by the button's id; `topBar.overflowMenu`) and the search scope
 * (`topBar.searchBar.selectedScope`).
 *
 * It goes over everything else as the node's options resolve (defaults < parents < own < user), by identity rather
 * than as a tree merged over: a menu's check by the button's id and the item's id, the scope by its id. A choice whose
 * button, item or scope is gone is not applied, and leaves at the node's next merge; a merge that gives a chosen field
 * a value other than the one the app had there before takes the choice away ([yieldTo]). `setDefaultOptions` never
 * clears it. The node owns it and writes it through `ViewController.changeChoices`, which moves [OptionsCache]. Main
 * thread only.
 */
internal class UserChoices {
    // The menus' checks: per menu (a button's id, OVERFLOW for `overflowMenu`) the item picked in each of its levels,
    // in the order of the picks.
    private val checks = LinkedHashMap<String, MutableList<String>>()
    private var scope: String? = null

    /** The user chose nothing that stays. */
    val isEmpty: Boolean
        get() = checks.isEmpty() && scope == null

    /**
     * The user picked [itemId] in [menu], the menu of the button [buttonId] or `overflowMenu` (null), as it showed: it
     * takes the place of an earlier pick in the same level.
     */
    fun check(buttonId: String?, menu: ButtonMenuOptions, itemId: String) {
        val picks = checks.getOrPut(keyOf(buttonId)) { mutableListOf() }
        val level = levelItems(menu.items, itemId)?.map { it.id }.orEmpty()
        picks.removeAll { it == itemId || it in level }
        picks += itemId
    }

    /** The user picked the search scope [scopeId]. */
    fun pickScope(scopeId: String) {
        scope = scopeId
    }

    /**
     * Writes the choices into [resolved], a result of resolution nobody else holds yet; the menus it changes are copied
     * first, as a resolved result shares its menus with the layers they come from. Returns [resolved].
     */
    fun applyTo(resolved: Options): Options {
        if (isEmpty) return resolved
        val topBar = resolved.topBar
        for ((key, picks) in checks) {
            if (key == OVERFLOW) {
                val menu = topBar.overflowMenu ?: continue
                topBar.overflowMenu = menu.copyMenu().also { copy -> picks.forEach { mark(copy, it) } }
                continue
            }
            val button = buttonIn(topBar.buttons, key) ?: continue
            val menu = button.menu ?: continue
            button.menu = menu.copyMenu().also { copy -> picks.forEach { mark(copy, it) } }
        }
        scope?.let { picked ->
            val searchBar = topBar.searchBar
            if (searchBar.scopes?.any { it.id == picked } == true) searchBar.selectedScope = Text(picked)
        }
        return resolved
    }

    /**
     * The app merged [delta] into the node. A choice leaves when what it was made in is gone from [after] (the node's
     * options as they resolve after the merge, without this layer), or when [delta] gives its field a value other than
     * the one the node's own options had before the merge ([own]; where they had none, the app's resolution before the
     * merge, [before]). The same value sent again (a re-render) keeps the choice. Returns whether a choice left.
     */
    fun yieldTo(delta: Options, own: Options, before: Options, after: Options): Boolean {
        var changed = false
        val entries = checks.entries.iterator()
        while (entries.hasNext()) {
            val (key, picks) = entries.next()
            val now = menuIn(after, key)
            val given = menuIn(delta, key)
            val was = menuIn(own, key) ?: menuIn(before, key)
            val left = picks.removeAll { itemId ->
                val gone = now == null || levelItems(now.items, itemId) == null
                gone || (given != null && checkedInLevel(given, itemId) != was?.let { checkedInLevel(it, itemId) })
            }
            if (left) changed = true
            if (picks.isEmpty()) entries.remove()
        }
        scope?.let { picked ->
            val given = delta.topBar.searchBar.selectedScope
            val ownScope = own.topBar.searchBar.selectedScope
            val was = (if (ownScope.hasValue()) ownScope else before.topBar.searchBar.selectedScope).get(null)
            val changedByApp = given.hasValue() && given.get(null) != was
            val gone = after.topBar.searchBar.scopes?.any { it.id == picked } != true
            if (changedByApp || gone) {
                scope = null
                changed = true
            }
        }
        return changed
    }

    companion object {
        // The key of `overflowMenu`, which has no button; no placed button has an empty id.
        private const val OVERFLOW = ""

        private fun keyOf(buttonId: String?): String = buttonId ?: OVERFLOW

        /** Whether picking [itemId] in [menu] moves a check: its level has `singleSelection`. */
        fun checks(menu: ButtonMenuOptions, itemId: String): Boolean =
            levelOf(menu.items, menu.singleSelection, itemId) == true

        /** Moves the check of [itemId]'s level to it, in [menu] itself; nothing in a level without single selection. */
        fun mark(menu: ButtonMenuOptions, itemId: String) {
            mark(menu.items, menu.singleSelection, itemId)
        }

        private fun buttonIn(buttons: TopBarButtons, id: String): ButtonOptions? =
            buttons.left?.firstOrNull { it.id.get(null) == id } ?: buttons.right?.firstOrNull { it.id.get(null) == id }

        // The menu the key names in [options]: `overflowMenu`, or the menu of the button with that id.
        private fun menuIn(options: Options, key: String): ButtonMenuOptions? =
            if (key == OVERFLOW) options.topBar.overflowMenu else buttonIn(options.topBar.buttons, key)?.menu

        // The checked items of the level that holds [itemId] in [menu]: the field a merge gives that level.
        private fun checkedInLevel(menu: ButtonMenuOptions, itemId: String): Set<String>? =
            levelItems(menu.items, itemId)?.filter { it.checked }?.map { it.id }?.toSet()

        // The items of the level that holds [itemId]; null when it isn't in [elements].
        private fun levelItems(
            elements: List<ButtonMenuOptions.Element>,
            itemId: String,
        ): List<ButtonMenuOptions.Item>? {
            val items = elements.filterIsInstance<ButtonMenuOptions.Item>()
            if (items.any { it.id == itemId }) return items
            return elements.filterIsInstance<ButtonMenuOptions.Submenu>().firstNotNullOfOrNull {
                levelItems(it.items, itemId)
            }
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
}

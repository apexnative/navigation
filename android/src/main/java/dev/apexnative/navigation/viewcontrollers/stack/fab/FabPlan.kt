package dev.apexnative.navigation.viewcontrollers.stack.fab

import dev.apexnative.navigation.options.FabOptions

/**
 * What becomes of a stack's FAB when the screen on top changes or its `fab` options merge: a FAB exists only while the
 * options give `fab.id`; the same id updates the FAB that shows, another id replaces it, none takes it away. Non-empty
 * `actions` make it a menu; an empty list in a merge takes the actions away, and it is a plain button again. Pure.
 */
internal object FabPlan {
    enum class Step {
        /** Nothing shows, and nothing is to show. */
        None,

        /** A FAB shows, and the options give none: it goes. */
        Remove,

        /** Nothing shows: a FAB is made and shows. */
        Create,

        /** The FAB that shows stays and takes the options again. */
        Update,

        /** The FAB that shows goes, and another is made: another id, or a menu that becomes a button or back. */
        Replace,
    }

    /** The id the options give the FAB; none without one. */
    fun idOf(options: FabOptions): String? = options.id.takeIf { it.hasValue() && !it.isNone }?.get()?.ifEmpty { null }

    /** Whether the options make a menu: non-empty `actions`. */
    fun isMenu(options: FabOptions): Boolean = !options.actions.isNullOrEmpty()

    /** The step from the FAB that shows ([shownId], a menu or not) to the [options] resolved now. */
    fun step(shownId: String?, shownIsMenu: Boolean, options: FabOptions): Step {
        val id = idOf(options)
        return when {
            id == null -> if (shownId == null) Step.None else Step.Remove
            shownId == null -> Step.Create
            shownId != id || shownIsMenu != isMenu(options) -> Step.Replace
            else -> Step.Update
        }
    }
}

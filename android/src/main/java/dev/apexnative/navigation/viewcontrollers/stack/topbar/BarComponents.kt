package dev.apexnative.navigation.viewcontrollers.stack.topbar

import dev.apexnative.navigation.options.ButtonOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.TitleComponentOptions
import dev.apexnative.navigation.options.TopBarBackgroundComponentOptions
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.viewcontroller.IReactView
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The React components of the bar of one stack, by screen: each screen of the stack keeps its title, its background and
 * its buttons (by `componentId`) while it is in the stack. The same `name` and `componentId` at the same screen is the
 * same root, never made again; another replaces it, and the old one stops at once. A background with the same `name`
 * and `componentId` on several screens is one root, shared; it stops when no screen of the stack names it any more. The
 * roots go with their screen ([screenDestroyed]) or the stack ([destroyAll]). Each root renders at once, without
 * waiting for its layout: [makeRoot] marks its id so before its surface starts. Main thread only.
 */
internal class BarComponents(private val makeRoot: (componentId: String, name: String) -> IReactView) {
    /** A root of the bar: what it renders, and its content. */
    class Root(val name: String, val componentId: String, val view: IReactView)

    private data class Key(val name: String, val componentId: String)

    private class ScreenRoots {
        var title: Root? = null
        var background: Key? = null
        val buttons = LinkedHashMap<String, Root>()
    }

    private val screens = HashMap<ViewController<*>, ScreenRoots>()
    private val backgrounds = HashMap<Key, Root>()

    /** The title of [screen] for [options] (`topBar.title.component`): kept, made, or none without a `name`. */
    fun title(screen: ViewController<*>, options: TitleComponentOptions): Root? {
        val roots = rootsOf(screen)
        val key = keyOf(options.name, options.componentId, options.id)
        val current = roots.title
        if (key == null) {
            roots.title = null
            current?.let(::stop)
            return null
        }
        if (current != null && current.name == key.name && current.componentId == key.componentId) return current
        current?.let(::stop)
        return make(key).also { roots.title = it }
    }

    /** The background of [screen] for [options] (`topBar.background.component`): shared by `name` + `componentId`. */
    fun background(screen: ViewController<*>, options: TopBarBackgroundComponentOptions): Root? {
        val roots = rootsOf(screen)
        val key = keyOf(options.name, options.componentId, options.id)
        val old = roots.background
        roots.background = key
        if (old != null && old != key) release(old)
        if (key == null) return null
        return backgrounds.getOrPut(key) { make(key) }
    }

    /**
     * The React buttons of [screen] for [buttons] (all the sides at once), by `componentId`: those kept, those made;
     * the roots no button names any more stop (one map, a merge makes no extra one).
     */
    fun buttons(screen: ViewController<*>, buttons: List<ButtonOptions>): Map<String, Root> {
        val roots = rootsOf(screen)
        val wanted = LinkedHashMap<String, Key>()
        for (button in buttons) {
            val key = keyOf(button.component.name, button.component.componentId, button.component.id) ?: continue
            wanted[key.componentId] = key
        }
        val iterator = roots.buttons.entries.iterator()
        while (iterator.hasNext()) {
            val (componentId, root) = iterator.next()
            val key = wanted[componentId]
            if (key == null || key.name != root.name) {
                iterator.remove()
                stop(root)
            }
        }
        for ((componentId, key) in wanted) roots.buttons.getOrPut(componentId) { make(key) }
        return roots.buttons
    }

    /** The title of [screen] now; none. */
    fun titleOf(screen: ViewController<*>): Root? = screens[screen]?.title

    /** The background of [screen] now; none. */
    fun backgroundOf(screen: ViewController<*>): Root? = screens[screen]?.background?.let(backgrounds::get)

    /** The screens that have components here. */
    fun screens(): List<ViewController<*>> = screens.keys.toList()

    /** Whether [root] is a background still alive here. */
    fun holds(root: Root): Boolean = backgrounds.values.any { it === root }

    /** The React buttons of [screen] now. */
    fun buttonsOf(screen: ViewController<*>): Collection<Root> = screens[screen]?.buttons?.values.orEmpty()

    /**
     * Whether the components of the bar [screen] shows with [options] have drawn: its title unless its `waitForRender`
     * is `false`, and its background unless its own is `false`. Its React buttons don't count: they render during the
     * transition.
     */
    fun isDrawn(screen: ViewController<*>, options: Options): Boolean = waited(screen, options).all { it.isReady() }

    /** Calls [listener] once [isDrawn]; at once when it is. It can't be taken back: the caller guards it. */
    fun whenDrawn(screen: ViewController<*>, options: Options, listener: () -> Unit) {
        val waited = waited(screen, options)
        if (waited.all { it.isReady() }) return listener()
        var told = false
        for (view in waited) {
            view.whenReady {
                if (!told && waited.all { it.isReady() }) {
                    told = true
                    listener()
                }
            }
        }
    }

    /** [screen] is destroyed: its roots stop, its background when no other screen names it. */
    fun screenDestroyed(screen: ViewController<*>) {
        val roots = screens.remove(screen) ?: return
        roots.title?.let(::stop)
        roots.buttons.values.forEach(::stop)
        roots.background?.let(::release)
    }

    /** The stack is destroyed: every root stops. */
    fun destroyAll() {
        screens.keys.toList().forEach(::screenDestroyed)
        backgrounds.values.toList().forEach(::stop)
        backgrounds.clear()
    }

    private fun waited(screen: ViewController<*>, options: Options): List<IReactView> {
        val waited = ArrayList<IReactView>(2)
        val title = options.topBar.title.component
        if (!title.waitForRender.isFalse) titleOf(screen)?.let { waited += it.view }
        val background = options.topBar.background.component
        if (!background.waitForRender.isFalse) backgroundOf(screen)?.let { waited += it.view }
        return waited
    }

    private fun rootsOf(screen: ViewController<*>): ScreenRoots = screens.getOrPut(screen, ::ScreenRoots)

    // A shared background no screen names any more stops.
    private fun release(key: Key) {
        if (screens.values.any { it.background == key }) return
        backgrounds.remove(key)?.let(::stop)
    }

    // Made first, counted after: the maker asks whether another root with the id lives.
    private fun make(key: Key): Root {
        val root = Root(key.name, key.componentId, makeRoot(key.componentId, key.name))
        LiveIds.add(key.componentId)
        return root
    }

    // Stops a root once; what the readiness store kept of its id goes unless another live root holds the id.
    private fun stop(root: Root) {
        root.view.destroy()
        if (LiveIds.remove(root.componentId)) ScreenReadiness.remove(root.componentId)
    }

    private fun keyOf(name: Text, componentId: Text, id: Text): Key? {
        val componentName = name.get(null)?.takeIf { it.isNotEmpty() } ?: return null
        val rootId = componentId.get(null)?.takeIf { it.isNotEmpty() } ?: id.get(null)?.takeIf { it.isNotEmpty() }
            ?: return null
        return Key(componentName, rootId)
    }

    companion object {
        /** Whether a bar root with [id] is alive now, in any stack. */
        fun isLive(id: String): Boolean = LiveIds.has(id)

        /**
         * Another root that renders at once lives with [id] (a cell of the tabs' custom row): it counts as the bar's
         * roots do, so a root that stops clears the id's readiness only when it was the last one with it.
         */
        internal fun rootMade(id: String) = LiveIds.add(id)

        /** A root of [rootMade] stopped; true when no live root has [id] any more. */
        internal fun rootStopped(id: String): Boolean = LiveIds.remove(id)

        /** The JavaScript went (a reload): no id counts as held by a live root any more, as `ScreenReadiness.reset`. */
        fun reset() = LiveIds.clear()
    }

    // The ids of the bar roots alive now, in every stack: a root that stops clears its id's readiness only when it was
    // the last one with it.
    private object LiveIds {
        private val counts = HashMap<String, Int>()

        fun has(id: String): Boolean = (counts[id] ?: 0) > 0

        fun clear() = counts.clear()

        fun add(id: String) {
            counts[id] = (counts[id] ?: 0) + 1
        }

        // True when no root with the id is left.
        fun remove(id: String): Boolean {
            val count = (counts[id] ?: return true) - 1
            if (count <= 0) counts.remove(id) else counts[id] = count
            return count <= 0
        }
    }
}

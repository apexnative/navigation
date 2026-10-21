package dev.apexnative.navigation.options

/**
 * The one point where the options of the app change: a process-wide generation that grows at each change. A node's
 * resolved options are kept while the generation stays and the nodes they were built along are the same
 * (`ViewController.resolveCurrentOptions`). Exactly two places call [optionsChanged]: the write of a node's own options
 * (`ViewController.options`, and `changeOptions` through it) and the store of new default options
 * ([DefaultOptions.set]). Nothing else changes a node's own options or the defaults. Main thread only.
 */
object OptionsCache {
    /** The generation of the options; it only grows. */
    @JvmStatic
    var generation: Long = 0
        private set

    /** A node's own options or the default options changed: every resolved result built before is stale. */
    @JvmStatic
    fun optionsChanged() {
        generation++
    }
}

package dev.apexnative.navigation.options

/**
 * The default options of the app (`setDefaultOptions`): one tree per process, which `setDefaultOptions` replaces whole.
 * Every node reads them when it resolves its options, so new defaults reach every layer and every node made later; they
 * are never written into a node's own options. Empty before the first `setDefaultOptions`; they work without an
 * activity. Nobody changes [current] in place. Main thread only.
 */
object DefaultOptions {
    /** The default options now; an empty tree before the first `setDefaultOptions`. */
    @JvmStatic
    var current: Options = Options()
        private set

    /** [options] become the defaults, in place of the ones before (never merged into them). */
    @JvmStatic
    fun set(options: Options) {
        current = options
        OptionsCache.optionsChanged()
    }
}

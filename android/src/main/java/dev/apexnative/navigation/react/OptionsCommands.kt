package dev.apexnative.navigation.react

import android.content.Context
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.OptionsReader
import dev.apexnative.navigation.system.AppDirection
import dev.apexnative.navigation.viewcontrollers.navigator.Navigators
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The commands of the options, `setDefaultOptions` and `mergeOptions`: both without a promise. Their options are read
 * on the module thread, in the runner's step before the main task; the main task stores and applies them. What can't be
 * read is logged (`[ApexNavigation] <method>: <text>`); the app goes on.
 */
internal class OptionsCommands(
    private val runner: CommandRunner,
    /** The app's context, which platform colors resolve with; none in unit tests. */
    private val context: () -> Context?,
    /** The node a command names, in the navigation of the current activity; none when it has none. Main thread. */
    private val findNode: (String) -> ViewController<*>?,
    /**
     * Repaints what shows in every live navigation activity after new default options, a direction they changed first
     * (`Navigator.defaultOptionsChanged`). Main thread.
     */
    private val repaint: () -> Unit,
    /**
     * A merge of the options went into the node: the modal or the overlay it is in reads its options again, and the
     * system UI follows (`Navigator.optionsMerged`, `mergeOptions` step 5). Main thread.
     */
    private val merged: (ViewController<*>, Options) -> Unit = { _, _ -> },
) {
    constructor(runner: CommandRunner, context: () -> Context?) : this(
        runner = runner,
        context = context,
        findNode = { Navigators.current()?.navigator?.findNode(it) },
        repaint = {
            val direction = AppDirection.defaultsChanged(DefaultOptions.current)
            Navigators.all().forEach { it.navigator?.defaultOptionsChanged(direction) }
        },
        merged = { node, options -> Navigators.current()?.navigator?.optionsMerged(node, options) },
    )

    /**
     * `setDefaultOptions`: the tree read whole replaces the default options (never merged into them), with or without
     * an activity, and what shows is repainted. A value it can't read keeps the default options before. A
     * `layout.direction` that changed applies to every activity with a root before the repaint.
     */
    fun setDefaultOptions(options: ReadableMap) = runner.runVoid(
        name = SET_DEFAULT_OPTIONS,
        what = "set the default options",
        prepare = { OptionsReader.read(options, context()) },
    ) { read ->
        val path = read.unreadablePath
        if (path != null) {
            runner.logFailure(SET_DEFAULT_OPTIONS, ErrorTexts.unreadableDefaultOption(path))
        } else {
            DefaultOptions.set(read.options)
            repaint()
        }
    }

    /**
     * `mergeOptions`: the options go into the node [componentId] names (`ViewController.mergeOptions`), then the modal
     * or the overlay it is in takes them (its sheet, its `testID`s, `interceptTouchOutside`). No node with that id (or
     * a node of the old root's modals): nothing, not even a log line. Without an activity: one log line; JavaScript
     * keeps the options for the next activity. A value it can't read is logged, and what could be read still merges.
     */
    fun mergeOptions(componentId: String, options: ReadableMap) = runner.runVoid(
        name = MERGE_OPTIONS,
        what = "merge the options of the screen '$componentId'",
        prepare = { OptionsReader.read(options, context()) },
    ) { read ->
        if (!runner.hasActivity()) {
            runner.logFailure(MERGE_OPTIONS, ErrorTexts.mergeOptionsNoActivity())
            return@runVoid
        }
        val node = findNode(componentId) ?: return@runVoid
        read.unreadablePath?.let { runner.logFailure(MERGE_OPTIONS, ErrorTexts.unreadableOption(it, componentId)) }
        node.mergeOptions(read.options)
        merged(node, read.options)
    }

    private companion object {
        const val SET_DEFAULT_OPTIONS = "setDefaultOptions"
        const val MERGE_OPTIONS = "mergeOptions"
    }
}

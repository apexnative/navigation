package dev.apexnative.navigation.react

import android.content.Context
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import dev.apexnative.navigation.layout.LayoutException
import dev.apexnative.navigation.layout.LayoutReader
import dev.apexnative.navigation.nativescreen.NativeScreenCalls
import dev.apexnative.navigation.options.OptionsReader
import dev.apexnative.navigation.slots.SlotHost
import dev.apexnative.navigation.viewcontrollers.externalcomponent.NativeScreens
import dev.apexnative.navigation.viewcontrollers.navigator.BackCallbackState
import dev.apexnative.navigation.viewcontrollers.navigator.Navigator
import dev.apexnative.navigation.viewcontrollers.navigator.Navigators
import dev.apexnative.navigation.viewcontrollers.stack.PreparedFrame

/**
 * The TurboModule `ANNTurboModule` (`src/adapters/NativeANNTurboModule.ts`): the commands and the questions of
 * JavaScript. Every asynchronous method goes one path, [CommandRunner]: its layout is read and checked on the module
 * thread, its work runs in one main-thread task, in call order. The synchronous methods read the stores the main thread
 * fills and answer at once on the JavaScript thread. The stack's commands go to the activity's `StackCommands` (the
 * split view's too: its screens go into a stack or over one), the modals' and the overlays' to its `Navigator`.
 * `dismissPreview` is iOS's: Android has no preview, and it does nothing.
 */
@ReactModule(name = NativeANNTurboModuleSpec.NAME)
class NavigationTurboModule(reactContext: ReactApplicationContext) : NativeANNTurboModuleSpec(reactContext) {
    // Whether this module's JavaScript is the first of the process: fixed as the module is made.
    private val coldStart = StartInfo.countRuntime()
    private val runner = CommandRunner()
    private val optionsCommands = OptionsCommands(runner) { appContext() }

    override fun setRoot(commandId: String, layout: ReadableMap, promise: Promise) = runner.run(
        name = "setRoot",
        commandId = commandId,
        what = "show the root '${LayoutReader.idOf(rootOf(layout))}'",
        promise = promise,
        check = { LayoutReader.root(layout, context = appContext()) },
    ) { root, answer -> navigator().setRoot(root, answer) }

    // With the activity the user sees, once per process (the slots module keeps it once); without one, nothing:
    // JavaScript's wait for the host ends at its limit.
    override fun startHost() = runner.runVoid("startHost", "start the host of the slots") {
        Navigators.current()?.let(SlotHost::ensureStarted)
    }

    // Without an activity too: kept for the layouts to come.
    override fun setDefaultOptions(options: ReadableMap) = optionsCommands.setDefaultOptions(options)

    // Without an activity there is nothing to merge into: one line in the log.
    override fun mergeOptions(componentId: String, options: ReadableMap) =
        optionsCommands.mergeOptions(componentId, options)

    override fun push(commandId: String, componentId: String, layout: ReadableMap, promise: Promise) {
        val what = "push the screen '${LayoutReader.idOf(layout)}'"
        runner.run(
            name = "push",
            commandId = commandId,
            what = what,
            promise = promise,
            check = { LayoutReader.node(layout, context = appContext()) },
        ) { node, answer -> navigator().stackCommands.push(componentId, node, answer, what) }
    }

    // Android prepares the screen of a slot for its push: a layout that fails its check, a case that doesn't prepare,
    // or an exception is answered with null, and the push goes the ordinary way.
    override fun preparePush(commandId: String, componentId: String, layout: ReadableMap, promise: Promise) {
        val what = "prepare the screen '${LayoutReader.idOf(layout)}'"
        runner.run(
            name = "preparePush",
            commandId = commandId,
            what = what,
            promise = promise,
            needsActivity = false,
            check = {
                try {
                    LayoutReader.node(layout, context = appContext())
                } catch (e: Throwable) {
                    val text = (e as? LayoutException)?.text ?: ErrorTexts.couldNot(what, ErrorTexts.reasonOf(e))
                    runner.logFailure("preparePush", text)
                    null
                }
            },
        ) { node, answer ->
            val frame = try {
                node?.let {
                    Navigators.current()?.navigator?.stackCommands
                        ?.preparePush(componentId, it, hasActivity = runner.hasActivity())
                }
            } catch (e: Throwable) {
                runner.logFailure("preparePush", ErrorTexts.couldNot(what, ErrorTexts.reasonOf(e)))
                null
            }
            answer.resolve(frame?.let(::frameMap))
        }
    }

    override fun cancelPreparedPush(componentId: String) =
        runner.runVoid("cancelPreparedPush", "cancelPreparedPush for the screen '$componentId'") {
            Navigators.current()?.navigator?.stackCommands?.cancelPreparedPush(componentId)
        }

    override fun pop(commandId: String, componentId: String, promise: Promise) {
        val what = "pop the screen '$componentId'"
        runner.run("pop", commandId, what, promise, check = {}) { _, answer ->
            navigator().stackCommands.pop(componentId, answer, what)
        }
    }

    override fun popTo(commandId: String, componentId: String, promise: Promise) {
        val what = "pop to the screen '$componentId'"
        runner.run("popTo", commandId, what, promise, check = {}) { _, answer ->
            navigator().stackCommands.popTo(componentId, answer, what)
        }
    }

    override fun popToRoot(commandId: String, componentId: String, promise: Promise) {
        val what = "pop the stack of the screen '$componentId'"
        runner.run("popToRoot", commandId, what, promise, check = {}) { _, answer ->
            navigator().stackCommands.popToRoot(componentId, answer, what)
        }
    }

    override fun setStackRoot(commandId: String, componentId: String, layout: ReadableArray, promise: Promise) {
        val what = "set the stack of the screen '$componentId'"
        runner.run(
            name = "setStackRoot",
            commandId = commandId,
            what = what,
            promise = promise,
            check = {
                LayoutReader.nodes(layout, context = appContext()).ifEmpty {
                    throw LayoutException(ErrorTexts.noScreensForStack(componentId))
                }
            },
        ) { nodes, answer -> navigator().stackCommands.setStackRoot(componentId, nodes, answer, what) }
    }

    // For every navigation activity; JavaScript that goes resets it (`ReloadCoordinator.onJavaScriptGoes`).
    override fun setBackListeners(active: Boolean) =
        runner.runVoid("setBackListeners", "setBackListeners") { BackCallbackState.setJsListeners(active) }

    override fun showModal(commandId: String, layout: ReadableMap, promise: Promise) = runner.run(
        name = "showModal",
        commandId = commandId,
        what = "show the screen '${LayoutReader.idOf(layout)}'",
        promise = promise,
        check = { LayoutReader.node(layout, context = appContext()) },
    ) { node, answer -> navigator().showModal(node, answer) }

    // The options are read on the module thread; a value that can't be read rejects before anything changes, with the
    // command's screen. The modal's own lookup answers the rest: a modal closed below is no node any more, and still
    // resolves.
    override fun dismissModal(commandId: String, componentId: String, options: ReadableMap?, promise: Promise) =
        runner.run(
            name = "dismissModal",
            commandId = commandId,
            what = "dismiss the modal of the screen '$componentId'",
            promise = promise,
            check = {
                options?.let { map ->
                    val read = OptionsReader.read(map, appContext())
                    read.unreadablePath?.let { throw LayoutException(ErrorTexts.unreadableOption(it, componentId)) }
                    read.options
                }
            },
        ) { read, answer -> navigator().dismissModal(componentId, read, answer) }

    // A value that can't be read rejects with the top modal's visible screen, which the main thread knows; without a
    // modal the command resolves as without options.
    override fun dismissAllModals(commandId: String, options: ReadableMap?, promise: Promise) = runner.run(
        name = "dismissAllModals",
        commandId = commandId,
        what = "dismiss all modals",
        promise = promise,
        check = { options?.let { OptionsReader.read(it, appContext()) } },
    ) { read, answer -> navigator().dismissAllModals(read?.options, read?.unreadablePath, answer) }

    override fun showOverlay(commandId: String, layout: ReadableMap, promise: Promise) = runner.run(
        name = "showOverlay",
        commandId = commandId,
        what = "show the screen '${LayoutReader.idOf(layout)}'",
        promise = promise,
        check = { LayoutReader.node(layout, context = appContext()) },
    ) { node, answer -> navigator().showOverlay(node, answer) }

    override fun dismissOverlay(commandId: String, componentId: String, promise: Promise) = runner.run(
        name = "dismissOverlay",
        commandId = commandId,
        what = "dismiss the overlay of the screen '$componentId'",
        promise = promise,
        check = {},
    ) { _, answer -> navigator().dismissOverlay(componentId, answer) }

    override fun dismissAllOverlays(commandId: String, promise: Promise) = runner.run(
        name = "dismissAllOverlays",
        commandId = commandId,
        what = "dismiss all overlays",
        promise = promise,
        check = {},
    ) { _, answer -> navigator().dismissAllOverlays(answer) }

    override fun showInColumn(
        commandId: String,
        componentId: String,
        column: String,
        layout: ReadableMap,
        promise: Promise,
    ) {
        // Android's split view has the primary and the secondary pane; another column rejects (`showInColumn`).
        val what = "show the screen '${LayoutReader.idOf(layout)}' in the '$column' column"
        runner.run(
            name = "showInColumn",
            commandId = commandId,
            what = what,
            promise = promise,
            check = { LayoutReader.node(layout, context = appContext()) },
        ) { node, answer -> navigator().stackCommands.showInColumn(componentId, column, node, answer, what) }
    }

    override fun setColumnShown(
        commandId: String,
        componentId: String,
        column: String,
        shown: Boolean,
        promise: Promise,
    ) = runner.run(
        name = "setColumnShown",
        commandId = commandId,
        what = "${if (shown) "show" else "hide"} the '$column' column of the screen '$componentId'",
        promise = promise,
        check = {},
    ) { _, answer -> navigator().stackCommands.setColumnShown(componentId, column, shown, answer) }

    override fun getArrangementState(componentId: String): WritableMap = ScreenEnvironment.getArrangementState(componentId)

    override fun getScreenInsets(componentId: String): WritableMap = ScreenEnvironment.getScreenInsets(componentId)

    override fun getScreenRegions(componentId: String): WritableMap = ScreenEnvironment.getScreenRegions(componentId)

    // With the activity the user sees, if there is one; the hinge remembers it and goes on with the next activity
    // (`Hinge.attach`).
    override fun setHingeUpdates(enabled: Boolean) = runner.runVoid("setHingeUpdates", "setHingeUpdates") {
        Hinge.setEnabled(Navigators.current(), enabled)
    }

    override fun getHinge(): WritableMap = Hinge.get()

    override fun getLaunchLink(promise: Promise) = runner.run(
        name = "getLaunchLink",
        commandId = null,
        what = "read the launch link",
        promise = promise,
        needsActivity = false,
        check = {},
    ) { _, answer ->
        // The link of the activity the user sees, once; none without one, or for one that is finishing.
        val activity = Navigators.current()?.takeIf { !it.isFinishing }
        answer.resolve(activity?.let { LaunchLink.take(it.intent) })
    }

    override fun getStartInfo(): WritableMap = StartInfo.toMap(coldStart, reactApplicationContext.resources)

    // The update is read here, on the JavaScript thread, `null` kept as a value; the native screen's context takes it
    // on the main thread. Another node, or none, takes nothing (`updateNativeScreenProps`).
    override fun updateNativeScreenProps(componentId: String, update: ReadableMap) = runner.runVoid(
        name = "updateNativeScreenProps",
        what = "updateNativeScreenProps for the screen '$componentId'",
        prepare = { ReadableJson.toJson(update) },
    ) { json -> NativeScreens.contextOf(NativeScreens.find(componentId))?.applyUpdate(json) }

    // The call ends once, on the main thread; an unknown or answered id does nothing (`resolveNativeScreenCall`).
    override fun resolveNativeScreenCall(callId: String, result: ReadableMap) = runner.runVoid(
        name = "resolveNativeScreenCall",
        what = "resolveNativeScreenCall",
        prepare = { ReadableJson.toJson(result) },
    ) { json -> NativeScreenCalls.resolve(callId, json) }

    override fun dismissPreview(previewId: String) =
        runner.runVoid("dismissPreview", "dismissPreview for the screen '$previewId'") {}

    // The frame of a prepared screen as JavaScript reads it: `{ width, height, padding }` in dp.
    private fun frameMap(frame: PreparedFrame): WritableMap = Arguments.createMap().apply {
        putDouble("width", frame.width.toDouble())
        putDouble("height", frame.height.toDouble())
        putMap("padding", Arguments.createMap().apply {
            putDouble("top", frame.padding.top)
            putDouble("right", frame.padding.right)
            putDouble("bottom", frame.padding.bottom)
            putDouble("left", frame.padding.left)
        })
    }

    // The navigation of the activity the command runs in; the runner checked there is an activity. One without its
    // navigation yet (or any more) has nowhere to show screens either: the command rejects with the runner's own text.
    private fun navigator(): Navigator =
        Navigators.current()?.navigator ?: throw LayoutException(ErrorTexts.nowhereToShow())

    // The app's context, which the colors of the options resolve platform colors with.
    private fun appContext(): Context = reactApplicationContext.applicationContext

    // The root of the layout of `setRoot`, for the text of a failure; none when it isn't there.
    private fun rootOf(layout: ReadableMap): ReadableMap? = try {
        if (layout.hasKey("root") && !layout.isNull("root")) layout.getMap("root") else null
    } catch (e: RuntimeException) {
        null
    }
}

package dev.apexnative.navigation.nativescreen

import androidx.annotation.RestrictTo
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.EventEmitter
import dev.apexnative.navigation.utils.UiThread
import org.json.JSONArray
import org.json.JSONObject

/** What a native screen gets when JS opens it, for as long as it is open. */
class NativeScreenContext @RestrictTo(RestrictTo.Scope.LIBRARY) constructor(
    /** The screen's layout id: `externalComponent.id`, or the one JS generated. */
    val componentId: String,
    props: JSONObject,
    functions: Collection<String>,
    private val eventEmitter: EventEmitter,
) {
    /**
     * The props: those of the command that showed the screen, with what its `useProps` returns over them. Plain data;
     * they change while the screen is open, and [onPropsChange] tells when.
     */
    var props: JSONObject = props
        private set

    /** The names of the functions in the props, which [call] runs in JS. */
    var functions: Set<String> = functions.toSet()
        private set

    // The JavaScript the screen was made for: once it went, its emitter may reach the next one, which never showed
    // this screen (bridgeless: an old context sends through the host's current instance).
    private val generation = ReloadCoordinator.generation

    /** Called on the main thread with the new props, each time they change. */
    var onPropsChange: ((JSONObject) -> Unit)? = null

    /**
     * Runs a function of the props in JS. [result] gets, on the main thread, what it returned (for a promise, what it
     * resolved with) as plain data, or the error: a function the props don't have, one that threw, a screen gone.
     */
    @JvmOverloads
    fun call(function: String, arguments: JSONArray = JSONArray(), result: ((Result<Any?>) -> Unit)? = null) =
        send("function", function, arguments, result)

    /**
     * Runs a command of the navigation from this screen in JS, as `useNavigation()` of a React screen does: push, pop,
     * popTo, popToRoot, showInColumn, showModal, dismiss, showOverlay, setOptions, selectTab, setTabBadge.
     * [arguments] are those of the JS command; [result] gets what it resolved with.
     */
    @JvmOverloads
    fun navigate(command: String, arguments: JSONArray = JSONArray(), result: ((Result<Any?>) -> Unit)? = null) =
        send("command", command, arguments, result)

    /** New props from JS, on the main thread: `{ props, removed, functions }`, the changed ones and the names gone. */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun applyUpdate(update: JSONObject) {
        val next = JSONObject(props, props.keys().asSequence().toList().toTypedArray())
        update.optJSONObject("props")?.let { changed ->
            changed.keys().forEach { key -> next.put(key, changed.get(key)) }
        }
        update.optJSONArray("removed")?.let { removed ->
            for (i in 0 until removed.length()) next.remove(removed.getString(i))
        }
        props = next
        update.optJSONArray("functions")?.let { names ->
            functions = (0 until names.length()).map(names::getString).toSet()
        }
        onPropsChange?.invoke(next)
    }

    // The call waits for its answer under its id; the event takes it to JS. Without a sender for the JavaScript that
    // runs (none yet, or the screen's went) nothing could answer it: it fails at once.
    private fun send(kind: String, name: String, arguments: JSONArray, result: ((Result<Any?>) -> Unit)?) {
        UiThread.post {
            if (!eventEmitter.canSend || !ReloadCoordinator.isCurrent(generation)) {
                val text = ErrorTexts.nativeCallNotRunning(name, componentId)
                result?.invoke(Result.failure(NativeScreenCallException(text)))
                return@post
            }
            val callId = NativeScreenCalls.add(componentId, name, result)
            eventEmitter.emitNativeScreenCall(componentId, callId, kind, name, arguments)
        }
    }
}

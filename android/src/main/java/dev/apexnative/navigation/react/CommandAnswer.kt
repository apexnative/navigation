package dev.apexnative.navigation.react

import android.util.Log
import com.facebook.react.bridge.Promise
import dev.apexnative.navigation.react.events.EventEmitter
import dev.apexnative.navigation.react.events.NavigationStateReporter
import dev.apexnative.navigation.utils.NavigationLog

/**
 * The one answer of a command with a promise: [resolve] or [reject], once; after that, and once the JavaScript it came
 * from went ([generation]), nothing. [CommandRunner] makes it on the main thread for each command; area code that takes
 * a [CommandListener] answers through [asListener]. Main thread only.
 */
internal class CommandAnswer(
    /** The method of the command: `push`, `setRoot`, … */
    val commandName: String,
    /** The command's id; none for `getLaunchLink`, the push's own for `preparePush`. */
    val commandId: String?,
    private val promise: Promise,
    /** The number of the JavaScript the command came from (`ReloadCoordinator`). */
    val generation: Int,
    private val events: AnswerEvents = AnswerEvents.Current,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = { Log.e(NavigationLog.TAG, it) },
) {
    /** The command has answered. */
    var isAnswered = false
        private set

    /** Whether an answer now would reach the JavaScript the command came from. */
    val canAnswer: Boolean
        get() = !isAnswered && ReloadCoordinator.isCurrent(generation)

    /**
     * Resolves the promise with [value]. For the commands that change what shows, the navigation state goes first,
     * when it changed, so JavaScript has it when its `await` goes on; `ANN.CommandCompleted` follows the promise, for
     * the commands that show or dismiss. A failure of either is logged; the promise goes all the same.
     */
    fun resolve(value: Any?) {
        if (!canAnswer) return
        isAnswered = true
        if (commandName in CommandContract.STATE) guarded("report the navigation state") { events.beforeAnswer() }
        promise.resolve(value)
        val commandId = commandId
        if (commandName in CommandContract.COMPLETED && commandId != null) {
            guarded("send ANN.CommandCompleted") { events.commandCompleted(commandName, commandId, clock()) }
        }
    }

    /** Rejects the promise with [text] alone: no code of the navigation's own. */
    fun reject(text: String) {
        if (!canAnswer) return
        isAnswered = true
        promise.reject(null, text)
    }

    /** A [CommandListener] that answers this command: success resolves with the id, an error rejects. */
    fun asListener(): CommandListener = AnswerListener(this)

    // A step around the promise that must not keep the promise from going.
    private inline fun guarded(what: String, step: () -> Unit) {
        try {
            step()
        } catch (e: Throwable) {
            log("[ApexNavigation] $commandName: ${ErrorTexts.couldNot(what, ErrorTexts.reasonOf(e))}")
        }
    }
}

/** What goes to JavaScript around the answer of a command. Main thread only. */
internal interface AnswerEvents {
    /** Right before the promise resolves: the navigation state, when it changed. */
    fun beforeAnswer()

    /** After the promise resolved: `ANN.CommandCompleted`. */
    fun commandCompleted(commandName: String, commandId: String, completionTime: Long)

    /** Through the sender of the JavaScript that runs now. */
    object Current : AnswerEvents {
        override fun beforeAnswer() {
            val emitter = EventEmitter.current()
            if (emitter.canSend) NavigationStateReporter.updateNow(emitter)
        }

        override fun commandCompleted(commandName: String, commandId: String, completionTime: Long) {
            EventEmitter.current().emitCommandCompleted(commandName, commandId, completionTime)
        }
    }
}

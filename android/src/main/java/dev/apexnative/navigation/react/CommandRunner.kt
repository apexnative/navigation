package dev.apexnative.navigation.react

import android.util.Log
import com.facebook.react.bridge.Promise
import dev.apexnative.navigation.layout.LayoutException
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.utils.UiThread
import dev.apexnative.navigation.viewcontrollers.navigator.BackCallbackState
import dev.apexnative.navigation.viewcontrollers.navigator.Navigators

/**
 * The one path of every asynchronous method of `ANNTurboModule`. On the module thread, at once: the command takes the
 * number of its JavaScript ([ReloadCoordinator]) and its layout is read and checked whole; a failure rejects right
 * there and nothing is queued. Then one main-thread task per command, in call order, does all its work: nothing when
 * its JavaScript went, a rejection when the navigation has nowhere to show screens, else the command's body. A command
 * without a promise always runs its body: one that needs the navigation finds none without an activity and does
 * nothing, or logs it ([hasActivity]). An exception in the body rejects the command (or, without a promise, writes one
 * line to the log); it never ends the app. After each main-thread task, done or rejected, what back does is computed
 * again for every activity ([afterCommand]): `setDefaultOptions` and `mergeOptions` too.
 */
internal class CommandRunner(
    private val post: (Runnable) -> Unit,
    private val activityPresent: () -> Boolean,
    private val events: AnswerEvents,
    private val log: (String) -> Unit,
    private val afterCommand: () -> Unit = { BackCallbackState.update() },
) {
    constructor() : this(
        post = { UiThread.post(it) },
        activityPresent = { Navigators.current() != null },
        events = AnswerEvents.Current,
        log = { Log.e(NavigationLog.TAG, it) },
    )

    /** Whether the app has a navigation activity for commands to run in (`Navigators.current`). Main thread. */
    fun hasActivity(): Boolean = activityPresent()

    /**
     * Runs the command [name] with a promise. [check] reads and checks its layout on the calling (module) thread; a
     * `LayoutException` rejects with its text, another exception with `ErrorTexts.couldNot` of [what]. [body] runs
     * on the main thread with what [check] read and the command's [CommandAnswer]; [needsActivity] false for the
     * commands that answer without one (`preparePush`, `getLaunchLink`).
     */
    fun <L> run(
        name: String,
        commandId: String?,
        what: String,
        promise: Promise,
        needsActivity: Boolean = true,
        check: () -> L,
        body: (L, CommandAnswer) -> Unit,
    ) {
        val generation = ReloadCoordinator.generation
        val layout = try {
            check()
        } catch (e: LayoutException) {
            promise.reject(null, e.text)
            return
        } catch (e: Throwable) {
            promise.reject(null, ErrorTexts.couldNot(what, ErrorTexts.reasonOf(e)))
            return
        }
        post(Runnable {
            if (!ReloadCoordinator.isCurrent(generation)) return@Runnable
            val answer = CommandAnswer(name, commandId, promise, generation, events, log = log)
            if (needsActivity && !hasActivity()) {
                answer.reject(ErrorTexts.nowhereToShow())
                afterCommand()
                return@Runnable
            }
            val outer = CommandScope.isRunning
            CommandScope.isRunning = true
            try {
                body(layout, answer)
            } catch (e: LayoutException) {
                // A layout that can't be made as the command makes it (a native screen whose creator made nothing).
                answer.reject(e.text)
            } catch (e: Throwable) {
                answer.reject(ErrorTexts.couldNot(what, ErrorTexts.reasonOf(e)))
            } finally {
                CommandScope.isRunning = outer
            }
            afterCommand()
        })
    }

    /**
     * Runs the command [name] without a promise: one main-thread task, nothing when its JavaScript went, [body]
     * inside the protection, with or without an activity; a failure writes
     * `[ApexNavigation] <name>: could not <what>: <reason>` to the log.
     */
    fun runVoid(name: String, what: String, body: () -> Unit) = runVoid(name, what, prepare = {}) { body() }

    /**
     * As [runVoid], with a step on the calling (module) thread first: [prepare] reads what the command brings (its
     * options) and [body] gets it on the main thread. A failure of [prepare] is logged as one of [body], and nothing
     * is queued.
     */
    fun <P> runVoid(name: String, what: String, prepare: () -> P, body: (P) -> Unit) {
        val generation = ReloadCoordinator.generation
        val prepared = try {
            prepare()
        } catch (e: Throwable) {
            logFailure(name, ErrorTexts.couldNot(what, ErrorTexts.reasonOf(e)))
            return
        }
        post(Runnable {
            if (!ReloadCoordinator.isCurrent(generation)) return@Runnable
            try {
                body(prepared)
            } catch (e: Throwable) {
                logFailure(name, ErrorTexts.couldNot(what, ErrorTexts.reasonOf(e)))
            }
            afterCommand()
        })
    }

    /** The one line a command without a promise writes when it fails: [text] from `ErrorTexts`. */
    fun logFailure(name: String, text: String) {
        log("[ApexNavigation] $name: $text")
    }
}

/**
 * Whether the main-thread work of a command with a promise runs now ([CommandRunner.run]): what fails while it makes
 * its layout rejects that command (a `LayoutException` with its text); what fails later, when no command can be
 * answered any more, is logged instead. Main thread only.
 */
internal object CommandScope {
    var isRunning = false
}

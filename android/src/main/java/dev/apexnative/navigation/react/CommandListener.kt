package dev.apexnative.navigation.react

/**
 * The answer of a command on the native side: [onSuccess] with the id of the screen it names, or [onError] with the
 * reason, which rejects the command's promise. A command's answer gives one ([CommandAnswer.asListener]). Main thread.
 */
interface CommandListener {
    /** The command succeeded; [childId] is the screen it names. */
    fun onSuccess(childId: String)

    /** The command failed for [message], a text of the navigation's table. */
    fun onError(message: String)
}

/** A [CommandListener] that does nothing until a method is overridden. */
open class CommandListenerAdapter : CommandListener {
    override fun onSuccess(childId: String) = Unit

    override fun onError(message: String) = Unit
}

/** The [CommandListener] of a command's [CommandAnswer]: [onSuccess] resolves with the id, [onError] rejects. */
internal class AnswerListener(private val answer: CommandAnswer) : CommandListener {
    override fun onSuccess(childId: String) = answer.resolve(childId)

    override fun onError(message: String) = answer.reject(message)
}

package dev.apexnative.navigation.react

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap

/** One record of what reached JavaScript, in order: `state`, `resolve:<value>`, `reject:<text>`, `completed:<name>`. */
class Journal {
    val entries = mutableListOf<String>()
    val completed = mutableListOf<Triple<String, String, Long>>()
}

/** A promise that writes its answers into [journal]. */
class FakePromise(private val journal: Journal) : Promise {
    val codes = mutableListOf<String?>()

    override fun resolve(value: Any?) {
        journal.entries += "resolve:$value"
    }

    override fun reject(code: String?, message: String?) = record(code, message)

    override fun reject(code: String?, throwable: Throwable?) = record(code, throwable?.message)

    override fun reject(code: String?, message: String?, throwable: Throwable?) = record(code, message)

    override fun reject(throwable: Throwable) = record(null, throwable.message)

    override fun reject(throwable: Throwable, userInfo: WritableMap) = record(null, throwable.message)

    override fun reject(code: String?, userInfo: WritableMap) = record(code, null)

    override fun reject(code: String?, throwable: Throwable?, userInfo: WritableMap) = record(code, throwable?.message)

    override fun reject(code: String?, message: String?, userInfo: WritableMap) = record(code, message)

    override fun reject(code: String?, message: String?, throwable: Throwable?, userInfo: WritableMap?) =
        record(code, message)

    @Deprecated("React Native's own, not used by the navigation")
    override fun reject(message: String) = record(null, message)

    private fun record(code: String?, message: String?) {
        codes += code
        journal.entries += "reject:$message"
    }
}

/** The answer's state step (`state`) and `ANN.CommandCompleted`, written into [journal]. */
internal class FakeEvents(private val journal: Journal) : AnswerEvents {
    override fun stateNow() {
        journal.entries += "state"
    }

    override fun commandCompleted(commandName: String, commandId: String, completionTime: Long) {
        journal.entries += "completed:$commandName"
        journal.completed += Triple(commandName, commandId, completionTime)
    }
}

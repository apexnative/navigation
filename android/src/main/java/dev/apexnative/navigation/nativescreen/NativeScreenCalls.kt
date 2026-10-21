package dev.apexnative.navigation.nativescreen

import androidx.annotation.RestrictTo
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import org.json.JSONArray
import org.json.JSONObject

/** A call of a native screen failed in JS, or could not reach it. */
class NativeScreenCallException(message: String) : Exception(message)

/** The calls of native screens waiting for JS to answer, by their ids. Main thread only. */
@RestrictTo(RestrictTo.Scope.LIBRARY)
object NativeScreenCalls {
    private class Call(val componentId: String, val name: String, val result: ((Result<Any?>) -> Unit)?)

    private val calls = HashMap<String, Call>()
    private var lastId = 0

    /** Keeps the result of the call [name] of the screen; returns the id JS answers it with. */
    fun add(componentId: String, name: String, result: ((Result<Any?>) -> Unit)?): String {
        val callId = "NativeScreenCall${++lastId}"
        calls[callId] = Call(componentId, name, result)
        return callId
    }

    /** JS answered: `{ value }`, or `{ error }` with its message. */
    @JvmStatic
    fun resolve(callId: String, answer: JSONObject) {
        if (answer.has("error")) {
            finish(callId, Result.failure(NativeScreenCallException(answer.getString("error"))))
        } else {
            finish(callId, Result.success(answer.opt("value").takeUnless { it == JSONObject.NULL }))
        }
    }

    /** The JavaScript that would answer is gone (a reload): every call fails with [reason] of its name and screen. */
    @JvmStatic
    fun failAll(reason: (name: String, componentId: String) -> String) =
        calls.toList().forEach { (callId, call) -> fail(callId, reason(call.name, call.componentId)) }

    /** The arguments of a call as JS reads them. */
    @JvmStatic
    fun toArguments(arguments: JSONArray): WritableArray = Arguments.makeNativeArray(toJava(arguments) as List<*>)

    private fun fail(callId: String, reason: String) =
        finish(callId, Result.failure(NativeScreenCallException(reason)))

    private fun finish(callId: String, result: Result<Any?>) {
        val call = calls.remove(callId) ?: return
        call.result?.invoke(result)
    }

    // JSON as the lists and maps React Native converts; JSONObject.NULL as null.
    private fun toJava(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { toJava(value.opt(it)) }
        is JSONArray -> (0 until value.length()).map { toJava(value.opt(it)) }
        JSONObject.NULL -> null
        else -> value
    }
}

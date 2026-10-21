package dev.apexnative.navigation.utils

import android.os.Handler
import android.os.Looper

/** The main thread, where the navigation changes its views and its state. */
object UiThread {
    private val handler = Handler(Looper.getMainLooper())

    /** Runs [task] on the main thread on a later pass of its loop. */
    @JvmStatic
    fun post(task: Runnable) {
        handler.post(task)
    }

    /** Runs [task] at once when the caller is on the main thread, else on a later pass of its loop. */
    @JvmStatic
    fun runOnMain(task: Runnable) {
        if (isMainThread()) task.run() else handler.post(task)
    }

    /** Runs [task] on the main thread after [delay] milliseconds. */
    @JvmStatic
    fun postDelayed(task: Runnable, delay: Long) {
        handler.postDelayed(task, delay)
    }

    /** Whether the caller runs on the main thread. */
    @JvmStatic
    fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()
}

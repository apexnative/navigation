package dev.apexnative.navigation.react

import dev.apexnative.navigation.NavigationApplication
import dev.apexnative.navigation.utils.UiThread

/**
 * The calls JavaScript made to the module that may not have reached the main thread yet: [after] runs a task on the
 * main thread behind every one of them. A screen's first mount reaches the main thread from Fabric while a
 * `mergeOptions` of the same React commit (its layout effects: `useScreenOptions`) may still be on its way: an
 * asynchronous method goes from the JavaScript thread to the module's thread (`BridgelessNativeMethodCallInvoker`),
 * which reads its options there and posts its body to the main thread ([CommandRunner]). The task goes the same way: a
 * turn of the JavaScript thread, which comes after the task of the commit with its layout effects; then the module's
 * thread, behind the calls that task made; then the main thread, behind their bodies. Each of the three runs its queue
 * in order, so every call JavaScript made before the mount has applied when the task runs.
 *
 * Without JavaScript (none yet, a reload, a queue that ended) the task is posted to the main thread at once: nothing is
 * on its way. A queue that ends while the task is on it drops it, and a wait with no limit of its own (a push with
 * `waitForRender: true` whose content has mounted: the task is its only end) would not end; a queue ends only as the
 * JavaScript goes (a reload, the host destroyed), and then the layouts go with it (`ReloadCoordinator`), the stack
 * and its waiting push too. Main thread.
 */
internal object PendingCalls {
    /** A queue the task goes through: it runs what it takes in order, and says false when it ended and took nothing. */
    fun interface Queue {
        fun run(task: Runnable): Boolean
    }

    /** Runs [task] on the main thread, on a later turn, after every call JavaScript made so far has applied there. */
    fun after(task: () -> Unit) {
        val context = NavigationApplication.instanceOrNull?.reactGatewayOrNull?.currentReactContext
            ?.takeIf { it.hasActiveReactInstance() }
        val js = context?.jsMessageQueueThread?.let { Queue(it::runOnQueue) }
        val module = context?.nativeModulesMessageQueueThread?.let { Queue(it::runOnQueue) }
        after(js, module, UiThread::post, task)
    }

    /**
     * The way of [after] over the queues given: [js], then [module], then [main]; straight to [main] without one of
     * them, or from where a queue refused it. The unit tests run it over their own queues.
     */
    fun after(js: Queue?, module: Queue?, main: (Runnable) -> Unit, task: () -> Unit) {
        val onMain = Runnable(task)
        if (js == null || module == null) return main(onMain)
        val toMain = Runnable { main(onMain) }
        val toModule = Runnable { if (!module.run(toMain)) main(onMain) }
        if (!js.run(toModule)) main(onMain)
    }
}

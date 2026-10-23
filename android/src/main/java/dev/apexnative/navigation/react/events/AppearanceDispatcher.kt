package dev.apexnative.navigation.react.events

import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.ReactView
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.ScreenAppearance.Event
import dev.apexnative.navigation.react.events.ScreenAppearance.Phase
import dev.apexnative.navigation.slots.SlotScreenView
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.externalcomponent.ExternalComponentViewController
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.parent.ScreensLeavingWithNode
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import java.util.Collections
import java.util.WeakHashMap

/**
 * The one place that tells JavaScript when screens and components appear and disappear, and the events of the
 * transitions and the nodes that end around them: `ANN.ScreenPopped`, `ANN.ModalDismissed`, `ANN.NativeScreenReleased`,
 * `ANN.SlotDestroyed`.
 *
 * Appearance goes per record ([ScreenAppearance]), synchronously: at most one WillAppear and one DidAppear per
 * appearance, DidDisappear only after an appearance whose WillAppear went. An event asked before the content is ready
 * is held, and the held events go in order as soon as the content mounts, a command marks it ready, or
 * [HOLD_LIMIT_MILLIS] passed since the first was held — the hold never delays a transition. A DidAppear or a
 * DidDisappear that goes asks the navigation state for one update in this pass of the main loop. Nothing goes for a
 * record or a node of a JavaScript that went; their phases still move.
 *
 * Containers and commands report through the nodes: a screen node tells its record from `onViewWillAppear`,
 * `onViewDidAppear` and `onViewDisappear`, which its layouts forward. [beginTransition] and [endTransition] put a
 * transition's events. Main thread only.
 */
internal object AppearanceDispatcher {
    /** How long held appearance events wait for the content at most. */
    const val HOLD_LIMIT_MILLIS = 500L

    /** Where the events go: the JavaScript that runs now; a journal in the tests. */
    interface Sender {
        /** Sends `ANN.ComponentWillAppear`: the component starts to appear. */
        fun componentWillAppear(componentId: String, componentName: String, type: ComponentType)

        /** Sends `ANN.ComponentDidAppear`: the component has appeared. */
        fun componentDidAppear(componentId: String, componentName: String, type: ComponentType)

        /** Sends `ANN.ComponentDidDisappear`: the component that appeared is hidden or gone. */
        fun componentDidDisappear(componentId: String, componentName: String, type: ComponentType)

        /** Sends `ANN.ScreenPopped`: the screen left its stack. */
        fun screenPopped(componentId: String)

        /** Sends `ANN.ModalDismissed` with the screen the modal showed and the number of modals that went with it. */
        fun modalDismissed(componentId: String, componentName: String, modalsDismissed: Int)

        /** Sends `ANN.NativeScreenReleased`: the native screen left the layout. */
        fun nativeScreenReleased(componentId: String)

        /** Sends `ANN.SlotDestroyed`: JavaScript takes the content of the screen's slot away. */
        fun slotDestroyed(componentId: String)

        /** Sends `ANN.NavigationButtonPressed`: a button the navigation draws for the screen was pressed. */
        fun navigationButtonPressed(componentId: String, buttonId: String) = Unit

        /** Sends `ANN.BottomTabPressed`: the user pressed a tab, before any selection. */
        fun bottomTabPressed(tabIndex: Int, bottomTabsId: String, screenId: String?, wasSelected: Boolean) = Unit

        /** Sends `ANN.BottomTabSelected`: a press made another tab the selected one. */
        fun bottomTabSelected(selectedTabIndex: Int, unselectedTabIndex: Int, bottomTabsId: String) = Unit

        /** Sends `ANN.BottomTabLongPressed`: the user long-pressed a tab. */
        fun bottomTabLongPressed(selectedTabIndex: Int, bottomTabsId: String) = Unit

        /** Sends `ANN.ModalAttemptedToDismiss`: the user tried to close a modal that holds them. */
        fun modalAttemptedToDismiss(componentId: String) = Unit

        /** Sends `ANN.SheetDetentChanged`: the user dragged a sheet to another detent (`medium`, `large`, dp). */
        fun sheetDetentChanged(componentId: String, detent: Any) = Unit
    }

    /**
     * A node that owns the appearance record of what it shows: a screen (its content's record, a native screen's
     * own). The dispatcher finds a screen's record through it ([afterDidAppear]).
     */
    interface RecordOwner {
        /** The record of the node's appearance; none while there is nothing to show. */
        val appearanceRecord: ScreenAppearance?
    }

    /** The navigation state ([NavigationState]): one update in this pass of the main loop, or one now. */
    interface StateUpdates {
        /**
         * Asks for one update in this pass of the main loop: the calls of one pass make one, after a DidAppear or a
         * DidDisappear went. It is sent only when it changed.
         */
        fun setNeedsUpdate()

        /**
         * Computes the state now and sends it when it changed: a command's answer, once
         * (`CommandAnswer.reportStateNow`), the end of a user's transition, a tab chosen from code.
         */
        fun updateNow()

        /**
         * Holds the updates asked from now on ([setNeedsUpdate]): one goes when the last hold is released ([release]).
         * [updateNow] is never held. A `setRoot` with modals or overlays in its layout holds them until its answer.
         */
        fun hold()

        /** Ends one [hold]. */
        fun release()
    }

    /** Runs a task after a delay on the main thread; what it returns cancels the task. */
    fun interface Scheduler {
        /** Runs [task] on the main thread after [delayMillis]; the function returned cancels it if it hasn't run. */
        fun schedule(delayMillis: Long, task: () -> Unit): () -> Unit
    }

    /** The events reach the JavaScript of the app's React context ([EventEmitter.current]). */
    object EmitterSender : Sender {
        override fun componentWillAppear(componentId: String, componentName: String, type: ComponentType) =
            EventEmitter.current().emitComponentWillAppear(componentId, componentName, type)

        override fun componentDidAppear(componentId: String, componentName: String, type: ComponentType) =
            EventEmitter.current().emitComponentDidAppear(componentId, componentName, type)

        override fun componentDidDisappear(componentId: String, componentName: String, type: ComponentType) =
            EventEmitter.current().emitComponentDidDisappear(componentId, componentName, type)

        override fun screenPopped(componentId: String) = EventEmitter.current().emitScreenPopped(componentId)

        override fun modalDismissed(componentId: String, componentName: String, modalsDismissed: Int) =
            EventEmitter.current().emitModalDismissed(componentId, componentName, modalsDismissed)

        override fun nativeScreenReleased(componentId: String) =
            EventEmitter.current().emitNativeScreenReleased(componentId)

        override fun slotDestroyed(componentId: String) = EventEmitter.current().emitSlotDestroyed(componentId)

        override fun navigationButtonPressed(componentId: String, buttonId: String) =
            EventEmitter.current().emitNavigationButtonPressed(componentId, buttonId)

        override fun bottomTabPressed(tabIndex: Int, bottomTabsId: String, screenId: String?, wasSelected: Boolean) =
            EventEmitter.current().emitBottomTabPressed(tabIndex, bottomTabsId, screenId, wasSelected)

        override fun bottomTabSelected(selectedTabIndex: Int, unselectedTabIndex: Int, bottomTabsId: String) =
            EventEmitter.current().emitBottomTabSelected(selectedTabIndex, unselectedTabIndex, bottomTabsId)

        override fun bottomTabLongPressed(selectedTabIndex: Int, bottomTabsId: String) =
            EventEmitter.current().emitBottomTabLongPressed(selectedTabIndex, bottomTabsId)

        override fun modalAttemptedToDismiss(componentId: String) =
            EventEmitter.current().emitModalAttemptedToDismiss(componentId)

        override fun sheetDetentChanged(componentId: String, detent: Any) =
            EventEmitter.current().emitSheetDetentChanged(componentId, detent)
    }

    /** The main thread's loop. */
    object MainScheduler : Scheduler {
        private val handler by lazy { Handler(Looper.getMainLooper()) }

        override fun schedule(delayMillis: Long, task: () -> Unit): () -> Unit {
            val runnable = Runnable { task() }
            handler.postDelayed(runnable, delayMillis)
            return { handler.removeCallbacks(runnable) }
        }
    }

    /** Where the events go: [EmitterSender]; the tests put a journal here. */
    var sender: Sender = EmitterSender

    /** The navigation state the appearance events and transitions update: [NavigationState]; a journal in tests. */
    var state: StateUpdates = NavigationState

    /** The clock of the hold limit: [MainScheduler]; the tests put a clock that moves when they say. */
    var scheduler: Scheduler = MainScheduler

    /** Where a failed step of a transition is logged: the error log; the tests read it. */
    var log: (String) -> Unit = { Log.e(NavigationLog.TAG, it) }

    // A transition whose caller sends the state itself ends: the DidDisappears it sends ask for no update.
    private var holdsState = false

    // The native screens that told JavaScript they went: once per node.
    private val released: MutableSet<ViewController<*>> = Collections.newSetFromMap(WeakHashMap())

    // Appearance of a record.

    /** The record starts to appear: WillAppear, now when it is ready, else held. Only once per appearance. */
    fun willAppear(record: ScreenAppearance) {
        if (record.phase != Phase.Hidden) return
        record.phase = Phase.Appearing
        if (record.isReady) send(record, Event.WillAppear) else hold(record, Event.WillAppear)
    }

    /**
     * The record has appeared: DidAppear, after a WillAppear (asked now when it wasn't), now when nothing is held and
     * it is ready, else held behind what is held. Only once per appearance.
     */
    fun didAppear(record: ScreenAppearance) {
        if (record.phase == Phase.Shown) return
        if (record.phase == Phase.Hidden) willAppear(record)
        record.phase = Phase.Shown
        if (record.held.isEmpty() && record.isReady) send(record, Event.DidAppear) else hold(record, Event.DidAppear)
    }

    /**
     * The record has disappeared. Nothing when it didn't appear. When its WillAppear is still held, JavaScript never
     * heard it appear: what is held goes away and nothing is sent. Otherwise a held DidAppear goes away and
     * DidDisappear is sent: a swipe that was cancelled gives WillAppear, then DidDisappear.
     */
    fun didDisappear(record: ScreenAppearance) {
        if (record.phase == Phase.Hidden) return
        record.phase = Phase.Hidden
        val appeared = Event.WillAppear !in record.held
        record.held.clear()
        cancelLimit(record)
        // What waited for its DidAppear goes now: none will be sent for this appearance.
        runAfterDidAppear(record)
        if (appeared) send(record, Event.DidDisappear)
    }

    /**
     * Runs [task] right after the DidAppear of [screen] is sent, in the same turn of the main loop: at once when it was
     * sent already or none is on its way, when [screen] owns no record, and when [screen] disappears before it goes. A
     * tab switch sends `ANN.BottomTabSelected` this way, right after the new tab's screen appeared; no transition waits
     * for it.
     */
    fun afterDidAppear(screen: ViewController<*>, task: () -> Unit) {
        val record = recordOf(screen)
        if (record == null || record.phase != Phase.Shown || Event.DidAppear !in record.held) return task()
        record.afterDidAppear += task
    }

    // The record of what [screen] shows: its React content's or slot's, a native screen's own.
    private fun recordOf(screen: ViewController<*>): ScreenAppearance? = when (screen) {
        is RecordOwner -> screen.appearanceRecord
        is ComponentViewController -> when (val content = screen.content) {
            is ReactView -> content.appearance
            is SlotScreenView -> content.appearance
            else -> null
        }
        is ExternalComponentViewController -> screen.appearance
        else -> null
    }

    private fun runAfterDidAppear(record: ScreenAppearance) {
        if (record.afterDidAppear.isEmpty()) return
        val tasks = record.afterDidAppear.toList()
        record.afterDidAppear.clear()
        tasks.forEach { it() }
    }

    /**
     * The record's content is ready: the held events go at once, in order, one at a time. A send may make JavaScript
     * hide the record at once ([didDisappear] from inside the send): what is still held goes no more then.
     */
    fun contentReady(record: ScreenAppearance) {
        record.setReady()
        cancelLimit(record)
        while (record.held.isNotEmpty() && record.phase != Phase.Hidden) send(record, record.held.removeAt(0))
    }

    private fun hold(record: ScreenAppearance, event: Event) {
        record.held += event
        if (record.cancelLimit == null) {
            record.cancelLimit = scheduler.schedule(HOLD_LIMIT_MILLIS) {
                record.cancelLimit = null
                contentReady(record)
            }
        }
        record.waitForContent { contentReady(record) }
    }

    private fun cancelLimit(record: ScreenAppearance) {
        val cancel = record.cancelLimit ?: return
        record.cancelLimit = null
        cancel()
    }

    private fun send(record: ScreenAppearance, event: Event) {
        if (!ReloadCoordinator.isCurrent(record.generation)) {
            if (event == Event.DidAppear) runAfterDidAppear(record)
            return
        }
        val id = record.componentId
        val name = record.componentName
        val type = record.componentType
        when (event) {
            Event.WillAppear -> sender.componentWillAppear(id, name, type)
            Event.DidAppear -> {
                sender.componentDidAppear(id, name, type)
                state.setNeedsUpdate()
                runAfterDidAppear(record)
            }
            Event.DidDisappear -> {
                sender.componentDidDisappear(id, name, type)
                if (!holdsState) state.setNeedsUpdate()
            }
        }
    }

    // Transitions.

    /** A transition starts: what comes in starts to appear. */
    fun beginTransition(showing: List<ViewController<*>>) {
        showing.forEach { it.onViewWillAppear() }
    }

    /**
     * A transition ended: what went disappears ([hiding]), what stands appears ([showing], with its WillAppear first
     * where it wasn't asked), the navigation state goes when it changed, then [events] (the transition's own:
     * `ANN.ScreenPopped`, `ANN.ModalDismissed`, and what must follow them, like stopping the content of what went),
     * then [answer] resolves with [value] (the promise, then `ANN.CommandCompleted`; no second state). The state is the
     * answer's one state step (`CommandAnswer.reportStateNow`), which sends nothing for a JavaScript that went; a
     * user's action has no answer, and a command answered before its transition ended had its state then: the state
     * of the end goes all the same, unless its JavaScript went. A state that fails is logged and the transition goes
     * on: [events] and the answer always come. Without [reportsState] the caller sends the state itself, later (a tab
     * switch: after `ANN.BottomTabSelected`; a `setRoot` with layers: from its answer).
     */
    fun endTransition(
        hiding: List<ViewController<*>>,
        showing: List<ViewController<*>>,
        events: (() -> Unit)?,
        answer: CommandAnswer?,
        value: Any?,
        reportsState: Boolean = true,
    ) {
        // Without the state, what went asks for none: the caller's own update comes after its events.
        holdsState = !reportsState
        try {
            hiding.forEach { it.onViewDisappear() }
        } finally {
            holdsState = false
        }
        showing.forEach { it.onViewDidAppear() }
        if (reportsState) {
            // The state before the transition's events, once per answer: the answer's own step before its promise
            // finds it sent. Without an answer to wait for, the dispatcher sends it; nothing for a JavaScript that
            // went.
            when {
                answer == null -> reportState()
                answer.isAnswered -> if (ReloadCoordinator.isCurrent(answer.generation)) reportState()
                else -> answer.reportStateNow()
            }
        }
        events?.invoke()
        answer?.resolve(value)
    }

    /**
     * The navigation state now, when it changed, without a command's answer (a command's goes through its answer,
     * `CommandAnswer.reportStateNow`); a failure is logged ([log]), nothing else stops for it.
     */
    fun reportState() {
        try {
            state.updateNow()
        } catch (e: Throwable) {
            val reason = ErrorTexts.couldNot("report the navigation state", ErrorTexts.reasonOf(e))
            log("[ApexNavigation] transition: $reason")
        }
    }

    // Events of nodes.

    /**
     * [node] left its stack (EVT-ScreenPopped): `ANN.ScreenPopped` for each screen that leaves with it (both screens of
     * a pair, `ScreensLeavingWithNode`), else for the node. The stack sends it in the events of [endTransition], before
     * the content of the node stops.
     */
    fun screenPopped(node: ViewController<*>) {
        val screens = (node as? ScreensLeavingWithNode)?.screensLeavingWithNode ?: listOf(node)
        for (screen in screens) {
            if (ReloadCoordinator.isCurrent(screen.jsGeneration)) sender.screenPopped(screen.id)
        }
    }

    /**
     * [modal] went with [count] modals (EVT-ModalDismissed): `ANN.ModalDismissed` with the id and the name of the
     * screen the modal shows, down its current children ([modalScreen]); a modal that shows no screen gives its own id
     * and no name.
     */
    fun modalDismissed(modal: ViewController<*>, count: Int) {
        if (!ReloadCoordinator.isCurrent(modal.jsGeneration)) return
        val named = modalScreen(modal)
        val name = if (named is ParentController<*>) "" else named.currentComponentName.orEmpty()
        sender.modalDismissed(named.id, name, count)
    }

    /**
     * The native screen [screen] left the layout (EVT-NativeScreenReleased): `ANN.NativeScreenReleased` once, however
     * it went; nothing for a screen of a JavaScript that went.
     */
    fun nativeScreenReleased(screen: ViewController<*>) {
        if (!released.add(screen)) return
        if (ReloadCoordinator.isCurrent(screen.jsGeneration)) sender.nativeScreenReleased(screen.id)
    }

    /**
     * The container of the slot screen [componentId], made for the JavaScript [generation], went while the id was
     * still its (EVT-SlotDestroyed): JavaScript takes the slot's content away; nothing for a JavaScript that went.
     */
    fun slotDestroyed(componentId: String, generation: Int) {
        if (ReloadCoordinator.isCurrent(generation)) sender.slotDestroyed(componentId)
    }

    /**
     * A button the core draws for [screen] was pressed, or the user tried to leave a screen that holds them
     * (EVT-NavigationButtonPressed): the back button or back with `popStackOnPress: false`, the FAB and its actions.
     * Nothing for a screen of a JavaScript that went.
     */
    fun navigationButtonPressed(screen: ViewController<*>, buttonId: String) {
        if (ReloadCoordinator.isCurrent(screen.jsGeneration)) sender.navigationButtonPressed(screen.id, buttonId)
    }

    /**
     * The user pressed the tab at [index] of [tabs] (EVT-BottomTabPressed): with [screenId], the tab's screen, and
     * whether it [wasSelected]. Nothing for tabs of a JavaScript that went.
     */
    fun bottomTabPressed(tabs: ViewController<*>, index: Int, screenId: String?, wasSelected: Boolean) {
        if (!ReloadCoordinator.isCurrent(tabs.jsGeneration)) return
        sender.bottomTabPressed(index, tabs.id, screenId, wasSelected)
    }

    /** A press made the tab at [index] of [tabs] the selected one, after [previous] (EVT-BottomTabSelected). */
    fun bottomTabSelected(tabs: ViewController<*>, index: Int, previous: Int) {
        if (ReloadCoordinator.isCurrent(tabs.jsGeneration)) sender.bottomTabSelected(index, previous, tabs.id)
    }

    /** The user long-pressed the tab at [index] of [tabs] (EVT-BottomTabLongPressed). */
    fun bottomTabLongPressed(tabs: ViewController<*>, index: Int) {
        if (ReloadCoordinator.isCurrent(tabs.jsGeneration)) sender.bottomTabLongPressed(index, tabs.id)
    }

    /**
     * The user tried to close the modal of [modal], which holds them (EVT-ModalAttemptedToDismiss): with the screen the
     * modal shows. Nothing for a JavaScript that went.
     */
    fun modalAttemptedToDismiss(modal: ViewController<*>) {
        val screen = modalScreen(modal)
        if (ReloadCoordinator.isCurrent(screen.jsGeneration)) sender.modalAttemptedToDismiss(screen.id)
    }

    /**
     * The user dragged the sheet of [modal] to [detent] (EVT-SheetDetentChanged): with the screen the modal shows.
     * Nothing for a JavaScript that went.
     */
    fun sheetDetentChanged(modal: ViewController<*>, detent: Any) {
        val screen = modalScreen(modal)
        if (ReloadCoordinator.isCurrent(screen.jsGeneration)) sender.sheetDetentChanged(screen.id, detent)
    }

    /**
     * The screen [node] shows: the node at the end of its current children (the one "visible screen" of the core): what
     * back's `ANN.NavigationButtonPressed` of a modal names, and, through [modalScreen], its `ANN.ModalDismissed`,
     * `ANN.ModalAttemptedToDismiss` and `ANN.SheetDetentChanged`. A layout without a child answers itself.
     */
    fun visibleNode(node: ViewController<*>): ViewController<*> {
        var current = node
        while (current is ParentController<*>) current = current.currentChild ?: break
        return current
    }

    /**
     * The node the events of the modal [modal] (its root) name: its [visibleNode] when that is a screen; the root
     * itself when the chain ends in a layout without a child, as on iOS. Asked again of what it gave, it gives the
     * same.
     */
    fun modalScreen(modal: ViewController<*>): ViewController<*> =
        visibleNode(modal).takeUnless { it is ParentController<*> } ?: modal
}

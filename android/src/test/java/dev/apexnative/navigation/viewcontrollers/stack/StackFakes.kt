package dev.apexnative.navigation.viewcontrollers.stack

import android.app.Activity
import android.widget.FrameLayout
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap
import dev.apexnative.navigation.options.BottomTabsEnterExitAnimationOptions
import dev.apexnative.navigation.options.LayoutAnimation
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.react.events.ComponentType
import dev.apexnative.navigation.react.events.FakeContent
import dev.apexnative.navigation.react.events.FakeScheduler
import dev.apexnative.navigation.react.events.ScreenAppearance
import dev.apexnative.navigation.viewcontrollers.bottomtabs.TabBarMotion
import dev.apexnative.navigation.viewcontrollers.child.ChildController
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.Readiness
import dev.apexnative.navigation.viewcontrollers.parent.ScreensLeavingWithNode
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.Motion
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.animations.AnimationRules

// The fakes of the stack's unit tests: views are stubs there, so the queue runs over a display that records what it
// is asked, a clock that moves when the test says, and screens that report their appearance through their records.

/**
 * A screen of a stack in the tests: its appearance goes through its own record, ready with its [content]; it tells
 * [journal] when it is destroyed (after its events) and [calls] when it starts to leave or comes to the front. With
 * [slot], its destroy sends `ANN.SlotDestroyed` unless it is only prepared, as a slot screen's container does.
 */
internal class QueueScreen(
    activity: Activity,
    registry: ChildControllersRegistry,
    id: String,
    private val journal: MutableList<String>,
    private val calls: MutableList<String>,
    options: Options = Options(),
    val content: FakeContent = FakeContent(ready = true),
    private val slot: Boolean = false,
) : ChildController<FrameLayout>(activity, registry, id, Presenter(activity, Options()), options),
    AppearanceDispatcher.RecordOwner {
    val appearance = ScreenAppearance(id, id, ComponentType.Component, content)

    override val appearanceRecord: ScreenAppearance
        get() = appearance

    /** Nothing ever told how it renders: no safe area view. */
    var renderUnknown = false

    /** Prepared for a push and not pushed yet. */
    var isPreparedPush = false

    /** Thrown once, as the screen is destroyed: a teardown that fails (a surface that can't stop). */
    var destroyFailure: RuntimeException? = null

    override val currentComponentName: String = id

    override fun createView(): FrameLayout = FrameLayout(activity)

    override fun onViewWillAppear() {
        if (!isDestroyed) AppearanceDispatcher.willAppear(appearance)
    }

    override fun onViewDidAppear() {
        if (isDestroyed) return
        AppearanceDispatcher.didAppear(appearance)
        super.onViewDidAppear()
    }

    override fun onViewDisappear() {
        if (!isDestroyed) AppearanceDispatcher.didDisappear(appearance)
    }

    override fun onLeaveStarted() {
        calls += "leave:$id"
    }

    override fun onViewBroughtToFront() {
        calls += "front:$id"
    }

    override fun applyTopInset() {
        calls += "topInset:$id"
    }

    override fun destroy() {
        destroyFailure?.let {
            destroyFailure = null
            throw it
        }
        if (isDestroyed) return
        onViewDisappear()
        super.destroy()
        journal += "destroyed:$id"
        if (slot && !isPreparedPush) AppearanceDispatcher.slotDestroyed(id, appearance.generation)
    }
}

/** A pair in a stack: one entry, two screens that leave with it. */
internal class QueuePair(
    activity: Activity,
    registry: ChildControllersRegistry,
    id: String,
    private val journal: MutableList<String>,
    private val screens: List<ViewController<*>>,
) : ChildController<FrameLayout>(activity, registry, id, Presenter(activity, Options()), Options()),
    ScreensLeavingWithNode {
    override val screensLeavingWithNode: List<ViewController<*>>
        get() = screens

    override fun createView(): FrameLayout = FrameLayout(activity)

    override fun destroy() {
        if (isDestroyed) return
        screens.forEach { it.destroy() }
        super.destroy()
        journal += "destroyed:$id"
    }
}

/** The readiness of [QueueScreen]s: their content, mounted when the test says. */
internal class QueueReadiness(private val log: MutableList<String>) : Readiness {
    override fun isReady(screen: ViewController<*>): Boolean = (screen as? QueueScreen)?.content?.isReady() ?: true

    override fun whenReady(screen: ViewController<*>, listener: () -> Unit) {
        val content = (screen as? QueueScreen)?.content ?: return listener()
        content.whenReady { listener() }
    }

    override fun markReady(screen: ViewController<*>) {
        log += "markReady:${screen.id}"
        (screen as? QueueScreen)?.appearance?.markReady()
    }

    override fun isRenderUnknown(screen: ViewController<*>): Boolean = (screen as? QueueScreen)?.renderUnknown == true

    /** The components of the bar over a screen drawn in advance, mounted when the test says; none: ready. */
    val bars = HashMap<ViewController<*>, FakeContent>()

    override fun areBarComponentsReady(screen: ViewController<*>): Boolean = bars[screen]?.isReady() ?: true

    override fun whenBarComponentsReady(screen: ViewController<*>, listener: () -> Unit) {
        val bar = bars[screen] ?: return listener()
        bar.whenReady { listener() }
    }
}

/**
 * What shows the stack in the tests: it records into [log] where the views go (`placeWaiting:<id>`, `raise:<id>`,
 * `reveal:<id>`, `hide:<id>`, `detach:<id>`, `remove:<id>`, `placeLower:<id>`, `stop:<id>`), keeps the alpha and the
 * translation (in dp) of each screen, and the animators it starts with the values each starts from
 * ([starts]: `<motion>:<id>:<alpha>:<translation>`), which the test ends ([finish]) or cancels.
 */
internal class FakeStackDisplay(
    private val log: MutableList<String>,
    private val scheduler: FakeScheduler,
) : StackDisplay {
    data class Values(var alpha: Float = 1f, var translation: Float = 0f)

    private class Prepared(val waiting: () -> Boolean, val mounted: () -> Unit, val start: () -> Unit) {
        var cancelLimit: () -> Unit = {}
    }

    override var hasView = true
    override var isOnScreen = true
    override var canAnimate = true

    /** Thrown once, as the next screen is raised: a display that fails. */
    var failure: RuntimeException? = null

    /** Thrown once, as the next lower screen is placed. */
    var lowerFailure: RuntimeException? = null

    /** Thrown once, as the next default animator starts. */
    var animateFailure: RuntimeException? = null

    /** Thrown once, as the next screen's view leaves the container. */
    var removeFailure: RuntimeException? = null

    /** Runs as an animator is stopped: a test may end other animators from there, as a cancel callback can. */
    var onStop: ((ViewController<*>) -> Unit)? = null

    /** What the animators started from, in order. */
    val starts = mutableListOf<String>()

    /** The end of every animator started, in order: a test may call one again, as a late callback would. */
    val ends = mutableListOf<Pair<ViewController<*>, () -> Unit>>()

    private val values = HashMap<ViewController<*>, Values>()
    private val placed = LinkedHashSet<ViewController<*>>()
    // The animators that run: a default motion, or none for an animation of its own ([customs]).
    private val running = LinkedHashMap<ViewController<*>, Pair<Motion?, () -> Unit>>()
    private val customs = HashMap<ViewController<*>, StackDisplay.Custom>()
    private val targets = LinkedHashMap<ViewController<*>, Pair<() -> Boolean, () -> Unit>>()
    private val prepared = HashMap<ViewController<*>, Prepared>()

    /** The screens whose animators run. */
    val animating: Set<ViewController<*>>
        get() = running.keys

    fun valuesOf(screen: ViewController<*>): Values = values.getOrPut(screen) { Values() }

    fun isRunning(screen: ViewController<*>): Boolean = screen in running

    /** The screens in the container, bottom first as they were placed. */
    val inContainer: Set<ViewController<*>>
        get() = placed

    override fun isPlaced(screen: ViewController<*>): Boolean = screen in placed

    override fun placeWaiting(screen: ViewController<*>) {
        log += "placeWaiting:${screen.id}"
        placed += screen
        valuesOf(screen).alpha = 0f
    }

    override fun placeLower(screen: ViewController<*>) {
        lowerFailure?.let {
            lowerFailure = null
            throw it
        }
        log += "placeLower:${screen.id}"
        placed += screen
        valuesOf(screen).alpha = 0f
    }

    override fun raise(screen: ViewController<*>) {
        failure?.let {
            failure = null
            throw it
        }
        log += "raise:${screen.id}"
        placed += screen
    }

    override fun reveal(screen: ViewController<*>, over: ViewController<*>) {
        log += "reveal:${screen.id}"
        placed += screen
    }

    override fun hide(screen: ViewController<*>) {
        log += "hide:${screen.id}"
    }

    override fun detach(screen: ViewController<*>) {
        running.remove(screen)
        log += "detach:${screen.id}"
        rest(screen)
        placed -= screen
    }

    override fun remove(screen: ViewController<*>) {
        removeFailure?.let {
            removeFailure = null
            throw it
        }
        running.remove(screen)
        if (placed.remove(screen)) log += "remove:${screen.id}"
    }

    override fun rest(screen: ViewController<*>) {
        val values = valuesOf(screen)
        values.alpha = 1f
        values.translation = 0f
    }

    override fun animate(screen: ViewController<*>, motion: Motion, fromCurrent: Boolean, done: () -> Unit) {
        animateFailure?.let {
            animateFailure = null
            throw it
        }
        val values = valuesOf(screen)
        when (motion) {
            Motion.PushIn -> {
                values.translation = DISTANCE
                values.alpha = 0f
            }
            Motion.PopReveal -> if (!fromCurrent) values.translation = -DISTANCE
            Motion.FadeIn -> values.alpha = 0f
            else -> Unit
        }
        starts += "$motion:${screen.id}:${values.alpha}:${values.translation}"
        running[screen] = motion to done
        ends += screen to done
    }

    /**
     * An animation of its own: recorded in [starts] as `Custom:<id>:<what>:<alpha>` ([describe]); a screen nothing
     * moves ends at once, as the real display's.
     */
    override fun animateCustom(screen: ViewController<*>, custom: StackDisplay.Custom, done: () -> Unit) {
        val values = valuesOf(screen)
        custom.alpha?.let { values.alpha = it }
        if (custom.side == null && custom.fade == StackDisplay.Fade.In) values.alpha = 0f
        starts += "Custom:${screen.id}:${describe(custom)}:${values.alpha}"
        if (custom.side == null && custom.fade == StackDisplay.Fade.None && custom.elements == null) return done()
        running[screen] = null to done
        customs[screen] = custom
        ends += screen to done
    }

    override fun awaitTargets(
        animation: LayoutAnimation,
        from: ViewController<*>?,
        to: ViewController<*>,
        waiting: () -> Boolean,
        done: () -> Unit,
    ): () -> Unit {
        log += "awaitTargets:${from?.id}>${to.id}"
        targets[to] = waiting to done
        return {
            if (targets.remove(to) != null) log += "stopTargets:${to.id}"
        }
    }

    /** Whether the transition of [screen] waits for its elements' targets. */
    fun awaitsTargets(screen: ViewController<*>): Boolean = screen in targets

    /** Whether the wait for [screen]'s targets would still go on now (its `waiting`). */
    fun targetsWaiting(screen: ViewController<*>): Boolean = targets[screen]?.first?.invoke() == true

    /**
     * The wait for [screen]'s targets ends: they are found, or its `waiting` stopped holding — as the real wait, which
     * ends either way unless it was stopped.
     */
    fun findTargets(screen: ViewController<*>) {
        val (_, done) = targets.remove(screen) ?: return
        done()
    }

    override fun stop(screen: ViewController<*>) {
        if (running.remove(screen) != null) log += "stop:${screen.id}"
        customs.remove(screen)
        onStop?.invoke(screen)
    }

    override fun awaitPrepared(
        screen: ViewController<*>,
        waiting: () -> Boolean,
        mounted: () -> Unit,
        start: () -> Unit,
    ) {
        log += "awaitPrepared:${screen.id}"
        val entry = Prepared(waiting, mounted, start)
        prepared[screen] = entry
        // The module's limit: mounted and started at 0.5 s if the content never mounts.
        entry.cancelLimit = scheduler.schedule(PREPARED_LIMIT) { runPrepared(screen, entry) }
    }

    /** Whether the push of [screen] waits for its slot's content. */
    fun awaitsPrepared(screen: ViewController<*>): Boolean = screen in prepared

    /** The waits of the prepared pushes end where the module would: [waiting] answers no, nothing runs later. */
    fun dropPrepared() {
        prepared.values.forEach { it.cancelLimit() }
        prepared.clear()
    }

    /** The slot's content of [screen] mounted: the transition starts on the pre-draw of that frame. */
    fun mountPrepared(screen: ViewController<*>) {
        val entry = prepared[screen] ?: return
        runPrepared(screen, entry)
    }

    private fun runPrepared(screen: ViewController<*>, entry: Prepared) {
        if (prepared[screen] !== entry) return
        prepared.remove(screen)
        entry.cancelLimit()
        if (!entry.waiting()) return
        entry.mounted()
        if (entry.waiting()) entry.start()
    }

    /** The transition moving [screen] goes [fraction] of its way: it is somewhere between its ends. */
    fun move(screen: ViewController<*>, fraction: Float) {
        val (motion, _) = running[screen] ?: return
        val values = valuesOf(screen)
        val (alpha, translation) = target(motion, values, screen)
        values.alpha += (alpha - values.alpha) * fraction
        values.translation += (translation - values.translation) * fraction
    }

    /** The animator of [screen] ends: its last values, then its end. */
    fun finish(screen: ViewController<*>) {
        val (motion, done) = running[screen] ?: return
        val values = valuesOf(screen)
        val (alpha, translation) = target(motion, values, screen)
        running.remove(screen)
        customs.remove(screen)
        values.alpha = alpha
        values.translation = translation
        done()
    }

    /** The system cancels the animator of [screen]: its end comes where it is. */
    fun cancelBySystem(screen: ViewController<*>) {
        val (_, done) = running.remove(screen) ?: return
        done()
    }

    /** Every animator ends, as long as some run. */
    fun finishAll() {
        while (running.isNotEmpty()) finish(running.keys.first())
    }

    private fun target(motion: Motion?, values: Values, screen: ViewController<*>): Pair<Float, Float> = when (motion) {
        Motion.PushIn, Motion.PopReveal, Motion.FadeIn -> 1f to 0f
        Motion.PushCover -> values.alpha to -DISTANCE
        Motion.PopOut -> 0f to DISTANCE
        Motion.FadeOut -> 0f to values.translation
        null -> when (customs[screen]?.fade) {
            StackDisplay.Fade.In -> 1f to values.translation
            StackDisplay.Fade.Out -> 0f to values.translation
            else -> values.alpha to values.translation
        }
    }

    companion object {
        const val DISTANCE = 96f
        const val PREPARED_LIMIT = 500L

        private val NAMES = listOf(
            "x", "y", "translationX", "translationY", "alpha", "scaleX", "scaleY", "rotationX", "rotationY", "rotation",
        )

        /**
         * A screen's part as `<side>[ fade-in|fade-out][ elements:<from>><to>]`: the properties of its side that play,
         * by name and joined by `+`, or `-` for none.
         */
        fun describe(custom: StackDisplay.Custom): String {
            val side = custom.side?.let { side ->
                NAMES.zip(AnimationRules.properties(side)).filter { AnimationRules.propertyPlays(it.second) }
                    .joinToString("+") { it.first }
            } ?: "-"
            val fade = when (custom.fade) {
                StackDisplay.Fade.In -> " fade-in"
                StackDisplay.Fade.Out -> " fade-out"
                StackDisplay.Fade.None -> ""
            }
            val elements = custom.elements?.let { " elements:${it.from?.id}>${it.to.id}" } ?: ""
            return side + fade + elements
        }
    }
}

/** What the queue tells its stack: the changes of its current child, the ends of its operations. */
internal class QueueHost(private val log: MutableList<String>) : StackQueue.Host {
    var settled = 0
    var ownRoot = false

    /** The stack's rule of the answers (`StackController.answerIdOf`), which the stack the host is given to sets. */
    var answers: (ViewController<*>, StackQueue.AnswerKind) -> String = { entry, _ -> entry.id }

    override fun answerIdOf(entry: ViewController<*>, kind: StackQueue.AnswerKind): String = answers(entry, kind)

    override fun currentChildChanged() = Unit

    /** How the bar's row went with each change of the current child: `Push`, `Pop`, `FadeThrough` or `none`. */
    val barMotions = mutableListOf<String>()

    /** How the bar would show or hide with each change: `at once`, `animated`, or `side` with a side of its own. */
    val barVisibilities = mutableListOf<String>()

    /** How the bar went with the screens of an animation of its own: `Forward`, `Back` or `none`. */
    val barTravels = mutableListOf<String>()

    override fun barTransition(
        motion: StackDisplay.BarMotion?,
        visibility: StackDisplay.BarVisibility,
        travel: StackDisplay.BarTravel?,
    ) {
        barMotions += motion?.name ?: "none"
        barTravels += travel?.name ?: "none"
        val side = visibility.side?.takeIf { it.enter.hasValue() || it.exit.hasValue() }
        barVisibilities += when {
            !visibility.animated -> "at once"
            side != null -> "side"
            else -> "animated"
        }
    }

    /** How the tab bar was told to go with each change of the current child. */
    val tabBarMoves = mutableListOf<String>()

    /** Whether each move came with a side of the options' own (`animations.*.bottomTabs`). */
    val tabBarSides = mutableListOf<Boolean>()

    override fun tabBarTransition(move: TabBarMotion.Move, side: BottomTabsEnterExitAnimationOptions?) {
        tabBarMoves += move.name
        tabBarSides += side != null && (side.enter.hasValue() || side.exit.hasValue())
    }

    var topSettled = 0

    override fun operationEnded(topSettled: Boolean) {
        settled++
        if (topSettled) this.topSettled++
    }

    override fun isOwnRootScreen(node: ViewController<*>): Boolean = ownRoot

    override fun log(text: String) {
        log += "log:$text"
    }
}

/** A promise whose answers go into [journal] as `<tag> resolve <value>` and `<tag> reject <text>`. */
internal class TaggedPromise(private val tag: String, private val journal: MutableList<String>) : Promise {
    override fun resolve(value: Any?) {
        journal += "$tag resolve $value"
    }

    override fun reject(code: String?, message: String?) = record(message)

    override fun reject(code: String?, throwable: Throwable?) = record(throwable?.message)

    override fun reject(code: String?, message: String?, throwable: Throwable?) = record(message)

    override fun reject(throwable: Throwable) = record(throwable.message)

    override fun reject(throwable: Throwable, userInfo: WritableMap) = record(throwable.message)

    override fun reject(code: String?, userInfo: WritableMap) = record(null)

    override fun reject(code: String?, throwable: Throwable?, userInfo: WritableMap) = record(throwable?.message)

    override fun reject(code: String?, message: String?, userInfo: WritableMap) = record(message)

    override fun reject(code: String?, message: String?, throwable: Throwable?, userInfo: WritableMap?) =
        record(message)

    @Deprecated("React Native's own, not used by the navigation")
    override fun reject(message: String) = record(message)

    private fun record(message: String?) {
        journal += "$tag reject $message"
    }
}

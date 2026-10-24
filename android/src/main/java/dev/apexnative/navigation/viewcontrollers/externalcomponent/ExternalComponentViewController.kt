package dev.apexnative.navigation.viewcontrollers.externalcomponent

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import dev.apexnative.navigation.NavigationApplication
import dev.apexnative.navigation.layout.LayoutException
import dev.apexnative.navigation.nativescreen.NativeScreenContext
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.react.CommandScope
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.ScreenEnvironment
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.react.events.ComponentType
import dev.apexnative.navigation.react.events.ScreenAppearance
import dev.apexnative.navigation.system.SystemUiOwner
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.viewcontrollers.child.ChildController
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.ComponentPresenter
import dev.apexnative.navigation.viewcontrollers.component.ScreenFocus
import dev.apexnative.navigation.viewcontrollers.component.ScreenFrame
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.views.externalcomponent.ExternalComponentLayout

/**
 * A native screen (`ExternalComponent` of the layout): the app's view, made by the creator the app registered under
 * [name] (`NavigationApplication.registerNativeScreen`), in a container of the navigation. Its [context] is made with
 * the node, not with its view: props that come before the view (a tab not selected yet) are kept, and
 * `updateNativeScreenProps` finds it by the node's id. The creator is called as the view is made; when none is
 * registered or it makes nothing, the command that makes the view rejects (`ErrorTexts.nativeScreenNotRegistered`,
 * `nativeScreenNotCreated`) and the node sends no `ANN.NativeScreenReleased`; a view made later, when no command can be
 * answered, stays an empty container with one line in the log.
 *
 * It is ready at once: the navigation waits for nothing of it. Its appearance (`ANN.ComponentWillAppear` as it starts
 * to appear, `ANN.ComponentDidAppear` after the transition, `ANN.ComponentDidDisappear` as it goes) is its own
 * record's, with its registration name and the type `Component`; it has no safe area: it keeps its edges itself. Its
 * view keeps the room for the bars as a React screen's does, without the status bar in a side menu and in a sheet. As
 * it leaves the layout, however it leaves, `ANN.NativeScreenReleased` goes once, and nothing after a reload.
 */
class ExternalComponentViewController internal constructor(
    activity: Activity,
    childRegistry: ChildControllersRegistry,
    id: String,
    /** The name the app registered the screen with (`data.name`). */
    val name: String,
    /** What the screen gets from JavaScript, for as long as it is open. */
    val context: NativeScreenContext,
    presenter: Presenter,
    initialOptions: Options,
    // The creator registered under the name, asked as the view is made (a later registration counts); none when none
    // is registered. It makes the app's view, or nothing. The unit tests give their own.
    private val findCreator: () -> ((Activity, NativeScreenContext) -> View?)? = { registeredCreator(name) },
) : ChildController<ExternalComponentLayout>(activity, childRegistry, id, presenter, initialOptions) {
    /** The room of the screen's view for the bars and the system; the container takes the insets itself. */
    internal val frame = ScreenFrame(this, consumesInsets = true)

    /** The appearance of the screen; ready at once. */
    internal val appearance = ScreenAppearance(id, name, ComponentType.Component, null)

    // The command that made the view rejected it: JavaScript never got the screen and releases it itself.
    private var notMade = false

    override val currentComponentName: String
        get() = name

    override fun createView(): ExternalComponentLayout {
        val layout = ExternalComponentLayout(activity)
        frame.attach(layout)
        // New window insets: the status bar's band of the background may have changed.
        frame.onInsetsApplied = {
            (presenter as? ComponentPresenter)?.applyBackground(this, resolveCurrentOptions(DefaultOptions.current))
        }
        layout.onAttached = { frame.onAttached(layout) }
        val creator = findCreator()
        val made = try {
            creator?.invoke(activity, context)
        } catch (e: Throwable) {
            // The command that makes the view rejects with the failure; later, nothing can be answered.
            if (CommandScope.isRunning) {
                notMade = true
                throw e
            }
            Log.e(NavigationLog.TAG, "[ApexNavigation] the native screen '$name' failed to make its view", e)
            null
        }
        if (made != null) {
            layout.setContent(made)
        } else {
            val text = if (creator == null) {
                ErrorTexts.nativeScreenNotRegistered(name)
            } else {
                ErrorTexts.nativeScreenNotCreated(name)
            }
            if (CommandScope.isRunning) {
                notMade = true
                throw LayoutException(text)
            }
            Log.e(NavigationLog.TAG, "[ApexNavigation] $text")
        }
        return layout
    }

    // As a React screen's: the layout around takes its part of the options.
    override fun applyOptions(options: Options) {
        presenter.applyOptions(this, options)
        performOnParentController { it.applyChildOptions(options, this) }
    }

    override fun getTopInset(): Int = frame.topRoom()

    override fun applyTopInset() = frame.applyRoom()

    override fun getBottomInset(): Int = frame.bottomRoom()

    override fun applyBottomInset() = frame.applyRoom()

    // The bar's buttons send ANN.NavigationButtonPressed with the screen's id.
    override fun sendOnNavigationButtonPressed(buttonId: String) =
        AppearanceDispatcher.navigationButtonPressed(this, buttonId)

    override fun onViewWillAppear() {
        if (isDestroyed) return
        super.onViewWillAppear()
        if (isViewCreated) applyOptions(resolveCurrentOptions(DefaultOptions.current))
        AppearanceDispatcher.willAppear(appearance)
        findParentStack()?.barScreenWillAppear(this)
    }

    override fun onViewDidAppear() {
        if (isDestroyed) return
        AppearanceDispatcher.didAppear(appearance)
        findParentStack()?.barScreenDidAppear(this)
        super.onViewDidAppear()
    }

    // As a React screen's: the system UI follows what is on top now.
    override fun onViewDisappear() {
        if (isDestroyed) return
        AppearanceDispatcher.didDisappear(appearance)
        findParentStack()?.barScreenDisappeared(this)
        super.onViewDisappear()
        SystemUiOwner.of(activity)?.invalidate()
    }

    /** As a React screen's (`blurOnUnmount`): a field of the app's view loses the focus as the screen leaves. */
    override fun onLeaveStarted() {
        if (isDestroyed) return
        if (resolveCurrentOptions(DefaultOptions.current).blurOnUnmount.isTrue) ScreenFocus.blur(activity)
    }

    /**
     * Destroys the screen once: it disappears when it showed, leaves the registry, its view leaves its parent, the
     * link to its layout breaks, and, unless a later node has the id now (a root set again with the same ids), what is
     * kept by the id goes and `ANN.NativeScreenReleased` goes, once and only to its own JavaScript: that later node's
     * screen keeps its hook in JavaScript. A screen whose command rejected because its view wasn't made sends nothing.
     */
    override fun destroy() {
        if (isDestroyed) return
        onViewDisappear()
        super.destroy()
        // The layout it is in lets go of what it kept for it (the components of its bar, its search).
        performOnParentController { it.onChildDestroyed(this) }
        peekView()?.let { (it.parent as? ViewGroup)?.removeView(it) }
        setParentController(null)
        if (isIdTakenByAnother) return
        ScreenEnvironment.remove(id)
        ScreenReadiness.remove(id)
        if (!notMade) AppearanceDispatcher.nativeScreenReleased(this)
    }

    private companion object {
        // The creator the app registered under [name]; none when none is. It needs the activity of the app's views.
        fun registeredCreator(name: String): ((Activity, NativeScreenContext) -> View?)? {
            val creator = NavigationApplication.instanceOrNull?.getNativeScreens()?.get(name) ?: return null
            return { activity, context -> (activity as? FragmentActivity)?.let { creator.create(it, context) } }
        }
    }
}

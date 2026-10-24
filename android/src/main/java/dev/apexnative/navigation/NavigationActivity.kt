package dev.apexnative.navigation

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge as enableActivityEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.facebook.react.devsupport.interfaces.DevSupportManager
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import com.facebook.react.views.view.setEdgeToEdgeFeatureFlagOn
import dev.apexnative.navigation.react.CommandListenerAdapter
import dev.apexnative.navigation.react.DoubleTapReload
import dev.apexnative.navigation.react.Hinge
import dev.apexnative.navigation.react.LaunchLink
import dev.apexnative.navigation.react.ReactGateway
import dev.apexnative.navigation.react.ReloadBroadcast
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.slots.SlotHost
import dev.apexnative.navigation.splash.SplashHold
import dev.apexnative.navigation.splash.SplashTheme
import dev.apexnative.navigation.splash.SplashView
import dev.apexnative.navigation.system.SystemUiWindow
import dev.apexnative.navigation.utils.MaterialTheme
import dev.apexnative.navigation.viewcontrollers.modal.SheetBack
import dev.apexnative.navigation.viewcontrollers.navigator.BackCallbackCheck
import dev.apexnative.navigation.viewcontrollers.navigator.BackCallbackState
import dev.apexnative.navigation.viewcontrollers.navigator.Navigator
import dev.apexnative.navigation.viewcontrollers.navigator.Navigators
import dev.apexnative.navigation.viewcontrollers.stack.StackController

/**
 * The activity of an app with the navigation: its content holds, bottom to top, the copy of the splash and the layers
 * of the navigation (the root, the modals, the overlays), views only. The app's `MainActivity` extends it as it is. It
 * holds the splash until its first root has drawn, starts the app's React host as it resumes, passes the activity's
 * callbacks to the host, and gives the developer tools their reload.
 */
open class NavigationActivity : AppCompatActivity(), DefaultHardwareBackBtnHandler, PermissionAwareActivity {
    /** The navigation of this activity; none before `onCreate` and after `onDestroy`. */
    var navigator: Navigator? = null
        private set

    /** The app's React host as the navigation runs it. */
    val reactGateway: ReactGateway
        get() = (application as NavigationApplication).reactGateway

    /** Between `onResume` and `onPause`: `ANN.AppLaunched` goes to a resumed activity only (EVT-AppLaunched). */
    internal var isNavigationResumed = false
        private set

    private var splash: SplashTheme? = null
    private var splashHold: SplashHold? = null
    private var splashView: View? = null
    private var permissionListener: PermissionListener? = null
    // The night-mode bits of the configuration the activity shows now: a light/dark switch changes them.
    private var nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED
    private val doubleTapReload = DoubleTapReload()
    private val reloadBroadcast = ReloadBroadcast(this) { reloadFromDeveloperTools() }

    // Enabled while back has something to do in the app (BackCallbackState); otherwise the system does its own back.
    // From Android 14, where the app hears the progress of the back gesture, a gesture that would pop a stack with the
    // default transition previews the pop as the system previews back between activities (`StackController
    // .BackGesture`), and one that would dismiss a sheet previews its hide as Material's sheets do (`SheetBack`), its
    // release closing it as a drag down does; JavaScript's listeners hear back first, so with them there is no preview.
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            backGesture = null
            sheetGesture = null
            val navigator = navigator ?: return
            backCheck?.atBack(navigator)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || BackCallbackState.hasJsListeners) return
            val stack = navigator.stackPoppedByBack()
            if (stack != null) {
                backGesture = stack.startBackGesture(backEvent.swipeEdge == BackEventCompat.EDGE_LEFT, backEvent.touchY)
                return
            }
            sheetGesture = navigator.sheetDismissedByBack()?.startBackPreview(backEvent)
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            backGesture?.progress(backEvent.progress, backEvent.touchY)
            sheetGesture?.progress(backEvent)
        }

        override fun handleOnBackCancelled() {
            backGesture?.cancel()
            backGesture = null
            sheetGesture?.cancel()
            sheetGesture = null
            navigator?.let(BackCallbackState::update)
        }

        override fun handleOnBackPressed() {
            navigator?.let { backCheck?.atBack(it) }
            val gesture = backGesture
            backGesture = null
            val sheet = sheetGesture
            sheetGesture = null
            when {
                gesture?.commit() == true -> BackCallbackState.update()
                sheet != null && commitSheet(sheet) -> BackCallbackState.update()
                else -> passBack()
            }
        }
    }

    // The back gesture that previews a pop now; none.
    private var backGesture: StackController.BackGesture? = null

    // The back gesture that previews a sheet's hide now; none.
    private var sheetGesture: SheetBack.Gesture? = null

    // A sheet's preview released: the sheet goes as the user's drag down closes it, and back doesn't go on (no
    // `passBack`, which would dismiss it a second time). A command that changed what back does since the gesture
    // started (a push in the sheet, a modal over it, a hold) leaves it to the usual back, the sheet back in place.
    private fun commitSheet(gesture: SheetBack.Gesture): Boolean {
        if (navigator?.sheetDismissedByBack() !== gesture.sheet) {
            gesture.cancel()
            return false
        }
        return gesture.commit()
    }

    // Debug builds only: the check that what back does never changes without its trigger (BackCallbackState), sampled
    // before frames and at back events. It only logs; release builds have neither the check nor the listener.
    private val backCheck = if (BuildConfig.DEBUG) BackCallbackCheck() else null
    private val backCheckBeforeFrame = ViewTreeObserver.OnPreDrawListener {
        val navigator = navigator
        if (navigator != null) backCheck?.sample(navigator, SystemClock.uptimeMillis())
        true
    }
    private var frameObserver: ViewTreeObserver? = null

    /**
     * The activity draws edge to edge ([enableEdgeToEdge], decided once in `onCreate`): what reads it (the custom row
     * of the tabs) takes this decision, not a copy of its own.
     */
    internal var isEdgeToEdge = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash theme's attributes are read before the app's theme replaces it; the app's theme comes before the
        // window does (edge to edge makes it), and both before super.onCreate.
        val splash = SplashTheme.read(this)
        splash?.postSplashTheme?.takeIf { it != 0 }?.let(::setTheme)
        this.splash = splash
        isEdgeToEdge = enableEdgeToEdge()
        if (isEdgeToEdge) activateEdgeToEdge()
        super.onCreate(savedInstanceState)
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (isFinishing) return
        if (savedInstanceState != null) {
            // Recreated from saved state: its link was given before, and it has no splash to copy.
            LaunchLink.markTaken(intent)
            this.splash = null
        }
        addDefaultSplashLayout()
        // A splash view of the app's own (an override that sets its own content) is taken away by reference as well.
        splashView = splashView ?: findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)
        val navigator = Navigator(this)
        navigator.onRootShown = ::removeSplashView
        this.navigator = navigator
        Navigators.add(this)
        BackCallbackState.attach(navigator, backCallback)
        // The hinge's updates that run go on with this activity.
        Hinge.attach(this)
        SlotHost.attachIfStarted(this)
        reactGateway.onActivityCreated(this)
        onBackPressedDispatcher.addCallback(this, backCallback)
        if (backCheck != null) {
            frameObserver = window.decorView.viewTreeObserver.also { it.addOnPreDrawListener(backCheckBeforeFrame) }
        }
        // The activity's system UI: its owner remembers the theme's colors and the manifest's orientation now, before
        // anything changes them.
        navigator.systemUi.start(SystemUiWindow.of(this))
        if (savedInstanceState == null) splashHold = SplashHold.start(this)
    }

    /**
     * Whether the activity draws edge to edge, decided before its `super.onCreate`: `edgeToEdgeEnabled` of the app's
     * gradle.properties — always; else as Android enforces it: Android 16+ with a target of 36+ always, Android 15+
     * with a target of 35+ unless the theme opts out (`windowOptOutEdgeToEdgeEnforcement`), Android 15+ with a lower
     * target only when the theme sets that attribute to `false`; below Android 15 never.
     */
    protected open fun enableEdgeToEdge(): Boolean = EdgeToEdgeDecision.isEnabled(
        appEnabled = BuildConfig.EDGE_TO_EDGE_ENABLED,
        sdk = Build.VERSION.SDK_INT,
        targetSdk = applicationInfo.targetSdkVersion,
        themeOptOut = themeEdgeToEdgeOptOut(),
    )

    /**
     * Draws the activity edge to edge, before its `super.onCreate`: transparent system bars, no contrast scrim behind
     * the navigation bar, a window that doesn't fit the system windows, and React Native's own flag, so `Modal`,
     * `Dimensions`, `StatusBar` and `measureInWindow` count the same way.
     */
    protected open fun activateEdgeToEdge() {
        val transparent = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        enableActivityEdgeToEdge(transparent, transparent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setEdgeToEdgeFeatureFlagOn()
    }

    // The theme's `windowOptOutEdgeToEdgeEnforcement`; null when it doesn't set it.
    private fun themeEdgeToEdgeOptOut(): Boolean? {
        val attributes = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowOptOutEdgeToEdgeEnforcement))
        return try {
            if (attributes.hasValue(0)) attributes.getBoolean(0, false) else null
        } finally {
            attributes.recycle()
        }
    }

    /** The copy of the splash at the bottom of the content, under the navigation until its first root shows. */
    protected open fun addDefaultSplashLayout() {
        val view = SplashView.create(this, splash)
        splashView = view
        setContentView(view)
    }

    // The first root of the activity shows: the copy of the splash goes, by its reference — the view of the slots'
    // host lies at the bottom of the content too.
    private fun removeSplashView() {
        val view = splashView ?: return
        splashView = null
        (view.parent as? ViewGroup)?.removeView(view)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // The layers go over the copy of the splash; the backgrounds of the system bars over the layers, and the
        // window's status bar transparent on Android 15+ with edge to edge.
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        navigator?.let {
            it.addLayers(content)
            it.systemUi.attach(content)
        }
    }

    override fun onResume() {
        super.onResume()
        reactGateway.onActivityResumed(this, this)
        isNavigationResumed = true
        // The root, the modals and the overlays lay out again; nothing is sent.
        navigator?.onResume()
        // A safety net: what back does changes on its own triggers, and the system asks the callback from here on.
        navigator?.let(BackCallbackState::update)
        if (developerSupport() != null) reloadBroadcast.register()
        ReloadCoordinator.launchIfDue(this)
    }

    override fun onPause() {
        super.onPause()
        isNavigationResumed = false
        reactGateway.onActivityPaused(this)
        reloadBroadcast.unregister()
        // What shows keeps its place in the navigation: no screen events at the background.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (reactGateway.currentReactContext == null) setIntent(intent) else reactGateway.host.onNewIntent(intent)
    }

    // Before React Native has its context, the host would report a soft exception.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (reactGateway.currentReactContext != null) reactGateway.host.onWindowFocusChange(hasFocus)
        // A safety net, as at onResume: back after a dialog or another window over the activity.
        if (hasFocus) navigator?.let(BackCallbackState::update)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        reactGateway.host.onHostLeaveHint(this)
    }

    // React Native's modules that start activities for a result hear the result through the host.
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        reactGateway.host.onActivityResult(this, requestCode, resultCode, data)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // A light/dark switch rebuilds the themes first, before anything reads its colors again; then what shows takes
        // the colors of the new mode.
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val switched = night != nightMode
        nightMode = night
        if (switched) MaterialTheme.rebase(this)
        super.onConfigurationChanged(newConfig)
        reactGateway.host.onConfigurationChanged(this)
        if (switched) navigator?.repaint()
        // Every change (rotation, size, cutout, light/dark) applies the system UI again, all of it.
        navigator?.systemUi?.configurationChanged()
    }

    /**
     * The developer tools, while the developer support is on: "R R" — two releases of R within a second, not in a text
     * field — reloads the JavaScript, the menu key opens the developer menu.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val developerSupport = developerSupport() ?: return super.onKeyUp(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            developerSupport.showDevOptionsDialog()
            return true
        }
        if (doubleTapReload.onKeyUp(keyCode, currentFocus is EditText, SystemClock.uptimeMillis())) {
            reloadFromDeveloperTools()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // The developer support of the host, when it is on (a debug build).
    private fun developerSupport(): DevSupportManager? =
        reactGateway.host.devSupportManager?.takeIf { it.devSupportEnabled }

    // The navigation goes first, without a word to the JavaScript that goes, then the JavaScript reloads.
    private fun reloadFromDeveloperTools() {
        val developerSupport = developerSupport() ?: return
        ReloadCoordinator.resetForDeveloperReload(this)
        developerSupport.handleReloadJS()
    }

    override fun onDestroy() {
        // Only this activity's system UI goes, first: the screens' disappearances of the destroy below change nothing.
        navigator?.systemUi?.release()
        splashHold?.release()
        splashHold = null
        reloadBroadcast.release()
        navigator?.let(BackCallbackState::detach)
        // The debug check's listener: from the window's observer now, and the one it was added to, if they differ.
        if (backCheck != null) {
            val observers = listOfNotNull(frameObserver, window?.peekDecorView()?.viewTreeObserver).distinct()
            observers.forEach { if (it.isAlive) it.removeOnPreDrawListener(backCheckBeforeFrame) }
        }
        frameObserver = null
        SlotHost.detach(this)
        navigator?.destroyWithActivity()
        navigator = null
        Navigators.remove(this)
        reactGateway.onActivityDestroyed(this)
        super.onDestroy()
    }

    /**
     * Back that JavaScript's listeners didn't take: the navigation does its step first — a stack's top leaves or holds,
     * a pane closes —; with nothing to do there, the activity's own back, as without the navigation. An app may
     * override it (e.g. `moveTaskToBack(true)`). The callback is off for that back only: the dispatcher's own back is
     * the activity's, not [onBackPressed]; then it is computed again, and `hardwareBackButton.systemAnimation: false`
     * turns it on again.
     */
    override fun invokeDefaultOnBackPressed() {
        val handled = navigator?.handleBack(CommandListenerAdapter()) == true
        if (!handled) {
            backCallback.isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
        BackCallbackState.update()
    }

    /**
     * Without predictive back (below Android 13, or without `android:enableOnBackInvokedCallback="true"`) a press
     * comes here, whatever the callback's state: it goes the old way, JavaScript first, then
     * [invokeDefaultOnBackPressed].
     */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() = passBack()

    // JavaScript hears a press first: React Native calls invokeDefaultOnBackPressed when no listener took it. Before
    // React Native has its context (the host would drop the press) it goes to the navigation at once.
    private fun passBack() {
        if (reactGateway.currentReactContext == null || !reactGateway.onBackPressed()) invokeDefaultOnBackPressed()
    }

    /**
     * The JavaScript went: every layer of the navigation goes, without a word to the new JavaScript, and the registry
     * of nodes is empty after it, before the new JavaScript sets its root. Main thread.
     */
    open fun onReload() {
        navigator?.destroy()
    }

    override fun requestPermissions(permissions: Array<String>, requestCode: Int, listener: PermissionListener?) {
        permissionListener = listener
        requestPermissions(permissions, requestCode)
    }

    // The activity result API doesn't answer a request of React Native's PermissionsAndroid, which asks this way.
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        (application as NavigationApplication).onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (permissionListener?.onRequestPermissionsResult(requestCode, permissions, grantResults) == true) {
            permissionListener = null
        }
    }
}

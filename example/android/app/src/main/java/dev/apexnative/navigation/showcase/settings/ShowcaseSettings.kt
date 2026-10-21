package dev.apexnative.navigation.showcase.settings

import android.content.Context
import com.facebook.react.ReactApplication
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.uimanager.ViewManager

/**
 * The launch settings of the Showcase on Android (one React tree or a root per screen, a native first screen), kept as
 * iOS keeps them in the app's settings, and read once at launch.
 */
class ShowcaseSettingsModule(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
  override fun getName() = NAME

  @ReactMethod(isBlockingSynchronousMethod = true)
  fun oneTree(): Boolean = preferences().getBoolean(ONE_TREE, false)

  /** Keeps the mode and starts the JavaScript again, which reads it; in a release build too. */
  @ReactMethod
  fun setOneTree(on: Boolean) {
    preferences().edit().putBoolean(ONE_TREE, on).commit()
    UiThreadUtil.runOnUiThread {
      (context.applicationContext as ReactApplication).reactHost?.reload("Showcase: one tree $on")
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  fun nativeFirst(): Boolean = preferences().getBoolean(NATIVE_FIRST, false)

  /** Keeps whether the Showcase starts with a native screen and starts the JavaScript again. */
  @ReactMethod
  fun setNativeFirst(on: Boolean) {
    preferences().edit().putBoolean(NATIVE_FIRST, on).commit()
    UiThreadUtil.runOnUiThread {
      (context.applicationContext as ReactApplication).reactHost?.reload("Showcase: native first $on")
    }
  }

  /** Whether this start fails; once: a new try goes on. */
  @ReactMethod(isBlockingSynchronousMethod = true)
  fun takeFailStart(): Boolean {
    val on = preferences().getBoolean(FAIL_START, false)
    if (on) preferences().edit().putBoolean(FAIL_START, false).commit()
    return on
  }

  /** Makes the next start fail once and starts the JavaScript again. */
  @ReactMethod
  fun failNextStart() {
    preferences().edit().putBoolean(FAIL_START, true).commit()
    UiThreadUtil.runOnUiThread {
      (context.applicationContext as ReactApplication).reactHost?.reload("Showcase: the start fails")
    }
  }

  private fun preferences() = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

  companion object {
    const val NAME = "ShowcaseSettings"
    private const val PREFERENCES = "showcase"
    private const val ONE_TREE = "oneTree"
    private const val NATIVE_FIRST = "nativeFirst"
    private const val FAIL_START = "failStart"
  }
}

class ShowcaseSettingsPackage : ReactPackage {
  override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
    listOf(ShowcaseSettingsModule(reactContext))

  override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}

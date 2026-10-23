package dev.apexnative.navigation.showcase

import com.facebook.react.PackageList
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import dev.apexnative.navigation.NavigationApplication
import dev.apexnative.navigation.showcase.nativescreens.NativeProfileScreen
import dev.apexnative.navigation.showcase.nativescreens.UsePropsDemoScreen
import dev.apexnative.navigation.showcase.settings.ShowcaseSettingsPackage

/**
 * NavigationApplication in place of the template's Application. `onCreate` keeps the template's
 * `loadReactNative(this)`: the navigation makes its React host after React Native is loaded.
 */
class MainApplication : NavigationApplication() {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          // The launch settings of the Showcase: one React tree or a root per screen, a native first screen.
          add(ShowcaseSettingsPackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    loadReactNative(this)
    // Native screens, which `screens` names with `nativeScreen`.
    registerNativeScreen("NativeProfileScreen") { activity, context -> NativeProfileScreen(activity, context).asView() }
    registerNativeScreen("UsePropsDemoScreen") { activity, context -> UsePropsDemoScreen(activity, context).view }
  }
}

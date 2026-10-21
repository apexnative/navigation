package dev.apexnative.navigation

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager
import dev.apexnative.navigation.react.NavigationTurboModule
import dev.apexnative.navigation.react.NativeANNTurboModuleSpec
import dev.apexnative.navigation.safearea.SafeAreaViewManager
import dev.apexnative.navigation.slots.SlotViewManager

/**
 * The library's React Native package, which autolinking adds to the app: the TurboModule `ANNTurboModule`, made when
 * JavaScript first asks for it, and the components `ANNSlot` and `ANNSafeAreaView`.
 */
class NavigationPackage : BaseReactPackage() {
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
        if (name == NativeANNTurboModuleSpec.NAME) NavigationTurboModule(reactContext) else null

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider = ReactModuleInfoProvider {
        mapOf(
            NativeANNTurboModuleSpec.NAME to ReactModuleInfo(
                name = NativeANNTurboModuleSpec.NAME,
                className = NavigationTurboModule::class.java.name,
                canOverrideExistingModule = false,
                needsEagerInit = false,
                isCxxModule = false,
                isTurboModule = true,
            ),
        )
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<in Nothing, in Nothing>> =
        listOf(SlotViewManager(), SafeAreaViewManager())
}

package dev.apexnative.navigation.safearea

import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.ReactStylesDiffMap
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.views.view.ReactViewManager

/** The safe area view component (`ANNSafeAreaView`): a view whose padding is its screen's safe area. */
@ReactModule(name = SafeAreaViewManager.NAME)
class SafeAreaViewManager : ReactViewManager() {
    override fun getName(): String = NAME

    override fun createViewInstance(context: ThemedReactContext): SafeAreaView = SafeAreaView(context)

    @ReactProp(name = "screenId")
    fun setScreenId(view: SafeAreaView, screenId: String?) {
        view.screenId = screenId
    }

    @ReactProp(name = "contentRendered")
    fun setContentRendered(view: SafeAreaView, contentRendered: Boolean) {
        view.contentRendered = contentRendered
    }

    override fun updateState(view: com.facebook.react.views.view.ReactViewGroup, props: ReactStylesDiffMap, stateWrapper: StateWrapper): Any? {
        (view as? SafeAreaView)?.stateWrapper = stateWrapper
        return null
    }

    override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> =
        (super.getExportedCustomDirectEventTypeConstants() ?: mutableMapOf()).apply {
            put(SafeAreaReadyEvent.EVENT_NAME, mapOf("registrationName" to "onReady"))
        }

    override fun onDropViewInstance(view: com.facebook.react.views.view.ReactViewGroup) {
        super.onDropViewInstance(view)
        (view as? SafeAreaView)?.drop()
    }

    companion object {
        const val NAME = "ANNSafeAreaView"
    }
}

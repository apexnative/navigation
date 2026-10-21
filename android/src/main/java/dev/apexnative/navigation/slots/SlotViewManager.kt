package dev.apexnative.navigation.slots

import android.view.View
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.ReactStylesDiffMap
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager

/** The slot component (`ANNSlot`): React children go into its content view, wherever that view is. */
@ReactModule(name = SlotViewManager.NAME)
class SlotViewManager : ViewGroupManager<SlotView>() {
    override fun getName(): String = NAME

    override fun createViewInstance(reactContext: ThemedReactContext): SlotView = SlotView(reactContext)

    override fun updateState(view: SlotView, props: ReactStylesDiffMap, stateWrapper: StateWrapper): Any? {
        view.stateWrapper = stateWrapper
        view.attachContent()
        return null
    }

    // The slot id (nativeID) comes with the props.
    override fun onAfterUpdateTransaction(view: SlotView) {
        super.onAfterUpdateTransaction(view)
        view.attachContent()
    }

    override fun addView(parent: SlotView, child: View, index: Int) {
        parent.content.addView(child, index)
        parent.contentChanged()
    }

    override fun getChildCount(parent: SlotView): Int = parent.content.childCount

    override fun getChildAt(parent: SlotView, index: Int): View? = parent.content.getChildAt(index)

    override fun removeViewAt(parent: SlotView, index: Int) = parent.content.removeViewAt(index)

    override fun removeView(parent: SlotView, view: View) = parent.content.removeView(view)

    override fun onDropViewInstance(view: SlotView) {
        super.onDropViewInstance(view)
        view.drop()
    }

    companion object {
        const val NAME = "ANNSlot"
    }
}

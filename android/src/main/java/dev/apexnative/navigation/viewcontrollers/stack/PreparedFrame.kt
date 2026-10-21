package dev.apexnative.navigation.viewcontrollers.stack

import android.view.View
import com.facebook.react.uimanager.PixelUtil.pxToDp
import dev.apexnative.navigation.react.ScreenInsets

/**
 * Where a screen prepared for a push (`preparePush`) shows, in dp: the size of the container its slot fills and the
 * padding of its safe area view, as the push lays it out. JavaScript renders the screen with them before the push, and
 * the slot and the safe area view start their states with them.
 */
internal data class PreparedFrame(val width: Float, val height: Float, val padding: ScreenInsets.Insets) {
    companion object {
        /** The size of [container] as its slot measures it (`SlotView`), so the slot finds it equal. */
        @JvmStatic
        fun of(container: View, padding: ScreenInsets.Insets) =
            PreparedFrame(container.width.toFloat().pxToDp(), container.height.toFloat().pxToDp(), padding)
    }
}

package dev.apexnative.navigation.nativescreen

import android.view.View
import androidx.fragment.app.FragmentActivity
import dev.apexnative.navigation.viewcontrollers.externalcomponent.ExternalComponentCreator

/** Makes the view of a native screen: Android views, or a ComposeView. */
fun interface NativeScreenCreator {
    fun create(activity: FragmentActivity, context: NativeScreenContext): View

    companion object {
        /** A creator of the 8.x registration (`registerExternalComponent`): it gets the props the screen showed with. */
        @JvmStatic
        fun of(creator: ExternalComponentCreator) =
            NativeScreenCreator { activity, context -> creator.create(activity, context.props).asView() }
    }
}

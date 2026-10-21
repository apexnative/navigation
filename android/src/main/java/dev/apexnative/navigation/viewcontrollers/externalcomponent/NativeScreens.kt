package dev.apexnative.navigation.viewcontrollers.externalcomponent

import dev.apexnative.navigation.nativescreen.NativeScreenContext
import dev.apexnative.navigation.viewcontrollers.navigator.Navigators
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/** The core's side of the commands of native screens (`updateNativeScreenProps`). Main thread only. */
internal object NativeScreens {
    /**
     * The node with [componentId]: in the navigation of the activity commands run in first, then in the others, newest
     * first (an activity going while a new one shows the same ids); none when there is none.
     */
    fun find(componentId: String): ViewController<*>? =
        Navigators.current()?.navigator?.registry?.find(componentId)
            ?: Navigators.all().asReversed().firstNotNullOfOrNull { it.navigator?.registry?.find(componentId) }

    /**
     * The context that takes new props for [node]: a native screen's, at once, during a transition too; none for any
     * other node, for none, and for a screen that left the layout.
     */
    fun contextOf(node: ViewController<*>?): NativeScreenContext? {
        val screen = node as? ExternalComponentViewController ?: return null
        return if (screen.isDestroyed) null else screen.context
    }
}

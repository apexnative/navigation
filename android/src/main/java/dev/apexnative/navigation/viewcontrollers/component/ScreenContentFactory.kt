package dev.apexnative.navigation.viewcontrollers.component

import android.app.Activity
import android.content.Context
import com.facebook.react.ReactApplication
import dev.apexnative.navigation.react.ReactView
import dev.apexnative.navigation.slots.SlotHost
import dev.apexnative.navigation.slots.SlotScreenView
import dev.apexnative.navigation.viewcontrollers.viewcontroller.IReactView

/**
 * Makes what a screen shows: for a name starting with [SLOT_PREFIX], the slot screen of the slots module, whose content
 * the app's one React tree renders — making one starts that tree's host; otherwise a React root of its own.
 */
object ScreenContentFactory {
    /** The prefix of the names of slot screens (`slot:<Name>`); events and the navigation state name them without. */
    const val SLOT_PREFIX = "slot:"

    @JvmStatic
    fun create(context: Context, componentId: String, componentName: String): IReactView =
        if (componentName.startsWith(SLOT_PREFIX)) {
            // The host needs the app's React host; an activity without one (the unit tests) has no tree to start.
            if ((context as? Activity)?.application is ReactApplication) SlotHost.ensureStarted(context)
            SlotScreenView(context, componentId, componentName.removePrefix(SLOT_PREFIX))
        } else {
            ReactView(context, componentId, componentName)
        }
}

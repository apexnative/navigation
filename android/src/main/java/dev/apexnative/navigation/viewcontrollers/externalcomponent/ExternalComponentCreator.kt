package dev.apexnative.navigation.viewcontrollers.externalcomponent

import android.view.View
import androidx.fragment.app.FragmentActivity
import org.json.JSONObject

/** What a creator of the 8.x registration made: the view of a native screen. */
fun interface ExternalComponent {
    fun asView(): View
}

/**
 * A creator of a native screen of the 8.x registration: it gets the props the screen showed with. Apps register
 * `NativeScreenCreator`; this one only stands behind `NativeScreenCreator.of`.
 */
fun interface ExternalComponentCreator {
    fun create(activity: FragmentActivity, props: JSONObject): ExternalComponent
}

package dev.apexnative.navigation.viewcontrollers.component

import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager

/**
 * How a screen with `blurOnUnmount` lets go of the focus as it starts to leave (`blurOnUnmount`): the view that has the
 * focus of the activity loses it, and the keyboard goes. React Native's text field hides the keyboard only when
 * JavaScript blurs it (`ReactEditText.clearFocusAndMaybeRefocus`), not on a plain `clearFocus`, so the keyboard is
 * hidden here. The unit tests put their own [blur] here. Main thread only.
 */
internal object ScreenFocus {
    /** Takes the focus away from what has it in the activity, and hides the keyboard. */
    var blur: (Activity) -> Unit = { activity ->
        activity.currentFocus?.let { focused ->
            focused.clearFocus()
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(focused.windowToken, 0)
        }
    }
}

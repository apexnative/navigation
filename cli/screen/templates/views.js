// @ts-check
'use strict';

/**
 * A native screen with Android views: a class that makes its view from `screen` and follows the props as they change.
 * @param {string} name such as `Profile`
 * @param {string} appPackage the package of the app's code
 * @param {string} definition where the screen's props are, for the comment
 * @returns {string}
 */
function viewsScreen(name, appPackage, definition) {
  return `package ${appPackage}.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import dev.apexnative.navigation.generated.NativeScreen
import dev.apexnative.navigation.generated.${name}Props

/** The native screen \`${name}\`. Its props (from ${definition}) and the navigation from it come in [screen]. */
class ${name}Screen(activity: FragmentActivity, private val screen: NativeScreen<${name}Props>) {
  private val title = TextView(activity).apply {
    gravity = Gravity.CENTER
    textSize = 20f
  }

  /** The view the navigation shows. */
  val view: View = FrameLayout(activity).apply { addView(title, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) }

  init {
    show(screen.props)
    screen.onChange(::show)
  }

  // The props as they are, and again each time they change.
  private fun show(props: ${name}Props) {
    title.text = props.title ?: "${name}"
  }
}
`;
}

module.exports = { viewsScreen };

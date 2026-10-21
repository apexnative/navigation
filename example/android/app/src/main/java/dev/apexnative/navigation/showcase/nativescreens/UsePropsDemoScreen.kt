package dev.apexnative.navigation.showcase.nativescreens

import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import dev.apexnative.navigation.nativescreen.NativeScreenContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The native screen of sections 18.4–18.7 with Android views: props from the app (`useProps`), functions of the props
 * that run in JS, and commands of the navigation from the screen. MainApplication registers it as `UsePropsDemoScreen`.
 */
class UsePropsDemoScreen(private val activity: FragmentActivity, private val screen: NativeScreenContext) {
  private val count = TextView(activity)
  private val user = TextView(activity)
  private val items = TextView(activity)
  private var shownCount: Int? = null

  val view: View = ScrollView(activity).apply {
    addView(LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(20), dp(16), dp(20), dp(32))
      addView(title("From the app"))
      addView(count)
      addView(user)
      addView(button("+1") { screen.call("increment") })
      addView(button("Sign in as Ann") { screen.call("signIn", JSONArray().put("Ann")) })
      addView(button("Sign out") { screen.call("signOut") })
      addView(title("A function with a result"))
      addView(items)
      addView(button("Load again") { load("loadItems") })
      addView(button("Fail to load") { load("failToLoad") })
      addView(title("Commands"))
      addView(button("Push Details") { screen.navigate("push", JSONArray().put("Details")) })
      addView(button("Show Details as a modal") {
        screen.navigate("showModal", JSONArray().put("Details").put(JSONObject().put("closeButton", true)))
      })
      addView(button("Close") { close() })
      if ("backToShowcase" in screen.functions) {
        addView(title("Native first"))
        addView(button("Back to the Showcase") { screen.call("backToShowcase") })
      }
    })
  }

  init {
    show(screen.props)
    screen.onPropsChange = ::show
    load("loadItems")
  }

  // The props as they are now; what the screen sees of the store goes to the Journal.
  private fun show(props: JSONObject) {
    val newCount = props.optInt("count")
    count.text = "Count: $newCount"
    user.text = "User: ${props.optString("user")}"
    if (shownCount != null && shownCount != newCount) {
      screen.call("log", JSONArray().put("The screen sees count $newCount"))
    }
    shownCount = newCount
  }

  // A function that returns the items, or one that fails: the screen shows what came back.
  private fun load(function: String) {
    screen.call(function) { result ->
      result
        .onSuccess { value ->
          val loaded = (value as? JSONArray)?.let { array -> (0 until array.length()).map(array::getString) }.orEmpty()
          items.text = loaded.joinToString("\n")
          screen.call("log", JSONArray().put("Loaded ${loaded.joinToString(", ")}"))
        }
        .onFailure { error ->
          items.text = error.message
          screen.call("log", JSONArray().put("Failed: ${error.message}"))
        }
    }
  }

  // Back in its stack, or out of its modal when it is the first screen of one.
  private fun close() {
    screen.navigate("pop") { popped ->
      if (popped.isFailure) screen.navigate("dismiss")
    }
  }

  private fun title(text: String) = TextView(activity).apply {
    this.text = text.uppercase()
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
    setPadding(0, dp(20), 0, dp(8))
  }

  private fun button(label: String, onClick: () -> Unit) = Button(activity).apply {
    text = label
    isAllCaps = false
    setOnClickListener { onClick() }
  }

  private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}

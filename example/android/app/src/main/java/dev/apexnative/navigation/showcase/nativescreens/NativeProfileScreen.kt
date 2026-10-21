package dev.apexnative.navigation.showcase.nativescreens

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import dev.apexnative.navigation.nativescreen.NativeScreenContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A screen written 100% with Android Views (no React). Its context has the props from JS, and runs the functions of
 * the props and the commands of the navigation in JS.
 */
class NativeProfileScreen(
  private val activity: FragmentActivity,
  private val context: NativeScreenContext,
) {
  private val props: JSONObject = context.props

  private val isDark =
    (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
  private val backgroundColor = Color.parseColor(if (isDark) "#0B0B12" else "#F4F5FA")
  private val cardColor = Color.parseColor(if (isDark) "#1A1A26" else "#FFFFFF")
  private val textColor = Color.parseColor(if (isDark) "#F2F2F7" else "#1B1B2F")
  private val mutedColor = Color.parseColor(if (isDark) "#A0A0B8" else "#6B6B80")

  private val accent = parseColor(props.optString("accentColor", "#6C5CE7"))
  private var counter = props.optInt("initialCount", 0)
  private lateinit var counterLabel: TextView

  private val root: View by lazy { buildView() }

  fun asView(): View = root

  private fun buildView(): View {
    val content = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(20), dp(24), dp(20), dp(32))
      setBackgroundColor(backgroundColor)
    }

    val name = props.optString("name", "Unknown")
    content.addView(avatar(name))
    content.addView(text(name, 24f, bold = true, color = textColor, center = true))
    content.addView(text(props.optString("role", ""), 15f, color = mutedColor, center = true))
    content.addView(badgeRow())

    content.addView(sectionTitle("Props received from JS"))
    content.addView(card(propsDump()))

    content.addView(sectionTitle("Native state"))
    counterLabel = text("", 18f, bold = true, color = accent, center = true)
    updateCounter()
    content.addView(card(counterLabel))
    content.addView(buttonRow(
      button("−1") { counter--; updateCounter() },
      button("+1") { counter++; updateCounter() },
    ))

    content.addView(sectionTitle("Native → JS"))
    content.addView(button("Send counter to JS") {
      context.call("sendCounter", JSONArray().put(counter))
    })
    content.addView(button("Open a React screen from here") {
      context.navigate("push", JSONArray().put("Details"))
    })
    content.addView(button("Close this screen") { close() })

    content.addView(sectionTitle("Pure native actions"))
    content.addView(button("Show native Toast") {
      Toast.makeText(activity, "Hello from Kotlin, $name!", Toast.LENGTH_SHORT).show()
    })
    content.addView(button("Show native AlertDialog") {
      AlertDialog.Builder(activity)
        .setTitle("Native dialog")
        .setMessage("Android ${Build.VERSION.RELEASE} • ${Build.MODEL}\nCounter = $counter")
        .setPositiveButton("OK", null)
        .show()
    })

    return ScrollView(activity).apply {
      isFillViewport = true
      addView(content)
    }
  }

  // Back in its stack, or out of its modal; the first screen of a tab stays.
  private fun close() {
    context.navigate("pop") { popped ->
      if (popped.isFailure) {
        context.navigate("dismiss") { dismissed ->
          if (dismissed.isFailure) {
            context.call("log", JSONArray().put("Close: the first screen of its stack stays"))
          }
        }
      }
    }
  }


  private fun updateCounter() {
    counterLabel.text = "Counter: $counter"
  }

  private fun propsDump(): TextView {
    val lines = props.keys().asSequence().sorted().joinToString("\n") { key ->
      val value = props.opt(key)
      val type = when (value) {
        is String -> "string"
        is Boolean -> "boolean"
        is Int, is Long, is Double -> "number"
        is org.json.JSONArray -> "array"
        is JSONObject -> "object"
        else -> value?.javaClass?.simpleName ?: "null"
      }
      val rendered = if (value is org.json.JSONArray) {
        (0 until value.length()).joinToString(", ") { value.optString(it) }
      } else value
      "$key ($type): $rendered"
    }
    return text(lines, 13f, color = textColor).apply {
      typeface = Typeface.MONOSPACE
    }
  }

  private fun badgeRow(): View {
    val row = LinearLayout(activity).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER
      setPadding(0, dp(10), 0, dp(4))
    }
    val tags = props.optJSONArray("tags")
    if (props.optBoolean("isPremium", false)) row.addView(chip("★ Premium"))
    if (tags != null) for (i in 0 until tags.length()) row.addView(chip(tags.optString(i)))
    return row
  }

  private fun avatar(name: String): View {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2)
      .joinToString("") { it.first().uppercase() }
    return TextView(activity).apply {
      text = initials
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
      typeface = Typeface.DEFAULT_BOLD
      gravity = Gravity.CENTER
      background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) }
      layoutParams = LinearLayout.LayoutParams(dp(88), dp(88)).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        bottomMargin = dp(12)
      }
    }
  }

  private fun chip(label: String) = TextView(activity).apply {
    text = label
    setTextColor(accent)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
    setPadding(dp(10), dp(4), dp(10), dp(4))
    background = GradientDrawable().apply {
      cornerRadius = dp(12).toFloat()
      setColor(Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent)))
    }
    layoutParams = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { marginStart = dp(4); marginEnd = dp(4) }
  }

  private fun sectionTitle(title: String) =
    text(title.uppercase(), 12f, bold = true, color = mutedColor).apply {
      setPadding(dp(4), dp(22), 0, dp(8))
    }

  private fun card(child: View) = LinearLayout(activity).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(14), dp(16), dp(14))
    background = GradientDrawable().apply {
      cornerRadius = dp(14).toFloat()
      setColor(cardColor)
    }
    addView(child)
  }

  private fun button(label: String, onClick: () -> Unit) = Button(activity).apply {
    text = label
    isAllCaps = false
    setTextColor(Color.WHITE)
    background = GradientDrawable().apply {
      cornerRadius = dp(12).toFloat()
      setColor(accent)
    }
    layoutParams = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT, dp(48), 1f
    ).apply { topMargin = dp(8); marginStart = dp(2); marginEnd = dp(2) }
    setOnClickListener { onClick() }
  }

  private fun buttonRow(vararg buttons: Button) = LinearLayout(activity).apply {
    orientation = LinearLayout.HORIZONTAL
    buttons.forEach { addView(it) }
  }

  private fun text(
    value: String,
    sizeSp: Float,
    bold: Boolean = false,
    color: Int = Color.BLACK,
    center: Boolean = false,
  ) = TextView(activity).apply {
    text = value
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
    if (bold) typeface = Typeface.DEFAULT_BOLD
    if (center) gravity = Gravity.CENTER_HORIZONTAL
  }

  private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

  private fun parseColor(hex: String) =
    runCatching { Color.parseColor(hex) }.getOrDefault(Color.parseColor("#6C5CE7"))
}

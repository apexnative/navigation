package dev.apexnative.navigation.views.stack.topbar.search

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.R as AppCompatR
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.dp

/**
 * The open search field of the top bar: a Material 3 pill with a back arrow, the text and a clear button, in a row of
 * the search's height. The back arrow closes the search ([onClose]); the clear button empties the text.
 */
@SuppressLint("ViewConstructor")
class SearchField(context: Context) : FrameLayout(context) {
    val editText = EditText(context)
    var onClose: (() -> Unit)? = null

    private val pill = LinearLayout(context)
    private val pillBackground = GradientDrawable()
    private val back: ImageButton
    private val clear: ImageButton
    @ColorInt private val defaultHighlight: Int
    @ColorInt private var themePillColor = SearchStyle.pillColor(context)
    @ColorInt private var optionPillColor: Int? = null

    init {
        pillBackground.cornerRadius = SearchStyle.PILL_HEIGHT_DP.dp / 2f
        pillBackground.setColor(SearchStyle.pillColor(context))
        pill.background = pillBackground
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL

        back = iconButton(AppCompatR.drawable.abc_ic_ab_back_material, AppCompatR.string.abc_action_bar_up_description) {
            onClose?.invoke()
        }
        clear = iconButton(AppCompatR.drawable.abc_ic_clear_material, AppCompatR.string.abc_searchview_description_clear) {
            editText.setText("")
        }
        editText.apply {
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(SearchStyle.textColor(context))
            setHintTextColor(SearchStyle.hintColor(context))
            setPadding(0, 0, 0, 0)
        }
        defaultHighlight = editText.highlightColor

        pill.addView(back, LinearLayout.LayoutParams(48.dp, 48.dp))
        pill.addView(editText, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        pill.addView(clear, LinearLayout.LayoutParams(48.dp, 48.dp))
        addView(pill, LayoutParams(LayoutParams.MATCH_PARENT, SearchStyle.PILL_HEIGHT_DP.dp, Gravity.CENTER_VERTICAL).apply {
            marginStart = 16.dp
            marginEnd = 16.dp
        })

        clear.visibility = GONE
        editText.doAfterTextChanged { clear.visibility = if (it.isNullOrEmpty()) GONE else VISIBLE }
    }

    /**
     * The `testID`s of the field (`topBar.searchBar.testID`), its back arrow (`.cancel`) and its clear button
     * (`.clear`).
     */
    fun setTestId(testId: String?) {
        TestIds.apply(editText, testId)
        TestIds.apply(back, testId?.let { "$it.cancel" })
        TestIds.apply(clear, testId?.let { "$it.clear" })
    }

    /** backgroundColor: the pill's; null gives back the theme's. */
    fun setPillColor(@ColorInt color: Int?) {
        optionPillColor = color
        pillBackground.setColor(color ?: themePillColor)
    }

    /** Light or dark changed: the theme's colors again, from [styleContext]. */
    fun restyle(styleContext: Context) {
        themePillColor = SearchStyle.pillColor(styleContext)
        pillBackground.setColor(optionPillColor ?: themePillColor)
        editText.setTextColor(SearchStyle.textColor(styleContext))
        editText.setHintTextColor(SearchStyle.hintColor(styleContext))
        val icons = ColorStateList.valueOf(SearchStyle.hintColor(styleContext))
        back.imageTintList = icons
        clear.imageTintList = icons
    }

    /** tintColor: the cursor and the selected text; null gives back the theme's. */
    fun setTintColor(@ColorInt color: Int?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            editText.textCursorDrawable?.mutate()?.let { cursor ->
                if (color != null) cursor.setTint(color) else cursor.setTintList(null)
                editText.textCursorDrawable = cursor
            }
        }
        editText.highlightColor = color?.let { ColorUtils.setAlphaComponent(it, 0x66) } ?: defaultHighlight
    }

    private fun iconButton(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(SearchStyle.hintColor(context))
            val ripple = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
                setBackgroundResource(ripple.resourceId)
            } else {
                background = null
            }
            contentDescription = context.getString(label)
            setOnClickListener { onClick() }
        }
}

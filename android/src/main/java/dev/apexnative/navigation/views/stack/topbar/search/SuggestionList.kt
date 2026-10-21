package dev.apexnative.navigation.views.stack.topbar.search

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.apexnative.navigation.options.SearchBarSuggestion
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.dp

/**
 * The suggestions of the search (`topBar.searchBar.suggestions`): rows of an icon and a title, as Material 3 lists
 * them under a search field. A tap goes to [onPick] with the suggestion's id.
 */
@SuppressLint("ViewConstructor")
class SuggestionList(
    context: Context,
    private val loadIcon: (String) -> Drawable?,
) : LinearLayout(context) {
    var onPick: ((String) -> Unit)? = null

    private var suggestions: List<SearchBarSuggestion> = emptyList()
    private var styleContext: Context = context

    init {
        orientation = VERTICAL
    }

    fun setSuggestions(suggestions: List<SearchBarSuggestion>) {
        if (suggestions == this.suggestions) return
        this.suggestions = suggestions
        removeAllViews()
        suggestions.forEach { addView(row(it), LayoutParams(LayoutParams.MATCH_PARENT, ROW_HEIGHT_DP.dp)) }
    }

    /** Light or dark changed: the rows again, with the theme's colors from [styleContext]. */
    fun restyle(styleContext: Context) {
        this.styleContext = styleContext
        val current = suggestions
        suggestions = emptyList()
        setSuggestions(current)
    }

    private fun row(suggestion: SearchBarSuggestion): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPaddingRelative(16.dp, 0, 16.dp, 0)
        val ripple = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            setBackgroundResource(ripple.resourceId)
        }
        contentDescription = suggestion.title
        TestIds.apply(this, suggestion.testID)
        setOnClickListener { onPick?.invoke(suggestion.id) }

        val icon = ImageView(context)
        suggestion.icon.get(null)?.let(loadIcon)?.let { icon.setImageDrawable(it.mutate()) }
        icon.imageTintList = ColorStateList.valueOf(SearchStyle.hintColor(styleContext))
        addView(icon, LayoutParams(24.dp, 24.dp).apply { marginEnd = 16.dp })

        val title = TextView(context)
        title.text = suggestion.title
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        title.setTextColor(SearchStyle.textColor(styleContext))
        title.isSingleLine = true
        addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    companion object {
        const val ROW_HEIGHT_DP = 56
    }
}

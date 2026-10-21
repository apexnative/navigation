package dev.apexnative.navigation.views.stack.topbar.search

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.chip.ChipGroup
import com.google.android.material.R as MaterialR
import dev.apexnative.navigation.options.SearchBarScope
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.dp

/**
 * The scopes of the search (`topBar.searchBar.scopes`): a row of Material 3 filter chips, one of them selected, that
 * scrolls sideways when they don't fit. A pick by the user goes to [onPick]; [setScopes] selects without it.
 */
@SuppressLint("ViewConstructor")
class ScopeRow(context: Context) : HorizontalScrollView(context) {
    var onPick: ((String) -> Unit)? = null

    private val group = ChipGroup(context)
    private var styleContext: Context = context
    private var scopes: List<SearchBarScope> = emptyList()
    private var selected: String? = null
    private val chipIds = HashMap<Int, String>()
    private var selecting = false

    init {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        setPadding(16.dp, 0, 16.dp, 0)
        group.isSingleLine = true
        group.isSingleSelection = true
        group.isSelectionRequired = true
        group.setOnCheckedStateChangeListener { _, checked ->
            if (selecting) return@setOnCheckedStateChangeListener
            checked.firstOrNull()?.let { chipIds[it] }?.let { onPick?.invoke(it) }
        }
        addView(group, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER_VERTICAL))
    }

    /** The chips of [scopes], [selected] checked; the same scopes keep their chips. */
    fun setScopes(scopes: List<SearchBarScope>, selected: String?) {
        this.selected = selected
        selecting = true
        if (scopes != this.scopes) {
            this.scopes = scopes
            group.removeAllViews()
            chipIds.clear()
            scopes.forEach { scope ->
                val chip = Chip(styleContext)
                chip.setChipDrawable(
                    ChipDrawable.createFromAttributes(styleContext, null, 0, MaterialR.style.Widget_Material3_Chip_Filter)
                )
                chip.id = View.generateViewId()
                chip.text = scope.title
                chip.isCheckable = true
                TestIds.apply(chip, scope.testID)
                chipIds[chip.id] = scope.id
                group.addView(chip)
            }
        }
        chipIds.entries.firstOrNull { it.value == selected }?.let { group.check(it.key) }
        selecting = false
    }

    /** Light or dark changed: the chips again, with the theme's colors from [styleContext]. */
    fun restyle(styleContext: Context) {
        this.styleContext = styleContext
        val current = scopes
        scopes = emptyList()
        setScopes(current, selected)
    }

    companion object {
        const val HEIGHT_DP = 48
    }
}

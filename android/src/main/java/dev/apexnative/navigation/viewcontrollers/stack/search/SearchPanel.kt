package dev.apexnative.navigation.viewcontrollers.stack.search

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import dev.apexnative.navigation.options.ComponentOptions
import dev.apexnative.navigation.react.ReactView
import dev.apexnative.navigation.react.events.ComponentType
import dev.apexnative.navigation.utils.dp
import dev.apexnative.navigation.views.stack.topbar.search.ScopeRow
import dev.apexnative.navigation.views.stack.topbar.search.SuggestionList

/**
 * The search of a screen with a results component: a Material search view over the stack, its field at the top and
 * the results component under it. A pill opens it with its own animation; a magnifier opens it by [open].
 */
class SearchPanel(
    context: Context,
    private val listener: Listener,
    results: ComponentOptions,
    pill: SearchBar?,
    loadIcon: (String) -> Drawable?,
) {
    interface Listener {
        /** It starts to open. */
        fun onPanelOpened()
        /** It is open, its field focused. */
        fun onPanelShown()
        fun onPanelClosed()
        fun onPanelTextChanged()
        fun onPanelSubmitted()
    }

    val searchView = SearchView(context)
    /** The scopes, over the results. */
    val scopeRow = ScopeRow(context)
    /** The suggestions, between the scopes and the results. */
    val suggestionList = SuggestionList(context, loadIcon)
    /** Which results component it shows: another one makes another panel. */
    val resultsKey: String = key(results)
    val resultsComponentId: String = results.componentId.get("")
    /** Opened by a tap on the pill under the title; without one (a magnifier), by [open]. */
    val opensFromPill: Boolean = pill != null
    private val resultsView = ReactView(context, resultsComponentId, results.name.get())
    private var settingText = false
    private var resultsShown = false
    private var resultsWhileEmpty = true
    private var screenShown = true

    val isOpen: Boolean
        get() = searchView.currentTransitionState.let {
            it == SearchView.TransitionState.SHOWING || it == SearchView.TransitionState.SHOWN
        }

    val text: String
        get() = searchView.text?.toString() ?: ""

    init {
        val content = LinearLayout(context)
        content.orientation = LinearLayout.VERTICAL
        scopeRow.visibility = View.GONE
        content.addView(scopeRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ScopeRow.HEIGHT_DP.dp))
        suggestionList.visibility = View.GONE
        content.addView(suggestionList, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        content.addView(resultsView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        searchView.addView(content)
        if (pill != null) searchView.setupWithSearchBar(pill)
        searchView.addTransitionListener { _, previous, next ->
            when (next) {
                SearchView.TransitionState.SHOWING -> if (previous == SearchView.TransitionState.HIDDEN) {
                    listener.onPanelOpened()
                    applyResultsVisibility()
                }
                SearchView.TransitionState.SHOWN -> {
                    applyResultsLifecycle()
                    listener.onPanelShown()
                }
                SearchView.TransitionState.HIDDEN -> {
                    applyResultsLifecycle()
                    listener.onPanelClosed()
                }
                else -> Unit
            }
        }
        searchView.editText.doAfterTextChanged {
            if (settingText) return@doAfterTextChanged
            applyResultsVisibility()
            // While it opens the search view writes the pill's text into its field: that is no typing.
            if (searchView.currentTransitionState == SearchView.TransitionState.SHOWN) listener.onPanelTextChanged()
        }
        searchView.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            searchView.clearFocusAndHideKeyboard()
            listener.onPanelSubmitted()
            true
        }
    }

    /** Over the stack, above its bar; nothing else of the stack goes over it. */
    fun attach(stack: ViewGroup) {
        if (searchView.parent === stack) return
        (searchView.parent as? ViewGroup)?.removeView(searchView)
        stack.addView(searchView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun open() {
        if (isOpen) searchView.requestFocusAndShowKeyboard() else searchView.show()
    }

    fun close() {
        if (isOpen) searchView.hide()
    }

    fun setHint(hint: String) {
        searchView.hint = hint
    }

    fun setText(value: String) {
        settingText = true
        searchView.setText(value)
        settingText = false
        applyResultsVisibility()
    }

    /** resultsActivation: `onSearchActivation` shows the results as the search opens, `onTextEntry` once it has text. */
    fun setResultsWhileEmpty(whileEmpty: Boolean) {
        resultsWhileEmpty = whileEmpty
        applyResultsVisibility()
    }

    /** Another screen on top hides it as it is, open or not; on the way back it shows again. */
    fun setScreenShown(shown: Boolean) {
        screenShown = shown
        searchView.visibility = if (shown) View.VISIBLE else View.GONE
        applyResultsLifecycle()
    }

    fun destroy() {
        if (resultsShown) resultsView.sendComponentStop(ComponentType.Component)
        resultsShown = false
        resultsView.destroy()
        (searchView.parent as? ViewGroup)?.removeView(searchView)
    }

    private fun applyResultsVisibility() {
        resultsView.visibility = if (resultsWhileEmpty || text.isNotEmpty()) View.VISIBLE else View.INVISIBLE
        applyResultsLifecycle()
    }

    // The results component appears as it shows in the open search (with text only, by resultsActivation) and
    // disappears as it hides, as the search closes, or as another screen comes on top.
    private fun applyResultsLifecycle() {
        val shown = screenShown && resultsView.visibility == View.VISIBLE &&
            searchView.currentTransitionState == SearchView.TransitionState.SHOWN
        if (shown == resultsShown) return
        resultsShown = shown
        if (shown) {
            resultsView.sendComponentWillStart(ComponentType.Component)
            resultsView.sendComponentStart(ComponentType.Component)
        } else {
            resultsView.sendComponentStop(ComponentType.Component)
        }
    }

    companion object {
        fun key(results: ComponentOptions): String = results.name.get("") + "/" + results.componentId.get("")
    }
}

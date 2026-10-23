package dev.apexnative.navigation.viewcontrollers.stack.search

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.R as AppCompatR
import com.google.android.material.search.SearchBar
import dev.apexnative.navigation.options.SearchBarOptions
import dev.apexnative.navigation.options.SearchBarScope
import dev.apexnative.navigation.options.SearchBarSuggestion
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.react.events.EventEmitter
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.dp
import dev.apexnative.navigation.views.stack.topbar.search.ScopeRow
import dev.apexnative.navigation.views.stack.topbar.search.SearchField
import dev.apexnative.navigation.views.stack.topbar.search.SearchStyle
import dev.apexnative.navigation.views.stack.topbar.search.SuggestionList

/**
 * The search of one screen (`topBar.searchBar`), kept while the screen is in its stack: a pill under the title, or a
 * magnifier in the bar (`placement: 'integrated…'`). Without a results component it opens the field in the bar and the
 * screen stays in sight under it, filtering its own content by the events; with one, a Material search view opens over
 * the stack with the results component in it ([SearchPanel]).
 */
class ScreenSearch(
    private val host: Host,
    private val screenId: String,
    /** The sender of the JavaScript the screen was made for: silent once it went. */
    private val emitter: EventEmitter,
    private val context: Context,
    private val loadIcon: (String) -> Drawable?,
    /** A scope the user picks goes into the screen's options, so it stays when they apply again. */
    private val keepScope: (String) -> Unit,
) : SearchPanel.Listener {
    interface Host {
        /** The search changed what the bar shows (open or closed, its text): shown again if it is the shown one. */
        fun refresh(search: ScreenSearch)
        fun isShown(search: ScreenSearch): Boolean
        fun keyboard(view: View, show: Boolean)
        /** The stack's view: a search view goes over it. */
        fun stackView(): ViewGroup?
        /** A Material 3 context with the colors of the app's light or dark mode as it is now. */
        fun styleContext(): Context
    }

    var options = SearchBarOptions()
        private set
    var isActive = false
        private set

    private val pillRow = FrameLayout(context)
    private val pill = SearchBar(context)
    private val searchField = SearchField(context)
    // The open field and its scopes under it, in the bar.
    private val activeArea = LinearLayout(context)
    private val scopeRow = ScopeRow(context)
    private var scopes: List<SearchBarScope> = emptyList()
    private var selectedScope: String? = null
    // The suggestions under the open field in the bar, on a card over the screen.
    private val suggestionList = SuggestionList(context, loadIcon)
    private val suggestionsCard = FrameLayout(context)
    private var suggestions: List<SearchBarSuggestion> = emptyList()
    private var appliedSuggestions: List<SearchBarSuggestion>? = null
    private var panel: SearchPanel? = null
    private var settingText = false
    private var applied = false
    private var focusWhenShown = false
    // Light or dark changed while the search view was open: it is made again once it closes.
    private var panelRestyleWhenClosed = false

    val text: String
        get() = panel?.text ?: searchField.editText.text?.toString() ?: ""

    /** What the bar shows of it: the open field, the pill under the title, or nothing (a magnifier among the buttons). */
    val barArea: View?
        get() = when {
            takesBar -> activeArea
            options.isIntegrated -> null
            else -> pillRow
        }

    val barAreaHeight: Int
        get() = SearchStyle.ROW_HEIGHT_DP.dp + if (takesBar && showsScopes) ScopeRow.HEIGHT_DP.dp else 0

    /** scopeBarActivation: `onTextEntry` once there is text; `onSearchActivation` and `automatic` as the search opens. */
    private val scopesWanted: Boolean
        get() = scopes.isNotEmpty() && (options.scopeBarActivation.get("automatic") != "onTextEntry" || text.isNotEmpty())

    private val showsScopes: Boolean
        get() = isActive && scopesWanted

    /** The open field takes the bar, in place of the title and the buttons; a search view goes over the bar instead. */
    val takesBar: Boolean
        get() = isActive && panel == null

    val showsMagnifier: Boolean
        get() = options.isIntegrated && !isActive

    /** The card of the suggestions under the bar, while the field in the bar is open and there are suggestions. */
    val suggestionsOverlay: View?
        get() = if (takesBar && suggestions.isNotEmpty()) suggestionsCard else null

    /** obscuresBackgroundDuringPresentation: the screen dims while the open field is empty. */
    val dimsScreen: Boolean
        get() = takesBar && options.obscuresBackgroundDuringPresentation.isTrue && text.isEmpty()

    init {
        pill.setOnClickListener { activate() }
        pillRow.addView(pill, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, SearchStyle.PILL_HEIGHT_DP.dp, Gravity.CENTER_VERTICAL
        ).apply {
            leftMargin = 16.dp
            rightMargin = 16.dp
            topMargin = 8.dp
            bottomMargin = 8.dp
        })
        activeArea.orientation = LinearLayout.VERTICAL
        activeArea.addView(searchField, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, SearchStyle.ROW_HEIGHT_DP.dp
        ))
        activeArea.addView(scopeRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ScopeRow.HEIGHT_DP.dp))
        scopeRow.onPick = { pickScope(it) }
        suggestionsCard.background = GradientDrawable().apply {
            cornerRadius = 16.dp.toFloat()
            setColor(SearchStyle.pillColor(context))
        }
        suggestionsCard.elevation = 3.dp.toFloat()
        suggestionsCard.clipToOutline = true
        suggestionsCard.addView(suggestionList)
        suggestionList.onPick = { pickSuggestion(it) }
        searchField.onClose = { cancel() }
        searchField.editText.doAfterTextChanged {
            if (settingText) return@doAfterTextChanged
            pill.setText(text)
            clearSuggestions()
            sendUpdate()
            refresh()
        }
        searchField.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            submit()
            true
        }
        searchField.editText.setOnFocusChangeListener { _, _ -> if (takesBar) sendUpdate() }
    }

    /**
     * The screen's options as they resolve: everything applies; text and focus only the first time, focus as
     * [initialFocus] says (a search a merge adds opens only by the merge's own focus).
     */
    fun apply(resolved: SearchBarOptions, initialFocus: Boolean = resolved.focus.isTrue) {
        options = resolved
        val placeholder = resolved.placeholder.get(context.getString(AppCompatR.string.abc_searchview_description_search))
        pill.hint = placeholder
        searchField.editText.hint = placeholder
        val pillColor = resolved.backgroundColor.get(null)
        pill.backgroundTintList = pillColor?.let { ColorStateList.valueOf(it) }
        searchField.setPillColor(pillColor)
        searchField.setTintColor(resolved.tintColor.get(null))
        applyPanel(resolved, placeholder)
        val testId = resolved.testID.get(null)
        TestIds.apply(pill, testId)
        searchField.setTestId(testId)
        panel?.let { TestIds.apply(it.searchView.editText, testId) }
        applyScopes(resolved)
        applySuggestions(resolved)
        if (!applied) {
            applied = true
            if (resolved.text.hasValue()) setText(resolved.text.get())
            focusWhenShown = initialFocus
        }
    }

    /** mergeOptions: text replaces what is typed (without searchBarUpdated), focus opens or hides the keyboard. */
    fun merge(toMerge: SearchBarOptions, resolved: SearchBarOptions) {
        val first = !applied
        apply(resolved, toMerge.focus.isTrue)
        if (first) return
        if (toMerge.text.hasValue()) setText(toMerge.text.get())
        if (toMerge.focus.isTrue) {
            if (host.isShown(this)) activate() else focusWhenShown = true
        } else if (toMerge.focus.isFalse) {
            focusWhenShown = false
            hideKeyboard()
        }
    }

    /** Its screen is on top of the stack: focus: true opens it now; its search view shows again as it was. */
    fun onShown() {
        panel?.setScreenShown(true)
        if (!focusWhenShown) return
        focusWhenShown = false
        // Not the field's own post: closed, it is out of the window, and its posts wait for it to come in.
        Handler(Looper.getMainLooper()).post { if (host.isShown(this)) activate() }
    }

    /** Another screen is on top: the keyboard goes; the search stays as it is for the way back. */
    fun onHidden() {
        hideKeyboard()
        panel?.setScreenShown(false)
    }

    /** The search opens with the keyboard; an open one only gets the keyboard back. */
    fun activate() {
        panel?.let {
            it.open()
            return
        }
        if (!isActive) {
            isActive = true
            refresh()
        }
        // A field that gets the focus sends searchBarUpdated by that; one that has it already, here.
        val hadFocus = searchField.editText.hasFocus()
        searchField.editText.requestFocus()
        searchField.editText.setSelection(text.length)
        host.keyboard(searchField.editText, true)
        if (hadFocus) sendUpdate()
    }

    /** The back arrow, the back key, a tap on the dimmed screen: the search closes and its text is cleared. */
    fun cancel() {
        if (!isActive) return
        panel?.let {
            // The search view tells when it is closed (onPanelClosed).
            it.close()
            return
        }
        // Closed first: the focus it loses sends nothing, the cancel and the empty text follow.
        isActive = false
        searchField.editText.clearFocus()
        host.keyboard(searchField.editText, false)
        setText("")
        refresh()
        sendCancel()
    }

    /** Its screen is gone: its views leave the bar and the stack. */
    fun destroy() {
        host.keyboard(searchField.editText, false)
        panel?.destroy()
        panel = null
        listOf(pillRow, activeArea).forEach { (it.parent as? ViewGroup)?.removeView(it) }
    }

    override fun onPanelOpened() {
        isActive = true
        refresh()
    }

    // The search view focuses its field a moment after it is open: it is focused as far as the app goes.
    override fun onPanelShown() {
        sendUpdate(focused = true)
    }

    override fun onPanelClosed() {
        if (!isActive) return
        isActive = false
        setText("")
        refresh()
        sendCancel()
        if (panelRestyleWhenClosed) {
            panelRestyleWhenClosed = false
            // Not inside the search view's own callback: it is still finishing its hide.
            Handler(Looper.getMainLooper()).post { remakePanel() }
        }
    }

    /** The tint of the screen's options as they resolve now (`navigation.setTheme`), for the restyle after it. */
    fun takeTint(tint: ThemeColour) {
        options.tintColor = tint
    }

    /** Light or dark changed: the theme's colors again. The search view is made again, when it is closed. */
    fun restyle() {
        val styleContext = host.styleContext()
        val pillColor = options.backgroundColor.get(null)
        pill.backgroundTintList = ColorStateList.valueOf(pillColor ?: SearchStyle.pillColor(styleContext))
        pill.textView.setTextColor(SearchStyle.textColor(styleContext))
        pill.textView.setHintTextColor(SearchStyle.hintColor(styleContext))
        pill.navigationIcon?.mutate()?.setTint(SearchStyle.hintColor(styleContext))
        searchField.restyle(styleContext)
        searchField.setTintColor(options.tintColor.get(null))
        (suggestionsCard.background as? GradientDrawable)?.setColor(SearchStyle.pillColor(styleContext))
        scopeRow.restyle(styleContext)
        suggestionList.restyle(styleContext)
        if (panel == null) return
        if (isActive) panelRestyleWhenClosed = true else remakePanel()
    }

    private fun remakePanel() {
        val current = panel ?: return
        current.destroy()
        panel = null
        apply(options)
    }

    override fun onPanelTextChanged() {
        clearSuggestions()
        refresh()
        sendUpdate()
    }

    override fun onPanelSubmitted() {
        sendSubmit()
    }

    // A results component of its own makes a search view; another one, or another placement, makes it again, and an
    // open search closes.
    private fun applyPanel(resolved: SearchBarOptions, placeholder: String) {
        if (!resolved.hasResultsComponent) {
            panel?.let {
                it.destroy()
                panel = null
                isActive = false
                // The search view took the pill's taps.
                pill.setOnClickListener { activate() }
            }
            return
        }
        val opensFromPill = !resolved.isIntegrated
        val existing = panel
        if (existing == null || existing.resultsKey != SearchPanel.key(resolved.resultsComponent) ||
            existing.opensFromPill != opensFromPill) {
            existing?.destroy()
            isActive = false
            if (!opensFromPill) pill.setOnClickListener { activate() }
            panel = SearchPanel(host.styleContext(), this, resolved.resultsComponent, if (opensFromPill) pill else null,
                loadIcon)
            panel?.scopeRow?.onPick = { pickScope(it) }
            panel?.suggestionList?.onPick = { pickSuggestion(it) }
            panel?.suggestionList?.setSuggestions(suggestions)
        }
        val current = panel ?: return
        current.setHint(placeholder)
        current.setResultsWhileEmpty(resolved.resultsActivation.get("onSearchActivation") != "onTextEntry")
        host.stackView()?.let { current.attach(it) }
        current.setScreenShown(host.isShown(this))
    }

    private fun submit() {
        searchField.editText.clearFocus()
        host.keyboard(searchField.editText, false)
        sendSubmit()
    }

    private fun hideKeyboard() {
        panel?.searchView?.clearFocusAndHideKeyboard()
        searchField.editText.clearFocus()
        host.keyboard(searchField.editText, false)
    }

    private fun setText(value: String) {
        settingText = true
        searchField.editText.setText(value)
        searchField.editText.setSelection(value.length)
        pill.setText(value)
        panel?.setText(value)
        settingText = false
        refresh()
    }

    // The scopes as the options give them: the selected one is selectedScope, or the first.
    private fun applyScopes(resolved: SearchBarOptions) {
        scopes = resolved.scopes ?: emptyList()
        val selected = resolved.selectedScope.get(null)
        selectedScope = scopes.firstOrNull { it.id == selected }?.id ?: scopes.firstOrNull()?.id
        scopeRow.setScopes(scopes, selectedScope)
        panel?.scopeRow?.setScopes(scopes, selectedScope)
        refresh()
    }

    // A list the options give shows; the same list applied again does not bring back what typing or a pick removed.
    private fun applySuggestions(resolved: SearchBarOptions) {
        val list = resolved.suggestions
        if (list === appliedSuggestions) return
        appliedSuggestions = list
        setSuggestions(list ?: emptyList())
    }

    private fun setSuggestions(list: List<SearchBarSuggestion>) {
        suggestions = list
        suggestionList.setSuggestions(list)
        panel?.suggestionList?.setSuggestions(list)
        refresh()
    }

    // The user goes on searching: the suggestions go, the app gives new ones from searchBarUpdated.
    private fun clearSuggestions() {
        if (suggestions.isNotEmpty()) setSuggestions(emptyList())
    }

    // A pick sends searchSuggestionPicked with the suggestion's id; the text stays.
    private fun pickSuggestion(suggestionId: String) {
        emitter.emitSearchSuggestionPicked(screenId, suggestionId)
        setSuggestions(emptyList())
    }

    private fun pickScope(scopeId: String) {
        if (scopeId == selectedScope) return
        selectedScope = scopeId
        keepScope(scopeId)
        scopeRow.setScopes(scopes, scopeId)
        panel?.scopeRow?.setScopes(scopes, scopeId)
        sendUpdate()
    }

    // The bar shows it again, with the scope rows as they go now.
    private fun refresh() {
        scopeRow.visibility = if (showsScopes) View.VISIBLE else View.GONE
        panel?.scopeRow?.visibility = if (scopesWanted) View.VISIBLE else View.GONE
        panel?.suggestionList?.visibility = if (suggestions.isNotEmpty()) View.VISIBLE else View.GONE
        host.refresh(this)
    }

    private fun sendUpdate(
        focused: Boolean = isActive && (panel?.searchView?.editText ?: searchField.editText).hasFocus(),
    ) {
        eventTargets().forEach { emitter.emitSearchBarUpdated(it, text, focused, scope()) }
    }

    private fun sendCancel() {
        eventTargets().forEach { emitter.emitSearchBarCancelPressed(it) }
        sendUpdate()
    }

    private fun sendSubmit() {
        eventTargets().forEach { emitter.emitSearchBarSubmitted(it, text, scope()) }
    }

    // The events carry the selected scope while there are scopes.
    private fun scope(): String? = if (scopes.isEmpty()) null else selectedScope

    // The results component gets the search events of the screen, with its own id.
    private fun eventTargets(): List<String> =
        listOfNotNull(screenId, panel?.resultsComponentId?.takeIf { it.isNotEmpty() })
}

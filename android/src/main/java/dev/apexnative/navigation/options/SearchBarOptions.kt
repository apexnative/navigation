package dev.apexnative.navigation.options

import android.content.Context
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.NullThemeColour
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.IconParser
import dev.apexnative.navigation.options.parsers.TextParser

/** A segment of the scope row: chips under the search field; [testID] is the identifier tests find it by. */
data class SearchBarScope(val id: String, val title: String, val testID: String? = null)

/**
 * A row of the suggestions under the search field; a pick sends navigationButtonPressed with its id. [testID] is the
 * identifier tests find it by.
 */
data class SearchBarSuggestion(val id: String, val title: String, val icon: Text, val testID: String? = null)

/**
 * `topBar.searchBar`: the search of the screen, in its top bar. In mergeOptions only the given fields change; a list
 * given replaces the one before, an empty one removes it.
 */
class SearchBarOptions {
    @JvmField var visible: Bool = NullBool()
    @JvmField var focus: Bool = NullBool()
    @JvmField var hideOnScroll: Bool = NullBool()
    @JvmField var hideTopBarOnFocus: Bool = NullBool()
    @JvmField var obscuresBackgroundDuringPresentation: Bool = NullBool()
    @JvmField var backgroundColor: ThemeColour = NullThemeColour()
    @JvmField var tintColor: ThemeColour = NullThemeColour()
    @JvmField var placeholder: Text = NullText()
    @JvmField var text: Text = NullText()
    @JvmField var placement: Text = NullText()
    @JvmField var scopes: List<SearchBarScope>? = null
    @JvmField var selectedScope: Text = NullText()
    @JvmField var scopeBarActivation: Text = NullText()
    @JvmField var suggestions: List<SearchBarSuggestion>? = null
    @JvmField var resultsComponent: ComponentOptions = ComponentOptions()
    @JvmField var resultsActivation: Text = NullText()
    @JvmField var testID: Text = NullText()

    /** `integrated`, `integratedButton` and `integratedCentered`: a magnifier in the bar instead of a field. */
    val isIntegrated: Boolean
        get() = placement.get("stacked").startsWith("integrated")

    val hasResultsComponent: Boolean
        get() = resultsComponent.name.hasValue()

    fun mergeWith(other: SearchBarOptions) {
        if (other.visible.hasValue()) visible = other.visible
        if (other.focus.hasValue()) focus = other.focus
        if (other.hideOnScroll.hasValue()) hideOnScroll = other.hideOnScroll
        if (other.hideTopBarOnFocus.hasValue()) hideTopBarOnFocus = other.hideTopBarOnFocus
        if (other.obscuresBackgroundDuringPresentation.hasValue())
            obscuresBackgroundDuringPresentation = other.obscuresBackgroundDuringPresentation
        backgroundColor = backgroundColor.mergeWith(other.backgroundColor)
        tintColor = tintColor.mergeWith(other.tintColor)
        if (other.placeholder.hasValue()) placeholder = other.placeholder
        if (other.text.hasValue()) text = other.text
        if (other.placement.hasValue()) placement = other.placement
        other.scopes?.let { scopes = it }
        if (other.selectedScope.hasValue()) selectedScope = other.selectedScope
        if (other.scopeBarActivation.hasValue()) scopeBarActivation = other.scopeBarActivation
        other.suggestions?.let { suggestions = it }
        if (other.resultsComponent.hasValue()) resultsComponent = other.resultsComponent
        if (other.resultsActivation.hasValue()) resultsActivation = other.resultsActivation
        if (other.testID.hasValue()) testID = other.testID
    }

    fun mergeWithDefault(defaults: SearchBarOptions) {
        if (!visible.hasValue()) visible = defaults.visible
        if (!focus.hasValue()) focus = defaults.focus
        if (!hideOnScroll.hasValue()) hideOnScroll = defaults.hideOnScroll
        if (!hideTopBarOnFocus.hasValue()) hideTopBarOnFocus = defaults.hideTopBarOnFocus
        if (!obscuresBackgroundDuringPresentation.hasValue())
            obscuresBackgroundDuringPresentation = defaults.obscuresBackgroundDuringPresentation
        backgroundColor = backgroundColor.withDefault(defaults.backgroundColor)
        tintColor = tintColor.withDefault(defaults.tintColor)
        if (!placeholder.hasValue()) placeholder = defaults.placeholder
        if (!text.hasValue()) text = defaults.text
        if (!placement.hasValue()) placement = defaults.placement
        if (scopes == null) scopes = defaults.scopes
        if (!selectedScope.hasValue()) selectedScope = defaults.selectedScope
        if (!scopeBarActivation.hasValue()) scopeBarActivation = defaults.scopeBarActivation
        if (suggestions == null) suggestions = defaults.suggestions
        if (!resultsComponent.hasValue()) resultsComponent = defaults.resultsComponent
        if (!resultsActivation.hasValue()) resultsActivation = defaults.resultsActivation
        if (!testID.hasValue()) testID = defaults.testID
    }

    companion object {
        private val PLACEMENTS =
            setOf("stacked", "automatic", "integrated", "integratedButton", "integratedCentered")
        private val SCOPE_BAR_ACTIVATIONS = setOf("automatic", "onTextEntry", "onSearchActivation")
        private val RESULTS_ACTIVATIONS = setOf("onSearchActivation", "onTextEntry")

        @JvmStatic
        fun parse(context: Context?, json: ReadableMap?): SearchBarOptions {
            val options = SearchBarOptions()
            if (json == null) return options
            options.visible = BoolParser.parse(json, "visible")
            options.focus = BoolParser.parse(json, "focus")
            options.hideOnScroll = BoolParser.parse(json, "hideOnScroll")
            options.hideTopBarOnFocus = BoolParser.parse(json, "hideTopBarOnFocus")
            options.obscuresBackgroundDuringPresentation =
                BoolParser.parse(json, "obscuresBackgroundDuringPresentation")
            options.backgroundColor = ThemeColour.parse(context, json, "backgroundColor")
            options.tintColor = ThemeColour.parse(context, json, "tintColor")
            options.placeholder = TextParser.parse(json, "placeholder")
            options.text = TextParser.parse(json, "text")
            options.placement = TextParser.parse(json, "placement", PLACEMENTS)
            options.scopes = json.optArray("scopes")?.let { parseScopes(it) }
            options.selectedScope = TextParser.parse(json, "selectedScope")
            options.scopeBarActivation = TextParser.parse(json, "scopeBarActivation", SCOPE_BAR_ACTIVATIONS)
            options.suggestions = json.optArray("suggestions")?.let { parseSuggestions(it) }
            options.resultsComponent = ComponentOptions.parse(json.optMap("resultsComponent"))
            options.resultsActivation = TextParser.parse(json, "resultsActivation", RESULTS_ACTIVATIONS)
            options.testID = TextParser.parse(json, "testID")
            return options
        }

        private fun ReadableMap.testIDOrNull(): String? = optString("testID").takeIf { it.isNotEmpty() }

        private fun parseScopes(array: ReadableArray): List<SearchBarScope> =
            (0 until array.size()).mapNotNull { index ->
                array.optMap(index)?.let { item ->
                    val id = item.optString("id")
                    if (id.isEmpty()) null else SearchBarScope(id, item.optString("title", id), item.testIDOrNull())
                }
            }

        private fun parseSuggestions(array: ReadableArray): List<SearchBarSuggestion> =
            (0 until array.size()).mapNotNull { index ->
                array.optMap(index)?.let { item ->
                    val id = item.optString("id")
                    if (id.isEmpty()) {
                        null
                    } else {
                        val title = item.optString("title", id)
                        SearchBarSuggestion(id, title, IconParser.parse(item, "icon"), item.testIDOrNull())
                    }
                }
            }
    }
}

package dev.apexnative.navigation.viewcontrollers.stack.search

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.apexnative.navigation.options.SearchBarOptions
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.react.events.EventEmitter
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.dp
import dev.apexnative.navigation.viewcontrollers.parent.OptionsScreen
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import dev.apexnative.navigation.views.stack.topbar.TopBar
import dev.apexnative.navigation.views.stack.topbar.TopBarDefaults
import dev.apexnative.navigation.views.stack.topbar.search.SearchStyle
import dev.apexnative.navigation.views.stack.topbar.titlebar.BarButtonView

/**
 * The search of the screens of a stack (`topBar.searchBar`): each screen has its own while it is in the stack, and the
 * top bar shows the one of the screen on top; a screen's search view (with a results component) shows only while the
 * screen is on top.
 */
class StackSearchPresenter(
    private val activity: Activity,
    private val loadIcon: (String) -> Drawable?,
) : ScreenSearch.Host {
    private var topBar: TopBar? = null
    private val searches = HashMap<View, ScreenSearch>()
    private var shown: ScreenSearch? = null
    private var scrim: View? = null
    private var suggestions: View? = null
    private var wasActive = false
    private var onActiveChanged: Runnable? = null
    private var magnifier: BarButtonView? = null

    /** The search of the screen on top opened or closed: the bar's hideOnScroll stops or goes on. */
    fun setOnActiveChanged(listener: Runnable?) {
        onActiveChanged = listener
    }

    /** The search of the screen on top is open. */
    fun isActive(): Boolean = shown?.isActive == true

    fun bindTopBar(topBar: TopBar) {
        this.topBar = topBar
        magnifier = null
        // The suggestions hang from the bar: they follow its height as the search opens and its scopes show.
        topBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> topBar.post { positionSuggestions(topBar) } }
    }

    /** The options of the screen on top of the stack, resolved: its search shows in the bar, or none. */
    fun apply(screen: ViewController<*>, resolved: SearchBarOptions) {
        val view = screen.view
        if (!resolved.visible.isTrue) {
            remove(view)
            show(null)
            return
        }
        val search = searchOf(screen)
        search.apply(resolved)
        show(search)
    }

    /** mergeOptions of the screen on top: only what it gives changes; visible: false removes the search. */
    fun merge(screen: ViewController<*>, toMerge: SearchBarOptions, resolved: SearchBarOptions) {
        if (!resolved.visible.isTrue) {
            if (toMerge.visible.isFalse) {
                remove(screen.view)
                show(null)
            }
            return
        }
        val search = searchOf(screen)
        search.merge(toMerge, resolved)
        show(search)
    }

    fun onScreenDestroyed(view: View) {
        remove(view)
    }

    /** The back key: an open search closes first. */
    fun closeSearch(): Boolean {
        val search = shown?.takeIf { it.isActive } ?: return false
        search.cancel()
        return true
    }

    /**
     * After the right buttons change: the magnifier of an integrated search stands after them, before "⋮", as a button
     * of the bar, with the id `<topBar.searchBar.testID>.button`.
     */
    fun applyMagnifier() {
        val bar = topBar ?: return
        val row = bar.rightButtonBar
        val search = shown
        if (search?.showsMagnifier != true) {
            row.setTrailing(null)
            return
        }
        val button = magnifier ?: BarButtonView(row.context).also { button ->
            val context = styleContext()
            val icon = AppCompatResources.getDrawable(context, AppCompatR.drawable.abc_ic_search_api_material)?.mutate()
            icon?.setTint(TopBarDefaults.overflowIconColor(context))
            val label = context.getString(AppCompatR.string.abc_searchview_description_search)
            button.show(label, icon, null, true)
            button.setOnClickListener { shown?.activate() }
            magnifier = button
        }
        TestIds.apply(button, search.options.testID.get(null)?.let { "$it.button" })
        row.setTrailing(button)
    }

    override fun refresh(search: ScreenSearch) {
        if (search === shown) refresh()
    }

    override fun isShown(search: ScreenSearch): Boolean = search === shown

    override fun stackView(): ViewGroup? = topBar?.parent as? ViewGroup

    override fun styleContext(): Context = SearchStyle.materialContext(activity)

    /**
     * Light or dark changed, or the default options (`navigation.setTheme`): the searches take the theme's colors
     * again, the magnifier too. The shown one takes [tint], from its screen's options as they resolve now.
     */
    fun onConfigurationChanged(tint: ThemeColour) {
        shown?.takeTint(tint)
        searches.values.forEach { it.restyle() }
        // The magnifier is made again in the new mode's colors.
        magnifier = null
        topBar?.rightButtonBar?.setTrailing(null)
        refresh()
    }

    override fun keyboard(view: View, show: Boolean) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        if (show) controller.show(WindowInsetsCompat.Type.ime()) else controller.hide(WindowInsetsCompat.Type.ime())
    }

    private fun searchOf(screen: ViewController<*>): ScreenSearch =
        searches.getOrPut(screen.view) {
            // The search of a pair of screens is its primary's: its events name that screen, which keeps the scope in
            // its user layer, where a merge that sends the same options again doesn't take it away.
            val owner = (screen as? OptionsScreen)?.optionsScreen ?: screen
            // Its events go to the JavaScript the screen was made for, and to no other.
            ScreenSearch(this, owner.id, EventEmitter.of(owner.jsGeneration), styleContext(), loadIcon) { scope ->
                owner.changeChoices { it.pickScope(scope) }
            }
        }

    private fun remove(view: View) {
        val search = searches.remove(view) ?: return
        if (search === shown) show(null)
        search.destroy()
    }

    private fun show(search: ScreenSearch?) {
        if (shown !== search) {
            shown?.onHidden()
            shown = search
            search?.onShown()
        }
        refresh()
    }

    private fun refresh() {
        val bar = topBar ?: return
        val search = shown
        bar.setSearchArea(search?.barArea, search?.barAreaHeight ?: 0, search?.takesBar == true)
        applyMagnifier()
        applyScrim(bar)
        applySuggestions(bar)
        if (isActive() != wasActive) {
            wasActive = isActive()
            onActiveChanged?.run()
        }
    }

    // Over the screens and under the bar: a tap on it closes the search.
    private fun applyScrim(bar: TopBar) {
        if (shown?.dimsScreen != true) {
            scrim?.visibility = View.GONE
            return
        }
        val stack = bar.parent as? ViewGroup ?: return
        val view = scrim ?: View(activity).apply {
            setBackgroundColor(SearchStyle.scrimColor())
            isClickable = true
            setOnClickListener { shown?.cancel() }
        }.also { scrim = it }
        if (view.parent !== stack) {
            (view.parent as? ViewGroup)?.removeView(view)
            stack.addView(view, stack.indexOfChild(bar), CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        view.visibility = View.VISIBLE
    }

    // Under the bar, over the screen and its dimming.
    private fun applySuggestions(bar: TopBar) {
        val card = shown?.suggestionsOverlay
        if (card !== suggestions) {
            (suggestions?.parent as? ViewGroup)?.removeView(suggestions)
            suggestions = card
        }
        if (card == null) return
        val stack = bar.parent as? ViewGroup ?: return
        val scrimIndex = scrim?.takeIf { it.parent === stack }?.let { stack.indexOfChild(it) } ?: -1
        if (card.parent !== stack || stack.indexOfChild(card) < scrimIndex) {
            (card.parent as? ViewGroup)?.removeView(card)
            stack.addView(card, stack.indexOfChild(bar), CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 16.dp
                rightMargin = 16.dp
            })
        }
        positionSuggestions(bar)
    }

    private fun positionSuggestions(bar: TopBar) {
        val card = suggestions ?: return
        val lp = card.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val top = bar.bottom + 4.dp
        if (lp.topMargin == top) return
        lp.topMargin = top
        card.layoutParams = lp
    }
}

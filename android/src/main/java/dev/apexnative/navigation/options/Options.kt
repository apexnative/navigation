package dev.apexnative.navigation.options

import android.content.Context
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.options.params.NullFraction
import dev.apexnative.navigation.options.params.NullText
import dev.apexnative.navigation.options.params.NullVariant
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.Variant
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.TextParser
import dev.apexnative.navigation.options.parsers.VariantParser

/**
 * The options of a layout, read once from the JSON JavaScript sends ([OptionsReader]): a node's own, the result of a
 * merge, or its own over those of the layouts around it and the default options. Every group merges key by key at any
 * depth; a value replaces; an array replaces whole, `[]` included; a key the upper side doesn't set leaves the lower
 * one; colors merge side by side. Merging copies groups and lists of groups, so two options never share one; values are
 * immutable and may be shared. The one exception is a button menu ([ButtonMenuOptions]), shared on purpose.
 *
 * Fields follow the TS interface `Options` (`src/interfaces/Options.ts`); `window` and `layout.direction` come only in
 * the default options.
 */
class Options {
    @JvmField var topBar = TopBarOptions()
    @JvmField var toolbar = ToolbarOptions()
    @JvmField var scrollEdgeEffect = ScrollEdgeEffectOptions()
    @JvmField var statusBar = StatusBarOptions()
    @JvmField var navigationBar = NavigationBarOptions()
    @JvmField var layout = LayoutOptions()
    @JvmField var modal = ModalOptions()
    @JvmField var modalPresentationStyle: Text = NullText()
    @JvmField var modalTransitionStyle: Text = NullText()
    @JvmField var overlay = OverlayOptions()
    @JvmField var sideMenu = SideMenuOptions()
    @JvmField var splitView = SplitViewGroupOptions()
    @JvmField var bottomTabs = BottomTabsOptions()
    @JvmField var bottomTab = BottomTabOptions()
    @JvmField var animations = AnimationsOptions()
    /** `boolean | 'edge'`. */
    @JvmField var popGesture: Variant = NullVariant()
    @JvmField var hardwareBackButton = HardwareBackButtonOptions()
    @JvmField var fab = FabOptions()
    /** `keyCommands`; null when the options don't give them. */
    @JvmField var keyCommands: List<KeyCommandOptions>? = null
    @JvmField var blurOnUnmount: Bool = NullBool()
    @JvmField var preview = PreviewOptions()
    @JvmField var window = WindowOptions()

    /** A copy of every group: changing it leaves these options as they are. */
    fun copy(): Options = Options().mergeWith(this)

    /** The values [other] gives go over these; returns these options. */
    fun mergeWith(other: Options): Options {
        topBar.mergeWith(other.topBar)
        toolbar.mergeWith(other.toolbar)
        scrollEdgeEffect.mergeWith(other.scrollEdgeEffect)
        statusBar.mergeWith(other.statusBar)
        navigationBar.mergeWith(other.navigationBar)
        layout.mergeWith(other.layout)
        modal.mergeWith(other.modal)
        if (other.modalPresentationStyle.hasValue()) modalPresentationStyle = other.modalPresentationStyle
        if (other.modalTransitionStyle.hasValue()) modalTransitionStyle = other.modalTransitionStyle
        overlay.mergeWith(other.overlay)
        sideMenu.mergeWith(other.sideMenu)
        splitView.mergeWith(other.splitView)
        bottomTabs.mergeWith(other.bottomTabs)
        bottomTab.mergeWith(other.bottomTab)
        animations.mergeWith(other.animations)
        if (other.popGesture.hasValue()) popGesture = other.popGesture
        hardwareBackButton.mergeWith(other.hardwareBackButton)
        fab.mergeWith(other.fab)
        other.keyCommands?.let { keyCommands = it.map(KeyCommandOptions::copy) }
        if (other.blurOnUnmount.hasValue()) blurOnUnmount = other.blurOnUnmount
        preview.mergeWith(other.preview)
        window.mergeWith(other.window)
        return this
    }

    /**
     * The values these options don't give come from [defaultOptions], which doesn't change and shares no group with
     * these options afterwards; returns these options.
     */
    fun withDefaultOptions(defaultOptions: Options): Options {
        topBar.mergeWithDefault(defaultOptions.topBar)
        toolbar.mergeWithDefault(defaultOptions.toolbar)
        scrollEdgeEffect.mergeWithDefault(defaultOptions.scrollEdgeEffect)
        statusBar.mergeWithDefault(defaultOptions.statusBar)
        navigationBar.mergeWithDefault(defaultOptions.navigationBar)
        layout.mergeWithDefault(defaultOptions.layout)
        modal.mergeWithDefault(defaultOptions.modal)
        if (!modalPresentationStyle.hasValue()) modalPresentationStyle = defaultOptions.modalPresentationStyle
        if (!modalTransitionStyle.hasValue()) modalTransitionStyle = defaultOptions.modalTransitionStyle
        overlay.mergeWithDefault(defaultOptions.overlay)
        sideMenu.mergeWithDefault(defaultOptions.sideMenu)
        splitView.mergeWithDefault(defaultOptions.splitView)
        bottomTabs.mergeWithDefault(defaultOptions.bottomTabs)
        bottomTab.mergeWithDefault(defaultOptions.bottomTab)
        animations.mergeWithDefault(defaultOptions.animations)
        if (!popGesture.hasValue()) popGesture = defaultOptions.popGesture
        hardwareBackButton.mergeWithDefault(defaultOptions.hardwareBackButton)
        fab.mergeWithDefault(defaultOptions.fab)
        if (keyCommands == null) keyCommands = defaultOptions.keyCommands?.map(KeyCommandOptions::copy)
        if (!blurOnUnmount.hasValue()) blurOnUnmount = defaultOptions.blurOnUnmount
        preview.mergeWithDefault(defaultOptions.preview)
        window.mergeWithDefault(defaultOptions.window)
        return this
    }

    /** One of the options that act once is set ([clearOneShotOptions]). */
    fun hasOneShotOptions(): Boolean = bottomTabs.currentTabIndex.hasValue() || bottomTabs.currentTabId.hasValue() ||
        sideMenu.left.visible.hasValue() || sideMenu.right.visible.hasValue()

    /**
     * Takes away the options that act once: `bottomTabs.currentTabIndex`, `bottomTabs.currentTabId`,
     * `sideMenu.left.visible`, `sideMenu.right.visible`. A node's own options never keep them; whoever acts on them
     * reads them from the options that bring them (the layout's at creation, the merged ones at `mergeOptions`).
     */
    fun clearOneShotOptions() {
        bottomTabs.currentTabIndex = NullFraction()
        bottomTabs.currentTabId = NullText()
        sideMenu.left.visible = NullBool()
        sideMenu.right.visible = NullBool()
    }

    companion object {
        private val PRESENTATION_STYLES = setOf(
            "pageSheet", "formSheet", "fullScreen", "overFullScreen", "overCurrentContext", "currentContext", "popover",
        )
        private val TRANSITION_STYLES = setOf("coverVertical", "crossDissolve", "flipHorizontal", "partialCurl")

        /**
         * Reads the options of [map]; [context] resolves platform colors. Use [OptionsReader.read], which also gives
         * the path of a value that couldn't be read.
         */
        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): Options {
            val options = Options()
            if (map == null) return options
            options.topBar = map.group("topBar") { TopBarOptions.parse(context, it) }
            options.toolbar = map.group("toolbar") { ToolbarOptions.parse(context, it) }
            options.scrollEdgeEffect = map.group("scrollEdgeEffect") { ScrollEdgeEffectOptions.parse(it) }
            options.statusBar = map.group("statusBar") { StatusBarOptions.parse(context, it) }
            options.navigationBar = map.group("navigationBar") { NavigationBarOptions.parse(context, it) }
            options.layout = map.group("layout") { LayoutOptions.parse(context, it) }
            options.modal = map.group("modal") { ModalOptions.parse(it) }
            options.modalPresentationStyle = TextParser.parse(map, "modalPresentationStyle", PRESENTATION_STYLES)
            options.modalTransitionStyle = TextParser.parse(map, "modalTransitionStyle", TRANSITION_STYLES)
            options.overlay = map.group("overlay") { OverlayOptions.parse(it) }
            options.sideMenu = map.group("sideMenu") { SideMenuOptions.parse(it) }
            options.splitView = map.group("splitView") { SplitViewGroupOptions.parse(it) }
            options.bottomTabs = map.group("bottomTabs") { BottomTabsOptions.parse(context, it) }
            options.bottomTab = map.group("bottomTab") { BottomTabOptions.parse(context, it) }
            options.animations = map.group("animations") { AnimationsOptions.parse(it) }
            options.popGesture = VariantParser.parse(map, "popGesture", booleans = true, texts = setOf("edge"))
            options.hardwareBackButton = map.group("hardwareBackButton") { HardwareBackButtonOptions.parse(it) }
            options.fab = map.group("fab") { FabOptions.parse(context, it) }
            options.keyCommands = map.groupList("keyCommands") { KeyCommandOptions.parse(it) }
            options.blurOnUnmount = BoolParser.parse(map, "blurOnUnmount")
            options.preview = map.group("preview") { PreviewOptions.parse(it) }
            options.window = map.group("window") { WindowOptions.parse(context, it) }
            return options
        }
    }
}

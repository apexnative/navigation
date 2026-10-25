package dev.apexnative.navigation.options

import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap

/**
 * Options JSON as React Native gives it on Android, every path of the files of `src/interfaces/options/` set to a valid
 * sample: colors as JavaScript sends them on Android (`{ light, dark }` of ARGB numbers), components with the
 * `componentId` the engine adds. [full] is the whole tree; each group has a function of its own, which a new group adds
 * its sample to and [full] puts in its place. The whole-tree test reads it and checks that every field of every native
 * group has a value.
 */
object OptionsFixture {
    // Two different ARGB colors, as `processColor` gives them on Android (signed 32-bit numbers).
    const val LIGHT = 0xFF102030.toInt()
    const val DARK = 0xFF405060.toInt()

    /** Every option. */
    fun full(): JavaOnlyMap = JavaOnlyMap.of(
        "topBar", topBar(),
        "toolbar", toolbar(),
        "scrollEdgeEffect", scrollEdgeEffect(),
        "statusBar", statusBar(),
        "navigationBar", navigationBar(),
        "layout", layout(),
        "modal", modal(),
        "modalPresentationStyle", "pageSheet",
        "modalTransitionStyle", "crossDissolve",
        "overlay", JavaOnlyMap.of("interceptTouchOutside", true, "testID", "Overlay"),
        "sideMenu", sideMenu(),
        "splitView", splitView(),
        "bottomTabs", bottomTabs(),
        "bottomTab", bottomTab(),
        "animations", animations(),
        "popGesture", "edge",
        "hardwareBackButton", JavaOnlyMap.of(
            "bottomTabsOnPress", "previous",
            "popStackOnPress", true,
            "dismissModalOnPress", true,
            "systemAnimation", false,
        ),
        "fab", fab(),
        "keyCommands", JavaOnlyArray.of(
            JavaOnlyMap.of(
                "id", "save",
                "input", "s",
                "modifiers", JavaOnlyArray.of("command", "shift"),
                "title", "Save",
            ),
        ),
        "blurOnUnmount", true,
        "preview", preview(),
        "window", JavaOnlyMap.of("backgroundColor", color()),
    )

    /** A color pair. */
    fun color(light: Int = LIGHT, dark: Int = DARK): JavaOnlyMap = JavaOnlyMap.of("light", light, "dark", dark)

    /** The font fields of a text, merged into [map]. */
    fun withFont(map: JavaOnlyMap): JavaOnlyMap = map.apply {
        putString("fontFamily", "Inter")
        putString("fontStyle", "italic")
        putString("fontWeight", "bold")
        putDouble("fontSize", 17.0)
    }

    fun topBar(): JavaOnlyMap = JavaOnlyMap.of(
        "visible", true,
        "animate", true,
        "hideOnScroll", true,
        "hideOnTap", true,
        "hideOnKeyboard", true,
        "leftButtonColor", color(),
        "rightButtonColor", color(),
        "leftButtonBackgroundColor", color(),
        "rightButtonBackgroundColor", color(),
        "leftButtonDisabledColor", color(),
        "rightButtonDisabledColor", color(),
        "centerButtonColor", color(),
        "centerButtonDisabledColor", color(),
        "drawBehind", true,
        "testID", "TopBar",
        "title", title(),
        "subtitle", withFont(JavaOnlyMap.of("text", "Profile", "color", color(), "alignment", "center")),
        "backButton", backButton(),
        "leftButtons", JavaOnlyArray.of(button("Login")),
        "rightButtons", JavaOnlyArray.of(button("Settings"), button("Details")),
        "centerButtons", JavaOnlyArray.of(button("Home")),
        "customizationId", "Home",
        "overflowMenu", menu().apply { putString("testID", "More") },
        "verticalBarKeeps", "tabs",
        "background", JavaOnlyMap.of(
            "color", color(),
            "clipToBounds", true,
            "component", JavaOnlyMap.of(
                "id", "Backdrop",
                "name", "Backdrop",
                "componentId", "Backdrop",
                "waitForRender", true,
            ),
            "translucent", true,
            "blur", true,
        ),
        "scrollEdgeAppearance", JavaOnlyMap.of(
            "title", withFont(JavaOnlyMap.of("color", color())),
            "background", JavaOnlyMap.of("color", color(), "translucent", true),
            "active", true,
            "noBorder", true,
            "borderColor", color(),
        ),
        "barStyle", "black",
        "style", "browser",
        "noBorder", true,
        "searchBar", searchBar(),
        "largeTitle", withFont(
            JavaOnlyMap.of("visible", true, "displayMode", "always", "collapseOnScroll", true, "color", color()),
        ),
        "height", 64,
        "borderColor", color(),
        "borderHeight", 1,
        "elevation", 4,
        "topMargin", 8,
        "animateLeftButtons", true,
        "animateRightButtons", true,
    )

    fun title(): JavaOnlyMap = withFont(
        JavaOnlyMap.of(
            "text", "Home",
            "color", color(),
            "component", JavaOnlyMap.of(
                "id", "HomeTitle",
                "name", "Title",
                "componentId", "HomeTitle",
                "alignment", "fill",
                "waitForRender", true,
            ),
            "menu", menu(),
            "rename", true,
            "document", JavaOnlyMap.of("url", "file:///Home.txt"),
            "glass", "clear",
            "height", 44,
            "topMargin", 4,
            "alignment", "center",
        ),
    )

    fun backButton(): JavaOnlyMap = withFont(
        JavaOnlyMap.of(
            "accessibilityLabel", "Back",
            "id", "back",
            "icon", "ic_back",
            "visible", true,
            "title", "Home",
            "showTitle", true,
            "color", color(),
            "iconBackground", iconBackground(),
            "testID", "Back",
            "enableMenu", true,
            "displayMode", "minimal",
            "hideSharedBackground", true,
            "popStackOnPress", true,
        ),
    )

    fun button(id: String): JavaOnlyMap = withFont(
        JavaOnlyMap.of(
            "id", id,
            "allCaps", true,
            "icon", JavaOnlyMap.of("uri", "ic_$id", "width", 24, "height", 24, "scale", 2),
            "iconInsets", JavaOnlyMap.of("top", 1, "left", 2, "bottom", 3, "right", 4),
            "component", JavaOnlyMap.of(
                "id", "${id}Button",
                "name", "Badge",
                "componentId", "${id}Button",
                "width", 40,
                "height", 40,
            ),
            "systemItem", "add",
            "text", id,
            "accessibilityLabel", id,
            "enabled", true,
            "disableIconTint", true,
            "color", color(),
            "disabledColor", color(),
            "iconBackground", iconBackground(),
            "style", "prominent",
            "testID", id,
            "hideSharedBackground", true,
            "showText", true,
            "group", "Main",
            "badge", 3,
            "badgeColor", color(),
            "badgeTextColor", color(),
            "toggle", true,
            "selected", true,
            "menu", menu(),
            "menuOnLongPress", true,
            "customization", "movable",
            "shownByDefault", true,
            "pinned", true,
            "priority", "high",
            "vertical", true,
        ),
    )

    fun iconBackground(): JavaOnlyMap = JavaOnlyMap.of(
        "color", color(),
        "disabledColor", color(),
        "cornerRadius", 8,
        "width", 32,
        "height", 32,
    )

    fun menu(): JavaOnlyMap = JavaOnlyMap.of(
        "title", "More",
        "singleSelection", true,
        "items", JavaOnlyArray.of(
            JavaOnlyMap.of(
                "id", "Profile",
                "title", "Profile",
                "icon", "ic_profile",
                "destructive", true,
                "disabled", true,
                "checked", true,
                "testID", "Profile",
            ),
            JavaOnlyMap.of(
                "title", "Settings",
                "icon", "ic_settings",
                "singleSelection", true,
                "items", JavaOnlyArray.of(JavaOnlyMap.of("id", "Details", "title", "Details")),
                "testID", "Settings",
            ),
        ),
    )

    fun searchBar(): JavaOnlyMap = JavaOnlyMap.of(
        "visible", true,
        "focus", true,
        "hideOnScroll", true,
        "hideTopBarOnFocus", true,
        "obscuresBackgroundDuringPresentation", true,
        "backgroundColor", color(),
        "tintColor", color(),
        "placeholder", "Search",
        "text", "Profile",
        "placement", "integrated",
        "scopes", JavaOnlyArray.of(JavaOnlyMap.of("id", "all", "title", "All", "testID", "All")),
        "selectedScope", "all",
        "scopeBarActivation", "onTextEntry",
        "tokens", JavaOnlyArray.of(
            JavaOnlyMap.of("id", "token", "text", "Token", "icon", "ic_token", "testID", "Token"),
        ),
        "suggestions", JavaOnlyArray.of(
            JavaOnlyMap.of(
                "id", "recent",
                "title", "Recent",
                "accessibilityLabel", "Recent",
                "icon", "ic_recent",
                "testID", "Recent",
            ),
        ),
        "resultsComponent", JavaOnlyMap.of("id", "Results", "name", "Results", "componentId", "Results"),
        "resultsActivation", "onTextEntry",
        "testID", "Search",
    )

    fun statusBar(): JavaOnlyMap = JavaOnlyMap.of(
        "visible", true,
        "style", "dark",
        "backgroundColor", color(),
        "drawBehind", true,
        "translucent", true,
        "animate", true,
        "hideWithTopBar", true,
        "blur", true,
    )

    fun navigationBar(): JavaOnlyMap = JavaOnlyMap.of("backgroundColor", color(), "visible", true, "drawBehind", true)

    fun layout(): JavaOnlyMap = JavaOnlyMap.of(
        "safeAreaEdges", JavaOnlyArray.of("top", "bottom"),
        "backgroundColor", color(),
        "componentBackgroundColor", color(),
        "orientation", JavaOnlyArray.of("portrait", "landscape"),
        "verticalBars", true,
        "drawBehindVerticalBar", true,
        "autoHideHomeIndicator", true,
        "deferSystemGestures", JavaOnlyArray.of("bottom"),
        "adjustResize", true,
        "direction", "rtl",
    )

    fun modal(): JavaOnlyMap = JavaOnlyMap.of(
        "sheet", JavaOnlyMap.of(
            "detents", JavaOnlyArray.of("medium", "large", 300.0),
            "selectedDetent", "large",
            "largestUndimmedDetent", "medium",
            "grabber", true,
            "expandsOnScroll", true,
            "cornerRadius", 24,
            "testID", "Sheet",
        ),
        "contentSize", JavaOnlyMap.of("width", 320, "height", "content"),
        "popover", JavaOnlyMap.of(
            "sourceButtonId", "Settings",
            "sourceNativeID", "SettingsView",
            "arrowDirections", JavaOnlyArray.of("up", "down"),
            "adaptive", true,
        ),
        "testID", "Modal",
        "swipeToDismiss", true,
    )

    fun sideMenu(): JavaOnlyMap = JavaOnlyMap.of(
        "left", side("Left"),
        "right", side("Right"),
        "openGestureMode", "bezel",
        "animationType", "door",
        "testID", "Menu",
    )

    fun side(testID: String): JavaOnlyMap = JavaOnlyMap.of(
        "visible", true,
        "animate", true,
        "enabled", true,
        "width", 280,
        "height", 600,
        "shouldStretchDrawer", true,
        "animationVelocity", 2500,
        "openMode", "pushContent",
        "glass", true,
        "testID", testID,
    )

    fun splitView(): JavaOnlyMap = JavaOnlyMap.of(
        "displayMode", "oneBesideSecondary",
        "primaryEdge", "leading",
        "width", 320,
        "minWidth", 280,
        "maxWidth", 400,
        "primaryBackgroundStyle", "sidebar",
        "supplementary", JavaOnlyMap.of("width", 300, "minWidth", 250, "maxWidth", 350),
        "inspector", JavaOnlyMap.of("width", 300, "minWidth", 250, "maxWidth", 350, "visible", true),
        "splitBehavior", "tile",
        "displayModeButton", "always",
        "presentsWithGesture", true,
        "showsSecondaryOnlyButton", true,
        "compactTopColumn", "secondary",
        "testID", "Split",
    )

    fun bottomTabs(): JavaOnlyMap = JavaOnlyMap.of(
        "visible", true,
        "animate", true,
        "switchAnimation", "none",
        "currentTabIndex", 1,
        "currentTabId", "Profile",
        "testID", "Tabs",
        "accessibilityLabel", "Tabs",
        "moreTestID", "More",
        "drawBehind", true,
        "backgroundColor", color(),
        "tabsAttachMode", "afterInitialTab",
        "barStyle", "black",
        "layoutStyle", "compact",
        "cornerRadius", 16,
        "bottomMargin", 8,
        "translucent", true,
        "hideShadow", true,
        "titleDisplayMode", "showWhenActive",
        "elevation", 4,
        "hideOnScroll", true,
        "borderColor", color(),
        "borderWidth", 1,
        "shadow", JavaOnlyMap.of("opacity", 0.5, "color", color(), "radius", 4),
        "customRow", JavaOnlyMap.of(
            "height", 64,
            "backgroundColor", color(),
            "backgroundEffect", "glass",
            "cornerRadius", 16,
            "horizontalMargin", 12,
            "bottomMargin", 8,
            "testID", "Row",
        ),
        "sidebar", "tabBar",
        "sidebarLayout", "overlap",
        "customizationId", "Tabs",
        "minimizeBehavior", "onScrollDown",
        "bottomAccessory", JavaOnlyMap.of(
            "component", JavaOnlyMap.of("id", "Player", "name", "Player", "componentId", "Player"),
            "visible", true,
            "testID", "Player",
        ),
    )

    fun bottomTab(): JavaOnlyMap = withFont(
        JavaOnlyMap.of(
            "dotIndicator", JavaOnlyMap.of("color", color(), "size", 8, "visible", true),
            "component", JavaOnlyMap.of("name", "TabIcon", "componentId", "TabIcon"),
            "text", "Home",
            "badge", "3",
            "badgeColor", color(),
            "animateBadge", true,
            "testID", "HomeTab",
            "icon", "ic_home",
            "iconColor", color(),
            "iconWidth", 24,
            "iconHeight", 24,
            "textColor", color(),
            "selectedIconColor", color(),
            "selectedTextColor", color(),
            "selectedFontSize", 18,
            "iconInsets", JavaOnlyMap.of("top", 1, "left", 2, "bottom", 3, "right", 4),
            "selectedIcon", "ic_home_selected",
            "disableIconTint", true,
            "disableSelectedIconTint", true,
            "selectTabOnPress", true,
            "popToRootOnPress", true,
            "popToRootOnBlur", true,
            "role", "search",
            "customization", "pinned",
            "shownByDefault", true,
            "allowsHiding", true,
        ),
    )

    fun toolbar(): JavaOnlyMap = JavaOnlyMap.of(
        "visible", true,
        // A list of groups is read item by item, so each item has every field: the space and the search field are
        // read as buttons with a `systemItem` and have a test of their own.
        "buttons", JavaOnlyArray.of(button("Home"), button("Search").apply { putString("systemItem", "searchField") }),
        "testID", "Toolbar",
    )

    fun scrollEdgeEffect(): JavaOnlyMap = JavaOnlyMap.of(
        "hidden", true,
        "style", "soft",
        "top", scrollEdge(),
        "bottom", scrollEdge(),
        "left", scrollEdge(),
        "right", scrollEdge(),
    )

    fun scrollEdge(): JavaOnlyMap = JavaOnlyMap.of("hidden", true, "style", "hard")

    fun fab(): JavaOnlyMap = fabItem("Add").apply {
        putArray("actions", JavaOnlyArray.of(fabItem("Compose")))
    }

    fun fabItem(id: String): JavaOnlyMap = JavaOnlyMap.of(
        "id", id,
        "testID", id,
        "backgroundColor", color(),
        "clickColor", color(),
        "rippleColor", color(),
        "visible", true,
        "icon", "ic_$id",
        "iconColor", color(),
        "alignHorizontally", "left",
        "hideOnScroll", true,
        "size", "mini",
        "scrimColor", color(),
    )

    fun preview(): JavaOnlyMap = JavaOnlyMap.of(
        "nativeID", "Preview",
        "commit", true,
        "actions", JavaOnlyArray.of(
            previewItem("share").apply { putArray("actions", JavaOnlyArray.of(previewItem("copy"))) },
        ),
        "width", 320,
        "height", 480,
    )

    fun previewItem(id: String): JavaOnlyMap = JavaOnlyMap.of("id", id, "title", id, "style", "destructive")

    fun animations(): JavaOnlyMap = JavaOnlyMap.of(
        "push", stackAnimation().apply { putMap("zoom", zoom()) },
        "pop", stackAnimation().apply { putBoolean("preview", false) },
        "setStackRoot", stackAnimation().apply { putBoolean("asPush", true) },
        "setRoot", JavaOnlyMap.of("enter", screenAnimation(), "exit", screenAnimation()),
        "showModal", modalAnimation(),
        "dismissModal", modalAnimation(),
    )

    // The curve of a spring with the fields of every type: the reader takes the fields of all of them.
    fun interpolation(): JavaOnlyMap = JavaOnlyMap.of(
        "type", "spring",
        "factor", 2,
        "tension", 1,
        "mass", 1,
        "damping", 20,
        "stiffness", 300,
        "allowsOverdamping", true,
        "initialVelocity", 0.5,
    )

    fun propertyAnimation(): JavaOnlyMap = JavaOnlyMap.of(
        "from", 0,
        "to", 1,
        "duration", 300,
        "startDelay", 10,
        "interpolation", interpolation(),
    )

    private val viewProperties = listOf(
        "x", "y", "translationX", "translationY", "alpha", "scaleX", "scaleY", "rotationX", "rotationY", "rotation",
    )

    fun viewAnimation(): JavaOnlyMap = JavaOnlyMap().also { map ->
        viewProperties.forEach { map.putMap(it, propertyAnimation()) }
    }

    fun screenAnimation(): JavaOnlyMap = viewAnimation().apply {
        putBoolean("enabled", true)
        putBoolean("waitForRender", true)
    }

    fun barAnimation(): JavaOnlyMap = viewAnimation().apply { putBoolean("enabled", true) }

    fun elementTransition(id: String): JavaOnlyMap = JavaOnlyMap.of("id", id).also { map ->
        viewProperties.forEach { map.putMap(it, propertyAnimation()) }
    }

    fun zoom(): JavaOnlyMap = JavaOnlyMap.of("fromId", "image1", "enabled", true)

    fun stackAnimation(): JavaOnlyMap = JavaOnlyMap.of(
        "enabled", true,
        "waitForRender", true,
        "content", JavaOnlyMap.of("enter", screenAnimation(), "exit", screenAnimation()),
        "topBar", JavaOnlyMap.of("enter", barAnimation(), "exit", barAnimation()),
        "bottomTabs", JavaOnlyMap.of("enter", barAnimation(), "exit", barAnimation()),
        "sharedElementTransitions", sharedElements(),
        "elementTransitions", elementTransitions(),
    )

    fun modalAnimation(): JavaOnlyMap = JavaOnlyMap.of(
        "enter", screenAnimation(),
        "exit", screenAnimation(),
        "sharedElementTransitions", sharedElements(),
        "elementTransitions", elementTransitions(),
        "zoom", zoom(),
    )

    fun sharedElements(): JavaOnlyArray = JavaOnlyArray.of(
        JavaOnlyMap.of(
            "fromId", "image1",
            "toId", "image2",
            "duration", 300,
            "startDelay", 10,
            "interpolation", interpolation(),
        ),
    )

    fun elementTransitions(): JavaOnlyArray = JavaOnlyArray.of(elementTransition("title"))
}

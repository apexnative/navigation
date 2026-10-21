package dev.apexnative.navigation.options

import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The groups of the rest of the tree: every interface of `src/interfaces/options/` has one, and they read as the
 * spec says.
 */
class TreeGroupsTest {
    private fun read(vararg keysAndValues: Any?): Options {
        val result = OptionsReader.read(JavaOnlyMap.of(*keysAndValues), null)
        assertNull(result.unreadablePath)
        return result.options
    }

    @Test
    fun everyInterfaceOfTheTypesHasAGroup() {
        val names = mutableSetOf<String>()
        val full = OptionsReader.read(OptionsFixture.full(), null).options
        OptionsTree.containers(full) { _, group -> names += group.javaClass.simpleName }
        val expected = setOf(
            "TopBarOptions", "TitleOptions", "TitleComponentOptions", "TitleDocumentOptions", "SubtitleOptions",
            "BackButtonOptions", "ButtonOptions", "ButtonComponentOptions", "IconInsetsOptions",
            "IconBackgroundOptions", "SearchBarOptions", "LargeTitleOptions", "TopBarBackgroundOptions",
            "TopBarBackgroundComponentOptions", "TopBarScrollEdgeAppearanceOptions",
            "ScrollEdgeAppearanceTitleOptions", "ScrollEdgeAppearanceBackgroundOptions", "ToolbarOptions",
            "ScrollEdgeEffectOptions", "ScrollEdgeOptions", "BottomTabsOptions", "ShadowOptions",
            "BottomTabsCustomRowOptions", "BottomTabsAccessoryOptions", "ComponentOptions", "BottomTabOptions",
            "DotIndicatorOptions", "BottomTabComponentOptions", "ModalOptions", "SheetOptions",
            "ModalContentSizeOptions", "ModalPopoverOptions", "OverlayOptions", "SideMenuOptions",
            "SideMenuSideOptions", "SplitViewGroupOptions", "SplitViewColumnOptions", "SplitViewInspectorOptions",
            "LayoutOptions", "StatusBarOptions", "NavigationBarOptions", "AnimationsOptions",
            "StackPushAnimationOptions", "StackAnimationOptions", "SetStackRootAnimationOptions",
            "EnterExitAnimationOptions", "TopBarEnterExitAnimationOptions", "BottomTabsEnterExitAnimationOptions",
            "ModalAnimationOptions", "ScreenAnimationOptions", "TopBarAnimationOptions",
            "BottomTabsAnimationOptions", "PropertyAnimationOptions", "InterpolationOptions", "SharedElements",
            "SharedElementTransitionOptions", "ElementTransitions", "ElementTransitionOptions",
            "ElementAnimationOptions", "ZoomTransitionOptions", "HardwareBackButtonOptions", "FabOptions",
            "KeyCommandOptions", "PreviewOptions", "PreviewMenuItemOptions", "WindowOptions",
        )
        assertEquals(emptySet<String>(), expected - names)
    }

    @Test
    fun aToolbarHoldsButtonsSpacesAndTheSearchField() {
        val items = JavaOnlyArray.of(
            JavaOnlyMap.of("id", "Home", "systemItem", "add"),
            JavaOnlyMap.of("systemItem", "flexibleSpace"),
            JavaOnlyMap.of("systemItem", "fixedSpace"),
            JavaOnlyMap.of("systemItem", "searchField"),
            JavaOnlyMap.of("systemItem", "unknown"),
        )
        val toolbar = read("toolbar", JavaOnlyMap.of("buttons", items, "visible", true)).toolbar
        val buttons = requireNotNull(toolbar.buttons)
        val systemItems = buttons.take(4).map { it.systemItem.get() }
        assertEquals(listOf("add", "flexibleSpace", "fixedSpace", "searchField"), systemItems)
        assertFalse(buttons[4].systemItem.hasValue())
        assertEquals("Home", buttons[0].id.get())
        assertFalse(buttons[1].id.hasValue())
    }

    @Test
    fun aTopBarButtonDoesNotTakeTheSearchField() {
        val button = JavaOnlyMap.of("systemItem", "searchField")
        val bar = read("topBar", JavaOnlyMap.of("rightButtons", JavaOnlyArray.of(button)))
        assertFalse(requireNotNull(bar.topBar.buttons.right)[0].systemItem.hasValue())
    }

    @Test
    fun theContentSizeHeightIsANumberOrContent() {
        fun height(value: Any) =
            read("modal", JavaOnlyMap.of("contentSize", JavaOnlyMap.of("height", value))).modal.contentSize.height
        assertEquals("content", height("content").asText())
        assertEquals(300.0, requireNotNull(height(300).asNumber()), 0.0)
        assertFalse(height("fit").hasValue())
        assertFalse(height(true).hasValue())
    }

    @Test
    fun aTabBadgeTakesNull() {
        val tab = read("bottomTab", JavaOnlyMap.of("badge", null)).bottomTab
        assertTrue(tab.badge.isNone)
        val resolved = read("bottomTab", JavaOnlyMap.of("badge", null)).withDefaultOptions(
            read("bottomTab", JavaOnlyMap.of("badge", "3")),
        )
        assertTrue(resolved.bottomTab.badge.isNone)
    }

    @Test
    fun durationsBelowZeroAreUnset() {
        val animation = JavaOnlyMap.of(
            "elementTransitions", JavaOnlyArray.of(
                JavaOnlyMap.of("id", "title", "alpha", JavaOnlyMap.of("from", 0, "duration", -1, "startDelay", 5)),
            ),
        )
        val alpha = read("animations", JavaOnlyMap.of("push", animation))
            .animations.push.elementTransitions.transitions[0].alpha
        assertFalse(alpha.duration.hasValue())
        assertEquals(5.0, alpha.startDelay.get(), 0.0)
        assertEquals(0.0, alpha.from.get(), 0.0)
    }

    @Test
    fun anAnimationMergesItsOwnAndItsBaseFields() {
        val below = read(
            "animations", JavaOnlyMap.of(
                "push", JavaOnlyMap.of(
                    "enabled", true,
                    "sharedElementTransitions", JavaOnlyArray.of(JavaOnlyMap.of("fromId", "a", "toId", "b")),
                    "zoom", JavaOnlyMap.of("fromId", "a"),
                ),
            ),
        )
        val upper = read(
            "animations", JavaOnlyMap.of(
                "push", JavaOnlyMap.of(
                    "waitForRender", true,
                    "content", JavaOnlyMap.of("enter", JavaOnlyMap.of("alpha", JavaOnlyMap.of("to", 1))),
                    "zoom", JavaOnlyMap.of("enabled", true),
                ),
            ),
        )
        below.mergeWith(upper)
        val push = below.animations.push
        assertTrue(push.enabled.isTrue)
        assertTrue(push.waitForRender.isTrue)
        assertEquals(1.0, push.content.enter.alpha.to.get(), 0.0)
        assertEquals("a", push.zoom.fromId.get())
        assertTrue(push.zoom.enabled.isTrue)
        assertEquals(listOf("b"), push.sharedElements.get().map { it.toId.get() })
        assertEquals(1, push.copy().sharedElements.get().size)
    }

    @Test
    fun aZoomTakesNull() {
        val push = JavaOnlyMap.of("zoom", JavaOnlyMap.of("fromId", null))
        assertTrue(read("animations", JavaOnlyMap.of("push", push)).animations.push.zoom.fromId.isNone)
    }

    @Test
    fun moduleEnumsTakeTheirValuesOnly() {
        val bar = read(
            "topBar", JavaOnlyMap.of(
                "searchBar", JavaOnlyMap.of(
                    "placement", "sideways",
                    "scopeBarActivation", "later",
                    "resultsActivation", "x",
                ),
                "largeTitle", JavaOnlyMap.of("displayMode", "huge"),
            ),
            "bottomTabs", JavaOnlyMap.of("customRow", JavaOnlyMap.of("backgroundEffect", "paper")),
        )
        assertFalse(bar.topBar.searchBar.placement.hasValue())
        assertFalse(bar.topBar.searchBar.scopeBarActivation.hasValue())
        assertFalse(bar.topBar.searchBar.resultsActivation.hasValue())
        assertFalse(bar.topBar.largeTitle.displayMode.hasValue())
        assertFalse(bar.bottomTabs.customRow.backgroundEffect.hasValue())
        val valid = read(
            "topBar", JavaOnlyMap.of(
                "searchBar", JavaOnlyMap.of(
                    "placement", "integratedCentered",
                    "scopeBarActivation", "onSearchActivation",
                ),
                "largeTitle", JavaOnlyMap.of("displayMode", "inline"),
            ),
            "bottomTabs", JavaOnlyMap.of("customRow", JavaOnlyMap.of("backgroundEffect", "blur")),
        )
        assertEquals("integratedCentered", valid.topBar.searchBar.placement.get())
        assertEquals("onSearchActivation", valid.topBar.searchBar.scopeBarActivation.get())
        assertEquals("inline", valid.topBar.largeTitle.displayMode.get())
        assertEquals("blur", valid.bottomTabs.customRow.backgroundEffect.get())
    }

    @Test
    fun moduleColorsMergeSideBySide() {
        val light = JavaOnlyMap.of("light", 1)
        val below = read(
            "topBar", JavaOnlyMap.of(
                "searchBar", JavaOnlyMap.of(
                    "backgroundColor", OptionsFixture.color(),
                    "tintColor", OptionsFixture.color(),
                ),
                "largeTitle", JavaOnlyMap.of("color", OptionsFixture.color()),
            ),
            "bottomTabs", JavaOnlyMap.of("customRow", JavaOnlyMap.of("backgroundColor", OptionsFixture.color())),
        )
        val upper = read(
            "topBar", JavaOnlyMap.of(
                "searchBar", JavaOnlyMap.of("backgroundColor", light, "tintColor", light),
                "largeTitle", JavaOnlyMap.of("color", light),
            ),
            "bottomTabs", JavaOnlyMap.of("customRow", JavaOnlyMap.of("backgroundColor", light)),
        )
        val defaulted = upper.copy().withDefaultOptions(below)
        below.mergeWith(upper)
        for (merged in listOf(below, defaulted)) {
            for (color in listOf(
                merged.topBar.searchBar.backgroundColor,
                merged.topBar.searchBar.tintColor,
                merged.topBar.largeTitle.color,
                merged.bottomTabs.customRow.backgroundColor,
            )) {
                assertEquals(1, color.get(false, null))
                assertEquals(OptionsFixture.DARK, color.get(true, null))
            }
        }
    }
}

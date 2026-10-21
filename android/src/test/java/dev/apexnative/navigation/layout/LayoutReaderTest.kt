package dev.apexnative.navigation.layout

import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.layout.Layouts.center
import dev.apexnative.navigation.layout.Layouts.left
import dev.apexnative.navigation.layout.Layouts.menu
import dev.apexnative.navigation.layout.Layouts.native
import dev.apexnative.navigation.layout.Layouts.node
import dev.apexnative.navigation.layout.Layouts.pair
import dev.apexnative.navigation.layout.Layouts.right
import dev.apexnative.navigation.layout.Layouts.root
import dev.apexnative.navigation.layout.Layouts.screen
import dev.apexnative.navigation.layout.Layouts.split
import dev.apexnative.navigation.layout.Layouts.stack
import dev.apexnative.navigation.layout.Layouts.tabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LayoutReaderTest {
    // The native screens the app registered.
    private val registered: (String) -> Boolean = { it == "Profile" }

    private fun read(map: ReadableMap?) = LayoutReader.node(map, registered)

    private fun rejection(read: () -> Unit): String {
        try {
            read()
        } catch (e: LayoutException) {
            return e.text
        }
        fail("the layout was accepted")
        return ""
    }

    private fun rejectionOf(map: ReadableMap?) = rejection { read(map) }

    // Every node: type and id.

    @Test
    fun unknownType() {
        assertEquals("the layout has a node of unknown type 'TopTabs'", rejectionOf(node("TopTabs", "Tabs1")))
    }

    @Test
    fun missingType() {
        val map = JavaOnlyMap.of("id", "Component1", "data", JavaOnlyMap.of("name", "Home"))
        assertEquals("the layout has a node of unknown type ''", rejectionOf(map))
    }

    @Test
    fun typeOfAnotherKind() {
        assertEquals("the layout has a node of unknown type ''", rejectionOf(JavaOnlyMap.of("id", "A", "type", 3)))
    }

    @Test
    fun childThatIsNotANode() {
        assertEquals("the layout has a node of unknown type ''", rejectionOf(stack("Stack1", null)))
    }

    @Test
    fun missingId() {
        val map = JavaOnlyMap.of("type", "Stack", "children", JavaOnlyArray.of(screen("Component1")))
        assertEquals("the layout has a node of type 'Stack' without an id", rejectionOf(map))
    }

    @Test
    fun emptyId() {
        assertEquals("the layout has a node of type 'Component' without an id", rejectionOf(screen("")))
    }

    @Test
    fun idOfAnotherKind() {
        val map = JavaOnlyMap.of("id", 12, "type", "Component", "data", JavaOnlyMap.of("name", "Home"))
        assertEquals("the layout has a node of type 'Component' without an id", rejectionOf(map))
    }

    // Screens.

    @Test
    fun screenWithoutName() {
        assertEquals(
            "the layout has a node 'Component1' of type 'Component' without a name",
            rejectionOf(node("Component", "Component1")),
        )
    }

    @Test
    fun screenWithEmptyName() {
        assertEquals(
            "the layout has a node 'Component1' of type 'Component' without a name",
            rejectionOf(screen("Component1", name = "")),
        )
    }

    @Test
    fun dataOfAnotherKindCountsAsMissing() {
        val map = JavaOnlyMap.of("id", "Component1", "type", "Component", "data", "Home")
        assertEquals("the layout has a node 'Component1' of type 'Component' without a name", rejectionOf(map))
    }

    @Test
    fun nativeScreenWithoutName() {
        assertEquals(
            "the layout has a node 'ExternalComponent1' of type 'ExternalComponent' without a name",
            rejectionOf(node("ExternalComponent", "ExternalComponent1")),
        )
    }

    @Test
    fun nativeScreenNotRegistered() {
        assertEquals(
            "no native screen is registered as 'Settings'",
            rejectionOf(native("ExternalComponent1", "Settings")),
        )
    }

    // Stacks.

    @Test
    fun emptyStack() {
        assertEquals("the stack 'Stack1' has no screens", rejectionOf(stack("Stack1")))
    }

    @Test
    fun stackWithoutChildrenField() {
        assertEquals("the stack 'Stack1' has no screens", rejectionOf(JavaOnlyMap.of("id", "Stack1", "type", "Stack")))
    }

    @Test
    fun stackInStack() {
        assertEquals(
            "the stack 'Stack1' takes screens, not a 'Stack'",
            rejectionOf(stack("Stack1", stack("Stack2", screen("Component3")))),
        )
    }

    @Test
    fun containersInStack() {
        assertEquals(
            "the stack 'Stack1' takes screens, not a 'BottomTabs'",
            rejectionOf(stack("Stack1", tabs("Tabs", screen("Component3")))),
        )
        assertEquals(
            "the stack 'Stack1' takes screens, not a 'SideMenuRoot'",
            rejectionOf(stack("Stack1", menu("Menu", center("Center", screen("Component3"))))),
        )
        assertEquals(
            "the stack 'Stack1' takes screens, not a 'SplitView'",
            rejectionOf(stack("Stack1", split("Split", null, screen("A"), screen("B")))),
        )
        assertEquals(
            "the stack 'Stack1' takes screens, not a 'SideMenuLeft'",
            rejectionOf(stack("Stack1", left("Left", screen("A")))),
        )
    }

    // Tabs.

    @Test
    fun emptyTabs() {
        assertEquals("the tabs 'Tabs' have no tabs", rejectionOf(tabs("Tabs")))
    }

    @Test
    fun tabsInTabs() {
        assertEquals(
            "the tabs 'Tabs' have a tab of type 'BottomTabs': tabs don't go inside tabs",
            rejectionOf(tabs("Tabs", tabs("Inner", screen("A")))),
        )
    }

    @Test
    fun tooManyTabs() {
        val six = (1..6).map { screen("Component$it") }.toTypedArray()
        assertEquals("the tabs 'Tabs' have 6 tabs: Android shows at most 5", rejectionOf(tabs("Tabs", *six)))
    }

    @Test
    fun fiveTabs() {
        val five = (1..5).map { stack("Stack$it", screen("Component$it")) }.toTypedArray()
        assertEquals(5, read(tabs("Tabs", *five)).children.size)
    }

    // Side menus.

    @Test
    fun sideMenuChildOfAnotherType() {
        assertEquals(
            "the side menu 'Menu' has a child of type 'Stack': it takes SideMenuLeft, SideMenuCenter and SideMenuRight",
            rejectionOf(menu("Menu", stack("Stack1", screen("A")))),
        )
    }

    @Test
    fun sideMenuWithoutCenter() {
        assertEquals(
            "the side menu 'Menu' takes one SideMenuCenter and at most one SideMenuLeft and one SideMenuRight",
            rejectionOf(menu("Menu", left("Left", screen("A")))),
        )
    }

    @Test
    fun sideMenuWithTwoSidesOfOneKind() {
        val text = "the side menu 'Menu' takes one SideMenuCenter and at most one SideMenuLeft and one SideMenuRight"
        assertEquals(
            text,
            rejectionOf(menu("Menu", left("Left1", screen("A")), left("Left2", screen("B")), center("C", screen("D")))),
        )
        assertEquals(
            text,
            rejectionOf(menu("Menu", center("Center1", screen("A")), center("Center2", screen("B")))),
        )
        assertEquals(
            text,
            rejectionOf(menu("Menu", center("C", screen("A")), right("R1", screen("B")), right("R2", screen("D")))),
        )
    }

    @Test
    fun sideWithoutChild() {
        assertEquals(
            "the side 'Center' of the side menu 'Menu' needs exactly one child",
            rejectionOf(menu("Menu", center("Center"))),
        )
    }

    @Test
    fun sideWithTwoChildren() {
        assertEquals(
            "the side 'Left' of the side menu 'Menu' needs exactly one child",
            rejectionOf(menu("Menu", left("Left", screen("A"), screen("B")), center("Center", screen("C")))),
        )
    }

    @Test
    fun sideMenuInSide() {
        assertEquals(
            "the side menu 'Menu' has a side menu in a side: a side menu doesn't go inside a side menu",
            rejectionOf(menu("Menu", center("Center", menu("Inner", center("InnerCenter", screen("A")))))),
        )
    }

    @Test
    fun sideOutsideMenu() {
        val text = "the layout has a node 'Left' of type 'SideMenuLeft' outside a side menu"
        assertEquals(text, rejectionOf(left("Left", screen("A"))))
        assertEquals(text, rejectionOf(tabs("Tabs", left("Left", screen("A")))))
        assertEquals(text, rejectionOf(split("Split", null, left("Left", screen("A")), screen("B"))))
        assertEquals(
            "the layout has a node 'Center' of type 'SideMenuCenter' outside a side menu",
            rejectionOf(menu("Menu", left("Left", center("Center", screen("A"))))),
        )
    }

    @Test
    fun sideOutsideMenuInModalsOverlaysAndStackScreens() {
        val side = right("Right", screen("A"))
        val text = "the layout has a node 'Right' of type 'SideMenuRight' outside a side menu"
        assertEquals(text, rejection { LayoutReader.root(root(screen("Home"), modals = listOf(side)), registered) })
        assertEquals(text, rejection { LayoutReader.root(root(screen("Home"), overlays = listOf(side)), registered) })
        assertEquals(text, rejection { LayoutReader.nodes(JavaOnlyArray.of(side), registered) })
    }

    // Split views.

    @Test
    fun splitViewWithoutSecondary() {
        assertEquals(
            "the split view 'Split' needs its primary and its secondary column",
            rejectionOf(split("Split", listOf("primary", "inspector"), stack("A", screen("A1")), screen("B"))),
        )
    }

    @Test
    fun splitViewWithoutColumnsAndOneChild() {
        assertEquals(
            "the split view 'Split' needs its primary and its secondary column",
            rejectionOf(split("Split", null, stack("A", screen("A1")))),
        )
    }

    @Test
    fun splitViewWithUnknownColumnNames() {
        assertEquals(
            "the split view 'Split' needs its primary and its secondary column",
            rejectionOf(split("Split", listOf("master", "detail"), screen("A"), screen("B"))),
        )
    }

    @Test
    fun splitViewInColumn() {
        assertEquals(
            "the split view 'Split' has a column of type 'SplitView': a split view doesn't go inside a split view",
            rejectionOf(
                split(
                    "Split",
                    listOf("primary", "secondary"),
                    screen("A"),
                    split("Inner", null, screen("B"), screen("C")),
                ),
            ),
        )
    }

    @Test
    fun splitViewInAChildWithoutColumnIsNotAColumn() {
        val node = read(
            split(
                "Split",
                listOf("primary", "secondary"),
                screen("A"),
                screen("B"),
                split("Inner", null, screen("C"), screen("D")),
            ),
        )
        assertEquals(listOf("primary", "secondary"), node.columnChildren().keys.toList())
        assertEquals(3, node.children.size)
    }

    // Pairs.

    @Test
    fun pairWithOneScreen() {
        assertEquals(
            "the pair 'Arrangement1' needs two screens: its primary and its secondary",
            rejectionOf(pair("Arrangement1", screen("A"))),
        )
    }

    @Test
    fun pairWithThreeScreens() {
        assertEquals(
            "the pair 'Arrangement1' needs two screens: its primary and its secondary",
            rejectionOf(pair("Arrangement1", screen("A"), screen("B"), screen("C"))),
        )
    }

    @Test
    fun pairWithAStack() {
        assertEquals(
            "the pair 'Arrangement1' needs two screens: its primary and its secondary",
            rejectionOf(pair("Arrangement1", screen("A"), stack("Stack1", screen("B")))),
        )
    }

    @Test
    fun pairInPair() {
        assertEquals(
            "the pair 'Arrangement1' needs two screens: its primary and its secondary",
            rejectionOf(pair("Arrangement1", screen("A"), pair("Arrangement2", screen("B"), screen("C")))),
        )
    }

    // Ids.

    @Test
    fun idGivenTwiceInATree() {
        assertEquals(
            "the layout gives the id 'Home' to two nodes",
            rejectionOf(stack("Stack1", screen("Home"), screen("Home"))),
        )
    }

    @Test
    fun containerAndScreenWithOneId() {
        assertEquals("the layout gives the id 'Main' to two nodes", rejectionOf(stack("Main", screen("Main"))))
    }

    @Test
    fun sideIdsCount() {
        assertEquals(
            "the layout gives the id 'Left' to two nodes",
            rejectionOf(menu("Menu", left("Left", screen("A")), center("Center", screen("Left")))),
        )
    }

    @Test
    fun idGivenTwiceAcrossTheLayersOfSetRoot() {
        assertEquals(
            "the layout gives the id 'Home' to two nodes",
            rejection { LayoutReader.root(root(screen("Home"), modals = listOf(screen("Home"))), registered) },
        )
        assertEquals(
            "the layout gives the id 'Login' to two nodes",
            rejection {
                LayoutReader.root(
                    root(screen("Home"), modals = listOf(screen("Login")), overlays = listOf(screen("Login"))),
                    registered,
                )
            },
        )
    }

    @Test
    fun idGivenTwiceAcrossTheScreensOfSetStackRoot() {
        assertEquals(
            "the layout gives the id 'Home' to two nodes",
            rejection { LayoutReader.nodes(JavaOnlyArray.of(screen("Home"), screen("Home")), registered) },
        )
    }

    @Test
    fun separateCommandsMayUseOneId() {
        read(screen("Home"))
        read(screen("Home"))
    }

    // setRoot.

    @Test
    fun setRootWithoutRoot() {
        assertEquals("the layout of setRoot has no root", rejection { LayoutReader.root(root(null), registered) })
        assertEquals("the layout of setRoot has no root", rejection { LayoutReader.root(null, registered) })
    }

    @Test
    fun setRootWithRootOfAnotherKind() {
        val map = JavaOnlyMap.of("root", "Home")
        assertEquals("the layout of setRoot has no root", rejection { LayoutReader.root(map, registered) })
    }

    @Test
    fun firstErrorWins() {
        // The stack comes before the tabs in the tree: its error is the one.
        val map = tabs("Tabs", stack("Stack1"), tabs("Inner", screen("A")))
        assertEquals("the stack 'Stack1' has no screens", rejectionOf(map))
    }

    // Valid layouts.

    @Test
    fun screen() {
        val options = JavaOnlyMap.of("topBar", JavaOnlyMap.of("visible", false))
        val node = read(screen("Component1", "Home", options, key = "Home:1"))
        assertEquals("Component1", node.id)
        assertEquals(LayoutType.Component, node.type)
        assertEquals("Home", node.name)
        assertEquals("Home", node.screenName)
        assertTrue(node.options.topBar.visible.isFalse)
        assertEquals("Home:1", node.key)
        assertTrue(node.isScreen)
        assertTrue(node.children.isEmpty())
    }

    @Test
    fun slotScreen() {
        val node = read(screen("Component1", "slot:Profile"))
        assertEquals("slot:Profile", node.name)
        assertEquals("Profile", node.screenName)
        assertTrue(node.isSlot)
    }

    @Test
    fun reactScreenPropsAreNotRead() {
        val props = JavaOnlyMap.of("user", "x")
        val data = JavaOnlyMap.of("name", "Home", "passProps", props, "functions", JavaOnlyArray.of("f"))
        val node = read(node("Component", "Component1", data))
        assertNull(node.passProps)
        assertTrue(node.functions.isEmpty())
    }

    @Test
    fun screenChildrenAreNotRead() {
        val node = read(node("Component", "Component1", JavaOnlyMap.of("name", "Home"), JavaOnlyMap.of("x", 1)))
        assertTrue(node.children.isEmpty())
    }

    @Test
    fun nativeScreen() {
        val props = JavaOnlyMap.of("user", "Ann", "count", 2)
        val node = read(native("ExternalComponent1", "Profile", props, listOf("onSave", "onClose")))
        assertEquals(LayoutType.ExternalComponent, node.type)
        assertEquals("Profile", node.name)
        assertEquals(props, node.passProps)
        assertEquals(listOf("onSave", "onClose"), node.functions)
        assertTrue(node.isScreen)
    }

    @Test
    fun nativeScreenWithoutPropsAndFunctions() {
        val node = read(native("ExternalComponent1", "Profile"))
        assertNull(node.passProps)
        assertTrue(node.functions.isEmpty())
    }

    @Test
    fun idsAsGiven() {
        assertEquals("my screen #1", read(screen("my screen #1")).id)
    }

    @Test
    fun extraFieldsAreTolerated() {
        val map = screen("Component1")
        map.putString("extra", "x")
        (map.getMap("data") as JavaOnlyMap).putInt("unknown", 3)
        assertEquals("Component1", read(map).id)
    }

    @Test
    fun stackOfScreensAndPairs() {
        val node = read(
            stack(
                "Stack1",
                screen("A"),
                native("B", "Profile"),
                pair("Arrangement1", screen("C"), native("D", "Profile")),
            ),
        )
        assertEquals(LayoutType.Stack, node.type)
        assertEquals(
            listOf(LayoutType.Component, LayoutType.ExternalComponent, LayoutType.Arrangement),
            node.children.map { it.type },
        )
    }

    @Test
    fun pair() {
        val node = read(pair("Arrangement1", screen("A"), screen("B")))
        assertEquals("Details", node.name)
        assertEquals(ArrangementData("overlay", "vertical", 0.3, "top", "pair"), node.arrangement)
        assertEquals(listOf("A", "B"), node.children.map { it.id })
    }

    @Test
    fun pairDataOfAnotherKindIsNone() {
        val data = JavaOnlyMap.of("name", "Details", "style", 1, "fraction", "half")
        val node = read(node("Arrangement", "Arrangement1", data, screen("A"), screen("B")))
        assertEquals(ArrangementData(null, null, null, null, null), node.arrangement)
    }

    @Test
    fun tabsOfEveryKind() {
        val node = read(
            tabs(
                "Tabs",
                stack("Stack1", screen("A")),
                screen("B"),
                native("C", "Profile"),
                menu("Menu", center("Center", screen("D"))),
                pair("Arrangement1", screen("E"), screen("F")),
            ),
        )
        assertEquals(5, node.children.size)
    }

    @Test
    fun splitViewInTabs() {
        read(tabs("Tabs", split("Split", null, stack("A", screen("A1")), stack("B", screen("B1")))))
    }

    @Test
    fun sideMenu() {
        val node = read(
            menu(
                "Menu",
                left("Left", screen("Settings")),
                center("Center", tabs("Tabs", stack("Stack1", screen("Home")))),
                right("Right", split("Split", null, screen("A"), screen("B"))),
            ),
        )
        assertEquals(LayoutType.SideMenuRoot, node.type)
        assertEquals(listOf("Left", "Center", "Right"), node.children.map { it.id })
        assertTrue(node.children.all { it.isSideMenuSide })
        assertEquals("Settings", node.children[0].children.single().id)
    }

    @Test
    fun sideMenuWithCenterOnly() {
        read(menu("Menu", center("Center", stack("Stack1", screen("Home")))))
    }

    @Test
    fun splitViewColumns() {
        val node = read(
            split(
                "Split",
                listOf("primary", "supplementary", "secondary", "compact", "inspector"),
                stack("P", screen("P1")),
                stack("S", screen("S1")),
                stack("D", screen("D1")),
                stack("C", screen("C1")),
                screen("I"),
            ),
        )
        assertEquals(listOf("primary", "supplementary", "secondary", "compact", "inspector"), node.columns)
        val columns = node.columnChildren()
        assertEquals("P", columns.getValue("primary").id)
        assertEquals("S", columns.getValue("supplementary").id)
        assertEquals("D", columns.getValue("secondary").id)
        assertEquals("C", columns.getValue("compact").id)
        assertEquals("I", columns.getValue("inspector").id)
    }

    @Test
    fun splitViewWithoutColumnsTakesPrimaryAndSecondary() {
        val node = read(split("Split", null, stack("A", screen("A1")), stack("B", screen("B1")), screen("C")))
        assertTrue(node.columns.isEmpty())
        assertEquals(mapOf("primary" to "A", "secondary" to "B"), node.columnChildren().mapValues { it.value.id })
    }

    @Test
    fun splitViewIgnoresUnknownColumnsAndChildrenWithoutOne() {
        val node = read(
            split(
                "Split",
                listOf("secondary", "sidebar", "primary"),
                screen("A"),
                screen("B"),
                screen("C"),
                screen("D"),
            ),
        )
        assertEquals(mapOf("secondary" to "A", "primary" to "C"), node.columnChildren().mapValues { it.value.id })
    }

    @Test
    fun rootWithModalsAndOverlays() {
        val layout = LayoutReader.root(
            root(stack("Stack1", screen("Home")), listOf(screen("Login")), listOf(screen("Banner"))),
            registered,
        )
        assertEquals("Stack1", layout.root.id)
        assertEquals(listOf("Login"), layout.modals.map { it.id })
        assertEquals(listOf("Banner"), layout.overlays.map { it.id })
    }

    @Test
    fun rootWithoutModalsAndOverlays() {
        val layout = LayoutReader.root(root(screen("Home")), registered)
        assertTrue(layout.modals.isEmpty())
        assertTrue(layout.overlays.isEmpty())
    }

    @Test
    fun screensOfSetStackRoot() {
        val nodes = LayoutReader.nodes(JavaOnlyArray.of(screen("Home"), screen("Details")), registered)
        assertEquals(listOf("Home", "Details"), nodes.map { it.id })
        assertTrue(LayoutReader.nodes(JavaOnlyArray(), registered).isEmpty())
    }

    @Test
    fun allNodesDepthFirst() {
        val node = read(tabs("Tabs", stack("Stack1", screen("A"), screen("B")), screen("C")))
        assertEquals(listOf("Tabs", "Stack1", "A", "B", "C"), node.allNodes().map { it.id })
    }

    @Test
    fun idOf() {
        assertEquals("Component1", LayoutReader.idOf(screen("Component1")))
        assertEquals("", LayoutReader.idOf(null))
        assertEquals("", LayoutReader.idOf(JavaOnlyMap.of("id", 3)))
    }

    @Test
    fun typeNames() {
        LayoutType.entries.forEach { assertEquals(it, LayoutType.fromName(it.jsName)) }
        assertNull(LayoutType.fromName("TopTabs"))
        assertNull(LayoutType.fromName(null))
    }

    // One order on both platforms: the first error of each layout below is the same text on iOS.

    private val foo = JavaOnlyMap.of("type", "Foo")

    private fun unnamed(id: String) = node("Component", id)

    @Test
    fun orderPlaceBeforeTheChildren() {
        assertEquals(
            "the stack 'S' takes screens, not a 'Stack'",
            rejectionOf(stack("S", stack("S2", foo))),
        )
    }

    @Test
    fun orderRootBeforeModals() {
        assertEquals(
            "the stack 'S' has no screens",
            rejection { LayoutReader.root(root(stack("S"), modals = listOf(foo)), registered) },
        )
    }

    @Test
    fun orderEarlierChildFirst() {
        assertEquals(
            "the stack 'Stack1' has no screens",
            rejectionOf(tabs("Tabs", stack("Stack1"), tabs("Inner", screen("A")))),
        )
    }

    @Test
    fun orderUnnamedScreenBeforeALaterContainer() {
        assertEquals(
            "the layout has a node 'A' of type 'Component' without a name",
            rejectionOf(stack("S", unnamed("A"), tabs("T", screen("B")))),
        )
    }

    @Test
    fun orderUnnamedScreenOfAPairBeforeItsStack() {
        assertEquals(
            "the layout has a node 'A' of type 'Component' without a name",
            rejectionOf(pair("P", unnamed("A"), stack("S", screen("B")))),
        )
    }

    @Test
    fun orderSideChildCountBeforeTheSides() {
        assertEquals(
            "the side 'C2' of the side menu 'M' needs exactly one child",
            rejectionOf(menu("M", center("C1", screen("A")), center("C2"))),
        )
    }

    @Test
    fun orderChildrenBeforeTheSides() {
        assertEquals(
            "the layout has a node 'A' of type 'Component' without a name",
            rejectionOf(menu("M", left("L", unnamed("A")))),
        )
    }

    @Test
    fun orderSideChildCountBeforeALaterChild() {
        assertEquals(
            "the side 'L' of the side menu 'M' needs exactly one child",
            rejectionOf(menu("M", left("L"), stack("S", screen("A")))),
        )
    }

    @Test
    fun orderColumnChildrenBeforeTheColumns() {
        assertEquals(
            "the stack 'S' has no screens",
            rejectionOf(split("V", listOf("primary"), stack("S"))),
        )
    }

    @Test
    fun orderPlaceBeforeDuplicateId() {
        assertEquals(
            "the layout has a node 'A' of type 'SideMenuLeft' outside a side menu",
            rejection {
                LayoutReader.root(root(screen("A"), modals = listOf(left("A", screen("B")))), registered)
            },
        )
    }

    @Test
    fun setStackRootEntriesInOrder() {
        assertEquals(
            "the layout has a node 'A' of type 'Component' without a name",
            rejection { LayoutReader.nodes(JavaOnlyArray.of(unnamed("A"), foo), registered) },
        )
    }

    // `columns`.

    @Test
    fun emptyColumnsTakePrimaryAndSecondary() {
        val node = read(split("V", emptyList(), screen("A"), screen("B")))
        assertEquals(mapOf("primary" to "A", "secondary" to "B"), node.columnChildren().mapValues { it.value.id })
    }

    @Test
    fun columnsOfAnotherKindTakePrimaryAndSecondary() {
        val data = JavaOnlyMap.of("columns", "primary")
        val node = read(node("SplitView", "V", data, screen("A"), screen("B")))
        assertEquals(mapOf("primary" to "A", "secondary" to "B"), node.columnChildren().mapValues { it.value.id })
    }

    @Test
    fun columnsShorterThanTheChildren() {
        val node = read(split("V", listOf("secondary", "primary"), screen("A"), screen("B"), screen("C")))
        assertEquals(mapOf("secondary" to "A", "primary" to "B"), node.columnChildren().mapValues { it.value.id })
        assertEquals(3, node.children.size)
    }

    @Test
    fun columnThatIsNotAString() {
        val data = JavaOnlyMap.of("columns", JavaOnlyArray.of("primary", 2, "secondary"))
        val node = read(node("SplitView", "V", data, screen("A"), screen("B"), screen("C")))
        assertEquals(listOf("primary", "", "secondary"), node.columns)
        assertEquals(mapOf("primary" to "A", "secondary" to "C"), node.columnChildren().mapValues { it.value.id })
    }

    @Test
    fun firstChildOfAColumnWins() {
        val node = read(split("V", listOf("primary", "primary", "secondary"), screen("A"), screen("B"), screen("C")))
        assertEquals(mapOf("primary" to "A", "secondary" to "C"), node.columnChildren().mapValues { it.value.id })
    }

    // Options: read once the whole layout passed its checks, depth first.

    private fun unreadable(): JavaOnlyMap =
        JavaOnlyMap.of("topBar", JavaOnlyMap.of("title", JavaOnlyMap.of("text", Any())))

    @Test
    fun everyNodeGetsItsOptions() {
        val stackOptions = JavaOnlyMap.of("topBar", JavaOnlyMap.of("visible", true))
        val stack = node("Stack", "Stack1", JavaOnlyMap.of("options", stackOptions), screen("A"))
        val tabs = read(tabs("Tabs", stack, screen("B", options = JavaOnlyMap.of("layout", JavaOnlyMap()))))
        assertTrue(tabs.children[0].options.topBar.visible.isTrue)
        assertFalse(tabs.children[0].children[0].options.topBar.visible.hasValue())
        assertFalse(tabs.options.topBar.visible.hasValue())
    }

    @Test
    fun anUnreadableOptionRejects() {
        assertEquals(
            "the option 'topBar.title.text' of the screen 'A' has a value the navigation can't read",
            rejectionOf(stack("Stack1", screen("A", options = unreadable()))),
        )
    }

    @Test
    fun theChecksComeBeforeTheOptions() {
        val map = tabs("Tabs", stack("Stack1", screen("A", options = unreadable())), stack("Stack2"))
        assertEquals("the stack 'Stack2' has no screens", rejectionOf(map))
    }

    @Test
    fun theFirstUnreadableOptionInLayoutOrder() {
        val data = JavaOnlyMap.of("options", unreadable())
        val stack = node("Stack", "Stack1", data, screen("A", options = unreadable()))
        val map = root(screen("B"), modals = listOf(stack))
        assertEquals(
            "the option 'topBar.title.text' of the screen 'Stack1' has a value the navigation can't read",
            rejection { LayoutReader.root(map, registered) },
        )
    }
}

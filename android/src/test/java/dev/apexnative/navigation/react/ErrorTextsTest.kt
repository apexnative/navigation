package dev.apexnative.navigation.react

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every text of the table, word for word. */
class ErrorTextsTest {
    @Test
    fun screenTexts() {
        assertEquals("the screen 'Home' is not shown", ErrorTexts.screenNotShown("Home"))
        assertEquals("the screen 'Home' is not in a stack", ErrorTexts.notInStack("Home"))
        assertEquals("the screen 'Home' is the first screen of its stack", ErrorTexts.firstScreenOfStack("Home"))
        assertEquals("the screen 'Home' is not in this stack", ErrorTexts.notInThisStack("Home"))
        assertEquals("the screen 'Home' is not in a modal", ErrorTexts.notInModal("Home"))
        assertEquals("the screen 'Home' is not in an overlay", ErrorTexts.notInOverlay("Home"))
        assertEquals("the screen 'Home' is not in a split view", ErrorTexts.notInSplitView("Home"))
        assertEquals(
            "the screen 'Home' is neither in a split view nor in a stack",
            ErrorTexts.neitherSplitViewNorStack("Home"),
        )
        assertEquals(
            "the screen 'Home' shows already: an id goes to one screen",
            ErrorTexts.alreadyShown("Home"),
        )
    }

    @Test
    fun columnTexts() {
        assertEquals(
            "the split view of the screen 'Home' has no 'inspector' column",
            ErrorTexts.noSuchColumn("Home", "inspector"),
        )
        assertEquals(
            "the 'secondary' column of the split view of the screen 'Home' is a stack: it takes a screen, not a stack",
            ErrorTexts.columnIsStack("Home", "secondary"),
        )
        assertEquals(
            "the 'secondary' column of the split view of the screen 'Home' is not a stack",
            ErrorTexts.columnNotStack("Home", "secondary"),
        )
    }

    @Test
    fun commandTexts() {
        assertEquals(
            "there are no screens to put in the stack of the screen 'Home'",
            ErrorTexts.noScreensForStack("Home"),
        )
        assertEquals(
            "setRoot was replaced by a newer setRoot before the root 'Stack1' finished showing",
            ErrorTexts.setRootReplaced("Stack1"),
        )
        assertEquals("the root 'Stack1' was destroyed before it showed", ErrorTexts.rootDestroyed("Stack1"))
        assertEquals(
            "the modal 'Login' can't be dismissed: there is no root under it",
            ErrorTexts.modalWithoutRoot("Login"),
        )
        assertEquals(
            "the navigation has nowhere to show screens: the app has no activity or window",
            ErrorTexts.nowhereToShow(),
        )
        assertEquals("the preview 'Preview3' went without being opened", ErrorTexts.previewGone("Preview3"))
        assertEquals(
            "could not push the screen 'Details': it broke",
            ErrorTexts.couldNot("push the screen 'Details'", "it broke"),
        )
        assertEquals(
            "could not run 'push': the native core doesn't do it yet",
            ErrorTexts.notDoneYet("push"),
        )
    }

    @Test
    fun nativeScreenTexts() {
        assertEquals("no native screen is registered as 'Profile'", ErrorTexts.nativeScreenNotRegistered("Profile"))
        assertEquals(
            "the native screen 'Profile' could not be created: its creator returned nothing",
            ErrorTexts.nativeScreenNotCreated("Profile"),
        )
        assertEquals(
            "JavaScript reloaded before the call 'save' of the screen 'Profile' was answered",
            ErrorTexts.nativeCallReloaded("save", "Profile"),
        )
        assertEquals(
            "the navigation is not running: the call 'save' of the screen 'Profile' can't be answered",
            ErrorTexts.nativeCallNotRunning("save", "Profile"),
        )
    }

    @Test
    fun layoutTexts() {
        assertEquals("the layout has a node of unknown type 'TopTabs'", ErrorTexts.unknownNodeType("TopTabs"))
        assertEquals(
            "the layout has a node of type 'Component' without an id",
            ErrorTexts.nodeWithoutId("Component"),
        )
        assertEquals(
            "the side menu 'Menu' has a child of type 'Stack': it takes SideMenuLeft, SideMenuCenter and SideMenuRight",
            ErrorTexts.sideMenuChild("Menu", "Stack"),
        )
        assertEquals(
            "the stack 'Stack1' takes screens, not a 'BottomTabs'",
            ErrorTexts.stackTakesScreens("Stack1", "BottomTabs"),
        )
        assertEquals("the stack 'Stack1' has no screens", ErrorTexts.emptyStack("Stack1"))
        assertEquals("the tabs 'Tabs' have no tabs", ErrorTexts.emptyTabs("Tabs"))
        assertEquals("the tabs 'Tabs' have 6 tabs: Android shows at most 5", ErrorTexts.tooManyTabs("Tabs", 6))
        assertEquals(
            "the split view 'Split' needs its primary and its secondary column",
            ErrorTexts.splitViewColumns("Split"),
        )
        assertEquals(
            "the pair 'Arrangement1' needs iOS 27.1 and an app built with its SDK",
            ErrorTexts.pairUnsupported("Arrangement1"),
        )
        assertEquals(
            "the pair 'Arrangement1' needs two screens: its primary and its secondary",
            ErrorTexts.pairNeedsTwoScreens("Arrangement1"),
        )
        assertEquals(
            "the option 'topBar.title.color' of the screen 'Home' has a value the navigation can't read",
            ErrorTexts.unreadableOption("topBar.title.color", "Home"),
        )
        // The default options belong to no screen.
        assertEquals(
            "the option 'topBar.title.color' of the default options has a value the navigation can't read",
            ErrorTexts.unreadableDefaultOption("topBar.title.color"),
        )
    }

    @Test
    fun layoutTextsOfQ24() {
        assertEquals(
            "the layout has a node 'Home' of type 'Component' without a name",
            ErrorTexts.nodeWithoutName("Component", "Home"),
        )
        assertEquals("the layout of setRoot has no root", ErrorTexts.rootMissing())
        assertEquals(
            "the tabs 'Tabs' have a tab of type 'BottomTabs': tabs don't go inside tabs",
            ErrorTexts.tabsInTabs("Tabs"),
        )
        assertEquals(
            "the side menu 'Menu' takes one SideMenuCenter and at most one SideMenuLeft and one SideMenuRight",
            ErrorTexts.sideMenuSides("Menu"),
        )
        assertEquals(
            "the side 'Left' of the side menu 'Menu' needs exactly one child",
            ErrorTexts.sideChildCount("Left", "Menu"),
        )
        assertEquals(
            "the side menu 'Menu' has a side menu in a side: a side menu doesn't go inside a side menu",
            ErrorTexts.sideMenuInSide("Menu"),
        )
        assertEquals(
            "the layout has a node 'Left' of type 'SideMenuLeft' outside a side menu",
            ErrorTexts.sideOutsideMenu("Left", "SideMenuLeft"),
        )
        assertEquals(
            "the split view 'Split' has a column of type 'SplitView': a split view doesn't go inside a split view",
            ErrorTexts.splitViewInColumn("Split"),
        )
        assertEquals("the layout gives the id 'Home' to two nodes", ErrorTexts.idGivenTwice("Home"))
    }
}

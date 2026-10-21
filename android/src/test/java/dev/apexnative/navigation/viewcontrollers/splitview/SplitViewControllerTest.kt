package dev.apexnative.navigation.viewcontrollers.splitview

import android.app.Activity
import dev.apexnative.navigation.layout.LayoutNode
import dev.apexnative.navigation.layout.LayoutType
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.SplitViewOptions
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventEmitter
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.navigator.LayoutFactory
import dev.apexnative.navigation.viewcontrollers.parent.ShowsColumns
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestScreen
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The split view the core makes and gives its options to, without its view, which needs a device: the maker's columns,
 * a screen's merge of `splitView` into the split view's own options and on to the layouts around without it, the
 * repaint's resolved `splitView` with the defaults, and the children shown before the view measures.
 */
class SplitViewControllerTest {
    private val fixture = DispatcherFixture()
    private val activity = Activity()
    private val registry = ChildControllersRegistry()

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() {
        fixture.uninstall()
        DefaultOptions.set(Options())
    }

    private fun node(type: LayoutType, id: String, columns: List<String>, vararg children: LayoutNode) = LayoutNode(
        id = id,
        type = type,
        name = if (type == LayoutType.Component) "Home" else null,
        options = Options(),
        key = null,
        passProps = null,
        functions = emptyList(),
        columns = columns,
        arrangement = null,
        children = children.toList(),
    )

    private fun screen(id: String) = node(LayoutType.Component, id, emptyList())

    // A split view of two screens of the unit tests, in a layout that keeps what its children merged.
    private inner class Made {
        val primary = TestScreen(activity, registry, "Home1")
        val secondary = TestScreen(activity, registry, "Details1")
        val presenter = SplitViewPresenter(activity)
        val split = SplitViewController(
            activity,
            registry,
            "Split1",
            presenter,
            Options().apply { splitView.width = Fraction(400.0) },
            SplitViewOptions(400.0, true, null),
            primary,
            secondary,
            null,
            EventEmitter(null),
        )
        val outer = Around()

        init {
            outer.add(split)
        }
    }

    private inner class Around : TestLayout(activity, registry, "Tabs1") {
        val merged = mutableListOf<Options>()

        override fun mergeChildOptions(options: Options, child: ViewController<*>) {
            merged += options
        }
    }

    @Test
    fun theFactoryMakesTheSplitViewOfItsAndroidColumns() {
        val factory = LayoutFactory(activity, registry)
        val layout = node(
            LayoutType.SplitView,
            "Split1",
            listOf("primary", "supplementary", "secondary"),
            screen("Home1"),
            screen("Settings1"),
            screen("Details1"),
        )
        assertTrue(factory.canMake(layout))
        val made = factory.create(layout)
        assertTrue(made is ShowsColumns)
        assertSame(made, registry.find("Split1"))
        assertSame(made, registry.find("Home1")?.parentController)
        assertSame(made, registry.find("Details1")?.parentController)
        // Android has no supplementary column: nothing of it is made.
        assertNull(registry.find("Settings1"))
        made.destroy()
        assertNull(registry.find("Home1"))
        assertNull(registry.find("Details1"))
    }

    @Test
    fun aScreensMergeOfSplitViewGoesIntoTheSplitViewAndTheRestGoesOn() {
        val made = Made()
        val merge = Options().apply {
            splitView.width = Fraction(420.0)
            statusBar.style = Text("dark")
        }
        made.primary.mergeOptions(merge)
        assertEquals(420.0, made.split.options.splitView.width.get(0.0), 0.0)
        val passed = made.outer.merged.single()
        assertFalse(passed.splitView.hasValue())
        assertEquals("dark", passed.statusBar.style.get(null))
    }

    @Test
    fun theRepaintGivesTheSplitViewsOwnSplitViewWithTheDefaults() {
        val made = Made()
        val given = mutableListOf<SplitViewOptions>()
        made.presenter.onSplitOptions = { given += it }
        made.secondary.mergeOptions(Options().apply { splitView.compactTopColumn = Text("secondary") })
        DefaultOptions.set(Options().apply {
            splitView.width = Fraction(300.0)
            splitView.presentsWithGesture = Bool(false)
        })
        made.presenter.onConfigurationChanged(made.split, Options())
        val split = given.single()
        // Its own width over the defaults', the defaults' gesture, a screen's merged column.
        assertEquals(400.0, split.width, 0.0)
        assertFalse(split.presentsWithGesture)
        assertEquals("secondary", split.compactTopColumn)
    }

    @Test
    fun beforeItsViewMeasuresTheSplitViewShowsAndWaitsForItsCurrentChild() {
        val made = Made()
        assertSame(made.secondary, made.split.currentChild)
        assertEquals(listOf(made.secondary), made.split.visibleChildren())
        assertEquals(listOf(made.secondary), made.split.waitedChildren())
    }
}

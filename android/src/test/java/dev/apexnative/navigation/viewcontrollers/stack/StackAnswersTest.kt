package dev.apexnative.navigation.viewcontrollers.stack

import android.app.Activity
import android.widget.FrameLayout
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.parent.OptionsScreen
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The one rule of the id a stack entry answers with (`StackController.answerIdOf`): `push` answers the entry's own id
 * (a pair's), every other command the screen it stands for (a pair's primary), the split view's `showInColumn` through
 * its replace too.
 */
class StackAnswersTest {
    private val h = StackQueueHarness()

    @Before
    fun install() = h.install()

    @After
    fun uninstall() = h.uninstall()

    // A pair as the core sees it: one entry whose options are its primary's; its view is never made.
    private class Pair(
        activity: Activity,
        registry: ChildControllersRegistry,
        id: String,
        private val primary: ViewController<*>,
        private val secondary: ViewController<*>,
    ) : ParentController<FrameLayout>(activity, registry, id, Presenter(activity, Options()), Options()),
        OptionsScreen {
        init {
            primary.parentController = this
            secondary.parentController = this
        }

        override val optionsScreen: ViewController<*>
            get() = primary

        override fun getChildControllers(): Collection<ViewController<*>> = listOf(primary, secondary)

        override fun getCurrentChild(): ViewController<*> = primary

        override fun createView(): FrameLayout = FrameLayout(activity)
    }

    private fun pair(id: String, primary: String, secondary: String) =
        Pair(h.activity, h.registry, id, h.screen(primary), h.screen(secondary))

    @Test
    fun setStackRootOfAPairAnswersItsPrimary() {
        h.start(h.screen("A"))
        h.setStackRoot(pair("Pair1", "Home1", "Details1"))
        h.display.finishAll()
        assertTrue(h.events().toString(), "setStackRoot1 resolve Home1" in h.events())
    }

    @Test
    fun pushOfAPairAnswersThePair() {
        h.start(h.screen("A"))
        h.push(pair("Pair1", "Home1", "Details1"))
        h.display.finishAll()
        assertTrue(h.events().toString(), "push1 resolve Pair1" in h.events())
    }

    @Test
    fun theSplitViewsReplaceOfAPairAnswersItsPrimary() {
        h.start(h.screen("A"))
        h.splitSetRoot(pair("Pair1", "Home1", "Details1"))
        h.display.finishAll()
        assertTrue(h.events().toString(), "listener success Home1" in h.events())
    }

    @Test
    fun popToRootOfAPairAnswersItsPrimary() {
        h.start(h.screen("A"))
        h.setStackRoot(pair("Pair1", "Home1", "Details1"), h.screen("B"))
        h.display.finishAll()
        h.popToRoot()
        h.display.finishAll()
        assertTrue(h.events().toString(), "popToRoot2 resolve Home1" in h.events())
    }
}

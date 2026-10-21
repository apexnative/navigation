package dev.apexnative.navigation.viewcontrollers.externalcomponent

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import dev.apexnative.navigation.layout.LayoutException
import dev.apexnative.navigation.nativescreen.NativeScreenCalls
import dev.apexnative.navigation.nativescreen.NativeScreenContext
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.react.CommandScope
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.ReadableJson
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventEmitter
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.views.externalcomponent.ExternalComponentLayout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A native screen: its context made with the node, its creator asked as the view is made — nothing made rejects the
 * command that makes it, and is only logged later —, a lone view in a frame, its appearance with its registration name,
 * `ANN.NativeScreenReleased` once and only to its JavaScript; the commands' side: new props for a native screen only,
 * `null` kept, a call answered once.
 */
class NativeScreenTest {
    private val fixture = DispatcherFixture()
    private val entries = fixture.journal.entries
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private var made = 0

    // The app's creator: a view each time it is asked.
    private val creator: (Activity, NativeScreenContext) -> View? = { activity, _ ->
        made++
        FrameLayout(activity)
    }

    private fun native(id: String, create: ((Activity, NativeScreenContext) -> View?)? = creator) =
        ExternalComponentViewController(
            activity,
            registry,
            id,
            "Profile",
            NativeScreenContext(id, JSONObject(), listOf("onSave"), EventEmitter(null)),
            Presenter(activity, Options()),
            Options(),
            { create },
        )

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() {
        fixture.uninstall()
        CommandScope.isRunning = false
    }

    @Test
    fun theContextIsMadeWithTheNodeAndTheCreatorWithTheView() {
        val profile = native("Profile1")
        val context = profile.context
        assertSame(context, NativeScreens.contextOf(profile))
        assertEquals(setOf("onSave"), context.functions)
        assertEquals(0, made)
        profile.view
        assertEquals(1, made)
        assertSame(context, profile.context)
    }

    @Test
    fun onlyANativeScreenTakesNewProps() {
        val profile = native("Profile1")
        val home = ComponentViewController(activity, registry, "Home2", "Home", Presenter(activity, Options()), Options())
        assertNull(NativeScreens.contextOf(home))
        assertNull(NativeScreens.contextOf(null))
        assertNull(NativeScreens.find("Nobody3"))
        profile.destroy()
        assertNull(NativeScreens.contextOf(profile))
        home.destroy()
    }

    @Test
    fun aCreatorThatMakesNothingRejectsTheCommandThatMakesTheView() {
        val profile = native("Profile1") { _, _ -> null }
        CommandScope.isRunning = true
        val error = assertThrows(LayoutException::class.java) { profile.view }
        assertEquals(ErrorTexts.nativeScreenNotCreated("Profile"), error.text)
        assertNull(profile.peekView())
        // JavaScript never got the screen: its node goes without ANN.NativeScreenReleased.
        profile.destroy()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun aScreenWithoutACreatorRejectsAsNotRegistered() {
        val profile = native("Profile1", create = null)
        CommandScope.isRunning = true
        val error = assertThrows(LayoutException::class.java) { profile.view }
        assertEquals(ErrorTexts.nativeScreenNotRegistered("Profile"), error.text)
    }

    @Test
    fun aViewMadeOutsideACommandIsOnlyLogged() {
        var asked = 0
        val profile = native("Profile1") { _, _ ->
            asked++
            null
        }
        assertFalse(CommandScope.isRunning)
        profile.view
        assertEquals(1, asked)
        // The screen is in the layout: it is released as it goes.
        profile.destroy()
        assertEquals(listOf("released:Profile1"), entries)
    }

    @Test
    fun aLoneViewIsWrappedAndAGroupIsNot() {
        val text = TextView(activity)
        val content = ExternalComponentLayout.contentOf(text)
        assertTrue(content is FrameLayout)
        assertNotSame(text, content)
        val group = FrameLayout(activity)
        assertSame(group, ExternalComponentLayout.contentOf(group))
    }

    @Test
    fun itAppearsWithItsNameAndIsReleasedOnce() {
        val profile = native("Profile1")
        profile.onViewWillAppear()
        profile.onViewDidAppear()
        profile.destroy()
        profile.destroy()
        assertEquals(
            listOf("will:Profile1", "didAppear:Profile1", "stateNeeded", "didDisappear:Profile1", "stateNeeded",
                "released:Profile1"),
            entries,
        )
        assertEquals("will:Profile1:Profile:Component", fixture.journal.payloads.first())
        assertNull(registry.find("Profile1"))
    }

    @Test
    fun aLaterScreenWithTheSameIdKeepsItsHook() {
        val first = native("Profile1")
        val later = native("Profile1")
        first.destroy()
        assertTrue(entries.isEmpty())
        assertSame(later, registry.find("Profile1"))
        later.destroy()
        assertEquals(listOf("released:Profile1"), entries)
    }

    @Test
    fun willAppearAloneSendsNoDidAppear() {
        val profile = native("Profile1")
        profile.onViewWillAppear()
        assertEquals(listOf("will:Profile1"), entries)
    }

    @Test
    fun aScreenOfAJavaScriptThatWentGoesSilently() {
        val profile = native("Profile1")
        ReloadCoordinator.advance()
        profile.destroy()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun aCallIsAnsweredOnce() {
        val results = mutableListOf<Result<Any?>>()
        val callId = NativeScreenCalls.add("Profile1", "onSave") { results += it }
        NativeScreenCalls.resolve(callId, JSONObject())
        NativeScreenCalls.resolve(callId, JSONObject())
        NativeScreenCalls.resolve("NativeScreenCall0", JSONObject())
        assertEquals(1, results.size)
        assertTrue(results.first().isSuccess)
    }

    @Test
    fun nullStaysAValue() {
        val update = JavaOnlyMap.of(
            "props",
            JavaOnlyMap.of("title", null, "count", 2.0, "tags", JavaOnlyArray.of("a", null)),
            "removed",
            JavaOnlyArray.of("subtitle"),
        )
        val plain = ReadableJson.plain(update)
        val props = plain["props"] as Map<*, *>
        assertTrue(props.containsKey("title"))
        assertNull(props["title"])
        assertEquals(2.0, props["count"])
        assertEquals(listOf("a", null), props["tags"])
        assertEquals(listOf("subtitle"), plain["removed"])
    }
}

package dev.apexnative.navigation.views.animations

import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import dev.apexnative.navigation.options.ElementTransitionOptions
import dev.apexnative.navigation.options.ElementTransitions
import dev.apexnative.navigation.options.EnterExitAnimationOptions
import dev.apexnative.navigation.options.ModalAnimationOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.OptionsReader
import dev.apexnative.navigation.options.ScreenAnimationOptions
import dev.apexnative.navigation.options.SharedElementTransitionOptions
import dev.apexnative.navigation.options.SharedElements
import dev.apexnative.navigation.options.StackAnimationOptions
import dev.apexnative.navigation.options.TopBarAnimationOptions
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.utils.DevelopmentLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The options of `animations`: the merge of a side (its properties as one set, its flags by key, copies), the rules of
 * what plays and what is own, the length of a transition, and the reader's warnings.
 */
class AnimationRulesTest {
    @After
    fun noListener() {
        DevelopmentLog.listener = null
    }

    private fun alphaFrom(value: Double) = ScreenAnimationOptions().apply { alpha.from = Fraction(value) }

    // The properties of a side are replaced whole when the upper layer has any; flags by key.

    @Test
    fun theUpperSidesPropertiesReplaceTheLowersWhole() {
        val lower = ScreenAnimationOptions().apply {
            alpha.from = Fraction(0.0)
            translationY.from = Fraction(100.0)
            enabled = Bool(true)
        }
        val upper = ScreenAnimationOptions().apply { scaleX.from = Fraction(0.5) }
        lower.mergeWith(upper)
        assertFalse(lower.alpha.hasValue())
        assertFalse(lower.translationY.hasValue())
        assertEquals(0.5, lower.scaleX.from.get(), 0.0)
        assertTrue(lower.enabled.isTrue)
        // A copy, not the upper layer's group: changing the result changes nothing above.
        assertNotSame(upper.scaleX, lower.scaleX)
        assertNotSame(upper.scaleX.interpolation, lower.scaleX.interpolation)
    }

    // An example: default `showModal.enter = { alpha }`, the screen's `{ waitForRender: true }`.
    @Test
    fun flagsAloneKeepTheDefaultsProperties() {
        val defaults = Options().apply { animations.showModal.enter.alpha.from = Fraction(0.0) }
        val own = Options().apply { animations.showModal.enter.waitForRender = Bool(true) }
        val resolved = own.copy().withDefaultOptions(defaults)
        assertEquals(0.0, resolved.animations.showModal.enter.alpha.from.get(), 0.0)
        assertTrue(resolved.animations.showModal.enter.waitForRender.isTrue)
        assertNotSame(defaults.animations.showModal.enter.alpha, resolved.animations.showModal.enter.alpha)
        // And merged over a layer that has the properties, the same.
        val merged = defaults.copy().mergeWith(own)
        assertEquals(0.0, merged.animations.showModal.enter.alpha.from.get(), 0.0)
        assertTrue(merged.animations.showModal.enter.waitForRender.isTrue)
        // A side of its own over the defaults' takes nothing of theirs.
        val other = Options().apply { animations.showModal.enter.translationY.from = Fraction(40.0) }
        val replaced = other.copy().withDefaultOptions(defaults)
        assertFalse(replaced.animations.showModal.enter.alpha.hasValue())
        assertEquals(40.0, replaced.animations.showModal.enter.translationY.from.get(), 0.0)
    }

    // `[]` replaces the lists of the layers under it.
    @Test
    fun anEmptyListClearsTheLowerOnes() {
        val lower = StackAnimationOptions().apply {
            sharedElements = SharedElements(listOf(SharedElementTransitionOptions(Text("a"), Text("b"))))
            elementTransitions = ElementTransitions(listOf(ElementTransitionOptions("c")))
        }
        val upper = StackAnimationOptions().apply {
            sharedElements = SharedElements(emptyList())
            elementTransitions = ElementTransitions(emptyList())
        }
        lower.mergeWith(upper)
        assertTrue(lower.sharedElements.hasValue())
        assertFalse(AnimationRules.hasElements(lower))
        assertFalse(AnimationRules.stackIsOwn(lower))
    }

    // A property plays with `from` or `to`; a side plays unless `enabled: false`.

    @Test
    fun whatPlays() {
        val durationOnly = ScreenAnimationOptions().apply { alpha.duration = Fraction(500.0) }
        assertFalse(AnimationRules.sidePlays(durationOnly))
        assertTrue(AnimationRules.sidePlays(alphaFrom(0.0)))
        assertFalse(AnimationRules.sidePlays(alphaFrom(0.0).apply { enabled = Bool(false) }))
        assertTrue(AnimationRules.isTurnedOff(TopBarAnimationOptions().apply { enabled = Bool(false) }))
        assertFalse(AnimationRules.sidePlays(null))
        assertTrue(AnimationRules.isSingleAlpha(alphaFrom(0.0)))
        assertFalse(AnimationRules.isSingleAlpha(alphaFrom(0.0).apply { scaleX.from = Fraction(0.8) }))
        assertFalse(AnimationRules.isSingleAlpha(ScreenAnimationOptions().apply { scaleX.from = Fraction(0.8) }))
    }

    @Test
    fun whatIsOwn() {
        // The bars never make a transition own.
        val bars = StackAnimationOptions().apply {
            topBar.enter.alpha.from = Fraction(0.0)
            bottomTabs.exit.translationY.to = Fraction(80.0)
        }
        assertFalse(AnimationRules.stackIsOwn(bars))
        // A side turned off is no side.
        val off = StackAnimationOptions().apply { content.enter = alphaFrom(0.0).apply { enabled = Bool(false) } }
        assertFalse(AnimationRules.stackIsOwn(off))
        // Elements alone make it own, and `content.exit` alone.
        val elements = StackAnimationOptions().apply {
            elementTransitions = ElementTransitions(listOf(ElementTransitionOptions("Avatar")))
        }
        assertTrue(AnimationRules.stackIsOwn(elements))
        assertFalse(AnimationRules.contentIsOwn(elements))
        assertTrue(AnimationRules.stackIsOwn(StackAnimationOptions().apply { content.exit = alphaFrom(1.0) }))
        // A modal: `enter`, `exit` or elements.
        assertTrue(AnimationRules.modalIsOwn(ModalAnimationOptions().apply { exit = alphaFrom(1.0) }))
        assertFalse(AnimationRules.modalIsOwn(ModalAnimationOptions().apply { enter.waitForRender = Bool(true) }))
        // The root: an `exit` from the defaults turned off doesn't play.
        val root = EnterExitAnimationOptions().apply {
            enter.enabled = Bool(false)
            exit = alphaFrom(1.0).apply { enabled = Bool(false) }
        }
        assertFalse(AnimationRules.rootPlays(root))
        assertTrue(AnimationRules.rootAtOnce(root))
        assertTrue(AnimationRules.rootPlays(EnterExitAnimationOptions().apply { exit = alphaFrom(1.0) }))
    }

    @Test
    fun theCommandsSwitches() {
        assertTrue(AnimationRules.showAnimated(ModalAnimationOptions().apply { enter.waitForRender = Bool(true) }))
        assertFalse(AnimationRules.showAnimated(ModalAnimationOptions().apply { enter.enabled = Bool(false) }))
        assertFalse(AnimationRules.dismissAnimated(ModalAnimationOptions().apply { exit.enabled = Bool(false) }))
    }

    // A side's length is the latest end of its playing properties, 300 ms without a duration.
    @Test
    fun theLengthOfASide() {
        val enter = ScreenAnimationOptions().apply {
            alpha.from = Fraction(0.0)
            translationY.from = Fraction(50.0)
            translationY.duration = Fraction(200.0)
            translationY.startDelay = Fraction(250.0)
            scaleX.duration = Fraction(2000.0)
        }
        assertEquals(450L, AnimationRules.lengthOf(enter))
        assertEquals(0L, AnimationRules.lengthOf(ScreenAnimationOptions()))
        assertEquals(0L, AnimationRules.durationOf(0.0, 300L))
        assertEquals(300L, AnimationRules.durationOf(-1.0, 300L))
        assertEquals(300L, AnimationRules.durationOf(Double.NaN, 300L))
    }

    // An unknown key of a side and a property without `from` and `to` are skipped with a warning.
    @Test
    fun theReaderWarnsAndSkips() {
        val warnings = mutableListOf<String>()
        DevelopmentLog.listener = { level, text -> if (level == 'w') warnings += text }
        val enter = JavaOnlyMap.of(
            "alpha", JavaOnlyMap.of("duration", 300),
            "translationY", JavaOnlyMap.of("from", 100),
            "bkgColor", JavaOnlyMap.of("from", 1),
            "waitForRender", true,
        )
        val topBarEnter = JavaOnlyMap.of("waitForRender", true, "enabled", false)
        val map = JavaOnlyMap.of("animations", JavaOnlyMap.of(
            "push", JavaOnlyMap.of(
                "content", JavaOnlyMap.of("enter", enter),
                "topBar", JavaOnlyMap.of("enter", topBarEnter),
                "elementTransitions", JavaOnlyArray.of(JavaOnlyMap.of("id", "Avatar", "alpha", JavaOnlyMap.of())),
            ),
        ))
        val options = OptionsReader.read(map, null).options
        assertEquals(
            listOf(
                "Animation of 'alpha' without 'from' and 'to': skipped",
                "Animation of 'bkgColor' is not supported: skipped",
                "Animation of 'waitForRender' is not supported: skipped",
            ),
            warnings,
        )
        val push = options.animations.push
        assertFalse(push.content.enter.alpha.hasValue())
        assertEquals(100.0, push.content.enter.translationY.from.get(), 0.0)
        assertTrue(push.content.enter.waitForRender.isTrue)
        assertTrue(push.topBar.enter.enabled.isFalse)
    }
}

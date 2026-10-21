package dev.apexnative.navigation.options.params

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import com.facebook.react.bridge.ColorPropConverter
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.OptionsReader
import dev.apexnative.navigation.options.has
import dev.apexnative.navigation.options.opt
import dev.apexnative.navigation.utils.DevelopmentLog
import java.util.Collections

/**
 * A color of the options as JavaScript gives it on Android: a pair `{ light, dark }`, one side for each mode of the
 * app. A side is a color (ARGB), `'NoColor'` (explicitly no color: the place looks as without the option), a platform
 * color (`PlatformColor`, its resource paths, resolved again at each [get] so that it follows the theme), or unset (the
 * layers below give it). [get] takes the side of the mode the app shows now. Immutable: merged colors are new objects,
 * and one may be shared between options.
 */
open class ThemeColour private constructor(
    private val context: Context?,
    private val lightSide: Side?,
    private val darkSide: Side?,
) {
    /** A pair of ARGB colors; null leaves a side unset. */
    constructor(light: Int?, dark: Int?) : this(null, light?.let(Side::Argb), dark?.let(Side::Argb))

    /** One side of a pair. */
    sealed class Side {
        /** A color, ARGB. Transparent (alpha 0) is an ordinary color. */
        data class Argb(val colour: Int) : Side()

        /** `'NoColor'`: the place looks as without the option. */
        object None : Side()

        /** `PlatformColor`: theme attributes (`?attr/…`) or color resources (`@color/…`); first found. */
        data class Platform(val paths: List<String>) : Side()
    }

    /** The options give the color: either side is set, `'NoColor'` included. */
    open fun hasValue(): Boolean = lightSide != null || darkSide != null

    /** The color of the current mode; [defaultValue] when that side is `'NoColor'` or unset. */
    open fun get(defaultValue: Int?): Int? = get(context, defaultValue)

    /**
     * The color of the current mode, platform colors resolved with [context] (an activity's theme); [defaultValue]
     * when that side is `'NoColor'`, unset, or a platform color that doesn't resolve.
     */
    open fun get(context: Context?, defaultValue: Int?): Int? {
        val themed = context ?: this.context
        val side = sideOf(isDark(themed))
        val colour = resolve(side, themed)
        if (colour == null && side is Side.Platform && themed != null) unresolved(side, themed)
        return colour ?: defaultValue
    }

    /**
     * The color of the current mode; transparent when that side has none ([hasColour]), which is the look of no color
     * where the option is a background. Never throws.
     */
    fun get(): Int = get(null) ?: Color.TRANSPARENT

    /** The side of the current mode gives a color. */
    fun hasColour(): Boolean = get(null) != null

    /** The side of the current mode is `'NoColor'`. */
    fun isNoColour(): Boolean = sideOf(isDark(context)) == Side.None

    /** The color of the [dark] or the light side; [defaultValue] as [get] gives it. */
    fun get(dark: Boolean, defaultValue: Int?): Int? = resolve(sideOf(dark), context) ?: defaultValue

    /** The side of the light or the [dark] mode as the options give it; null when unset. */
    fun sideOf(dark: Boolean): Side? = if (dark) darkSide else lightSide

    /**
     * This color with [other] over it, side by side: a side [other] doesn't set stays this one's. A new object; neither
     * changes.
     */
    fun mergeWith(other: ThemeColour): ThemeColour {
        if (!other.hasValue()) return this
        if (!hasValue() || (other.lightSide != null && other.darkSide != null)) return other
        return ThemeColour(other.context ?: context, other.lightSide ?: lightSide, other.darkSide ?: darkSide)
    }

    /** This color, its unset sides taken from [defaults]. A new object; neither changes. */
    fun withDefault(defaults: ThemeColour): ThemeColour = defaults.mergeWith(this)

    override fun equals(other: Any?): Boolean =
        other is ThemeColour && other.lightSide == lightSide && other.darkSide == darkSide

    override fun hashCode(): Int = 31 * (lightSide?.hashCode() ?: 0) + (darkSide?.hashCode() ?: 0)

    override fun toString(): String = "ThemeColour(light=$lightSide, dark=$darkSide)"

    companion object {
        private const val NO_COLOUR = "NoColor"
        private const val RESOURCE_PATHS = "resource_paths"
        private const val ATTRIBUTE_PREFIX = "?"
        private const val ATTR = "attr"
        private const val ANDROID = "android"
        // The platform colors already told of, so each is told once.
        private val told = Collections.synchronizedSet(HashSet<List<String>>())

        /**
         * The color of [key] in [parent], in any form JavaScript sends: `{ light, dark }`, a platform color
         * `{ resource_paths }` (both sides), a number (both sides), `'NoColor'` or `null` (no color, both sides).
         * Unset when the key is missing; a form it can't read is unset with a warning in development.
         */
        @JvmStatic
        fun parse(context: Context?, parent: ReadableMap?, key: String): ThemeColour {
            if (parent == null || !parent.has(key)) return NullThemeColour()
            val path = OptionsReader.pathOf(key)
            val app = context?.applicationContext ?: context
            return when (val value = parent.opt(key)) {
                null -> ThemeColour(app, Side.None, Side.None)
                is ReadableMap -> parseObject(app, value, path)
                else -> readSide(app, value, path)?.let { ThemeColour(app, it, it) } ?: NullThemeColour()
            }
        }

        /** The color of the object [map]: `{ light, dark }` or `{ resource_paths }`; unset for none. */
        @JvmStatic
        fun parse(context: Context?, map: ReadableMap?): ThemeColour {
            if (map == null) return NullThemeColour()
            return parseObject(context?.applicationContext ?: context, map, OptionsReader.pathOf("color"))
        }

        private fun parseObject(context: Context?, map: ReadableMap, path: String): ThemeColour {
            // An empty object is a pair whose sides JavaScript couldn't read.
            if (!map.has("light") && !map.has("dark") && map.keySetIterator().hasNextKey()) {
                val side = readSide(context, map, path) ?: return NullThemeColour()
                return ThemeColour(context, side, side)
            }
            val light = pairSide(context, map, "light", path)
            val dark = pairSide(context, map, "dark", path)
            if (light == null && dark == null) return NullThemeColour()
            return ThemeColour(context, light, dark)
        }

        // A side of a pair; a missing one is a color JavaScript couldn't read: unset, and a warning.
        private fun pairSide(context: Context?, map: ReadableMap, side: String, path: String): Side? {
            if (!map.has(side)) {
                warn(path, "its $side side is missing: JavaScript could not read the color")
                return null
            }
            return readSide(context, map.opt(side), "$path.$side")
        }

        // One side: a number, 'NoColor' (null too), a platform color, or another object React Native's converter
        // reads. What it can't read is unset, with a warning.
        private fun readSide(context: Context?, value: Any?, path: String): Side? = when (value) {
            null, NO_COLOUR -> Side.None
            is Double -> if (value.isFinite()) Side.Argb(value.toLong().toInt()) else unreadable(path, "$value")
            is ReadableMap -> objectSide(context, value, path)
            else -> unreadable(path, "$value")
        }

        private fun objectSide(context: Context?, map: ReadableMap, path: String): Side? {
            val paths = map.opt(RESOURCE_PATHS)
            if (paths is ReadableArray) {
                val names = (0 until paths.size()).mapNotNull { paths.opt(it) as? String }
                if (names.isEmpty()) return unreadable(path, "a platform color without paths")
                // Without a context (unit tests) the paths are kept as they are; they resolve at each get.
                if (context != null && names.none { exists(context, it) }) {
                    return unreadable(path, "none of the platform colors ${names.joinToString()} exists")
                }
                return Side.Platform(names)
            }
            if (context == null) return unreadable(path, "an object without a context to convert it")
            val colour = try {
                ColorPropConverter.getColor(map, context)
            } catch (e: Exception) {
                null
            }
            return colour?.let(Side::Argb) ?: unreadable(path, "an object React Native can't convert")
        }

        private fun unreadable(path: String, what: String): Side? {
            warn(path, "$what is not a color")
            return null
        }

        private fun warn(path: String, why: String) =
            DevelopmentLog.warn("the option '$path' has a color the navigation can't read: $why")

        // A platform color that resolves with no theme: the parse kept it for an activity's theme, and the activity's
        // theme doesn't set it either. Told in development, once; the app's own context alone tells nothing, as the
        // activity's theme may still set it.
        private fun unresolved(side: Side.Platform, context: Context) {
            if (context === context.applicationContext || !told.add(side.paths)) return
            DevelopmentLog.warn(
                "the platform color ${side.paths.joinToString()} resolves with neither the app's nor the activity's " +
                    "theme: the default color shows",
            )
        }

        // Whether the app has [path]: it resolves with [context] (the app's), or it is a theme attribute the app or
        // Android declares. An activity's own theme may set an attribute the app's theme doesn't; its value comes at
        // each get, with the theme of the activity.
        private fun exists(context: Context, path: String): Boolean {
            if (resolvePath(context, path) != null) return true
            val (owner, name) = attributeOf(path) ?: return false
            return try {
                val resources = context.resources
                resources.getIdentifier(name, ATTR, owner ?: context.packageName) != 0 ||
                    resources.getIdentifier(name, ATTR, ANDROID) != 0
            } catch (e: RuntimeException) {
                false
            }
        }

        /**
         * The package (none: the app's) and the name of a theme attribute's path — `?attr/name`, `?name`,
         * `?package:attr/name` —, as React Native's converter reads it; none for another path.
         */
        internal fun attributeOf(path: String): Pair<String?, String>? {
            if (!path.startsWith(ATTRIBUTE_PREFIX) || path.length <= ATTRIBUTE_PREFIX.length) return null
            val rest = path.substring(ATTRIBUTE_PREFIX.length).replace("$ATTR/", "")
            val tokens = rest.split(":")
            return if (tokens.size > 1) tokens[0] to tokens[1] else null to rest
        }

        // A platform color's path, resolved with the theme of [context]; none when it doesn't exist or can't be read.
        private fun resolvePath(context: Context, path: String): Int? = try {
            ColorPropConverter.resolveResourcePath(context, path)
        } catch (e: RuntimeException) {
            null
        }

        private fun resolve(side: Side?, context: Context?): Int? = when (side) {
            is Side.Argb -> side.colour
            is Side.Platform -> context?.let { c -> side.paths.firstNotNullOfOrNull { resolvePath(c, it) } }
            Side.None, null -> null
        }

        // The dark side when the app forces night mode, the light one when it forces day, else as the configuration.
        private fun isDark(context: Context?): Boolean = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                val mode = context?.resources?.configuration?.uiMode ?: 0
                (mode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
}

/** A color the options don't give. */
class NullThemeColour : ThemeColour(null, null) {
    override fun hasValue(): Boolean = false

    override fun get(defaultValue: Int?): Int? = defaultValue

    override fun get(context: Context?, defaultValue: Int?): Int? = defaultValue
}

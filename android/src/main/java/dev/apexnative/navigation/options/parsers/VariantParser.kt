package dev.apexnative.navigation.options.parsers

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.options.has
import dev.apexnative.navigation.options.opt
import dev.apexnative.navigation.options.params.NoneVariant
import dev.apexnative.navigation.options.params.NullVariant
import dev.apexnative.navigation.options.params.Variant

/**
 * Reads a value whose option takes several forms, with the check of the forms its type allows: [booleans] takes
 * `true` / `false`, [numbers] a finite number, [texts] the strings it names (null: any string), [noneOnNull] takes
 * `null` as "none" ([NoneVariant]). Any other value is unset.
 */
object VariantParser {
    @JvmStatic
    fun parse(
        map: ReadableMap?,
        key: String,
        booleans: Boolean = false,
        numbers: Boolean = false,
        texts: Set<String>? = emptySet(),
        noneOnNull: Boolean = false,
    ): Variant {
        if (map == null || !map.has(key)) return NullVariant()
        return when (val value = map.opt(key)) {
            null -> if (noneOnNull) NoneVariant() else NullVariant()
            is Boolean -> if (booleans) Variant(value) else NullVariant()
            is Double -> if (numbers && value.isFinite()) Variant(value) else NullVariant()
            is String -> if (texts == null || value in texts) Variant(value) else NullVariant()
            else -> NullVariant()
        }
    }
}

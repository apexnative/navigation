package dev.apexnative.navigation.options.params

import android.view.View

/** `layout.direction`: how a view lays out its children, from the left, from the right or as the language does. */
enum class LayoutDirection(private val direction: Int) {
    LTR(View.LAYOUT_DIRECTION_LTR),
    RTL(View.LAYOUT_DIRECTION_RTL),
    LOCALE(View.LAYOUT_DIRECTION_LOCALE),
    DEFAULT(View.LAYOUT_DIRECTION_INHERIT),
    ;

    /** The direction as [View.setLayoutDirection] takes it. */
    fun get(): Int = direction

    companion object {
        /** `ltr`, `rtl`, `locale`; anything else is [DEFAULT]. */
        @JvmStatic
        fun fromString(value: String?): LayoutDirection = when (value) {
            "ltr" -> LTR
            "rtl" -> RTL
            "locale" -> LOCALE
            else -> DEFAULT
        }
    }
}

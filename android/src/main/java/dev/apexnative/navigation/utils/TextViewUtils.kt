package dev.apexnative.navigation.utils

import android.widget.TextView

/** What texts show. */
object TextViewUtils {
    /** The size of the text of [view], in pixels; 0 without one. */
    @JvmStatic
    fun getTextSize(view: TextView): Float = view.textSize
}

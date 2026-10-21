package dev.apexnative.navigation.utils

import android.content.res.Resources
import kotlin.math.roundToInt

// The density of the device's screen: the navigation's views are all on it.
private val density: Float
    get() = Resources.getSystem().displayMetrics.density

/** This many dp in whole pixels. */
val Int.dp: Int
    get() = (this * density).roundToInt()

/** This many dp in whole pixels. */
val Float.dp: Int
    get() = (this * density).roundToInt()

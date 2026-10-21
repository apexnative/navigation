package dev.apexnative.navigation.views.bottomtabs

import android.graphics.drawable.Drawable

/** A tab of the bottom tabs: its title, icons and testID. */
data class BottomTabItem(
    val title: String,
    val icon: Drawable?,
    val selectedIcon: Drawable?,
    val testId: String?
) {
    fun hasIcon() = icon != null
}

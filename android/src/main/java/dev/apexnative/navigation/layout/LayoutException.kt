package dev.apexnative.navigation.layout

/** A layout the navigation can't show; [text] is the rejection of the command (`ErrorTexts`). */
internal class LayoutException(
    /** The rejection of the command. */
    val text: String,
) : RuntimeException(text)

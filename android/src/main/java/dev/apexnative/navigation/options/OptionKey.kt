package dev.apexnative.navigation.options

/**
 * The JSON key of an option field whose name is not that key (`topBar.backButton` is `TopBarButtons.back`), for the
 * paths of [OptionPaths]; also the flag that tells a value is given, which goes with it. A field without it answers to
 * its own name.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class OptionKey(val name: String)

/**
 * A group the reader takes from the object of the group that holds it, not from a key of its own (the font of a text,
 * the top bar's buttons): for the paths of [OptionPaths] its fields answer to the keys of that object.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReadFromParent

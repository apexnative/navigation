package dev.apexnative.navigation.options

import dev.apexnative.navigation.options.params.Param
import dev.apexnative.navigation.options.params.ThemeColour
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * The options at a JSON key path, the keys of the options joined with dots (`topBar.subtitle`, `bottomTab.badge`), as
 * the readers parse them: `clearOptions` takes them out of a node's own options. Generic over the whole tree, by
 * reflection on the fields of the groups: a field answers to the key of its name, or of its [OptionKey]; a group the
 * reader takes from the object of its parent ([ReadFromParent]: the font of a text, the top bar's buttons) answers with
 * its fields to the keys of that object. A group is a class with a `mergeWith` of its own class; anything else (a
 * value, a color, a list, a button menu) ends a path.
 */
internal object OptionPaths {
    /**
     * What a path names: the fields of [group] that answer to its last key, [members] (one, or a value and the flag
     * that tells it is given, as `modal.sheet.largestUndimmedDetent`).
     */
    class Place(val group: Any, val members: List<Field>) {
        /** The value at the path, of the first field. */
        val value: Any? get() = members.first().get(group)
    }

    // The fields of each class walked, its superclasses' first.
    private val fieldsOfType = ConcurrentHashMap<Class<*>, List<Field>>()

    /**
     * Takes the value at [path] out of [options], which it changes in place: the field gets the value a freshly made
     * group has for it (unset; a group, an empty one). Whether the path names a field; an empty or unknown path, or
     * one that goes on inside a value (a side of a color), changes nothing.
     */
    fun clear(options: Options, path: String): Boolean {
        val place = find(options, path) ?: return false
        val fresh = newGroup(place.group.javaClass) ?: return false
        place.members.forEach { it.set(place.group, it.get(fresh)) }
        return true
    }

    /** The fields [path] names in [options]; none for an empty or unknown path or one inside a value. */
    fun find(options: Options, path: String): Place? {
        val keys = path.split('.')
        if (keys.any(String::isEmpty)) return null
        var group: Any = options
        for (key in keys.dropLast(1)) {
            group = fieldOf(group, key)?.value?.takeIf(::isGroup) ?: return null
        }
        return fieldOf(group, keys.last())?.takeIf { newGroup(it.group.javaClass) != null }
    }

    // The fields of [group] that answer to [key]: its own, else those of a group read from the same object.
    private fun fieldOf(group: Any, key: String): Place? {
        val fields = fieldsOf(group.javaClass)
        fields.filter { !it.isAnnotationPresent(ReadFromParent::class.java) && keyOf(it) == key }
            .takeIf { it.isNotEmpty() }
            ?.let { return Place(group, it) }
        for (field in fields) {
            if (!field.isAnnotationPresent(ReadFromParent::class.java)) continue
            val inner = field.get(group)?.takeIf(::isGroup) ?: continue
            fieldOf(inner, key)?.let { return it }
        }
        return null
    }

    private fun keyOf(field: Field): String = field.getAnnotation(OptionKey::class.java)?.name ?: field.name

    private fun fieldsOf(type: Class<*>): List<Field> = fieldsOfType.getOrPut(type) {
        generateSequence(type) { it.superclass }
            .takeWhile { it != Any::class.java }
            .toList()
            .asReversed()
            .flatMap { it.declaredFields.asList() }
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .onEach { it.isAccessible = true }
    }

    private fun isGroup(value: Any): Boolean {
        if (value is Param<*> || value is ThemeColour) return false
        val type = value.javaClass
        return type.declaredMethods.any { it.name == "mergeWith" && it.parameterTypes.contentEquals(arrayOf(type)) }
    }

    // A group as its class makes it, every field unset; none for a class without a constructor of no arguments.
    private fun newGroup(type: Class<*>): Any? = try {
        type.getDeclaredConstructor().newInstance()
    } catch (_: ReflectiveOperationException) {
        null
    }
}

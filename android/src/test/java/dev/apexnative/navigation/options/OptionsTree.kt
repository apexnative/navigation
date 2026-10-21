package dev.apexnative.navigation.options

import dev.apexnative.navigation.options.params.Param
import dev.apexnative.navigation.options.params.ThemeColour
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

/**
 * Walks an options tree by reflection: every group reachable from [Options] (a class with `mergeWith` of its own
 * class), its fields, and the groups in lists of groups. Values ([Param], [ThemeColour], module values such as a
 * button menu or a sheet's detents) are leaves.
 */
object OptionsTree {
    /** A group of the options, as opposed to a value. */
    fun isGroup(value: Any?): Boolean {
        if (value == null || value is Param<*> || value is ThemeColour) return false
        val type = value.javaClass
        return type.declaredMethods.any { it.name == "mergeWith" && it.parameterTypes.contentEquals(arrayOf(type)) }
    }

    /** The fields of a group, its own and those of the groups it extends, readable. */
    fun fields(group: Any): List<Field> = generateSequence<Class<*>>(group.javaClass) { it.superclass }
        .takeWhile { it != Any::class.java }
        .toList()
        .asReversed()
        .flatMap { it.declaredFields.asList() }
        .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        .onEach { it.isAccessible = true }

    // A list of the class that holds it: a submenu of a preview's menu, the actions of a floating button.
    private fun isRecursiveList(field: Field): Boolean {
        val type = field.genericType as? ParameterizedType ?: return false
        return type.actualTypeArguments.singleOrNull() == field.declaringClass
    }

    /** A list whose items are groups. */
    fun isGroupList(value: Any?): Boolean = value is List<*> && value.isNotEmpty() && isGroup(value.first())

    /** Calls [visit] with the path and the value of every leaf under [group]. */
    fun leaves(group: Any, path: String = "", visit: (String, Any?) -> Unit) {
        for (field in fields(group)) {
            val value = field.get(group)
            // The list that holds the class itself has no last level to set: a leaf of the deepest item stays empty.
            if (isRecursiveList(field) && !isGroupList(value)) continue
            val fieldPath = if (path.isEmpty()) field.name else "$path.${field.name}"
            when {
                isGroup(value) -> leaves(requireNotNull(value), fieldPath, visit)
                isGroupList(value) -> (value as List<*>).forEachIndexed { index, item ->
                    leaves(requireNotNull(item), "$fieldPath[$index]", visit)
                }
                else -> visit(fieldPath, value)
            }
        }
    }

    /** Every group object and list of groups under [group], with its path; [group] itself included. */
    fun containers(group: Any, path: String = "", visit: (String, Any) -> Unit) {
        visit(path, group)
        for (field in fields(group)) {
            val value = field.get(group) ?: continue
            val fieldPath = if (path.isEmpty()) field.name else "$path.${field.name}"
            when {
                isGroup(value) -> containers(value, fieldPath, visit)
                isGroupList(value) -> {
                    visit(fieldPath, value)
                    (value as List<*>).forEachIndexed { index, item ->
                        containers(requireNotNull(item), "$fieldPath[$index]", visit)
                    }
                }
            }
        }
    }

    /** A leaf the options give: a set value, a true flag, a non-empty text or list, any other value. */
    fun hasValue(value: Any?): Boolean = when (value) {
        null -> false
        is Param<*> -> value.hasValue()
        is ThemeColour -> value.hasValue()
        is Boolean -> value
        is String -> value.isNotEmpty()
        is List<*> -> value.isNotEmpty()
        else -> true
    }

    /** The paths of the leaves under [group] without a value. */
    fun unset(group: Any): List<String> {
        val paths = mutableListOf<String>()
        leaves(group) { path, value -> if (!hasValue(value)) paths += path }
        return paths
    }

    /** The paths where [a] and [b] differ, leaf by leaf. */
    fun differences(a: Any, b: Any): List<String> {
        val left = LinkedHashMap<String, Any?>()
        val right = LinkedHashMap<String, Any?>()
        leaves(a) { path, value -> left[path] = comparable(value) }
        leaves(b) { path, value -> right[path] = comparable(value) }
        return (left.keys + right.keys).filter { left[it] != right[it] || (it in left) != (it in right) }
    }

    // A button menu has no equality of its own (its items are mutable): it compares by what it holds.
    private fun comparable(value: Any?): Any? = if (value is ButtonMenuOptions) describe(value) else value

    private fun describe(menu: ButtonMenuOptions): String =
        "menu(${menu.singleSelection}, ${menu.testID}, ${menu.items.joinToString { describe(it) }})"

    private fun describe(element: ButtonMenuOptions.Element): String = when (element) {
        is ButtonMenuOptions.Item -> "item(${element.id}, ${element.title}, ${element.icon}, ${element.destructive}, " +
            "${element.disabled}, ${element.checked}, ${element.testID})"
        is ButtonMenuOptions.Submenu -> "submenu(${element.title}, ${element.icon}, ${element.singleSelection}, " +
            "${element.testID}, ${element.items.joinToString { describe(it) }})"
    }

    /** The paths of the group objects and lists of groups that [a] and [b] share. */
    fun shared(a: Any, b: Any): List<String> {
        val ofB = java.util.IdentityHashMap<Any, String>()
        containers(b) { path, container -> ofB[container] = path }
        val paths = mutableListOf<String>()
        containers(a) { path, container -> if (ofB.containsKey(container)) paths += path.ifEmpty { "<root>" } }
        return paths
    }
}

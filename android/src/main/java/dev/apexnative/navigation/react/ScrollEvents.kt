package dev.apexnative.navigation.react

import android.content.Context
import android.util.Log
import com.facebook.react.ReactApplication
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.EventDispatcherListener
import com.facebook.react.views.scroll.ScrollEvent
import com.facebook.react.views.scroll.ScrollEventType
import dev.apexnative.navigation.utils.NavigationLog
import dev.apexnative.navigation.utils.UiThread
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap

/**
 * The scroll events of React Native's lists, as the screens' scroll listeners hear them: from the event dispatcher of
 * the running React Native, every list of the app. React Native keeps the offset, the speed and the kind of a scroll
 * event in private fields: they are looked up once, with their types; if React Native renames them, one warning goes to
 * the log and the screens hear no scroll, with no error at each event.
 */
internal object ScrollEvents {
    /** What a scroll event is. */
    enum class Kind { BeginDrag, EndDrag, Scroll, MomentumBegin, MomentumEnd }

    /** One scroll event: the list's tag (its view's id), its kind, its vertical offset and speed, in pixels. */
    class Reading(val viewTag: Int, val kind: Kind, val offsetY: Float, val velocityY: Float)

    private class Fields(val type: Field, val offsetY: Field, val velocityY: Field)

    private var fields: Fields? = null
    private var lookedUp = false

    // The screens' listeners that found no event dispatcher, held weakly until React Native has one. Main thread.
    private val waiting: MutableSet<ScrollEventListener> = Collections.newSetFromMap(WeakHashMap())

    /** [listener] found no event dispatcher: it is asked to listen again once React Native has one. */
    fun waitForDispatcher(listener: ScrollEventListener) {
        waiting += listener
    }

    /** A React context is ready (`ReloadCoordinator`): the listeners waiting for an event dispatcher listen now. */
    fun dispatcherReady() {
        val listeners = waiting.toList()
        waiting.clear()
        listeners.forEach(ScrollEventListener::attachIfNeeded)
    }

    /**
     * Calls [handler] on the main thread with each scroll event of the app's React Native, until the function returned
     * is called. None when React Native has no event dispatcher now (around a reload, the caller asks again later) or
     * its scroll events can't be read.
     */
    fun listen(context: Context, handler: (Reading) -> Unit): (() -> Unit)? {
        if (fields() == null) return null
        val reactContext = (context.applicationContext as? ReactApplication)?.reactHost?.currentReactContext
            ?: return null
        val dispatcher = try {
            UIManagerHelper.getEventDispatcher(reactContext)
        } catch (e: RuntimeException) {
            null
        } ?: return null
        val listener = EventDispatcherListener { event ->
            // The event goes back to its pool after the dispatch: it is read now, on the thread that dispatches.
            val reading = read(event) ?: return@EventDispatcherListener
            if (UiThread.isMainThread()) handler(reading) else UiThread.post { handler(reading) }
        }
        dispatcher.addListener(listener)
        return { dispatcher.removeListener(listener) }
    }

    private fun read(event: Event<*>): Reading? {
        if (event !is ScrollEvent) return null
        val fields = fields() ?: return null
        return try {
            val type = fields.type.get(event) as? ScrollEventType ?: return null
            val offsetY = readFloat(fields.offsetY, event) ?: return null
            val velocityY = readFloat(fields.velocityY, event) ?: return null
            Reading(event.viewTag, kindOf(type), offsetY, velocityY)
        } catch (e: ReflectiveOperationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun kindOf(type: ScrollEventType): Kind = when (type) {
        ScrollEventType.BEGIN_DRAG -> Kind.BeginDrag
        ScrollEventType.END_DRAG -> Kind.EndDrag
        ScrollEventType.SCROLL -> Kind.Scroll
        ScrollEventType.MOMENTUM_BEGIN -> Kind.MomentumBegin
        ScrollEventType.MOMENTUM_END -> Kind.MomentumEnd
    }

    @Synchronized
    private fun fields(): Fields? {
        if (lookedUp) return fields
        lookedUp = true
        fields = try {
            val owner = ScrollEvent::class.java
            val type = declaredField(owner, TYPE_NAMES) { ScrollEventType::class.java.isAssignableFrom(it) }
            val offsetY = declaredField(owner, OFFSET_NAMES, ::isNumeric)
            val velocityY = declaredField(owner, VELOCITY_NAMES, ::isNumeric)
            if (type != null && offsetY != null && velocityY != null) Fields(type, offsetY, velocityY) else null
        } catch (e: ReflectiveOperationException) {
            null
        } catch (e: SecurityException) {
            null
        }
        if (fields == null) {
            Log.w(
                NavigationLog.TAG,
                "[ApexNavigation] React Native's scroll events changed: hideOnScroll and the large title " +
                    "don't follow the scroll",
            )
        }
        return fields
    }

    // The names of the fields: React Native 0.87 first, then the earlier ones with the `m` prefix.
    private val TYPE_NAMES = listOf("scrollEventType", "mScrollEventType")
    private val OFFSET_NAMES = listOf("scrollY", "mScrollY")
    private val VELOCITY_NAMES = listOf("yVelocity", "mYVelocity")

    /**
     * The first of the fields [names] that [owner] declares with a type [accepted] takes, made readable; none when no
     * name fits. React Native renamed them and changed their types between versions.
     */
    fun declaredField(owner: Class<*>, names: List<String>, accepted: (Class<*>) -> Boolean): Field? {
        for (name in names) {
            val field = try {
                owner.getDeclaredField(name)
            } catch (e: NoSuchFieldException) {
                continue
            }
            if (!accepted(field.type)) continue
            field.isAccessible = true
            return field
        }
        return null
    }

    /** A number of any kind: a primitive one (`int`, `float`, `double`, …) or a boxed one. */
    fun isNumeric(type: Class<*>): Boolean =
        type in PRIMITIVE_NUMBERS || Number::class.java.isAssignableFrom(type)

    /** [field] of [target] as a float, whatever numeric type it has; none when it holds no number. */
    fun readFloat(field: Field, target: Any): Float? = (field.get(target) as? Number)?.toFloat()

    private val PRIMITIVE_NUMBERS = setOf<Class<*>?>(
        Byte::class.javaPrimitiveType,
        Short::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Long::class.javaPrimitiveType,
        Float::class.javaPrimitiveType,
        Double::class.javaPrimitiveType,
    )
}

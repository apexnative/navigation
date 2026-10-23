package dev.apexnative.navigation.react

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.ReactApplication
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import dev.apexnative.navigation.react.events.EventContract
import dev.apexnative.navigation.react.events.EventEmitter
import dev.apexnative.navigation.utils.WindowFolds
import java.lang.ref.WeakReference

/**
 * The device's hinge (`useHinge`): its state from the fold in the activity's window, its angle from the hinge angle
 * sensor (Android 11+), in degrees to a tenth as on iOS. Android has no public state of a closed device: closed is a
 * window without a fold, or an angle under 90° ([valueOf]). No sensor and no fold: no hinge, `{}`. It runs while
 * JavaScript has a hook mounted (`setHingeUpdates`), the sensor only while the activity is resumed too; the same value
 * is not sent twice. Main thread only, but [get].
 */
internal object Hinge {
    /** A value of the hinge: its state, and its angle in degrees to a tenth; none while the state is unknown. */
    data class Value(val state: String, val angle: Double?)

    private const val UNKNOWN = "unknown"

    private const val CLOSED_BELOW = 90f

    private var enabled = false
    private var activity: WeakReference<Activity>? = null
    private var folds: WindowFolds? = null
    private var sensorManager: SensorManager? = null
    private var sensor: Sensor? = null
    private var listening = false
    private var angle: Float? = null
    // The last value, as JavaScript has it; null without a hinge, and before the first one.
    @Volatile
    private var last: Value? = null

    private val onFoldsChanged = Runnable { update() }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) = listen(enabled)
        override fun onPause(owner: LifecycleOwner) = listen(false)
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            angle = event.values.firstOrNull() ?: return
            update()
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    /** Starts or stops the updates; [activity] is the one the hinge is read for. */
    fun setEnabled(activity: Activity?, enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            detach()
            return
        }
        activity?.let(::attachTo)
        update()
    }

    /** A new activity: the updates that run go on with it. */
    fun attach(activity: Activity) {
        if (!enabled) return
        attachTo(activity)
        update()
    }

    /** `{ state, angle? }`, the last value; `{}` before the first one and without a hinge. Any thread. */
    fun get(): WritableMap = toMap(last)

    private fun attachTo(activity: Activity) {
        if (this.activity?.get() === activity) return
        detach()
        val owner = activity as? LifecycleOwner ?: return
        this.activity = WeakReference(activity)
        folds = WindowFolds.of(activity)?.also { it.addListener(onFoldsChanged) }
        val manager = activity.getSystemService(SensorManager::class.java)
        sensorManager = manager
        // A wake-up sensor wakes the device for each change: one that doesn't is taken when there is one.
        sensor = if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE, false) ?: manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        } else {
            null
        }
        // Added to a resumed activity, the observer hears onResume at once.
        owner.lifecycle.addObserver(lifecycleObserver)
    }

    private fun detach() {
        listen(false)
        (activity?.get() as? LifecycleOwner)?.lifecycle?.removeObserver(lifecycleObserver)
        folds?.removeListener(onFoldsChanged)
        activity = null
        folds = null
        sensorManager = null
        sensor = null
        angle = null
    }

    private fun listen(on: Boolean) {
        val listen = on && enabled && isResumed()
        if (listen == listening) return
        val manager = sensorManager
        val sensor = sensor
        if (manager == null || sensor == null) return
        listening = listen
        if (listen) {
            manager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            manager.unregisterListener(sensorListener)
        }
    }

    private fun isResumed(): Boolean =
        (activity?.get() as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true

    // The value once the window's first folds came, and the sensor's first angle while it listens.
    private fun update() {
        if (!enabled) return
        val folds = folds ?: return
        if (!folds.hasInfo || (listening && angle == null)) return
        val value = valueOf(folds.folds.firstOrNull(), sensor != null, angle)
        if (value == last) return
        last = value
        val reactContext = (activity?.get()?.application as? ReactApplication)?.reactHost?.currentReactContext ?: return
        EventEmitter(reactContext).emitHingeChanged(toMap(value))
    }

    /**
     * The value of the window's first [fold] and the sensor's [angle]: none without a fold and without a sensor
     * ([hasSensor]). `closed` without a fold or under 90°, else `partiallyOpen` for a half-opened fold and `fullyOpen`
     * for a flat one. The angle goes to a tenth, as iOS gives it: the sensor's noise doesn't make a new value, and it
     * is left out while the state is unknown.
     */
    fun valueOf(fold: WindowFolds.Fold?, hasSensor: Boolean, angle: Float?): Value? {
        if (fold == null && !hasSensor) return null
        val state = when {
            fold == null || (angle != null && angle < CLOSED_BELOW) -> "closed"
            fold.halfOpened -> "partiallyOpen"
            else -> "fullyOpen"
        }
        val tenths = angle?.let { Math.round(it * 10.0) / 10.0 }
        return Value(state, tenths.takeIf { state != UNKNOWN })
    }

    private fun toMap(value: Value?): WritableMap = Arguments.createMap().apply {
        if (value == null) return@apply
        putString(EventContract.HingeChanged.STATE, value.state)
        if (value.state != UNKNOWN) value.angle?.let { putDouble(EventContract.HingeChanged.ANGLE, it) }
    }
}

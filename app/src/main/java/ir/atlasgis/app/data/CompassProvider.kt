package ir.atlasgis.app.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device heading from the rotation-vector sensor, used to point the navigation arrow
 * at the target relative to where the phone is facing. Falls back to `null` on
 * devices without the sensor, in which case the UI shows a north-referenced arrow.
 */
class CompassProvider(context: Context) {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _heading = MutableStateFlow<Float?>(null)
    val heading: StateFlow<Float?> = _heading.asStateFlow()

    private var running = false
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.size < 3) return
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val degrees = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
            val previous = _heading.value
            // Circular low-pass: raw azimuth jumps between bearing values a few
            // degrees apart and would make the arrow twitch.
            _heading.value = if (previous == null) degrees else smooth(previous, degrees)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (running) return
        val manager = manager ?: return
        val sensor = sensor ?: return
        running = true
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running) return
        running = false
        manager?.unregisterListener(listener)
        _heading.value = null
    }

    private fun smooth(previous: Float, next: Float): Float {
        var delta = next - previous
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        val result = previous + delta * 0.18f
        return ((result % 360f) + 360f) % 360f
    }
}

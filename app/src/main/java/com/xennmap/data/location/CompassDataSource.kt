package com.xennmap.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Device compass (magnetic heading) so the position marker keeps pointing
 * somewhere useful even while the boat is nearly stationary.
 */
@Singleton
class CompassDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val sensorManager get() = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun headings(): Flow<Float> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            private val rotation = FloatArray(9)
            private val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                var degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                degrees = (degrees + 360f) % 360f
                trySend(degrees)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    fun rounded(deg: Float): Int = ((deg % 360f) + 360f).roundToInt() % 360
}

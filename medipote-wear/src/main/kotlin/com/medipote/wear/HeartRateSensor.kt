package com.medipote.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** The watch's own heart-rate sensor, read only while the user is measuring. */
object HeartRateSensor {
    /** Android 16 replaced the "body sensors" permission by a heart-rate one. */
    val permission: String
        get() = if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS

    fun available(context: Context): Boolean =
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null

    fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Beats per minute as the sensor gives them (0 while it has no contact). */
    fun readings(context: Context): Flow<Float> = callbackFlow {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (manager == null || sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val reliable = event.accuracy != SensorManager.SENSOR_STATUS_NO_CONTACT && event.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
                trySend(if (reliable) event.values.firstOrNull() ?: 0f else 0f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        awaitClose { manager.unregisterListener(listener) }
    }
}

package com.mysocial.mesh.emergency

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Sensor-driven compass bearing and directional radar finder for locating emergency SOS senders.
 * Computes magnetic North azimuth heading and calculates bearing / relative direction toward peers.
 */
class CompassDirectionFinder(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _azimuth = MutableStateFlow(0f) // Current device heading: 0° to 360°
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val gravityData = FloatArray(3)
    private val magneticData = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetic = false

    private var isListening = false

    fun start() {
        if (isListening || sensorManager == null) return
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }
        isListening = true
    }

    fun stop() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val rawDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                _azimuth.value = (rawDeg + 360f) % 360f
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityData, 0, 3)
                hasGravity = true
                updateFromOrientation()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magneticData, 0, 3)
                hasMagnetic = true
                updateFromOrientation()
            }
        }
    }

    private fun updateFromOrientation() {
        if (hasGravity && hasMagnetic) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityData, magneticData)) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val rawDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                _azimuth.value = (rawDeg + 360f) % 360f
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /**
         * Calculates initial bearing from current GPS coordinates to target coordinates in degrees (0..360).
         */
        fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val φ1 = Math.toRadians(lat1)
            val φ2 = Math.toRadians(lat2)
            val Δλ = Math.toRadians(lon2 - lon1)

            val y = sin(Δλ) * cos(φ2)
            val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
            val θ = atan2(y, x)
            val deg = Math.toDegrees(θ).toFloat()
            return (deg + 360f) % 360f
        }

        /**
         * Computes the relative angle between device heading and target bearing (-180..+180).
         * 0 = Straight ahead, 90 = Right, -90 = Left, 180 = Behind.
         */
        fun calculateRelativeBearing(deviceAzimuth: Float, targetBearing: Float): Float {
            var diff = targetBearing - deviceAzimuth
            while (diff < -180f) diff += 360f
            while (diff > 180f) diff -= 360f
            return diff
        }

        /**
         * Estimates approximate radio propagation distance from RSSI using log-distance path loss model.
         */
        fun estimateDistanceMeters(rssi: Int, txPower: Int = -59, pathLossExponent: Double = 2.4): Double {
            if (rssi == 0) return -1.0
            val ratio = (txPower - rssi) / (10.0 * pathLossExponent)
            return 10.0.pow(ratio).coerceIn(0.1, 500.0)
        }
    }
}


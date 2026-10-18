package com.example.dondeestoy

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class CompassSensor(
    context: Context,
    private val onDirectionChanged: (Float) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    override fun onSensorChanged(event: SensorEvent) {

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {

            SensorManager.getRotationMatrixFromVector(
                rotationMatrix,
                event.values
            )

            SensorManager.getOrientation(
                rotationMatrix,
                orientation
            )

            var grados = Math.toDegrees(
                orientation[0].toDouble()
            ).toFloat()

            if (grados < 0) {
                grados += 360f
            }

            onDirectionChanged(grados)
        }
    }

    fun start() {

        rotationSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
        // Lo utilizaremos más adelante
        // para controlar la calibración.
    }
}
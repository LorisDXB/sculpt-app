package com.poissoncassant.sculptapp.steps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object StepSnapshotReader {
  fun readCurrentTotal(context: Context, timeoutMillis: Long = 1500L): Int? {
    val sensorManager = context.getSystemService(SensorManager::class.java) ?: return null
    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
    val latch = CountDownLatch(1)
    val result = AtomicInteger(-1)

    val listener =
        object : SensorEventListener {
          override fun onSensorChanged(event: SensorEvent) {
            val value = event.values.firstOrNull()?.toInt() ?: return
            result.set(value.coerceAtLeast(0))
            latch.countDown()
            sensorManager.unregisterListener(this)
          }

          override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

    val registered =
        runCatching {
              sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
            .getOrDefault(false)
    if (!registered) {
      return null
    }

    return try {
      if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
        null
      } else {
        result.get().takeIf { it >= 0 }
      }
    } finally {
      runCatching { sensorManager.unregisterListener(listener) }
    }
  }
}

package com.poissoncassant.sculptapp.steps

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object StepSnapshotReader {
  fun readCurrentTotal(context: Context, timeoutMillis: Long = 1500L): Int? {
    val sensorManager = context.getSystemService(SensorManager::class.java) ?: return null
    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
    Log.d(TAG, "readCurrentTotal sensor=${sensor.name} timeoutMillis=$timeoutMillis")
    val handlerThread = HandlerThread("SculptStepSensorThread").apply { start() }
    val handler = Handler(handlerThread.looper)
    val latch = CountDownLatch(1)
    val result = AtomicInteger(-1)

    val listener =
        object : SensorEventListener {
          override fun onSensorChanged(event: SensorEvent) {
            val value = event.values.firstOrNull()?.toInt() ?: return
            Log.d(TAG, "onSensorChanged rawValue=$value")
            result.set(value.coerceAtLeast(0))
            latch.countDown()
            sensorManager.unregisterListener(this)
          }

          override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

    val registered =
        runCatching {
              sensorManager.registerListener(
                  listener,
                  sensor,
                  SensorManager.SENSOR_DELAY_NORMAL,
                  handler,
              )
            }
            .getOrDefault(false)
    Log.d(TAG, "registerListener registered=$registered")
    if (!registered) {
      handlerThread.quitSafely()
      return null
    }

    return try {
      if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
        Log.w(TAG, "Timed out waiting for step sensor callback")
        null
      } else {
        result.get().takeIf { it >= 0 }.also { Log.d(TAG, "Returning sensor total=$it") }
      }
    } finally {
      runCatching { sensorManager.unregisterListener(listener) }
      handlerThread.quitSafely()
    }
  }

  private const val TAG = "SculptStepSensor"
}

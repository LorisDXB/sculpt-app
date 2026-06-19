package com.poissoncassant.sculptapp.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class StepTrackingStatus {
  READY,
  PERMISSION_REQUIRED,
  SENSOR_UNAVAILABLE,
  BASELINE_PENDING,
  STALE_READING,
  READ_FAILED,
}

object StepTrackingSupport {
  fun resolveStatus(context: Context): StepTrackingStatus =
      when {
        !hasStepCounterSensor(context) -> StepTrackingStatus.SENSOR_UNAVAILABLE
        !hasActivityRecognitionPermission(context) -> StepTrackingStatus.PERMISSION_REQUIRED
        else -> StepTrackingStatus.READY
      }

  fun hasActivityRecognitionPermission(context: Context): Boolean =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
          ContextCompat.checkSelfPermission(
              context,
              Manifest.permission.ACTIVITY_RECOGNITION,
          ) == PackageManager.PERMISSION_GRANTED

  fun hasStepCounterSensor(context: Context): Boolean =
      context.getSystemService(SensorManager::class.java)
          ?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
}

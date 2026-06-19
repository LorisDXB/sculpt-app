package com.poissoncassant.sculptapp.steps

import android.content.Context
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

class StepTrackingRepository(context: Context) {
  private val appContext = context.applicationContext
  private val preferences =
      appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun readSnapshot(): StepTrackingSnapshot {
    val today = todayDate()
    ensureCurrentDay(today)
    return buildSnapshot(today).also { snapshot ->
      Log.d(
          TAG,
          "readSnapshot date=${snapshot.date} status=${snapshot.status} todaySteps=${snapshot.todaySteps} baseline=${snapshot.baselineTotal} lastSeen=${snapshot.lastSeenTotal} lastUpdatedAt=${snapshot.lastUpdatedAtMillis} lastSuccessAt=${snapshot.lastSuccessfulRefreshAtMillis} error=${snapshot.errorMessage}",
      )
    }
  }

  fun refreshCurrentStepSnapshot(): StepTrackingSnapshot {
    val today = todayDate()
    ensureCurrentDay(today)
    Log.d(
        TAG,
        "refreshCurrentStepSnapshot start date=$today storedStatus=${preferences.getString(KEY_STATUS, null)} storedTodaySteps=${storedInt(KEY_TODAY_STEPS)} baseline=${storedInt(KEY_BASELINE_TOTAL)} lastSeen=${storedInt(KEY_LAST_SEEN_TOTAL)}",
    )
    val supportStatus = StepTrackingSupport.resolveStatus(appContext)
    if (supportStatus != StepTrackingStatus.READY) {
      Log.w(TAG, "refreshCurrentStepSnapshot skipped supportStatus=$supportStatus")
      persistStatusOnly(supportStatus, errorMessage = null)
      return buildSnapshot(today)
    }

    val sensorTotal = StepSnapshotReader.readCurrentTotal(appContext)
    if (sensorTotal == null) {
      val nextStatus =
          if (preferences.contains(KEY_LAST_SEEN_TOTAL)) {
            StepTrackingStatus.STALE_READING
          } else {
            StepTrackingStatus.READ_FAILED
          }
      Log.w(TAG, "refreshCurrentStepSnapshot failed to obtain sensor total nextStatus=$nextStatus")
      persistStatusOnly(nextStatus, errorMessage = "No sensor callback received")
      return buildSnapshot(today)
    }

    val now = System.currentTimeMillis()
    val baselineTotal = storedInt(KEY_BASELINE_TOTAL)
    return if (baselineTotal == null) {
      Log.d(TAG, "Initializing daily baseline date=$today baselineTotal=$sensorTotal")
      preferences
          .edit()
          .putString(KEY_DATE, today)
          .putInt(KEY_BASELINE_TOTAL, sensorTotal)
          .putInt(KEY_LAST_SEEN_TOTAL, sensorTotal)
          .putInt(KEY_TODAY_STEPS, 0)
          .putLong(KEY_LAST_UPDATED_AT, now)
          .putLong(KEY_LAST_SUCCESSFUL_REFRESH_AT, now)
          .putString(KEY_STATUS, StepTrackingStatus.BASELINE_PENDING.name)
          .remove(KEY_ERROR_MESSAGE)
          .apply()
      buildSnapshot(today).also { snapshot ->
        Log.d(
            TAG,
            "refreshCurrentStepSnapshot baseline_initialized date=${snapshot.date} status=${snapshot.status} todaySteps=${snapshot.todaySteps} baseline=${snapshot.baselineTotal} lastSeen=${snapshot.lastSeenTotal}",
        )
      }
    } else {
      val todaySteps = (sensorTotal - baselineTotal).coerceAtLeast(0)
      Log.d(
          TAG,
          "Derived today steps date=$today sensorTotal=$sensorTotal baselineTotal=$baselineTotal todaySteps=$todaySteps",
      )
      preferences
          .edit()
          .putString(KEY_DATE, today)
          .putInt(KEY_LAST_SEEN_TOTAL, sensorTotal)
          .putInt(KEY_TODAY_STEPS, todaySteps)
          .putLong(KEY_LAST_UPDATED_AT, now)
          .putLong(KEY_LAST_SUCCESSFUL_REFRESH_AT, now)
          .putString(KEY_STATUS, StepTrackingStatus.READY.name)
          .remove(KEY_ERROR_MESSAGE)
          .apply()
      buildSnapshot(today).also { snapshot ->
        Log.d(
            TAG,
            "refreshCurrentStepSnapshot success date=${snapshot.date} status=${snapshot.status} todaySteps=${snapshot.todaySteps} baseline=${snapshot.baselineTotal} lastSeen=${snapshot.lastSeenTotal} lastUpdatedAt=${snapshot.lastUpdatedAtMillis}",
        )
      }
    }
  }

  fun resetForNewDay() {
    val today = todayDate()
    Log.d(TAG, "resetForNewDay date=$today")
    preferences
        .edit()
        .putString(KEY_DATE, today)
        .remove(KEY_BASELINE_TOTAL)
        .remove(KEY_LAST_SEEN_TOTAL)
        .putInt(KEY_TODAY_STEPS, 0)
        .remove(KEY_LAST_UPDATED_AT)
        .remove(KEY_LAST_SUCCESSFUL_REFRESH_AT)
        .putString(KEY_STATUS, StepTrackingStatus.BASELINE_PENDING.name)
        .remove(KEY_ERROR_MESSAGE)
        .apply()
  }

  fun clearAll() {
    Log.d(TAG, "clearAll")
    preferences.edit().clear().apply()
  }

  private fun ensureCurrentDay(today: String) {
    val storedDate = preferences.getString(KEY_DATE, null)
    if (storedDate == today) {
      return
    }

    Log.d(TAG, "ensureCurrentDay resetting storedDate=$storedDate newDate=$today")
    resetForNewDay()
  }

  private fun buildSnapshot(date: String): StepTrackingSnapshot {
    val supportStatus = StepTrackingSupport.resolveStatus(appContext)
    val storedStatus =
        preferences
            .getString(KEY_STATUS, StepTrackingStatus.BASELINE_PENDING.name)
            ?.let { runCatching { StepTrackingStatus.valueOf(it) }.getOrNull() }
            ?: StepTrackingStatus.BASELINE_PENDING
    val effectiveStatus =
        when (supportStatus) {
          StepTrackingStatus.READY -> storedStatus
          else -> supportStatus
        }

    return StepTrackingSnapshot(
        date = date,
        todaySteps = preferences.getInt(KEY_TODAY_STEPS, 0).coerceAtLeast(0),
        status = effectiveStatus,
        baselineTotal = storedInt(KEY_BASELINE_TOTAL),
        lastSeenTotal = storedInt(KEY_LAST_SEEN_TOTAL),
        lastUpdatedAtMillis = storedLong(KEY_LAST_UPDATED_AT),
        lastSuccessfulRefreshAtMillis = storedLong(KEY_LAST_SUCCESSFUL_REFRESH_AT),
        errorMessage = preferences.getString(KEY_ERROR_MESSAGE, null),
    )
  }

  private fun persistStatusOnly(status: StepTrackingStatus, errorMessage: String?) {
    Log.d(TAG, "persistStatusOnly status=$status errorMessage=$errorMessage")
    preferences
        .edit()
        .putString(KEY_STATUS, status.name)
        .apply {
          if (errorMessage.isNullOrBlank()) {
            remove(KEY_ERROR_MESSAGE)
          } else {
            putString(KEY_ERROR_MESSAGE, errorMessage)
          }
        }
        .apply()
  }

  private fun storedInt(key: String): Int? =
      if (!preferences.contains(key)) {
        null
      } else {
        preferences.getInt(key, 0)
      }

  private fun storedLong(key: String): Long? =
      if (!preferences.contains(key)) {
        null
      } else {
        preferences.getLong(key, 0L)
      }

  private fun todayDate(): String = LocalDate.now(ZoneId.systemDefault()).toString()

  companion object {
    private const val PREFERENCES_NAME = "sculpt_step_tracking"
    private const val KEY_DATE = "step_day_date"
    private const val KEY_BASELINE_TOTAL = "step_day_baseline_total"
    private const val KEY_LAST_SEEN_TOTAL = "step_last_seen_total"
    private const val KEY_TODAY_STEPS = "step_today_steps"
    private const val KEY_LAST_UPDATED_AT = "step_last_updated_at"
    private const val KEY_LAST_SUCCESSFUL_REFRESH_AT = "step_last_successful_refresh_at"
    private const val KEY_STATUS = "step_status"
    private const val KEY_ERROR_MESSAGE = "step_error_message"
    private const val TAG = "SculptStepRepository"
  }
}

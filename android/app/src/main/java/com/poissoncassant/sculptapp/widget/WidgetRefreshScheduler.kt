package com.poissoncassant.sculptapp.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.poissoncassant.sculptapp.config.AppConfigRepository
import com.poissoncassant.sculptapp.steps.StepTrackingStatus
import com.poissoncassant.sculptapp.steps.StepTrackingSupport
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object WidgetRefreshScheduler {
  fun syncSchedules(context: Context, forceStepReschedule: Boolean = false) {
    scheduleNextMidnightRefresh(context)
    scheduleStepRefresh(context, forceStepReschedule)
  }

  fun scheduleNextMidnightRefresh(context: Context) {
    val appContext = context.applicationContext
    val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
    val pendingIntent = buildPendingIntent(appContext)
    val canScheduleExact =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          alarmManager.canScheduleExactAlarms()
        } else {
          true
        }

    if (!hasActiveWidgets(appContext)) {
      Log.d(TAG, "No active widgets, canceling scheduled midnight refresh")
      alarmManager.cancel(pendingIntent)
      return
    }

    val triggerAtMillis =
        ZonedDateTime.now(ZoneId.systemDefault())
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(1)
            .toInstant()
            .toEpochMilli()

    Log.d(
        TAG,
        "Scheduling midnight refresh for epochMillis=$triggerAtMillis canScheduleExact=$canScheduleExact",
    )
    runCatching {
          alarmManager.setExactAndAllowWhileIdle(
              AlarmManager.RTC_WAKEUP,
              triggerAtMillis,
              pendingIntent,
          )
          Log.d(TAG, "Scheduled midnight refresh with setExactAndAllowWhileIdle")
        }
        .getOrElse {
          Log.w(TAG, "Exact idle alarm scheduling failed, falling back", it)
          alarmManager.setAndAllowWhileIdle(
              AlarmManager.RTC_WAKEUP,
              triggerAtMillis,
              pendingIntent,
          )
          Log.d(TAG, "Scheduled midnight refresh with setAndAllowWhileIdle fallback")
        }
  }

  private fun scheduleStepRefresh(context: Context, forceReschedule: Boolean) {
    val appContext = context.applicationContext
    val configRepository = AppConfigRepository(appContext)
    val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
    val pendingIntent = buildStepRefreshPendingIntent(appContext)
    val hasWidgets = hasActiveWidgets(appContext)
    val supportStatus = StepTrackingSupport.resolveStatus(appContext)
    Log.d(
        TAG,
        "scheduleStepRefresh forceReschedule=$forceReschedule hasWidgets=$hasWidgets supportStatus=$supportStatus existingTriggerAtMillis=${configRepository.getNextStepRefreshAtMillis()}",
    )
    if (!hasWidgets || supportStatus != StepTrackingStatus.READY) {
      Log.d(
          TAG,
          "Canceling step refresh work because widget or steps tracking is unavailable hasWidgets=$hasWidgets supportStatus=$supportStatus",
      )
      alarmManager.cancel(pendingIntent)
      configRepository.clearNextStepRefreshAtMillis()
      return
    }

    val pollingSeconds = configRepository.getStepPollingSeconds()
    val existingTriggerAtMillis = configRepository.getNextStepRefreshAtMillis()
    val shouldKeepExisting =
        !forceReschedule &&
            existingTriggerAtMillis != null &&
            existingTriggerAtMillis > System.currentTimeMillis()
    if (shouldKeepExisting) {
      Log.d(
          TAG,
          "Keeping existing step refresh schedule triggerAtMillis=$existingTriggerAtMillis pollingSeconds=$pollingSeconds",
      )
      return
    }

    val triggerAtMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(pollingSeconds.toLong())
    configRepository.saveNextStepRefreshAtMillis(triggerAtMillis)
    alarmManager.cancel(pendingIntent)
    Log.d(
        TAG,
        "Scheduling step refresh triggerAtMillis=$triggerAtMillis pollingSeconds=$pollingSeconds forceReschedule=$forceReschedule",
    )
    runCatching {
          alarmManager.setExactAndAllowWhileIdle(
              AlarmManager.RTC_WAKEUP,
              triggerAtMillis,
              pendingIntent,
          )
          Log.d(TAG, "Scheduled step refresh with setExactAndAllowWhileIdle")
        }
        .getOrElse {
          Log.w(TAG, "Exact step refresh scheduling failed, falling back", it)
          alarmManager.setAndAllowWhileIdle(
              AlarmManager.RTC_WAKEUP,
              triggerAtMillis,
              pendingIntent,
          )
          Log.d(TAG, "Scheduled step refresh with setAndAllowWhileIdle fallback")
        }
  }

  private fun hasActiveWidgets(context: Context): Boolean {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val widgetComponent = ComponentName(context, CalorieWidgetProvider::class.java)
    return appWidgetManager.getAppWidgetIds(widgetComponent).isNotEmpty()
  }

  private fun buildPendingIntent(context: Context): PendingIntent {
    val intent =
        Intent(context, CalorieWidgetProvider::class.java).apply {
          action = CalorieWidgetProvider.ACTION_MIDNIGHT_REFRESH
        }
    return PendingIntent.getBroadcast(
        context,
        REQUEST_MIDNIGHT_REFRESH,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun buildStepRefreshPendingIntent(context: Context): PendingIntent {
    val intent =
        Intent(context, CalorieWidgetProvider::class.java).apply {
          action = CalorieWidgetProvider.ACTION_STEP_REFRESH
        }
    return PendingIntent.getBroadcast(
        context,
        REQUEST_STEP_REFRESH,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private const val REQUEST_MIDNIGHT_REFRESH = 1008
  private const val REQUEST_STEP_REFRESH = 1009
  private const val TAG = "SculptWidgetRefresh"
}

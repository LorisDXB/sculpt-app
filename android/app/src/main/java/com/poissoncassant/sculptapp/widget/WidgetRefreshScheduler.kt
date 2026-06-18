package com.poissoncassant.sculptapp.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.ZoneId
import java.time.ZonedDateTime

object WidgetRefreshScheduler {
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

  private const val REQUEST_MIDNIGHT_REFRESH = 1008
  private const val TAG = "SculptWidgetRefresh"
}

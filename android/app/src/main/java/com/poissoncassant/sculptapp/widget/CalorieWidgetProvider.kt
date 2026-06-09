package com.poissoncassant.sculptapp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class CalorieWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
  ) {
    CalorieWidgetRenderer.render(context, appWidgetManager, appWidgetIds)
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)

    when (intent.action) {
      Intent.ACTION_DATE_CHANGED,
      Intent.ACTION_TIME_CHANGED,
      Intent.ACTION_TIMEZONE_CHANGED,
      Intent.ACTION_BOOT_COMPLETED -> {
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_LOG_SAMPLE_MEAL -> {
        WidgetStateRepository(context).logSampleMeal()
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_RESET_TODAY -> {
        WidgetStateRepository(context).resetToday()
        CalorieWidgetRenderer.refreshAll(context)
      }
    }
  }

  companion object {
    const val ACTION_LOG_SAMPLE_MEAL =
        "com.poissoncassant.sculptapp.widget.ACTION_LOG_SAMPLE_MEAL"
    const val ACTION_RESET_TODAY =
        "com.poissoncassant.sculptapp.widget.ACTION_RESET_TODAY"
  }
}

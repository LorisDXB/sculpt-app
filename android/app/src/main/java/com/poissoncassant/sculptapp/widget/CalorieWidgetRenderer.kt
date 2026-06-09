package com.poissoncassant.sculptapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.poissoncassant.sculptapp.MainActivity
import com.poissoncassant.sculptapp.R

object CalorieWidgetRenderer {
  fun render(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    val state = WidgetStateRepository(context).readState()

    appWidgetIds.forEach { appWidgetId ->
      val views = RemoteViews(context.packageName, R.layout.calorie_widget)
      views.setTextViewText(
          R.id.widget_remaining_value,
          context.getString(R.string.widget_remaining_format, state.caloriesRemaining),
      )
      views.setTextViewText(
          R.id.widget_last_meal_value,
          context.getString(R.string.widget_last_meal_format, state.lastMealCalories),
      )
      views.setTextViewText(
          R.id.widget_macro_value,
          context.getString(
              R.string.widget_macro_format,
              state.proteinGrams,
              state.carbsGrams,
              state.fatGrams,
          ),
      )
      views.setTextViewText(
          R.id.widget_step_value,
          context.getString(R.string.widget_step_format, state.adjustmentStep),
      )
      views.setOnClickPendingIntent(R.id.widget_open_app_button, buildOpenAppPendingIntent(context))
      views.setOnClickPendingIntent(R.id.widget_refresh_button, buildRefreshPendingIntent(context))
      appWidgetManager.updateAppWidget(appWidgetId, views)
    }
  }

  fun refreshAll(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val widgetComponent = ComponentName(context, CalorieWidgetProvider::class.java)
    val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
    render(context, appWidgetManager, appWidgetIds)
  }

  private fun buildOpenAppPendingIntent(context: Context): PendingIntent {
    val intent =
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    return PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun buildRefreshPendingIntent(context: Context): PendingIntent {
    val intent =
        Intent(context, CalorieWidgetProvider::class.java).apply {
          action = CalorieWidgetProvider.ACTION_REFRESH_DEMO
        }
    return PendingIntent.getBroadcast(
        context,
        REQUEST_REFRESH,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private const val REQUEST_OPEN_APP = 1001
  private const val REQUEST_REFRESH = 1002
}

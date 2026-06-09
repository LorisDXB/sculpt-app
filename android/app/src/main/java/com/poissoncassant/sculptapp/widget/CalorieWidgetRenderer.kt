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
    val lastMeal = state.lastMeal

    appWidgetIds.forEach { appWidgetId ->
      val views = RemoteViews(context.packageName, R.layout.calorie_widget)
      views.setTextViewText(
          R.id.widget_remaining_value,
          context.getString(R.string.widget_remaining_format, state.caloriesRemaining),
      )
      views.setTextViewText(
          R.id.widget_remaining_meta,
          context.getString(
              R.string.widget_remaining_meta_format,
              state.caloriesConsumedToday,
              state.dailyCalorieTarget,
          ),
      )
      views.setTextViewText(
          R.id.widget_step_value,
          context.getString(R.string.widget_step_format, state.adjustmentStep),
      )

      if (lastMeal == null) {
        views.setTextViewText(R.id.widget_last_meal_name, context.getString(R.string.widget_no_meal))
        views.setTextViewText(
            R.id.widget_last_meal_value,
            context.getString(R.string.widget_last_meal_empty_value),
        )
        views.setTextViewText(R.id.widget_macro_value, context.getString(R.string.widget_macro_empty))
      } else {
        views.setTextViewText(R.id.widget_last_meal_name, lastMeal.mealName)
        views.setTextViewText(
            R.id.widget_last_meal_value,
            context.getString(R.string.widget_last_meal_format, lastMeal.calories),
        )
        views.setTextViewText(
            R.id.widget_macro_value,
            context.getString(
                R.string.widget_macro_format,
                lastMeal.proteinGrams,
                lastMeal.carbsGrams,
                lastMeal.fatGrams,
            ),
        )
      }

      views.setOnClickPendingIntent(R.id.widget_open_app_button, buildOpenAppPendingIntent(context))
      views.setOnClickPendingIntent(R.id.widget_sample_button, buildSampleMealPendingIntent(context))
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

  private fun buildSampleMealPendingIntent(context: Context): PendingIntent {
    val intent =
        Intent(context, CalorieWidgetProvider::class.java).apply {
          action = CalorieWidgetProvider.ACTION_LOG_SAMPLE_MEAL
        }
    return PendingIntent.getBroadcast(
        context,
        REQUEST_SAMPLE_MEAL,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private const val REQUEST_OPEN_APP = 1001
  private const val REQUEST_SAMPLE_MEAL = 1002
}

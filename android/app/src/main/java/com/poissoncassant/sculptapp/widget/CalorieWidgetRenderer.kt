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
      views.setOnClickPendingIntent(
          R.id.widget_root_container,
          buildBroadcastPendingIntent(
              context,
              REQUEST_NO_OP,
              CalorieWidgetProvider.ACTION_NO_OP,
          ),
      )
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
          R.id.widget_step_button,
          context.getString(R.string.widget_step_button_format, state.adjustmentStep),
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
      views.setOnClickPendingIntent(
          R.id.widget_remaining_increase_zone,
          buildBroadcastPendingIntent(
              context,
              REQUEST_INCREASE_REMAINING,
              CalorieWidgetProvider.ACTION_INCREASE_REMAINING,
          ),
      )
      views.setOnClickPendingIntent(
          R.id.widget_remaining_decrease_zone,
          buildBroadcastPendingIntent(
              context,
              REQUEST_DECREASE_REMAINING,
              CalorieWidgetProvider.ACTION_DECREASE_REMAINING,
          ),
      )
      views.setOnClickPendingIntent(
          R.id.widget_last_meal_increase_zone,
          buildBroadcastPendingIntent(
              context,
              REQUEST_INCREASE_LAST_MEAL,
              CalorieWidgetProvider.ACTION_INCREASE_LAST_MEAL,
          ),
      )
      views.setOnClickPendingIntent(
          R.id.widget_last_meal_decrease_zone,
          buildBroadcastPendingIntent(
              context,
              REQUEST_DECREASE_LAST_MEAL,
              CalorieWidgetProvider.ACTION_DECREASE_LAST_MEAL,
          ),
      )
      views.setOnClickPendingIntent(
          R.id.widget_step_button,
          buildBroadcastPendingIntent(
              context,
              REQUEST_CYCLE_STEP,
              CalorieWidgetProvider.ACTION_CYCLE_STEP,
          ),
      )
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
    return buildBroadcastPendingIntent(
        context,
        REQUEST_SAMPLE_MEAL,
        CalorieWidgetProvider.ACTION_LOG_SAMPLE_MEAL,
    )
  }

  private fun buildBroadcastPendingIntent(
      context: Context,
      requestCode: Int,
      action: String,
  ): PendingIntent {
    val intent = Intent(context, CalorieWidgetProvider::class.java).apply { this.action = action }
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private const val REQUEST_OPEN_APP = 1001
  private const val REQUEST_SAMPLE_MEAL = 1002
  private const val REQUEST_CYCLE_STEP = 1003
  private const val REQUEST_INCREASE_REMAINING = 1004
  private const val REQUEST_DECREASE_REMAINING = 1005
  private const val REQUEST_INCREASE_LAST_MEAL = 1006
  private const val REQUEST_DECREASE_LAST_MEAL = 1007
  private const val REQUEST_NO_OP = 1008
}

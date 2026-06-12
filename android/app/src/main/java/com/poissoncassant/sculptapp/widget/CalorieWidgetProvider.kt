package com.poissoncassant.sculptapp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log

class CalorieWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
  ) {
    Log.d(TAG, "onUpdate for widgetIds=${appWidgetIds.joinToString()}")
    CalorieWidgetRenderer.render(context, appWidgetManager, appWidgetIds)
  }

  override fun onAppWidgetOptionsChanged(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetId: Int,
      newOptions: android.os.Bundle,
  ) {
    super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    Log.d(TAG, "onAppWidgetOptionsChanged for widgetId=$appWidgetId")
    CalorieWidgetRenderer.render(context, appWidgetManager, intArrayOf(appWidgetId))
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    Log.d(TAG, "onReceive action=${intent.action}")

    when (intent.action) {
      Intent.ACTION_DATE_CHANGED,
      Intent.ACTION_TIME_CHANGED,
      Intent.ACTION_TIMEZONE_CHANGED,
      Intent.ACTION_BOOT_COMPLETED -> {
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_MIDNIGHT_REFRESH -> {
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_NO_OP -> Unit
      ACTION_CYCLE_STEP -> {
        WidgetStateRepository(context).cycleAdjustmentStep()
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_INCREASE_REMAINING -> {
        WidgetStateRepository(context).adjustCaloriesRemaining(increase = true)
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_DECREASE_REMAINING -> {
        WidgetStateRepository(context).adjustCaloriesRemaining(increase = false)
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_INCREASE_LAST_MEAL -> {
        WidgetStateRepository(context).adjustLastMealCalories(increase = true)
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_DECREASE_LAST_MEAL -> {
        WidgetStateRepository(context).adjustLastMealCalories(increase = false)
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_SELECT_WEIGHT_HUNDREDS -> {
        WidgetStateRepository(context).selectWeightDigit(WeightDigit.HUNDREDS)
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_SELECT_WEIGHT_TENS -> {
        WidgetStateRepository(context).selectWeightDigit(WeightDigit.TENS)
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_SELECT_WEIGHT_ONES -> {
        WidgetStateRepository(context).selectWeightDigit(WeightDigit.ONES)
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_SELECT_WEIGHT_TENTHS -> {
        WidgetStateRepository(context).selectWeightDigit(WeightDigit.TENTHS)
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_INCREASE_WEIGHT_DIGIT -> {
        WidgetStateRepository(context).adjustSelectedWeightDigit(increase = true)
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_DECREASE_WEIGHT_DIGIT -> {
        WidgetStateRepository(context).adjustSelectedWeightDigit(increase = false)
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
    const val ACTION_NO_OP =
        "com.poissoncassant.sculptapp.widget.ACTION_NO_OP"
    const val ACTION_CYCLE_STEP =
        "com.poissoncassant.sculptapp.widget.ACTION_CYCLE_STEP"
    const val ACTION_INCREASE_REMAINING =
        "com.poissoncassant.sculptapp.widget.ACTION_INCREASE_REMAINING"
    const val ACTION_DECREASE_REMAINING =
        "com.poissoncassant.sculptapp.widget.ACTION_DECREASE_REMAINING"
    const val ACTION_INCREASE_LAST_MEAL =
        "com.poissoncassant.sculptapp.widget.ACTION_INCREASE_LAST_MEAL"
    const val ACTION_DECREASE_LAST_MEAL =
        "com.poissoncassant.sculptapp.widget.ACTION_DECREASE_LAST_MEAL"
    const val ACTION_LOG_SAMPLE_MEAL =
        "com.poissoncassant.sculptapp.widget.ACTION_LOG_SAMPLE_MEAL"
    const val ACTION_RESET_TODAY =
        "com.poissoncassant.sculptapp.widget.ACTION_RESET_TODAY"
    const val ACTION_MIDNIGHT_REFRESH =
        "com.poissoncassant.sculptapp.widget.ACTION_MIDNIGHT_REFRESH"
    const val ACTION_SELECT_WEIGHT_HUNDREDS =
        "com.poissoncassant.sculptapp.widget.ACTION_SELECT_WEIGHT_HUNDREDS"
    const val ACTION_SELECT_WEIGHT_TENS =
        "com.poissoncassant.sculptapp.widget.ACTION_SELECT_WEIGHT_TENS"
    const val ACTION_SELECT_WEIGHT_ONES =
        "com.poissoncassant.sculptapp.widget.ACTION_SELECT_WEIGHT_ONES"
    const val ACTION_SELECT_WEIGHT_TENTHS =
        "com.poissoncassant.sculptapp.widget.ACTION_SELECT_WEIGHT_TENTHS"
    const val ACTION_INCREASE_WEIGHT_DIGIT =
        "com.poissoncassant.sculptapp.widget.ACTION_INCREASE_WEIGHT_DIGIT"
    const val ACTION_DECREASE_WEIGHT_DIGIT =
        "com.poissoncassant.sculptapp.widget.ACTION_DECREASE_WEIGHT_DIGIT"
    private const val TAG = "SculptWidgetProvider"
  }
}

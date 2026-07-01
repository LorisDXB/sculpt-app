package com.poissoncassant.sculptapp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import com.poissoncassant.sculptapp.steps.StepRefreshCoordinator

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
      Intent.ACTION_TIMEZONE_CHANGED -> {
        // Day rollovers can change the progress gradient, so these paths need a full redraw.
        CalorieWidgetRenderer.refreshAll(context, usePartialUpdate = false)
      }
      Intent.ACTION_BOOT_COMPLETED -> {
        val pendingResult = goAsync()
        Thread {
          try {
            StepRefreshCoordinator(context).refreshNow(
                reason = "boot_completed",
                forceStepReschedule = true,
                forceFullWidgetRedraw = true,
            )
          } catch (throwable: Throwable) {
            Log.e(TAG, "Boot-completed refresh failed", throwable)
            CalorieWidgetRenderer.refreshAll(context, usePartialUpdate = false)
          } finally {
            pendingResult.finish()
          }
        }.start()
      }
      ACTION_MIDNIGHT_REFRESH -> {
        CalorieWidgetRenderer.refreshAll(context, usePartialUpdate = false)
      }
      ACTION_STEP_REFRESH -> {
        val pendingResult = goAsync()
        Log.d(
            TAG,
            "Automatic step refresh alarm fired, dispatching async refresh thread=${Thread.currentThread().name}",
        )
        Thread {
          try {
            Log.d(TAG, "Automatic step refresh worker thread started thread=${Thread.currentThread().name}")
            StepRefreshCoordinator(context).refreshNow(reason = "alarm")
            Log.d(TAG, "Automatic step refresh worker thread completed")
          } catch (throwable: Throwable) {
            Log.e(TAG, "Automatic step refresh worker thread failed", throwable)
          } finally {
            Log.d(TAG, "Automatic step refresh worker thread finishing pendingResult")
            pendingResult.finish()
          }
        }.start()
      }
      ACTION_NO_OP -> Unit
      ACTION_CYCLE_STEP -> {
        WidgetStateRepository(context).cycleAdjustmentStep()
        CalorieWidgetRenderer.refreshAll(context)
      }
      ACTION_INCREASE_REMAINING -> {
        WidgetStateRepository(context).adjustCaloriesRemaining(increase = true)
        CalorieWidgetRenderer.refreshAll(context, usePartialUpdate = false)
      }
      ACTION_DECREASE_REMAINING -> {
        WidgetStateRepository(context).adjustCaloriesRemaining(increase = false)
        CalorieWidgetRenderer.refreshAll(context, usePartialUpdate = false)
      }
      ACTION_INCREASE_LAST_MEAL -> {
        val repository = WidgetStateRepository(context)
        if (repository.readState().isWeightModeAvailable) {
          repository.adjustSelectedWeightDigit(increase = true)
        } else {
          repository.adjustLastMealCalories(increase = true)
        }
        CalorieWidgetRenderer.refreshAll(context, usePartialUpdate = false)
      }
      ACTION_DECREASE_LAST_MEAL -> {
        val repository = WidgetStateRepository(context)
        if (repository.readState().isWeightModeAvailable) {
          repository.adjustSelectedWeightDigit(increase = false)
        } else {
          repository.adjustLastMealCalories(increase = false)
        }
        CalorieWidgetRenderer.refreshAll(context, usePartialUpdate = false)
      }
      ACTION_SELECT_WEIGHT_DIGIT -> {
        val digitIndex = intent.getIntExtra(EXTRA_WEIGHT_DIGIT_INDEX, DEFAULT_WEIGHT_DIGIT_INDEX)
        WidgetStateRepository(context).selectWeightDigit(digitIndex)
        CalorieWidgetRenderer.refreshAll(context, usePartialUpdate = false)
      }
      ACTION_LOG_SAMPLE_MEAL -> {
        WidgetStateRepository(context).logSampleMeal()
        CalorieWidgetRenderer.refreshAll(context, usePartialUpdate = false)
      }
      ACTION_RESET_TODAY -> {
        WidgetStateRepository(context).resetToday()
        CalorieWidgetRenderer.refreshAll(context, usePartialUpdate = false)
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
    const val ACTION_SELECT_WEIGHT_DIGIT =
        "com.poissoncassant.sculptapp.widget.ACTION_SELECT_WEIGHT_DIGIT"
    const val ACTION_LOG_SAMPLE_MEAL =
        "com.poissoncassant.sculptapp.widget.ACTION_LOG_SAMPLE_MEAL"
    const val ACTION_RESET_TODAY =
        "com.poissoncassant.sculptapp.widget.ACTION_RESET_TODAY"
    const val ACTION_MIDNIGHT_REFRESH =
        "com.poissoncassant.sculptapp.widget.ACTION_MIDNIGHT_REFRESH"
    const val ACTION_STEP_REFRESH =
        "com.poissoncassant.sculptapp.widget.ACTION_STEP_REFRESH"
    const val EXTRA_WEIGHT_DIGIT_INDEX = "weight_digit_index"
    private const val DEFAULT_WEIGHT_DIGIT_INDEX = 3
    private const val TAG = "SculptWidgetProvider"
  }
}

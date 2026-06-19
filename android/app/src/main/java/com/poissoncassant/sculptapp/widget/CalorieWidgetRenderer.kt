package com.poissoncassant.sculptapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import com.poissoncassant.sculptapp.AddMealEntryActivity
import com.poissoncassant.sculptapp.R
import com.poissoncassant.sculptapp.steps.StepTrackingStatus
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.max

object CalorieWidgetRenderer {
  fun render(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    render(context, appWidgetManager, appWidgetIds, usePartialUpdate = false)
  }

  fun refreshAll(context: Context, usePartialUpdate: Boolean = true) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val widgetComponent = ComponentName(context, CalorieWidgetProvider::class.java)
    val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
    render(context, appWidgetManager, appWidgetIds, usePartialUpdate = usePartialUpdate)
  }

  private fun render(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
      usePartialUpdate: Boolean,
  ) {
    val state = WidgetStateRepository(context).readState()
    val lastMeal = state.lastMeal
    val weightMode = state.isWeightModeAvailable

    Log.d(
        TAG,
        "render widgetIds=${appWidgetIds.joinToString()} date=${state.date} consumed=${state.caloriesConsumedToday} target=${state.dailyCalorieTarget} status=${state.analysisStatus} partial=$usePartialUpdate",
    )
    WidgetRefreshScheduler.syncSchedules(context)

    appWidgetIds.forEach { appWidgetId ->
      val presentation = presentationFor(appWidgetManager.getAppWidgetOptions(appWidgetId))
      val views = RemoteViews(context.packageName, R.layout.calorie_widget)
      views.setOnClickPendingIntent(
          R.id.widget_root_container,
          buildBroadcastPendingIntent(
              context,
              REQUEST_NO_OP,
              CalorieWidgetProvider.ACTION_NO_OP,
          ),
      )
      if (!usePartialUpdate) {
        views.setImageViewBitmap(
            R.id.widget_background_image,
            createBackgroundBitmap(state),
        )
        applyPresentation(context, views, presentation)
      }

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
          R.id.widget_total_macro_value,
          context.getString(
              R.string.widget_macro_format,
              state.totalProteinGrams,
              state.totalCarbsGrams,
              state.totalFatGrams,
          ),
      )
      views.setTextViewText(
          R.id.widget_step_button,
          if (state.analysisStatus == AnalysisStatus.ANALYZING) {
            context.getString(R.string.widget_step_button_busy)
          } else {
            context.getString(R.string.widget_step_button_format, state.adjustmentStep)
          },
      )

      renderStepPanel(context, views, state.stepPanel)
      renderRightPanel(context, views, state, weightMode, lastMeal)
      bindTapTargets(context, views)

      if (usePartialUpdate) {
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
      } else {
        appWidgetManager.updateAppWidget(appWidgetId, views)
      }
    }
  }

  private fun renderRightPanel(
      context: Context,
      views: RemoteViews,
      state: CalorieWidgetState,
      weightMode: Boolean,
      lastMeal: LastMealState?,
  ) {
    if (weightMode) {
      views.setTextViewText(R.id.widget_last_meal_label, context.getString(R.string.widget_weight_label))
      views.setInt(R.id.widget_last_meal_label, "setGravity", android.view.Gravity.START)
      views.setViewVisibility(R.id.widget_last_meal_content_container, android.view.View.INVISIBLE)
      views.setViewVisibility(R.id.widget_weight_mode_overlay, android.view.View.VISIBLE)
      views.setViewVisibility(R.id.widget_analysis_progress, android.view.View.GONE)
      views.setViewVisibility(R.id.widget_step_button, android.view.View.GONE)
      renderWeightMode(context, views, state.weightPanel)
      return
    }

    views.setTextViewText(R.id.widget_last_meal_label, context.getString(R.string.widget_last_meal_label))
    views.setInt(R.id.widget_last_meal_label, "setGravity", android.view.Gravity.START)
    views.setViewVisibility(R.id.widget_last_meal_content_container, android.view.View.VISIBLE)
    views.setViewVisibility(R.id.widget_weight_mode_overlay, android.view.View.GONE)
    views.setViewVisibility(R.id.widget_step_button, android.view.View.VISIBLE)

    if (state.analysisStatus == AnalysisStatus.ANALYZING) {
      views.setTextViewText(R.id.widget_last_meal_name, context.getString(R.string.widget_analysis_in_progress))
      views.setTextViewText(R.id.widget_last_meal_value, context.getString(R.string.widget_analysis_busy_hint))
      views.setTextViewText(R.id.widget_macro_value, context.getString(R.string.widget_analysis_busy_supporting))
      views.setViewVisibility(R.id.widget_analysis_progress, android.view.View.VISIBLE)
      views.setViewVisibility(R.id.widget_last_meal_increase_zone, android.view.View.VISIBLE)
      views.setViewVisibility(R.id.widget_last_meal_decrease_zone, android.view.View.VISIBLE)
    } else if (state.analysisStatus == AnalysisStatus.ERROR) {
      views.setTextViewText(R.id.widget_last_meal_name, context.getString(R.string.widget_analysis_error_title))
      views.setTextViewText(R.id.widget_last_meal_value, context.getString(R.string.widget_analysis_error_secondary))
      views.setTextViewText(
          R.id.widget_macro_value,
          state.analysisMessage ?: context.getString(R.string.widget_analysis_error_fallback),
      )
      views.setViewVisibility(R.id.widget_analysis_progress, android.view.View.GONE)
      views.setViewVisibility(R.id.widget_last_meal_increase_zone, android.view.View.VISIBLE)
      views.setViewVisibility(R.id.widget_last_meal_decrease_zone, android.view.View.VISIBLE)
    } else if (lastMeal == null) {
      views.setTextViewText(R.id.widget_last_meal_name, context.getString(R.string.widget_no_meal))
      views.setTextViewText(R.id.widget_last_meal_value, context.getString(R.string.widget_no_meal_secondary))
      views.setTextViewText(R.id.widget_macro_value, context.getString(R.string.widget_macro_empty))
      views.setViewVisibility(R.id.widget_analysis_progress, android.view.View.GONE)
      views.setViewVisibility(R.id.widget_last_meal_increase_zone, android.view.View.INVISIBLE)
      views.setViewVisibility(R.id.widget_last_meal_decrease_zone, android.view.View.INVISIBLE)
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
      views.setViewVisibility(R.id.widget_analysis_progress, android.view.View.GONE)
      views.setViewVisibility(R.id.widget_last_meal_increase_zone, android.view.View.VISIBLE)
      views.setViewVisibility(R.id.widget_last_meal_decrease_zone, android.view.View.VISIBLE)
    }
  }

  private fun renderStepPanel(
      context: Context,
      views: RemoteViews,
      stepPanel: StepPanelState,
  ) {
    val valueText =
        when (stepPanel.status) {
          StepTrackingStatus.READY,
          StepTrackingStatus.BASELINE_PENDING,
          StepTrackingStatus.STALE_READING,
          StepTrackingStatus.READ_FAILED ->
              stepPanel.todaySteps?.let { formatSteps(it) } ?: STEP_PLACEHOLDER
          StepTrackingStatus.PERMISSION_REQUIRED,
          StepTrackingStatus.SENSOR_UNAVAILABLE -> STEP_PLACEHOLDER
        }
    val statusText =
        when (stepPanel.status) {
          StepTrackingStatus.READY,
          StepTrackingStatus.BASELINE_PENDING,
          StepTrackingStatus.STALE_READING,
          StepTrackingStatus.READ_FAILED -> context.getString(R.string.widget_steps_label)
          StepTrackingStatus.PERMISSION_REQUIRED -> context.getString(R.string.widget_steps_permission_required)
          StepTrackingStatus.SENSOR_UNAVAILABLE -> context.getString(R.string.widget_steps_unavailable)
        }

    views.setTextViewText(R.id.widget_steps_value, valueText)
    views.setTextViewText(R.id.widget_steps_status, statusText)
  }

  private fun renderWeightMode(
      context: Context,
      views: RemoteViews,
      weightPanel: WeightPanelState,
  ) {
    val digits = weightDigits(weightPanel.displayedWeightTenths)
    val digitIds =
        intArrayOf(
            R.id.widget_weight_digit_0,
            R.id.widget_weight_digit_1,
            R.id.widget_weight_digit_2,
            R.id.widget_weight_digit_3,
        )

    digitIds.forEachIndexed { index, viewId ->
      views.setTextViewText(viewId, digits[index])
      views.setInt(
          viewId,
          "setBackgroundResource",
          if (weightPanel.selectedDigitIndex == index) {
            R.drawable.widget_weight_digit_selected_background
          } else {
            R.drawable.widget_weight_digit_unselected_background
          },
      )
      views.setTextColor(viewId, UNSELECTED_WEIGHT_DIGIT_COLOR)
    }

    views.setTextViewText(
        R.id.widget_weight_vs_yesterday,
        comparisonText(
            context = context,
            labelRes = R.string.widget_weight_compare_yesterday,
            comparisonTenths = weightPanel.comparisonToYesterdayTenths,
        ),
    )
    views.setTextColor(R.id.widget_weight_vs_yesterday, comparisonColor(weightPanel.comparisonToYesterdayTenths))
    views.setTextViewText(
        R.id.widget_weight_vs_last_week,
        comparisonText(
            context = context,
            labelRes = R.string.widget_weight_compare_last_week,
            comparisonTenths = weightPanel.comparisonToLastWeekTenths,
        ),
    )
    views.setTextColor(R.id.widget_weight_vs_last_week, comparisonColor(weightPanel.comparisonToLastWeekTenths))
  }

  private fun bindTapTargets(context: Context, views: RemoteViews) {
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
        R.id.widget_remaining_dead_zone,
        buildBroadcastPendingIntent(
            context,
            REQUEST_NO_OP,
            CalorieWidgetProvider.ACTION_NO_OP,
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
        R.id.widget_center_dead_zone,
        buildBroadcastPendingIntent(
            context,
            REQUEST_NO_OP,
            CalorieWidgetProvider.ACTION_NO_OP,
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
        R.id.widget_last_meal_dead_zone,
        buildBroadcastPendingIntent(
            context,
            REQUEST_NO_OP,
            CalorieWidgetProvider.ACTION_NO_OP,
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

    WEIGHT_DIGIT_VIEW_IDS.forEachIndexed { index, viewId ->
      views.setOnClickPendingIntent(
          viewId,
          buildWeightDigitSelectionPendingIntent(context, index),
      )
    }
  }

  private fun buildOpenAppPendingIntent(context: Context): PendingIntent {
    val intent =
        Intent(context, AddMealEntryActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    return PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
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

  private fun buildWeightDigitSelectionPendingIntent(
      context: Context,
      digitIndex: Int,
  ): PendingIntent {
    val intent =
        Intent(context, CalorieWidgetProvider::class.java).apply {
          action = CalorieWidgetProvider.ACTION_SELECT_WEIGHT_DIGIT
          putExtra(CalorieWidgetProvider.EXTRA_WEIGHT_DIGIT_INDEX, digitIndex)
        }
    return PendingIntent.getBroadcast(
        context,
        REQUEST_SELECT_WEIGHT_DIGIT_BASE + digitIndex,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun applyPresentation(
      context: Context,
      views: RemoteViews,
      presentation: WidgetPresentation,
  ) {
    views.setViewPadding(
        R.id.widget_content_root,
        dpToPx(context, presentation.contentHorizontalPaddingDp),
        dpToPx(context, presentation.contentVerticalPaddingDp),
        dpToPx(context, presentation.contentHorizontalPaddingDp),
        dpToPx(context, presentation.contentVerticalPaddingDp),
    )
    views.setViewPadding(
        R.id.widget_left_column,
        0,
        0,
        dpToPx(context, presentation.columnGapDp),
        0,
    )
    views.setViewPadding(
        R.id.widget_right_column,
        dpToPx(context, presentation.columnGapDp),
        0,
        0,
        0,
    )

    views.setTextViewTextSize(R.id.widget_section_label, TypedValue.COMPLEX_UNIT_SP, presentation.labelSp)
    views.setTextViewTextSize(R.id.widget_remaining_value, TypedValue.COMPLEX_UNIT_SP, presentation.remainingValueSp)
    views.setTextViewTextSize(R.id.widget_remaining_meta, TypedValue.COMPLEX_UNIT_SP, presentation.metaSp)
    views.setTextViewTextSize(R.id.widget_total_macro_value, TypedValue.COMPLEX_UNIT_SP, presentation.macroSp)
    views.setTextViewTextSize(R.id.widget_last_meal_label, TypedValue.COMPLEX_UNIT_SP, presentation.labelSp)
    views.setTextViewTextSize(R.id.widget_last_meal_name, TypedValue.COMPLEX_UNIT_SP, presentation.mealNameSp)
    views.setTextViewTextSize(R.id.widget_last_meal_value, TypedValue.COMPLEX_UNIT_SP, presentation.mealValueSp)
    views.setTextViewTextSize(R.id.widget_macro_value, TypedValue.COMPLEX_UNIT_SP, presentation.macroSp)
    views.setTextViewTextSize(R.id.widget_steps_value, TypedValue.COMPLEX_UNIT_SP, presentation.stepsValueSp)
    views.setTextViewTextSize(R.id.widget_steps_status, TypedValue.COMPLEX_UNIT_SP, presentation.stepsLabelSp)
    views.setTextViewTextSize(R.id.widget_weight_vs_yesterday, TypedValue.COMPLEX_UNIT_SP, presentation.weightComparisonSp)
    views.setTextViewTextSize(R.id.widget_weight_vs_last_week, TypedValue.COMPLEX_UNIT_SP, presentation.weightComparisonSp)
    WEIGHT_DIGIT_VIEW_IDS.forEach { views.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, presentation.weightDigitSp) }
    views.setTextViewTextSize(R.id.widget_weight_separator, TypedValue.COMPLEX_UNIT_SP, presentation.weightDigitSp - 2f)
    views.setTextViewTextSize(R.id.widget_open_app_button, TypedValue.COMPLEX_UNIT_SP, presentation.buttonSp)
    views.setTextViewTextSize(R.id.widget_step_button, TypedValue.COMPLEX_UNIT_SP, presentation.buttonSp)
    views.setInt(R.id.widget_section_label, "setMaxLines", presentation.labelMaxLines)
    views.setInt(R.id.widget_remaining_meta, "setMaxLines", presentation.metaMaxLines)
    views.setInt(R.id.widget_total_macro_value, "setMaxLines", presentation.supportingMaxLines)
    views.setInt(R.id.widget_last_meal_label, "setMaxLines", presentation.labelMaxLines)
    views.setInt(R.id.widget_last_meal_name, "setMaxLines", presentation.lastMealNameMaxLines)
    views.setInt(R.id.widget_last_meal_value, "setMaxLines", presentation.secondaryMaxLines)
    views.setInt(R.id.widget_macro_value, "setMaxLines", presentation.supportingMaxLines)
    views.setInt(R.id.widget_steps_status, "setMaxLines", 1)
    views.setInt(R.id.widget_weight_vs_yesterday, "setMaxLines", 1)
    views.setInt(R.id.widget_weight_vs_last_week, "setMaxLines", 1)

    views.setViewPadding(
        R.id.widget_bottom_actions,
        0,
        dpToPx(context, presentation.bottomActionsMarginTopDp),
        0,
        0,
    )
    views.setViewPadding(
        R.id.widget_open_app_button,
        dpToPx(context, presentation.buttonHorizontalPaddingDp),
        dpToPx(context, presentation.buttonVerticalPaddingDp),
        dpToPx(context, presentation.buttonHorizontalPaddingDp),
        dpToPx(context, presentation.buttonVerticalPaddingDp),
    )
    views.setViewPadding(
        R.id.widget_step_button,
        dpToPx(context, presentation.buttonHorizontalPaddingDp),
        dpToPx(context, presentation.buttonVerticalPaddingDp),
        dpToPx(context, presentation.buttonHorizontalPaddingDp),
        dpToPx(context, presentation.buttonVerticalPaddingDp),
    )
  }

  private fun presentationFor(options: Bundle): WidgetPresentation {
    val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
    val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

    return when {
      minHeightDp >= 170 || minWidthDp >= 330 ->
          WidgetPresentation(
              contentHorizontalPaddingDp = 18,
              contentVerticalPaddingDp = 18,
              columnGapDp = 12,
              labelSp = 12f,
              labelMaxLines = 1,
              remainingValueSp = 31f,
              mealNameSp = 17f,
              mealValueSp = 23f,
              metaSp = 12f,
              macroSp = 12f,
              stepsValueSp = 15f,
              stepsLabelSp = 10f,
              weightDigitSp = 22f,
              weightComparisonSp = 11f,
              metaMaxLines = 2,
              lastMealNameMaxLines = 2,
              secondaryMaxLines = 1,
              supportingMaxLines = 2,
              buttonSp = 13f,
              buttonHorizontalPaddingDp = 12,
              buttonVerticalPaddingDp = 10,
              bottomActionsMarginTopDp = 12,
          )
      minHeightDp <= 125 || minWidthDp <= 250 ->
          WidgetPresentation(
              contentHorizontalPaddingDp = 12,
              contentVerticalPaddingDp = 12,
              columnGapDp = 6,
              labelSp = 9f,
              labelMaxLines = 1,
              remainingValueSp = 18f,
              mealNameSp = 11f,
              mealValueSp = 12f,
              metaSp = 9f,
              macroSp = 8f,
              stepsValueSp = 10f,
              stepsLabelSp = 7f,
              weightDigitSp = 16f,
              weightComparisonSp = 8f,
              metaMaxLines = 2,
              lastMealNameMaxLines = 2,
              secondaryMaxLines = 1,
              supportingMaxLines = 2,
              buttonSp = 11f,
              buttonHorizontalPaddingDp = 8,
              buttonVerticalPaddingDp = 7,
              bottomActionsMarginTopDp = 8,
          )
      else ->
          WidgetPresentation(
              contentHorizontalPaddingDp = 16,
              contentVerticalPaddingDp = 16,
              columnGapDp = 10,
              labelSp = 12f,
              labelMaxLines = 1,
              remainingValueSp = 28f,
              mealNameSp = 16f,
              mealValueSp = 22f,
              metaSp = 12f,
              macroSp = 12f,
              stepsValueSp = 14f,
              stepsLabelSp = 9f,
              weightDigitSp = 20f,
              weightComparisonSp = 10f,
              metaMaxLines = 2,
              lastMealNameMaxLines = 2,
              secondaryMaxLines = 1,
              supportingMaxLines = 2,
              buttonSp = 13f,
              buttonHorizontalPaddingDp = 12,
              buttonVerticalPaddingDp = 10,
              bottomActionsMarginTopDp = 12,
          )
    }
  }

  private fun createBackgroundBitmap(state: CalorieWidgetState): Bitmap {
    val width = 900
    val height = 420
    val cornerRadius = 48f
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(56, 255, 255, 255)
      style = Paint.Style.STROKE
      strokeWidth = 3f
    }

    val progress =
        if (state.dailyCalorieTarget <= 0) {
          1f
        } else {
          (state.caloriesConsumedToday.toFloat() / max(state.dailyCalorieTarget, 1).toFloat())
              .coerceIn(0f, 1f)
        }
    val accentHue = 135f - (135f * progress)
    val accentColor = Color.HSVToColor(floatArrayOf(accentHue, 0.72f, 0.88f))
    val midColor = blendColors(Color.parseColor("#0A1324"), accentColor, 0.48f)
    val baseColor = Color.parseColor("#08111F")

    paint.shader =
        LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(baseColor, midColor, accentColor),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )

    val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
    return bitmap
  }

  private fun comparisonText(
      context: Context,
      labelRes: Int,
      comparisonTenths: Int?,
  ): String {
    val value =
        comparisonTenths?.let {
          val sign = when {
            it > 0 -> "+"
            it < 0 -> "-"
            else -> ""
          }
          "$sign${formatTenths(abs(it))}"
        } ?: context.getString(R.string.widget_weight_compare_missing)
    return context.getString(labelRes, value)
  }

  private fun comparisonColor(comparisonTenths: Int?): Int =
      when {
        comparisonTenths == null || comparisonTenths == 0 -> NEUTRAL_COMPARISON_COLOR
        comparisonTenths > 0 -> GAIN_COMPARISON_COLOR
        else -> LOSS_COMPARISON_COLOR
      }

  private fun weightDigits(weightTenths: Int): List<String> {
    val clamped = weightTenths.coerceIn(0, MAX_WEIGHT_TENTHS)
    val wholeNumber = clamped / 10
    return listOf(
        ((wholeNumber / 100) % 10).toString(),
        ((wholeNumber / 10) % 10).toString(),
        (wholeNumber % 10).toString(),
        (clamped % 10).toString(),
    )
  }

  private fun formatTenths(value: Int): String = "${value / 10}.${value % 10}"

  private fun formatSteps(value: Int): String = NumberFormat.getIntegerInstance().format(value)

  private fun blendColors(from: Int, to: Int, ratio: Float): Int {
    val clamped = ratio.coerceIn(0f, 1f)
    val inverse = 1f - clamped
    val alpha = (Color.alpha(from) * inverse + Color.alpha(to) * clamped).toInt()
    val red = (Color.red(from) * inverse + Color.red(to) * clamped).toInt()
    val green = (Color.green(from) * inverse + Color.green(to) * clamped).toInt()
    val blue = (Color.blue(from) * inverse + Color.blue(to) * clamped).toInt()
    return Color.argb(alpha, red, green, blue)
  }

  private fun dpToPx(context: Context, dp: Int): Int =
      (dp * context.resources.displayMetrics.density).toInt()

  private data class WidgetPresentation(
      val contentHorizontalPaddingDp: Int,
      val contentVerticalPaddingDp: Int,
      val columnGapDp: Int,
      val labelSp: Float,
      val labelMaxLines: Int,
      val remainingValueSp: Float,
      val mealNameSp: Float,
      val mealValueSp: Float,
      val metaSp: Float,
      val macroSp: Float,
      val stepsValueSp: Float,
      val stepsLabelSp: Float,
      val weightDigitSp: Float,
      val weightComparisonSp: Float,
      val metaMaxLines: Int,
      val lastMealNameMaxLines: Int,
      val secondaryMaxLines: Int,
      val supportingMaxLines: Int,
      val buttonSp: Float,
      val buttonHorizontalPaddingDp: Int,
      val buttonVerticalPaddingDp: Int,
      val bottomActionsMarginTopDp: Int,
  )

  private val WEIGHT_DIGIT_VIEW_IDS =
      intArrayOf(
          R.id.widget_weight_digit_0,
          R.id.widget_weight_digit_1,
          R.id.widget_weight_digit_2,
          R.id.widget_weight_digit_3,
      )

  private const val TAG = "SculptWidgetRenderer"
  private const val REQUEST_OPEN_APP = 1001
  private const val REQUEST_CYCLE_STEP = 1002
  private const val REQUEST_INCREASE_REMAINING = 1003
  private const val REQUEST_DECREASE_REMAINING = 1004
  private const val REQUEST_INCREASE_LAST_MEAL = 1005
  private const val REQUEST_DECREASE_LAST_MEAL = 1006
  private const val REQUEST_NO_OP = 1007
  private const val REQUEST_SELECT_WEIGHT_DIGIT_BASE = 1100
  private const val MAX_WEIGHT_TENTHS = 9999
  private const val STEP_PLACEHOLDER = "--"
  private val GAIN_COMPARISON_COLOR = Color.parseColor("#F87171")
  private val LOSS_COMPARISON_COLOR = Color.parseColor("#4ADE80")
  private val NEUTRAL_COMPARISON_COLOR = Color.parseColor("#D9FFFFFF")
  private val UNSELECTED_WEIGHT_DIGIT_COLOR = Color.WHITE
}

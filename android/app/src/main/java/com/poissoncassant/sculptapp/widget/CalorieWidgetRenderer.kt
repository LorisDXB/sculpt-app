package com.poissoncassant.sculptapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.widget.RemoteViews
import com.poissoncassant.sculptapp.AddMealEntryActivity
import com.poissoncassant.sculptapp.R
import kotlin.math.max

object CalorieWidgetRenderer {
  fun render(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    val state = WidgetStateRepository(context).readState()
    val lastMeal = state.lastMeal
    val macroBottomPadding = dpToPx(context, 20)
    val statusBottomPadding = dpToPx(context, 12)

    Log.d(
        TAG,
        "render widgetIds=${appWidgetIds.joinToString()} date=${state.date} consumed=${state.caloriesConsumedToday} target=${state.dailyCalorieTarget} status=${state.analysisStatus}",
    )
    WidgetRefreshScheduler.scheduleNextMidnightRefresh(context)

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
      views.setImageViewBitmap(
          R.id.widget_background_image,
          createBackgroundBitmap(state),
      )
      applyPresentation(context, views, presentation)
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

      if (state.analysisStatus == AnalysisStatus.ANALYZING) {
        views.setViewVisibility(R.id.widget_last_meal_label, android.view.View.VISIBLE)
        views.setTextViewText(R.id.widget_last_meal_name, context.getString(R.string.widget_analysis_in_progress))
        views.setTextViewText(R.id.widget_last_meal_value, "")
        views.setTextViewText(R.id.widget_macro_value, context.getString(R.string.widget_analysis_busy_hint))
        views.setViewPadding(R.id.widget_macro_value, 0, 8, 0, statusBottomPadding)
        views.setViewVisibility(R.id.widget_last_meal_increase_zone, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.widget_last_meal_decrease_zone, android.view.View.VISIBLE)
      } else if (state.analysisStatus == AnalysisStatus.ERROR) {
        views.setViewVisibility(R.id.widget_last_meal_label, android.view.View.VISIBLE)
        views.setTextViewText(R.id.widget_last_meal_name, context.getString(R.string.widget_analysis_error_title))
        views.setTextViewText(R.id.widget_last_meal_value, "")
        views.setTextViewText(
            R.id.widget_macro_value,
            state.analysisMessage ?: context.getString(R.string.widget_analysis_error_fallback),
        )
        views.setViewPadding(R.id.widget_macro_value, 0, 8, 0, statusBottomPadding)
        views.setViewVisibility(R.id.widget_last_meal_increase_zone, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.widget_last_meal_decrease_zone, android.view.View.VISIBLE)
      } else if (lastMeal == null) {
        views.setViewVisibility(R.id.widget_last_meal_label, android.view.View.INVISIBLE)
        views.setTextViewText(R.id.widget_last_meal_name, "")
        views.setTextViewText(R.id.widget_last_meal_value, "")
        views.setTextViewText(R.id.widget_macro_value, "")
        views.setViewPadding(R.id.widget_macro_value, 0, 8, 0, macroBottomPadding)
        views.setViewVisibility(R.id.widget_last_meal_increase_zone, android.view.View.INVISIBLE)
        views.setViewVisibility(R.id.widget_last_meal_decrease_zone, android.view.View.INVISIBLE)
      } else {
        views.setViewVisibility(R.id.widget_last_meal_label, android.view.View.VISIBLE)
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
        views.setViewPadding(R.id.widget_macro_value, 0, 8, 0, 0)
        views.setViewVisibility(R.id.widget_last_meal_increase_zone, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.widget_last_meal_decrease_zone, android.view.View.VISIBLE)
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
        dpToPx(context, presentation.contentBottomInsetDp),
    )
    views.setViewPadding(
        R.id.widget_right_column,
        dpToPx(context, presentation.columnGapDp),
        0,
        0,
        dpToPx(context, presentation.contentBottomInsetDp),
    )

    views.setTextViewTextSize(R.id.widget_remaining_value, TypedValue.COMPLEX_UNIT_SP, presentation.remainingValueSp)
    views.setTextViewTextSize(R.id.widget_remaining_meta, TypedValue.COMPLEX_UNIT_SP, presentation.metaSp)
    views.setTextViewTextSize(R.id.widget_total_macro_value, TypedValue.COMPLEX_UNIT_SP, presentation.macroSp)
    views.setTextViewTextSize(R.id.widget_last_meal_name, TypedValue.COMPLEX_UNIT_SP, presentation.mealNameSp)
    views.setTextViewTextSize(R.id.widget_last_meal_value, TypedValue.COMPLEX_UNIT_SP, presentation.mealValueSp)
    views.setTextViewTextSize(R.id.widget_macro_value, TypedValue.COMPLEX_UNIT_SP, presentation.macroSp)
    views.setTextViewTextSize(R.id.widget_open_app_button, TypedValue.COMPLEX_UNIT_SP, presentation.buttonSp)
    views.setTextViewTextSize(R.id.widget_step_button, TypedValue.COMPLEX_UNIT_SP, presentation.buttonSp)
    views.setInt(R.id.widget_last_meal_name, "setMaxLines", presentation.lastMealNameMaxLines)
    views.setInt(R.id.widget_macro_value, "setMaxLines", presentation.lastMealMacroMaxLines)

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

    val macrosVisibility =
        if (presentation.showTotalMacros) android.view.View.VISIBLE else android.view.View.GONE
    views.setViewVisibility(R.id.widget_total_macro_value, macrosVisibility)
    views.setViewVisibility(
        R.id.widget_last_meal_label,
        if (presentation.showLastMealLabel) android.view.View.VISIBLE else android.view.View.GONE,
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
              contentBottomInsetDp = 24,
              remainingValueSp = 31f,
              mealNameSp = 17f,
              mealValueSp = 23f,
              metaSp = 12f,
              macroSp = 12f,
              lastMealNameMaxLines = 2,
              lastMealMacroMaxLines = 2,
              buttonSp = 13f,
              buttonHorizontalPaddingDp = 12,
              buttonVerticalPaddingDp = 10,
              bottomActionsMarginTopDp = 12,
              showTotalMacros = true,
              showLastMealLabel = true,
          )
      minHeightDp <= 125 || minWidthDp <= 250 ->
          WidgetPresentation(
              contentHorizontalPaddingDp = 13,
              contentVerticalPaddingDp = 13,
              columnGapDp = 7,
              contentBottomInsetDp = 14,
              remainingValueSp = 20f,
              mealNameSp = 10f,
              mealValueSp = 12f,
              metaSp = 10f,
              macroSp = 9f,
              lastMealNameMaxLines = 1,
              lastMealMacroMaxLines = 1,
              buttonSp = 12f,
              buttonHorizontalPaddingDp = 10,
              buttonVerticalPaddingDp = 8,
              bottomActionsMarginTopDp = 8,
              showTotalMacros = false,
              showLastMealLabel = false,
          )
      else ->
          WidgetPresentation(
              contentHorizontalPaddingDp = 16,
              contentVerticalPaddingDp = 16,
              columnGapDp = 10,
              contentBottomInsetDp = 20,
              remainingValueSp = 28f,
              mealNameSp = 16f,
              mealValueSp = 22f,
              metaSp = 12f,
              macroSp = 12f,
              lastMealNameMaxLines = 2,
              lastMealMacroMaxLines = 2,
              buttonSp = 13f,
              buttonHorizontalPaddingDp = 12,
              buttonVerticalPaddingDp = 10,
              bottomActionsMarginTopDp = 12,
              showTotalMacros = true,
              showLastMealLabel = true,
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
      val contentBottomInsetDp: Int,
      val remainingValueSp: Float,
      val mealNameSp: Float,
      val mealValueSp: Float,
      val metaSp: Float,
      val macroSp: Float,
      val lastMealNameMaxLines: Int,
      val lastMealMacroMaxLines: Int,
      val buttonSp: Float,
      val buttonHorizontalPaddingDp: Int,
      val buttonVerticalPaddingDp: Int,
      val bottomActionsMarginTopDp: Int,
      val showTotalMacros: Boolean,
      val showLastMealLabel: Boolean,
  )

  private const val TAG = "SculptWidgetRenderer"
  private const val REQUEST_OPEN_APP = 1001
  private const val REQUEST_CYCLE_STEP = 1002
  private const val REQUEST_INCREASE_REMAINING = 1003
  private const val REQUEST_DECREASE_REMAINING = 1004
  private const val REQUEST_INCREASE_LAST_MEAL = 1005
  private const val REQUEST_DECREASE_LAST_MEAL = 1006
  private const val REQUEST_NO_OP = 1007
}

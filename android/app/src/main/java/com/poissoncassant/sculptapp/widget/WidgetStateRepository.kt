package com.poissoncassant.sculptapp.widget

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WidgetStateRepository(context: Context) {
  private val appContext = context.applicationContext
  private val preferences =
      appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun readState(): CalorieWidgetState {
    val today = LocalDate.now(ZoneId.systemDefault()).toString()
    val storedDate = preferences.getString(KEY_DATE, null)

    return when {
      storedDate == null -> {
        val initialState = defaultState(today)
        persistState(initialState)
        Log.d(TAG, "Initialized widget state for date=$today")
        initialState
      }
      storedDate != today -> {
        Log.d(TAG, "Date rollover detected storedDate=$storedDate newDate=$today, resetting daily state")
        val resetState =
            readPersistedState(storedDate).copy(
                date = today,
                caloriesConsumedToday = 0,
                totalProteinGrams = 0,
                totalCarbsGrams = 0,
                totalFatGrams = 0,
                lastMeal = null,
                analysisStatus = AnalysisStatus.IDLE,
                analysisMessage = null,
            )
        persistState(resetState)
        resetState
      }
      else -> readPersistedState(today)
    }
  }

  fun logSampleMeal() {
    val current = readState()
    val sampleIndex = preferences.getInt(KEY_SAMPLE_INDEX, 0)
    val sampleTemplate = SAMPLE_MEALS[sampleIndex % SAMPLE_MEALS.size]
    val sampleMeal =
        LastMealState(
            timestamp = Instant.now().toString(),
            mealName = sampleTemplate.mealName,
            calories = sampleTemplate.calories,
            proteinGrams = sampleTemplate.proteinGrams,
            carbsGrams = sampleTemplate.carbsGrams,
            fatGrams = sampleTemplate.fatGrams,
        )
    val previousMealCalories = current.lastMeal?.calories ?: 0
    val previousMealProtein = current.lastMeal?.proteinGrams ?: 0
    val previousMealCarbs = current.lastMeal?.carbsGrams ?: 0
    val previousMealFat = current.lastMeal?.fatGrams ?: 0
    val nextConsumed =
        (current.caloriesConsumedToday - previousMealCalories + sampleMeal.calories).coerceAtLeast(0)

    persistState(
        current.copy(
            caloriesConsumedToday = nextConsumed,
            totalProteinGrams =
                (current.totalProteinGrams - previousMealProtein + sampleMeal.proteinGrams)
                    .coerceAtLeast(0),
            totalCarbsGrams =
                (current.totalCarbsGrams - previousMealCarbs + sampleMeal.carbsGrams)
                    .coerceAtLeast(0),
            totalFatGrams =
                (current.totalFatGrams - previousMealFat + sampleMeal.fatGrams).coerceAtLeast(0),
            lastMeal = sampleMeal,
        ),
    )

    preferences.edit().putInt(KEY_SAMPLE_INDEX, (sampleIndex + 1) % SAMPLE_MEALS.size).apply()
  }

  fun cycleAdjustmentStep() {
    val current = readState()
    val currentIndex = ADJUSTMENT_STEPS.indexOf(current.adjustmentStep).takeIf { it >= 0 } ?: 0
    val nextStep = ADJUSTMENT_STEPS[(currentIndex + 1) % ADJUSTMENT_STEPS.size]
    persistState(current.copy(adjustmentStep = nextStep))
  }

  fun setDailyCalorieTarget(target: Int) {
    val current = readState()
    persistState(current.copy(dailyCalorieTarget = target.coerceAtLeast(0)))
  }

  fun adjustCaloriesRemaining(increase: Boolean) {
    val current = readState()
    val delta = if (increase) current.adjustmentStep else -current.adjustmentStep
    val nextTarget = (current.dailyCalorieTarget + delta).coerceAtLeast(0)
    persistState(current.copy(dailyCalorieTarget = nextTarget))
  }

  fun adjustLastMealCalories(increase: Boolean) {
    val current = readState()
    val lastMeal = current.lastMeal ?: return
    val delta = if (increase) current.adjustmentStep else -current.adjustmentStep
    val nextMealCalories = (lastMeal.calories + delta).coerceAtLeast(0)
    val nextScaledMeal = scaleMeal(lastMeal, nextMealCalories)
    val appliedDelta = nextScaledMeal.calories - lastMeal.calories
    val nextConsumed = (current.caloriesConsumedToday + appliedDelta).coerceAtLeast(0)

    persistState(
        current.copy(
            caloriesConsumedToday = nextConsumed,
            totalProteinGrams =
                (current.totalProteinGrams + nextScaledMeal.proteinGrams - lastMeal.proteinGrams)
                    .coerceAtLeast(0),
            totalCarbsGrams =
                (current.totalCarbsGrams + nextScaledMeal.carbsGrams - lastMeal.carbsGrams)
                    .coerceAtLeast(0),
            totalFatGrams =
                (current.totalFatGrams + nextScaledMeal.fatGrams - lastMeal.fatGrams)
                    .coerceAtLeast(0),
            lastMeal = nextScaledMeal,
        ),
    )
  }

  fun resetToday() {
    val current = readState()
    persistState(
        defaultState(LocalDate.now(ZoneId.systemDefault()).toString()).copy(
            dailyCalorieTarget = current.dailyCalorieTarget,
            adjustmentStep = current.adjustmentStep,
        ),
    )
  }

  fun clearAllLocalData() {
    deleteStoredCaptureFiles()
    preferences.edit().clear().apply()
  }

  fun markAnalysisStarted() {
    val current = readState()
    persistState(
        current.copy(
            analysisStatus = AnalysisStatus.ANALYZING,
            analysisMessage = "Analyzing...",
        ),
    )
  }

  fun markAnalysisFailed(message: String) {
    val current = readState()
    persistState(
        current.copy(
            analysisStatus = AnalysisStatus.ERROR,
            analysisMessage = message,
        ),
    )
  }

  fun clearAnalysisFeedback() {
    val current = readState()
    persistState(
        current.copy(
            analysisStatus = AnalysisStatus.IDLE,
            analysisMessage = null,
        ),
    )
  }

  fun logAnalyzedMeal(
      mealName: String,
      calories: Int,
      proteinGrams: Int,
      carbsGrams: Int,
      fatGrams: Int,
      timestamp: String,
  ) {
    val current = readState()
    val sanitizedCalories = calories.coerceAtLeast(0)
    val nextMeal =
        LastMealState(
            timestamp = timestamp,
            mealName = mealName.ifBlank { "Meal" },
            calories = sanitizedCalories,
            proteinGrams = proteinGrams.coerceAtLeast(0),
            carbsGrams = carbsGrams.coerceAtLeast(0),
            fatGrams = fatGrams.coerceAtLeast(0),
        )

    persistState(
        current.copy(
            caloriesConsumedToday = (current.caloriesConsumedToday + sanitizedCalories).coerceAtLeast(0),
            totalProteinGrams = (current.totalProteinGrams + nextMeal.proteinGrams).coerceAtLeast(0),
            totalCarbsGrams = (current.totalCarbsGrams + nextMeal.carbsGrams).coerceAtLeast(0),
            totalFatGrams = (current.totalFatGrams + nextMeal.fatGrams).coerceAtLeast(0),
            lastMeal = nextMeal,
            analysisStatus = AnalysisStatus.IDLE,
            analysisMessage = null,
        ),
    )
  }

  fun saveCapturedImage(rawImagePath: String?, compressedImagePath: String, capturedAt: String) {
    deleteFileIfExists(preferences.getString(KEY_LAST_CAPTURED_RAW_PATH, null))
    deleteFileIfExists(preferences.getString(KEY_LAST_CAPTURED_COMPRESSED_PATH, null))
    preferences
        .edit()
        .putString(KEY_LAST_CAPTURED_AT, capturedAt)
        .putString(KEY_LAST_CAPTURED_RAW_PATH, rawImagePath)
        .putString(KEY_LAST_CAPTURED_COMPRESSED_PATH, compressedImagePath)
        .apply()
  }

  private fun scaleMeal(lastMeal: LastMealState, nextCalories: Int): LastMealState {
    if (nextCalories == 0) {
      return lastMeal.copy(calories = 0, proteinGrams = 0, carbsGrams = 0, fatGrams = 0)
    }

    if (lastMeal.calories <= 0) {
      return lastMeal.copy(
          calories = nextCalories,
          proteinGrams = ((nextCalories * 0.30) / 4.0).toInt(),
          carbsGrams = ((nextCalories * 0.40) / 4.0).toInt(),
          fatGrams = ((nextCalories * 0.30) / 9.0).toInt(),
      )
    }

    val ratio = nextCalories.toDouble() / lastMeal.calories.toDouble()
    val protein = kotlin.math.round(lastMeal.proteinGrams * ratio).toInt().coerceAtLeast(0)
    val carbs = kotlin.math.round(lastMeal.carbsGrams * ratio).toInt().coerceAtLeast(0)
    val fat = kotlin.math.round(lastMeal.fatGrams * ratio).toInt().coerceAtLeast(0)

    return lastMeal.copy(
        calories = nextCalories,
        proteinGrams = protein,
        carbsGrams = carbs,
        fatGrams = fat,
    )
  }

  private fun readPersistedState(date: String): CalorieWidgetState =
      CalorieWidgetState(
          date = date,
          dailyCalorieTarget = preferences.getInt(KEY_DAILY_CALORIE_TARGET, DEFAULT_DAILY_TARGET),
          caloriesConsumedToday = preferences.getInt(KEY_CALORIES_CONSUMED_TODAY, 0),
          totalProteinGrams = preferences.getInt(KEY_TOTAL_PROTEIN_GRAMS, 0),
          totalCarbsGrams = preferences.getInt(KEY_TOTAL_CARBS_GRAMS, 0),
          totalFatGrams = preferences.getInt(KEY_TOTAL_FAT_GRAMS, 0),
          adjustmentStep = preferences.getInt(KEY_ADJUSTMENT_STEP, DEFAULT_ADJUSTMENT_STEP),
          analysisStatus =
              preferences.getString(KEY_ANALYSIS_STATUS, AnalysisStatus.IDLE.name)?.let {
                runCatching { AnalysisStatus.valueOf(it) }.getOrDefault(AnalysisStatus.IDLE)
              } ?: AnalysisStatus.IDLE,
          analysisMessage = preferences.getString(KEY_ANALYSIS_MESSAGE, null),
          lastMeal =
              if (!preferences.getBoolean(KEY_HAS_LAST_MEAL, false)) {
                null
              } else {
                LastMealState(
                    timestamp =
                        preferences.getString(KEY_LAST_MEAL_TIMESTAMP, "") ?: "",
                    mealName = preferences.getString(KEY_LAST_MEAL_NAME, "") ?: "",
                    calories = preferences.getInt(KEY_LAST_MEAL_CALORIES, 0),
                    proteinGrams = preferences.getInt(KEY_PROTEIN_GRAMS, 0),
                    carbsGrams = preferences.getInt(KEY_CARBS_GRAMS, 0),
                    fatGrams = preferences.getInt(KEY_FAT_GRAMS, 0),
                )
              },
      )

  private fun persistState(state: CalorieWidgetState) {
    preferences
        .edit()
        .putString(KEY_DATE, state.date)
        .putInt(KEY_DAILY_CALORIE_TARGET, state.dailyCalorieTarget)
        .putInt(KEY_CALORIES_CONSUMED_TODAY, state.caloriesConsumedToday)
        .putInt(KEY_TOTAL_PROTEIN_GRAMS, state.totalProteinGrams)
        .putInt(KEY_TOTAL_CARBS_GRAMS, state.totalCarbsGrams)
        .putInt(KEY_TOTAL_FAT_GRAMS, state.totalFatGrams)
        .putInt(KEY_ADJUSTMENT_STEP, state.adjustmentStep)
        .putBoolean(KEY_HAS_LAST_MEAL, state.lastMeal != null)
        .putString(KEY_ANALYSIS_STATUS, state.analysisStatus.name)
        .apply {
          if (state.analysisMessage.isNullOrBlank()) {
            remove(KEY_ANALYSIS_MESSAGE)
          } else {
            putString(KEY_ANALYSIS_MESSAGE, state.analysisMessage)
          }
          if (state.lastMeal == null) {
            remove(KEY_LAST_MEAL_TIMESTAMP)
            remove(KEY_LAST_MEAL_NAME)
            remove(KEY_LAST_MEAL_CALORIES)
            remove(KEY_PROTEIN_GRAMS)
            remove(KEY_CARBS_GRAMS)
            remove(KEY_FAT_GRAMS)
          } else {
            putString(KEY_LAST_MEAL_TIMESTAMP, state.lastMeal.timestamp)
            putString(KEY_LAST_MEAL_NAME, state.lastMeal.mealName)
            putInt(KEY_LAST_MEAL_CALORIES, state.lastMeal.calories)
            putInt(KEY_PROTEIN_GRAMS, state.lastMeal.proteinGrams)
            putInt(KEY_CARBS_GRAMS, state.lastMeal.carbsGrams)
            putInt(KEY_FAT_GRAMS, state.lastMeal.fatGrams)
          }
        }
        .apply()
  }

  private fun defaultState(date: String): CalorieWidgetState =
      CalorieWidgetState(
          date = date,
          dailyCalorieTarget = DEFAULT_DAILY_TARGET,
          caloriesConsumedToday = 0,
          totalProteinGrams = 0,
          totalCarbsGrams = 0,
          totalFatGrams = 0,
          adjustmentStep = DEFAULT_ADJUSTMENT_STEP,
          lastMeal = null,
          analysisStatus = AnalysisStatus.IDLE,
          analysisMessage = null,
      )

  private fun deleteStoredCaptureFiles() {
    deleteFileIfExists(preferences.getString(KEY_LAST_CAPTURED_RAW_PATH, null))
    deleteFileIfExists(preferences.getString(KEY_LAST_CAPTURED_COMPRESSED_PATH, null))
  }

  private fun deleteFileIfExists(path: String?) {
    if (path.isNullOrBlank()) {
      return
    }

    runCatching { File(path).delete() }
  }

  companion object {
    private const val PREFERENCES_NAME = "sculpt_widget_state"
    private const val KEY_DATE = "date"
    private const val KEY_DAILY_CALORIE_TARGET = "daily_calorie_target"
    private const val KEY_CALORIES_CONSUMED_TODAY = "calories_consumed_today"
    private const val KEY_TOTAL_PROTEIN_GRAMS = "total_protein_grams"
    private const val KEY_TOTAL_CARBS_GRAMS = "total_carbs_grams"
    private const val KEY_TOTAL_FAT_GRAMS = "total_fat_grams"
    private const val KEY_ADJUSTMENT_STEP = "adjustment_step"
    private const val KEY_HAS_LAST_MEAL = "has_last_meal"
    private const val KEY_LAST_MEAL_TIMESTAMP = "last_meal_timestamp"
    private const val KEY_LAST_MEAL_NAME = "last_meal_name"
    private const val KEY_LAST_MEAL_CALORIES = "last_meal_calories"
    private const val KEY_PROTEIN_GRAMS = "protein_grams"
    private const val KEY_CARBS_GRAMS = "carbs_grams"
    private const val KEY_FAT_GRAMS = "fat_grams"
    private const val KEY_SAMPLE_INDEX = "sample_index"
    private const val KEY_LAST_CAPTURED_AT = "last_captured_at"
    private const val KEY_LAST_CAPTURED_RAW_PATH = "last_captured_raw_path"
    private const val KEY_LAST_CAPTURED_COMPRESSED_PATH = "last_captured_compressed_path"
    private const val KEY_ANALYSIS_STATUS = "analysis_status"
    private const val KEY_ANALYSIS_MESSAGE = "analysis_message"

    private const val DEFAULT_DAILY_TARGET = 2500
    private const val DEFAULT_ADJUSTMENT_STEP = 50
    private const val TAG = "SculptWidgetState"
    private val ADJUSTMENT_STEPS = listOf(10, 50, 100, 250)

    private val SAMPLE_MEALS =
        listOf(
            LastMealState(
                timestamp = "",
                mealName = "Chicken rice bowl",
                calories = 620,
                proteinGrams = 42,
                carbsGrams = 58,
                fatGrams = 18,
            ),
            LastMealState(
                timestamp = "",
                mealName = "Salmon avocado plate",
                calories = 710,
                proteinGrams = 39,
                carbsGrams = 28,
                fatGrams = 41,
            ),
            LastMealState(
                timestamp = "",
                mealName = "Pasta pesto lunch",
                calories = 840,
                proteinGrams = 27,
                carbsGrams = 96,
                fatGrams = 33,
            ),
        )
  }
}

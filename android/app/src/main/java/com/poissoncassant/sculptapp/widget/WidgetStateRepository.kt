package com.poissoncassant.sculptapp.widget

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WidgetStateRepository(context: Context) {
  private val preferences =
      context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun readState(): CalorieWidgetState {
    val today = LocalDate.now(ZoneId.systemDefault()).toString()
    val storedDate = preferences.getString(KEY_DATE, null)

    return when {
      storedDate == null -> {
        val initialState = defaultState(today)
        persistState(initialState)
        initialState
      }
      storedDate != today -> {
        val resetState = readPersistedState(storedDate).copy(date = today, caloriesConsumedToday = 0, lastMeal = null)
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
    val nextConsumed =
        (current.caloriesConsumedToday - previousMealCalories + sampleMeal.calories).coerceAtLeast(0)

    persistState(
        current.copy(
            caloriesConsumedToday = nextConsumed,
            lastMeal = sampleMeal,
        ),
    )

    preferences.edit().putInt(KEY_SAMPLE_INDEX, (sampleIndex + 1) % SAMPLE_MEALS.size).apply()
  }

  fun resetToday() {
    persistState(defaultState(LocalDate.now(ZoneId.systemDefault()).toString()))
  }

  private fun readPersistedState(date: String): CalorieWidgetState =
      CalorieWidgetState(
          date = date,
          dailyCalorieTarget = preferences.getInt(KEY_DAILY_CALORIE_TARGET, DEFAULT_DAILY_TARGET),
          caloriesConsumedToday = preferences.getInt(KEY_CALORIES_CONSUMED_TODAY, 0),
          adjustmentStep = preferences.getInt(KEY_ADJUSTMENT_STEP, DEFAULT_ADJUSTMENT_STEP),
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
        .putInt(KEY_ADJUSTMENT_STEP, state.adjustmentStep)
        .putBoolean(KEY_HAS_LAST_MEAL, state.lastMeal != null)
        .apply {
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
          adjustmentStep = DEFAULT_ADJUSTMENT_STEP,
          lastMeal = null,
      )

  companion object {
    private const val PREFERENCES_NAME = "sculpt_widget_state"
    private const val KEY_DATE = "date"
    private const val KEY_DAILY_CALORIE_TARGET = "daily_calorie_target"
    private const val KEY_CALORIES_CONSUMED_TODAY = "calories_consumed_today"
    private const val KEY_ADJUSTMENT_STEP = "adjustment_step"
    private const val KEY_HAS_LAST_MEAL = "has_last_meal"
    private const val KEY_LAST_MEAL_TIMESTAMP = "last_meal_timestamp"
    private const val KEY_LAST_MEAL_NAME = "last_meal_name"
    private const val KEY_LAST_MEAL_CALORIES = "last_meal_calories"
    private const val KEY_PROTEIN_GRAMS = "protein_grams"
    private const val KEY_CARBS_GRAMS = "carbs_grams"
    private const val KEY_FAT_GRAMS = "fat_grams"
    private const val KEY_SAMPLE_INDEX = "sample_index"

    private const val DEFAULT_DAILY_TARGET = 2500
    private const val DEFAULT_ADJUSTMENT_STEP = 50

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

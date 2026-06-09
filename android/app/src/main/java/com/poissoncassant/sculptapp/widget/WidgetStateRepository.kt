package com.poissoncassant.sculptapp.widget

import android.content.Context

class WidgetStateRepository(context: Context) {
  private val preferences =
      context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun readState(): CalorieWidgetState =
      CalorieWidgetState(
          caloriesRemaining = preferences.getInt(KEY_CALORIES_REMAINING, 1450),
          lastMealCalories = preferences.getInt(KEY_LAST_MEAL_CALORIES, 620),
          proteinGrams = preferences.getInt(KEY_PROTEIN_GRAMS, 42),
          carbsGrams = preferences.getInt(KEY_CARBS_GRAMS, 58),
          fatGrams = preferences.getInt(KEY_FAT_GRAMS, 18),
          adjustmentStep = preferences.getInt(KEY_ADJUSTMENT_STEP, 50),
      )

  fun cycleDemoState() {
    val current = readState()
    val nextRemaining = if (current.caloriesRemaining <= 950) 1450 else current.caloriesRemaining - 125
    val nextMeal = if (current.lastMealCalories >= 920) 620 else current.lastMealCalories + 75

    preferences
        .edit()
        .putInt(KEY_CALORIES_REMAINING, nextRemaining)
        .putInt(KEY_LAST_MEAL_CALORIES, nextMeal)
        .putInt(KEY_PROTEIN_GRAMS, scaleMacro(baseValue = 42, calories = nextMeal))
        .putInt(KEY_CARBS_GRAMS, scaleMacro(baseValue = 58, calories = nextMeal))
        .putInt(KEY_FAT_GRAMS, scaleMacro(baseValue = 18, calories = nextMeal))
        .putInt(KEY_ADJUSTMENT_STEP, current.adjustmentStep)
        .apply()
  }

  private fun scaleMacro(baseValue: Int, calories: Int): Int {
    val ratio = calories.toFloat() / 620f
    return (baseValue * ratio).toInt().coerceAtLeast(0)
  }

  companion object {
    private const val PREFERENCES_NAME = "sculpt_widget_state"
    private const val KEY_CALORIES_REMAINING = "calories_remaining"
    private const val KEY_LAST_MEAL_CALORIES = "last_meal_calories"
    private const val KEY_PROTEIN_GRAMS = "protein_grams"
    private const val KEY_CARBS_GRAMS = "carbs_grams"
    private const val KEY_FAT_GRAMS = "fat_grams"
    private const val KEY_ADJUSTMENT_STEP = "adjustment_step"
  }
}

package com.poissoncassant.sculptapp.widget

data class CalorieWidgetState(
    val date: String,
    val dailyCalorieTarget: Int,
    val caloriesConsumedToday: Int,
    val adjustmentStep: Int,
    val lastMeal: LastMealState?,
) {
  val caloriesRemaining: Int
    get() = dailyCalorieTarget - caloriesConsumedToday
}

data class LastMealState(
    val timestamp: String,
    val mealName: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
)

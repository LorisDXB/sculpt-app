package com.poissoncassant.sculptapp.widget

data class CalorieWidgetState(
    val caloriesRemaining: Int,
    val lastMealCalories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val adjustmentStep: Int,
)

package com.poissoncassant.sculptapp.ai

data class NutritionEstimate(
    val mealName: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val confidence: String,
)

data class ApiKeyValidationResult(
    val isValid: Boolean,
    val message: String,
)

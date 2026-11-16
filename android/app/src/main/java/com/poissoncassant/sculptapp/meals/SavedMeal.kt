package com.poissoncassant.sculptapp.meals

data class SavedMeal(
    val id: String,
    val name: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
)

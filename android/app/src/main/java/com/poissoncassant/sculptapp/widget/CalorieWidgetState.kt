package com.poissoncassant.sculptapp.widget

data class CalorieWidgetState(
    val date: String,
    val dailyCalorieTarget: Int,
    val caloriesConsumedToday: Int,
    val totalProteinGrams: Int,
    val totalCarbsGrams: Int,
    val totalFatGrams: Int,
    val adjustmentStep: Int,
    val lastMeal: LastMealState?,
    val analysisStatus: AnalysisStatus,
    val analysisMessage: String?,
    val currentWeightTenths: Int,
    val selectedWeightDigit: WeightDigit,
    val weightDeltaVsYesterdayTenths: Int?,
    val weightDeltaVsSevenDayAverageTenths: Int?,
) {
  val caloriesRemaining: Int
    get() = dailyCalorieTarget - caloriesConsumedToday
}

enum class AnalysisStatus {
  IDLE,
  ANALYZING,
  ERROR,
}

data class LastMealState(
    val timestamp: String,
    val mealName: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
)

enum class WeightDigit {
  HUNDREDS,
  TENS,
  ONES,
  TENTHS,
}

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
    val weightPanel: WeightPanelState,
) {
  val caloriesRemaining: Int
    get() = dailyCalorieTarget - caloriesConsumedToday

  val isWeightModeAvailable: Boolean
    get() = lastMeal == null && analysisStatus == AnalysisStatus.IDLE
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

data class WeightPanelState(
    val todayWeightTenths: Int?,
    val displayedWeightTenths: Int,
    val displayedWeightSource: WeightSource,
    val selectedDigitIndex: Int,
    val comparisonToYesterdayTenths: Int?,
    val comparisonToLastWeekTenths: Int?,
)

enum class WeightSource {
  TODAY,
  YESTERDAY,
  DEFAULT,
}

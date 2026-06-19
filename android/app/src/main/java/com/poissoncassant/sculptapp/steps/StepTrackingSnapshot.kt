package com.poissoncassant.sculptapp.steps

data class StepTrackingSnapshot(
    val date: String,
    val todaySteps: Int,
    val status: StepTrackingStatus,
    val baselineTotal: Int?,
    val lastSeenTotal: Int?,
    val lastUpdatedAtMillis: Long?,
    val lastSuccessfulRefreshAtMillis: Long?,
    val errorMessage: String?,
)

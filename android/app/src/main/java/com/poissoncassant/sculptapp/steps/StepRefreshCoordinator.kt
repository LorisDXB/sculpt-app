package com.poissoncassant.sculptapp.steps

import android.content.Context
import android.util.Log
import com.poissoncassant.sculptapp.widget.CalorieWidgetRenderer
import com.poissoncassant.sculptapp.widget.WidgetRefreshScheduler

class StepRefreshCoordinator(context: Context) {
  private val appContext = context.applicationContext
  private val repository = StepTrackingRepository(appContext)

  fun refreshNow(
      reason: String,
      forceStepReschedule: Boolean = false,
      forceFullWidgetRedraw: Boolean = false,
  ): StepTrackingSnapshot {
    Log.d(
        TAG,
        "refreshNow reason=$reason forceStepReschedule=$forceStepReschedule forceFullWidgetRedraw=$forceFullWidgetRedraw",
    )
    val beforeSnapshot = repository.readSnapshot()
    Log.d(
        TAG,
        "refreshNow before reason=$reason status=${beforeSnapshot.status} todaySteps=${beforeSnapshot.todaySteps} carried=${beforeSnapshot.carriedTodaySteps} baseline=${beforeSnapshot.baselineTotal} lastSeen=${beforeSnapshot.lastSeenTotal}",
    )
    val snapshot = repository.refreshCurrentStepSnapshot()
    WidgetRefreshScheduler.syncSchedules(appContext, forceStepReschedule = forceStepReschedule)
    CalorieWidgetRenderer.refreshAll(appContext, usePartialUpdate = !forceFullWidgetRedraw)
    Log.d(
        TAG,
        "refreshNow completed reason=$reason status=${snapshot.status} todaySteps=${snapshot.todaySteps} carried=${snapshot.carriedTodaySteps} baseline=${snapshot.baselineTotal} lastSeen=${snapshot.lastSeenTotal} lastSuccessAt=${snapshot.lastSuccessfulRefreshAtMillis}",
    )
    return snapshot
  }

  fun resetForNewDay(reason: String) {
    Log.d(TAG, "resetForNewDay reason=$reason")
    repository.resetForNewDay()
  }

  companion object {
    private const val TAG = "SculptStepCoordinator"
  }
}

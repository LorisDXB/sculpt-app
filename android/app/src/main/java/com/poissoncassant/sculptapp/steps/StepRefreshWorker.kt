package com.poissoncassant.sculptapp.steps

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.poissoncassant.sculptapp.widget.CalorieWidgetRenderer
import com.poissoncassant.sculptapp.widget.WidgetRefreshScheduler
import com.poissoncassant.sculptapp.widget.WidgetStateRepository

class StepRefreshWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {
  override fun doWork(): Result {
    WidgetStateRepository(applicationContext).refreshTodayStepsFromSensor()
    CalorieWidgetRenderer.refreshAll(applicationContext)
    WidgetRefreshScheduler.syncSchedules(applicationContext)
    return Result.success()
  }
}

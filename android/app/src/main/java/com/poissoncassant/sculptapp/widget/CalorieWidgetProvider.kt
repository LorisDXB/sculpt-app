package com.poissoncassant.sculptapp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class CalorieWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
  ) {
    CalorieWidgetRenderer.render(context, appWidgetManager, appWidgetIds)
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)

    if (intent.action == ACTION_REFRESH_DEMO) {
      WidgetStateRepository(context).cycleDemoState()
      CalorieWidgetRenderer.refreshAll(context)
    }
  }

  companion object {
    const val ACTION_REFRESH_DEMO = "com.poissoncassant.sculptapp.widget.ACTION_REFRESH_DEMO"
  }
}

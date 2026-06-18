package com.poissoncassant.sculptapp.bridge

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.poissoncassant.sculptapp.ai.NutritionApiClient
import com.poissoncassant.sculptapp.config.AppConfigRepository
import com.poissoncassant.sculptapp.widget.CalorieWidgetRenderer
import com.poissoncassant.sculptapp.widget.WidgetStateRepository

class SculptSettingsModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {
  override fun getName(): String = "SculptSettings"

  @ReactMethod
  fun getSettings(promise: Promise) {
    try {
      promise.resolve(buildSettingsMap())
    } catch (exception: Exception) {
      promise.reject("settings_read_failed", exception)
    }
  }

  @ReactMethod
  fun setDailyCalorieTarget(target: Double, promise: Promise) {
    try {
      WidgetStateRepository(reactApplicationContext).setDailyCalorieTarget(target.toInt())
      CalorieWidgetRenderer.refreshAll(reactApplicationContext)
      promise.resolve(buildSettingsMap())
    } catch (exception: Exception) {
      promise.reject("daily_target_update_failed", exception)
    }
  }

  @ReactMethod
  fun validateAndStoreApiKey(apiKey: String, promise: Promise) {
    Thread {
      val trimmedKey = apiKey.trim()
      if (trimmedKey.isBlank()) {
        promise.reject("api_key_empty", "API key is required.")
        return@Thread
      }

      val repository = AppConfigRepository(reactApplicationContext)
      val validation = NutritionApiClient().validateApiKey(trimmedKey)

      if (validation.isValid) {
        repository.saveValidatedApiKey(trimmedKey, validation.message)
        promise.resolve(buildSettingsMap())
      } else {
        repository.saveInvalidApiKeyAttempt(trimmedKey, validation.message)
        promise.reject("api_key_invalid", validation.message)
      }
    }.start()
  }

  @ReactMethod
  fun clearApiKey(promise: Promise) {
    try {
      AppConfigRepository(reactApplicationContext).clearApiKey()
      promise.resolve(buildSettingsMap())
    } catch (exception: Exception) {
      promise.reject("api_key_clear_failed", exception)
    }
  }

  @ReactMethod
  fun resetToday(promise: Promise) {
    try {
      WidgetStateRepository(reactApplicationContext).resetToday()
      CalorieWidgetRenderer.refreshAll(reactApplicationContext, usePartialUpdate = false)
      promise.resolve(buildSettingsMap())
    } catch (exception: Exception) {
      promise.reject("reset_today_failed", exception)
    }
  }

  @ReactMethod
  fun setDefaultWeight(weight: Double, promise: Promise) {
    try {
      if (!weight.isFinite() || weight < 0) {
        promise.reject("default_weight_invalid", "Default weight must be a positive number.")
        return
      }

      AppConfigRepository(reactApplicationContext).saveDefaultWeightTenths((weight * 10.0).toInt())
      CalorieWidgetRenderer.refreshAll(reactApplicationContext)
      promise.resolve(buildSettingsMap())
    } catch (exception: Exception) {
      promise.reject("default_weight_update_failed", exception)
    }
  }

  @ReactMethod
  fun clearAllLocalData(promise: Promise) {
    try {
      WidgetStateRepository(reactApplicationContext).clearAllLocalData()
      AppConfigRepository(reactApplicationContext).clearAll()
      CalorieWidgetRenderer.refreshAll(reactApplicationContext)
      promise.resolve(buildSettingsMap())
    } catch (exception: Exception) {
      promise.reject("clear_all_local_data_failed", exception)
    }
  }

  private fun buildSettingsMap() =
      Arguments.createMap().apply {
        val widgetState = WidgetStateRepository(reactApplicationContext).readState()
        val config = AppConfigRepository(reactApplicationContext)

        putInt("dailyCalorieTarget", widgetState.dailyCalorieTarget)
        putInt("caloriesConsumedToday", widgetState.caloriesConsumedToday)
        putDouble("defaultWeight", config.getDefaultWeightTenths() / 10.0)
        putBoolean("hasValidatedApiKey", config.hasValidatedApiKey())
        putBoolean("hasApiKey", config.getApiKey() != null)
        putString("lastValidationMessage", config.getLastValidationMessage())
      }
}

package com.poissoncassant.sculptapp.config

import android.content.Context

class AppConfigRepository(context: Context) {
  private val preferences =
      context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun getApiKey(): String? = preferences.getString(KEY_OPENAI_API_KEY, null)?.takeIf { it.isNotBlank() }

  fun getDefaultWeightTenths(): Int =
      preferences.getInt(KEY_DEFAULT_WEIGHT_TENTHS, DEFAULT_WEIGHT_TENTHS).coerceIn(0, MAX_WEIGHT_TENTHS)

  fun getStepPollingSeconds(): Int =
      normalizeStepPollingSeconds(
          preferences.getInt(KEY_STEP_POLLING_SECONDS, DEFAULT_STEP_POLLING_SECONDS),
      )

  fun getNextStepRefreshAtMillis(): Long? =
      preferences.takeIf { preferences.contains(KEY_NEXT_STEP_REFRESH_AT_MILLIS) }
          ?.getLong(KEY_NEXT_STEP_REFRESH_AT_MILLIS, 0L)

  fun hasValidatedApiKey(): Boolean =
      getApiKey() != null && preferences.getBoolean(KEY_API_KEY_VALIDATED, false)

  fun getLastValidationMessage(): String? =
      preferences.getString(KEY_LAST_VALIDATION_MESSAGE, null)?.takeIf { it.isNotBlank() }

  fun saveValidatedApiKey(apiKey: String, message: String = "API key validated.") {
    preferences
        .edit()
        .putString(KEY_OPENAI_API_KEY, apiKey.trim())
        .putBoolean(KEY_API_KEY_VALIDATED, true)
        .putString(KEY_LAST_VALIDATION_MESSAGE, message)
        .apply()
  }

  fun saveInvalidApiKeyAttempt(apiKey: String, message: String) {
    preferences
        .edit()
        .putString(KEY_OPENAI_API_KEY, apiKey.trim())
        .putBoolean(KEY_API_KEY_VALIDATED, false)
        .putString(KEY_LAST_VALIDATION_MESSAGE, message)
        .apply()
  }

  fun saveDefaultWeightTenths(weightTenths: Int) {
    preferences
        .edit()
        .putInt(KEY_DEFAULT_WEIGHT_TENTHS, weightTenths.coerceIn(0, MAX_WEIGHT_TENTHS))
        .apply()
  }

  fun saveStepPollingSeconds(stepPollingSeconds: Int) {
    preferences
        .edit()
        .putInt(
            KEY_STEP_POLLING_SECONDS,
            normalizeStepPollingSeconds(stepPollingSeconds),
        )
        .apply()
  }

  fun saveNextStepRefreshAtMillis(nextStepRefreshAtMillis: Long) {
    preferences.edit().putLong(KEY_NEXT_STEP_REFRESH_AT_MILLIS, nextStepRefreshAtMillis).apply()
  }

  fun clearNextStepRefreshAtMillis() {
    preferences.edit().remove(KEY_NEXT_STEP_REFRESH_AT_MILLIS).apply()
  }

  fun clearApiKey() {
    preferences
        .edit()
        .remove(KEY_OPENAI_API_KEY)
        .putBoolean(KEY_API_KEY_VALIDATED, false)
        .remove(KEY_LAST_VALIDATION_MESSAGE)
        .apply()
  }

  fun clearAll() {
    preferences.edit().clear().apply()
  }

  companion object {
    val SUPPORTED_STEP_POLLING_SECONDS = listOf(30, 15 * 60, 30 * 60, 60 * 60, 120 * 60)

    private const val PREFERENCES_NAME = "sculpt_app_config"
    private const val KEY_OPENAI_API_KEY = "openai_api_key"
    private const val KEY_API_KEY_VALIDATED = "openai_api_key_validated"
    private const val KEY_LAST_VALIDATION_MESSAGE = "openai_last_validation_message"
    private const val KEY_DEFAULT_WEIGHT_TENTHS = "default_weight_tenths"
    private const val KEY_STEP_POLLING_SECONDS = "step_polling_seconds"
    private const val KEY_NEXT_STEP_REFRESH_AT_MILLIS = "next_step_refresh_at_millis"
    private const val DEFAULT_WEIGHT_TENTHS = 700
    private const val DEFAULT_STEP_POLLING_SECONDS = 30 * 60
    private const val MAX_WEIGHT_TENTHS = 9999

    private fun normalizeStepPollingSeconds(value: Int): Int =
        SUPPORTED_STEP_POLLING_SECONDS.minByOrNull { kotlin.math.abs(it - value) }
            ?: DEFAULT_STEP_POLLING_SECONDS
  }
}

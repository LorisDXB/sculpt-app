package com.poissoncassant.sculptapp.camera

import android.content.Context
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.poissoncassant.sculptapp.ai.NutritionApiClient
import com.poissoncassant.sculptapp.config.AppConfigRepository
import com.poissoncassant.sculptapp.widget.CalorieWidgetRenderer
import com.poissoncassant.sculptapp.widget.WidgetStateRepository
import java.io.File
import java.io.IOException
import java.time.Instant

class MealAnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {
  override fun doWork(): Result {
    val rawPhotoPath = inputData.getString(KEY_RAW_PHOTO_PATH)
    if (rawPhotoPath.isNullOrBlank()) {
      Log.e(TAG, "Missing raw photo path input")
      WidgetStateRepository(applicationContext).markAnalysisFailed("Could not analyze captured photo.")
      CalorieWidgetRenderer.refreshAll(applicationContext)
      return Result.failure()
    }

    val rawPhotoFile = File(rawPhotoPath)
    if (!rawPhotoFile.exists()) {
      Log.e(TAG, "Raw photo file missing at $rawPhotoPath")
      WidgetStateRepository(applicationContext).markAnalysisFailed("Photo capture file was missing.")
      CalorieWidgetRenderer.refreshAll(applicationContext)
      return Result.failure()
    }

    var compressedFile: File? = null

    return runCatching {
          val apiKey = AppConfigRepository(applicationContext).getApiKey()
          if (apiKey.isNullOrBlank()) {
            throw IOException("No validated API key available")
          }

          Log.d(TAG, "Compressing raw photo at $rawPhotoPath")
          compressedFile = ImageCompressor.compressToJpeg(applicationContext, rawPhotoFile)
          Log.d(TAG, "Analyzing compressed image at ${compressedFile?.absolutePath}")
          val estimate = NutritionApiClient().analyzeMealImage(apiKey, compressedFile!!)

          if (estimate.mealName.equals("not food", ignoreCase = true) || estimate.calories <= 0) {
            throw NotFoodException()
          }

          WidgetStateRepository(applicationContext).saveCapturedImage(
              rawImagePath = rawPhotoFile.absolutePath,
              compressedImagePath = compressedFile!!.absolutePath,
              capturedAt = Instant.now().toString(),
          )
          WidgetStateRepository(applicationContext).logAnalyzedMeal(
              mealName = estimate.mealName,
              calories = estimate.calories,
              proteinGrams = estimate.proteinGrams,
              carbsGrams = estimate.carbsGrams,
              fatGrams = estimate.fatGrams,
              timestamp = Instant.now().toString(),
          )
          CalorieWidgetRenderer.refreshAll(applicationContext)
          Log.d(TAG, "Meal analysis completed successfully")
          Result.success()
        }
        .getOrElse { throwable ->
          Log.e(TAG, "Background meal analysis failed", throwable)
          rawPhotoFile.delete()
          compressedFile?.delete()

          val message = userFacingFailureMessage(throwable)
          if (shouldRenderFailureOnWidget(message)) {
            WidgetStateRepository(applicationContext).markAnalysisFailed(message)
          } else {
            WidgetStateRepository(applicationContext).clearAnalysisFeedback()
          }
          CalorieWidgetRenderer.refreshAll(applicationContext)
          Result.failure(workDataOf(KEY_FAILURE_MESSAGE to message))
        }
  }

  companion object {
    fun buildWorkRequest(rawPhotoPath: String) =
        OneTimeWorkRequestBuilder<MealAnalysisWorker>()
            .setInputData(workDataOf(KEY_RAW_PHOTO_PATH to rawPhotoPath))
            .build()

    private const val KEY_RAW_PHOTO_PATH = "raw_photo_path"
    private const val KEY_FAILURE_MESSAGE = "failure_message"
    private const val TAG = "SculptMealWorker"
  }

  private fun userFacingFailureMessage(throwable: Throwable): String {
    if (throwable is NotFoodException) {
      return "No food detected in that image."
    }

    val message = throwable.message.orEmpty()
    return when {
      "quota" in message.lowercase() || "billing" in message.lowercase() ->
          "OpenAI quota reached. Check billing."
      "rate limit" in message.lowercase() ->
          "OpenAI is rate-limiting requests. Try again."
      "api key" in message.lowercase() || "unauthorized" in message.lowercase() ->
          "API key issue. Revalidate it in the app."
      "base64" in message.lowercase() || "image_url" in message.lowercase() ->
          "Photo upload failed. Try taking the photo again."
      "timeout" in message.lowercase() || "timed out" in message.lowercase() ->
          "Analysis timed out. Try again."
      "unable to resolve host" in message.lowercase() ||
          "failed to connect" in message.lowercase() ||
          "network" in message.lowercase() ->
          "Network error while analyzing meal."
      else -> "Could not analyze captured photo."
    }
  }

  private fun shouldRenderFailureOnWidget(message: String): Boolean =
      message != "No food detected in that image."

  private class NotFoodException : IOException("Model did not detect a meal")
}

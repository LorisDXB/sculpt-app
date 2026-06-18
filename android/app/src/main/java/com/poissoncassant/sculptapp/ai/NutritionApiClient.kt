package com.poissoncassant.sculptapp.ai

import android.util.Log
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt

class NutritionApiClient {
  fun validateApiKey(apiKey: String): ApiKeyValidationResult {
    val connection = createConnection("$BASE_URL/models", "GET", apiKey)

    return runCatching {
          Log.d(TAG, "Validating API key against $BASE_URL/models")
          connection.connect()
          when (val statusCode = connection.responseCode) {
            HttpURLConnection.HTTP_OK -> {
              ApiKeyValidationResult(isValid = true, message = "API key validated.")
            }
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
              ApiKeyValidationResult(isValid = false, message = "Invalid API key.")
            }
            HttpURLConnection.HTTP_FORBIDDEN -> {
              ApiKeyValidationResult(
                  isValid = false,
                  message = "API key does not have access to this project.",
              )
            }
            else -> {
              ApiKeyValidationResult(
                  isValid = false,
                  message = readErrorMessage(connection, statusCode),
              )
            }
          }
        }
        .getOrElse {
          ApiKeyValidationResult(isValid = false, message = "Could not validate API key.")
        }
        .also {
          connection.disconnect()
        }
  }

  fun analyzeMealImage(
      apiKey: String,
      imageFile: File,
      mealContextTranscript: String? = null,
  ): NutritionEstimate {
    val connection = createConnection("$BASE_URL/responses", "POST", apiKey)
    val requestBody = buildAnalysisRequestBody(imageFile, mealContextTranscript)

    return runCatching {
          Log.d(
              TAG,
              "Sending meal analysis request imagePath=${imageFile.absolutePath} imageBytes=${imageFile.length()} transcriptPresent=${!mealContextTranscript.isNullOrBlank()} connectTimeoutMs=${connection.connectTimeout} readTimeoutMs=${connection.readTimeout}",
          )
          connection.outputStream.bufferedWriter().use { writer ->
            writer.write(requestBody.toString())
          }

          val statusCode = connection.responseCode
          Log.d(TAG, "Meal analysis response statusCode=$statusCode")
          if (statusCode !in 200..299) {
            throw IOException(readErrorMessage(connection, statusCode))
          }

          val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
          Log.d(TAG, "Meal analysis response bytes=${responseBody.length}")
          parseAnalysisResponse(responseBody)
        }
        .also {
          connection.disconnect()
        }
        .getOrElse {
          Log.e(
              TAG,
              "Meal analysis request failed root=${describeThrowableChain(it)}",
              it,
          )
          throw IOException(it.message ?: "Could not analyze meal image", it)
        }
  }

  private fun buildAnalysisRequestBody(imageFile: File, mealContextTranscript: String?): JSONObject {
    val encodedImage =
        Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
    val prompt =
      """
      You are estimating nutrition from a meal photo for personal calorie tracking.

      Return one best estimate for the visible meal.

      The image is the primary source of truth.

      Use spoken context only to refine ambiguous details such as portion size, hidden ingredients, oils, sauces, leftovers, or whether only part of the meal was eaten. Ignore spoken context when it clearly conflicts with the image.

      Assume the user consumed all visible foods and drinks. Count all visible items, sides, drinks, and repeated units exactly once.

      For packaged, boxed, plated, takeout, or combo meals, assume the visible container represents the intended serving unless the image or transcript indicates otherwise.

      Use visible labels, weights, volumes, serving counts, and nutrition information when legible.

      Include hidden calories from oils, sauces, dressings, cheese, butter, and cooking fat when plausible.

      Avoid optimistic low estimates. When uncertain, prefer a realistic midpoint-to-upper estimate.

      If the image is not food, return meal_name "not food" and all numeric values as 0.

      Return a short meal_name suitable for a small widget.
      Rules:
      - Use at most 2–3 words.
      - Prefer specific names.
      - Avoid words like "plate of", "bowl of", or unnecessary adjectives.
      - Include the brand name when useful for identification.
      - Prioritize brevity over detail.
      - Keep the name ideally under 15 characters.
      """.trimIndent()

    val schema =
        JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("meal_name", JSONObject().put("type", "string"))
                    .put("calories", JSONObject().put("type", "integer"))
                    .put("protein_g", JSONObject().put("type", "integer"))
                    .put("carbs_g", JSONObject().put("type", "integer"))
                    .put("fat_g", JSONObject().put("type", "integer"))
                    .put(
                        "confidence",
                        JSONObject()
                            .put("type", "string")
                            .put("enum", JSONArray(listOf("low", "medium", "high"))),
                    ),
            )
            .put(
                "required",
                JSONArray(
                    listOf("meal_name", "calories", "protein_g", "carbs_g", "fat_g", "confidence"),
                ),
            )
            .put("additionalProperties", false)

    val userContent =
        JSONArray()
            .put(JSONObject().put("type", "input_text").put("text", prompt))
            .apply {
              mealContextTranscript
                  ?.takeIf { it.isNotBlank() }
                  ?.let { transcript ->
                    put(
                        JSONObject()
                            .put(
                                "type",
                                "input_text",
                            )
                            .put(
                                "text",
                                "User provided extra meal context: $transcript",
                            ),
                    )
                  }
            }
            .put(
                JSONObject()
                    .put("type", "input_image")
                    .put("image_url", "data:image/jpeg;base64,$encodedImage"),
            )

    return JSONObject()
        .put("model", MODEL_NAME)
        .put(
            "input",
            JSONArray()
                .put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", userContent),
                ),
        )
        .put(
            "text",
            JSONObject()
                .put(
                    "format",
                    JSONObject()
                        .put("type", "json_schema")
                        .put("name", "meal_estimate")
                        .put("strict", true)
                        .put("schema", schema),
                ),
        )
    }

  private fun parseAnalysisResponse(responseBody: String): NutritionEstimate {
    val responseJson = JSONObject(responseBody)
    val output = responseJson.optJSONArray("output") ?: throw IOException("No model output found")

    var jsonText: String? = null
    for (index in 0 until output.length()) {
      val item = output.optJSONObject(index) ?: continue
      if (item.optString("type") != "message") {
        continue
      }

      val content = item.optJSONArray("content") ?: continue
      for (contentIndex in 0 until content.length()) {
        val part = content.optJSONObject(contentIndex) ?: continue
        if (part.optString("type") == "output_text") {
          jsonText = part.optString("text")
          break
        }
      }
    }

    if (jsonText.isNullOrBlank()) {
      throw IOException("No structured nutrition output returned")
    }

    val result = JSONObject(jsonText)
    val normalizedCalories = result.optInt("calories", 0).coerceAtLeast(0)
    val normalizedMacros =
        normalizeMacros(
            calories = normalizedCalories,
            proteinGrams = result.optInt("protein_g", 0).coerceAtLeast(0),
            carbsGrams = result.optInt("carbs_g", 0).coerceAtLeast(0),
            fatGrams = result.optInt("fat_g", 0).coerceAtLeast(0),
        )

    return NutritionEstimate(
        mealName = result.optString("meal_name", "Meal").ifBlank { "Meal" },
        calories = normalizedCalories,
        proteinGrams = normalizedMacros.first,
        carbsGrams = normalizedMacros.second,
        fatGrams = normalizedMacros.third,
        confidence = result.optString("confidence", "low").ifBlank { "low" },
    )
  }

  private fun normalizeMacros(
      calories: Int,
      proteinGrams: Int,
      carbsGrams: Int,
      fatGrams: Int,
  ): Triple<Int, Int, Int> {
    if (calories <= 0) {
      return Triple(0, 0, 0)
    }

    val macroCalories = proteinGrams * 4 + carbsGrams * 4 + fatGrams * 9
    if (macroCalories <= 0) {
      return Triple(
          ((calories * 0.30f) / 4f).roundToInt(),
          ((calories * 0.40f) / 4f).roundToInt(),
          ((calories * 0.30f) / 9f).roundToInt(),
      )
    }

    val differenceRatio = abs(macroCalories - calories).toFloat() / calories.toFloat()
    if (differenceRatio <= 0.25f) {
      return Triple(proteinGrams, carbsGrams, fatGrams)
    }

    val scale = calories.toFloat() / macroCalories.toFloat()
    return Triple(
        (proteinGrams * scale).roundToInt().coerceAtLeast(0),
        (carbsGrams * scale).roundToInt().coerceAtLeast(0),
        (fatGrams * scale).roundToInt().coerceAtLeast(0),
    )
  }

  private fun createConnection(url: String, method: String, apiKey: String): HttpURLConnection {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = 20_000
    connection.readTimeout = 45_000
    connection.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Accept", "application/json")
    connection.doInput = true
    connection.doOutput = method != "GET"
    return connection
  }

  private fun readErrorMessage(connection: HttpURLConnection, statusCode: Int): String {
    val errorBody =
        runCatching {
              connection.errorStream?.bufferedReader()?.use { it.readText() }
          }
          .getOrNull()

    if (errorBody.isNullOrBlank()) {
      return "OpenAI request failed with status $statusCode."
    }

    return runCatching {
          val errorJson = JSONObject(errorBody)
          errorJson.optJSONObject("error")?.optString("message")
        }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "OpenAI request failed with status $statusCode."
  }

  companion object {
    private const val TAG = "SculptNutritionApi"
    private const val BASE_URL = "https://api.openai.com/v1"
    private const val MODEL_NAME = "gpt-4.1-mini"
  }

  private fun describeThrowableChain(throwable: Throwable): String {
    val parts = mutableListOf<String>()
    var current: Throwable? = throwable
    while (current != null) {
      parts += "${current::class.java.simpleName}:${current.message.orEmpty()}"
      current = current.cause
    }
    return parts.joinToString(" <- ")
  }
}

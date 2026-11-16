package com.poissoncassant.sculptapp.widget

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader

internal object CalorieGradient {
  fun accentColor(state: CalorieWidgetState): Int {
    val progress =
        if (state.dailyCalorieTarget <= 0) {
          1f
        } else {
          (state.caloriesConsumedToday.toFloat() / state.dailyCalorieTarget).coerceIn(0f, 1f)
        }
    val accentHue = 135f - (135f * progress)
    return Color.HSVToColor(floatArrayOf(accentHue, 0.72f, 0.88f))
  }

  fun createShader(state: CalorieWidgetState, width: Int, height: Int): LinearGradient {
    val accentColor = accentColor(state)
    val midColor = blendColors(Color.parseColor("#0A1324"), accentColor, 0.48f)
    val baseColor = Color.parseColor("#08111F")

    return LinearGradient(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        intArrayOf(baseColor, midColor, accentColor),
        floatArrayOf(0f, 0.62f, 1f),
        Shader.TileMode.CLAMP,
    )
  }

  private fun blendColors(from: Int, to: Int, ratio: Float): Int {
    val clamped = ratio.coerceIn(0f, 1f)
    val inverse = 1f - clamped
    val alpha = (Color.alpha(from) * inverse + Color.alpha(to) * clamped).toInt()
    val red = (Color.red(from) * inverse + Color.red(to) * clamped).toInt()
    val green = (Color.green(from) * inverse + Color.green(to) * clamped).toInt()
    val blue = (Color.blue(from) * inverse + Color.blue(to) * clamped).toInt()
    return Color.argb(alpha, red, green, blue)
  }
}

package com.poissoncassant.sculptapp.camera

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class MealScanOverlay(context: Context, attrs: AttributeSet) : View(context, attrs) {
  private val density = resources.displayMetrics.density
  private val glowRadius = 18f * density
  private val glow = Paint()
  private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    strokeWidth = 2.5f * density
  }
  private var scanning = false
  private var position = 0.5f
  private var animator: ValueAnimator? = null

  fun setScanColor(color: Int) {
    line.color = color
    val red = Color.red(color)
    val green = Color.green(color)
    val blue = Color.blue(color)
    val transparent = Color.argb(0, red, green, blue)
    val center = Color.argb(170, red, green, blue)
    glow.shader = LinearGradient(
        0f, -glowRadius, 0f, glowRadius,
        intArrayOf(transparent, center, transparent),
        null, Shader.TileMode.CLAMP,
    )
    invalidate()
  }

  fun setScanning(active: Boolean) {
    if (scanning == active) return
    scanning = active
    position = 0.5f
    if (active) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()) {
        animator = ValueAnimator.ofFloat(0.5f, 0.85f, 0.15f, 0.5f).apply {
          duration = 2200L
          repeatCount = ValueAnimator.INFINITE
          interpolator = LinearInterpolator()
          addUpdateListener {
            position = it.animatedValue as Float
            invalidate()
          }
          start()
        }
      }
    } else {
      animator?.cancel()
      animator = null
    }
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (!scanning) return
    val y = height * position
    val right = width.toFloat()
    canvas.save()
    canvas.translate(0f, y)
    canvas.drawRect(0f, -glowRadius, right, glowRadius, glow)
    canvas.drawLine(0f, 0f, right, 0f, line)
    canvas.restore()
  }

  override fun onDetachedFromWindow() {
    setScanning(false)
    super.onDetachedFromWindow()
  }
}

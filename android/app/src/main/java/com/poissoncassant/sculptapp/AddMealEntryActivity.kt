package com.poissoncassant.sculptapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.poissoncassant.sculptapp.camera.MealCaptureActivity
import com.poissoncassant.sculptapp.config.AppConfigRepository

class AddMealEntryActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val nextIntent =
        if (AppConfigRepository(this).hasValidatedApiKey()) {
          Intent(this, MealCaptureActivity::class.java)
        } else {
          Toast.makeText(this, "Set and validate your API key before adding meals.", Toast.LENGTH_LONG)
              .show()
          Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_API_KEY_SETUP, true)
          }
        }

    nextIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    startActivity(nextIntent)
    finish()
  }

  companion object {
    const val EXTRA_OPEN_API_KEY_SETUP = "open_api_key_setup"
  }
}

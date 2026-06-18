package com.poissoncassant.sculptapp

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.poissoncassant.sculptapp.camera.MealCaptureActivity
import com.poissoncassant.sculptapp.config.AppConfigRepository

class AddMealEntryActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val configRepository = AppConfigRepository(this)

    val nextIntent =
        if (!configRepository.hasValidatedApiKey()) {
          Toast.makeText(this, "Set and validate your API key before adding meals.", Toast.LENGTH_LONG)
              .show()
          Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_API_KEY_SETUP, true)
          }
        } else if (!isNetworkAvailable()) {
          Toast.makeText(this, "No internet connection. Meal analysis needs network.", Toast.LENGTH_LONG)
              .show()
          null
        } else {
          Intent(this, MealCaptureActivity::class.java)
        }

    nextIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    nextIntent?.let(::startActivity)
    finish()
  }

  private fun isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  companion object {
    const val EXTRA_OPEN_API_KEY_SETUP = "open_api_key_setup"
  }
}

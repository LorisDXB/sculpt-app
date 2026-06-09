package com.poissoncassant.sculptapp.camera

import android.app.Activity
import android.content.ClipData
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import com.poissoncassant.sculptapp.widget.CalorieWidgetRenderer
import com.poissoncassant.sculptapp.widget.WidgetStateRepository
import java.io.File
import java.io.IOException
import java.time.Instant

class MealCaptureActivity : ComponentActivity() {
  private var pendingPhotoPath: String? = null
  private var pendingPhotoUri: Uri? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "MealCaptureActivity created")

    pendingPhotoPath = savedInstanceState?.getString(STATE_PENDING_PHOTO_PATH)
    pendingPhotoUri = savedInstanceState?.getString(STATE_PENDING_PHOTO_URI)?.let(Uri::parse)

    if (savedInstanceState == null) {
      startCaptureFlow()
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(STATE_PENDING_PHOTO_PATH, pendingPhotoPath)
    outState.putString(STATE_PENDING_PHOTO_URI, pendingPhotoUri?.toString())
  }

  private fun startCaptureFlow() {
    Log.d(TAG, "Starting capture flow")
    launchCamera()
  }

  private fun launchCamera() {
    val rawFile = createRawCaptureFile() ?: run {
      Log.e(TAG, "Failed to create raw capture file")
      Toast.makeText(this, "Unable to prepare camera capture.", Toast.LENGTH_SHORT).show()
      finish()
      return
    }

    runCatching {
          pendingPhotoPath = rawFile.absolutePath
          Log.d(TAG, "Raw capture file prepared at $pendingPhotoPath")
          val outputUri =
              FileProvider.getUriForFile(
                  this,
                  "$packageName.fileprovider",
                  rawFile,
              )
          pendingPhotoUri = outputUri
          Log.d(TAG, "Generated output uri $outputUri")

          val captureIntent =
              Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
                clipData = ClipData.newRawUri("", outputUri)
                addFlags(
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
              }

          Log.d(TAG, "Launching ACTION_IMAGE_CAPTURE")
          try {
            startActivityForResult(captureIntent, REQUEST_CAPTURE_IMAGE)
          } catch (exception: ActivityNotFoundException) {
            throw IOException("No camera application available", exception)
          }
        }
        .onFailure {
          Log.e(TAG, "Unable to open camera", it)
          cleanupPendingRawFile()
          Toast.makeText(this, "Unable to open camera.", Toast.LENGTH_SHORT).show()
          finish()
        }
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode != REQUEST_CAPTURE_IMAGE) {
      return
    }

    Log.d(TAG, "onActivityResult requestCode=$requestCode resultCode=$resultCode")
    if (resultCode == Activity.RESULT_OK) {
      persistCapturedImage()
    } else {
      Log.d(TAG, "Capture canceled or failed before returning a photo")
      revokePendingUriPermission()
      cleanupPendingRawFile()
      finish()
    }
  }

  private fun persistCapturedImage() {
    val sourceUri = pendingPhotoUri
    if (sourceUri == null) {
      Log.e(TAG, "Persist requested with null pendingPhotoUri")
      finish()
      return
    }

    Thread {
      runCatching {
            Log.d(TAG, "Compressing captured image from $sourceUri")
            val compressedFile = ImageCompressor.compressToJpeg(this, sourceUri)
            WidgetStateRepository(this).saveCapturedImage(
                rawImagePath = pendingPhotoPath,
                compressedImagePath = compressedFile.absolutePath,
                capturedAt = Instant.now().toString(),
            )
            CalorieWidgetRenderer.refreshAll(this)
            Log.d(TAG, "Compressed image saved to ${compressedFile.absolutePath}")
            compressedFile
          }
          .onSuccess {
            runOnUiThread {
              revokePendingUriPermission()
              Toast.makeText(this, "Meal photo captured.", Toast.LENGTH_SHORT).show()
              finish()
            }
          }
          .onFailure {
            Log.e(TAG, "Could not process captured photo", it)
            revokePendingUriPermission()
            cleanupPendingRawFile()
            runOnUiThread {
              Toast.makeText(this, "Could not process captured photo.", Toast.LENGTH_SHORT)
                  .show()
              finish()
            }
          }
    }.start()
  }

  private fun createRawCaptureFile(): File? =
      runCatching {
            val captureDirectory =
                File(externalCacheDir ?: cacheDir, "meal-captures").apply { mkdirs() }
            File.createTempFile(
                "meal-raw-",
                ".jpg",
                captureDirectory,
            )
          }
          .getOrNull()

  private fun cleanupPendingRawFile() {
    pendingPhotoPath?.let { path ->
      Log.d(TAG, "Cleaning up raw capture file at $path")
      runCatching { File(path).delete() }
    }
  }

  private fun revokePendingUriPermission() {
    pendingPhotoUri?.let { uri ->
      runCatching {
        revokeUriPermission(
            uri,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      }
    }
  }

  companion object {
    private const val TAG = "SculptCapture"
    private const val REQUEST_CAPTURE_IMAGE = 1401
    private const val STATE_PENDING_PHOTO_PATH = "pending_photo_path"
    private const val STATE_PENDING_PHOTO_URI = "pending_photo_uri"
  }
}

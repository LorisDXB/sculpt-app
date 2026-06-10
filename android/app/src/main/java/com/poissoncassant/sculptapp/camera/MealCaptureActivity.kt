package com.poissoncassant.sculptapp.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.poissoncassant.sculptapp.R
import com.poissoncassant.sculptapp.widget.CalorieWidgetRenderer
import com.poissoncassant.sculptapp.widget.WidgetStateRepository
import java.io.File
import java.util.Locale

class MealCaptureActivity : ComponentActivity() {
  private lateinit var previewView: PreviewView
  private lateinit var capturedPreview: ImageView
  private lateinit var statusText: TextView
  private lateinit var transcriptText: TextView
  private lateinit var backButton: ImageButton
  private lateinit var primaryButton: ImageButton
  private lateinit var microphoneButton: ImageButton

  private var imageCapture: ImageCapture? = null
  private var speechRecognizer: SpeechRecognizer? = null
  private var pendingPhotoPath: String? = null
  private var voiceTranscript: String? = null
  private var pendingVoiceTranscript: String? = null
  private var isReviewingCapture = false
  private var isRecordingVoice = false
  private var speechReady = false
  private var speechHasBegun = false
  private var stopRequested = false
  private val mainHandler = Handler(Looper.getMainLooper())
  private val forceStopVoiceRunnable =
      Runnable {
        if (stopRequested && isRecordingVoice) {
          Log.d(TAG, "Force stopping voice capture after release timeout")
          speechRecognizer?.stopListening()
        }
      }

  private val cameraPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
          startCamera()
        } else {
          Toast.makeText(this, getString(R.string.meal_capture_permission_denied), Toast.LENGTH_SHORT)
              .show()
          finish()
        }
      }

  private val audioPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
          startVoiceCapture()
        } else {
          Toast.makeText(this, getString(R.string.meal_capture_audio_permission_denied), Toast.LENGTH_SHORT)
              .show()
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_meal_capture)

    previewView = findViewById(R.id.camera_preview_view)
    capturedPreview = findViewById(R.id.captured_image_preview)
    statusText = findViewById(R.id.camera_status_text)
    transcriptText = findViewById(R.id.camera_transcript_text)
    backButton = findViewById(R.id.camera_back_button)
    primaryButton = findViewById(R.id.camera_primary_button)
    microphoneButton = findViewById(R.id.camera_microphone_button)

    pendingPhotoPath = savedInstanceState?.getString(STATE_PENDING_PHOTO_PATH)
    voiceTranscript = savedInstanceState?.getString(STATE_VOICE_TRANSCRIPT)
    isReviewingCapture = savedInstanceState?.getBoolean(STATE_IS_REVIEWING_CAPTURE) ?: false
    pendingPhotoPath
        ?.let(::File)
        ?.takeIf { it.exists() && isReviewingCapture }
        ?.let(::renderCapturedPreview)

    bindListeners()
    if (hasCameraPermission()) {
      startCamera()
    } else {
      Toast.makeText(this, getString(R.string.meal_capture_permission_needed), Toast.LENGTH_SHORT)
          .show()
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
    syncUiState()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(STATE_PENDING_PHOTO_PATH, pendingPhotoPath)
    outState.putString(STATE_VOICE_TRANSCRIPT, voiceTranscript)
    outState.putBoolean(STATE_IS_REVIEWING_CAPTURE, isReviewingCapture)
  }

  override fun onDestroy() {
    mainHandler.removeCallbacksAndMessages(null)
    speechRecognizer?.destroy()
    speechRecognizer = null
    super.onDestroy()
  }

  override fun onBackPressed() {
    if (isReviewingCapture) {
      discardCurrentCapture()
      syncUiState()
    } else {
      finish()
    }
  }

  private fun bindListeners() {
    backButton.setOnClickListener {
      if (isReviewingCapture) {
        discardCurrentCapture()
        syncUiState()
      } else {
        finish()
      }
    }

    primaryButton.setOnClickListener {
      if (isReviewingCapture) {
        if (isRecordingVoice || stopRequested) {
          return@setOnClickListener
        }
        persistCapturedImage()
      } else {
        takePhoto()
      }
    }

    microphoneButton.setOnTouchListener { _, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          handleMicrophonePress()
          true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          handleMicrophoneRelease()
          true
        }
        else -> false
      }
    }
  }

  private fun startCamera() {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener(
        {
          val cameraProvider = cameraProviderFuture.get()
          val preview =
              Preview.Builder()
                  .build()
                  .also { it.surfaceProvider = previewView.surfaceProvider }

          imageCapture =
              ImageCapture.Builder()
                  .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                  .build()

          runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
              }
              .onFailure {
                Log.e(TAG, "Unable to open camera", it)
                Toast.makeText(this, getString(R.string.meal_capture_open_error), Toast.LENGTH_SHORT)
                    .show()
                finish()
              }
        },
        ContextCompat.getMainExecutor(this),
    )
  }

  private fun takePhoto() {
    val capture = imageCapture ?: return
    val rawFile = createRawCaptureFile() ?: run {
      Log.e(TAG, "Failed to create raw capture file")
      Toast.makeText(this, getString(R.string.meal_capture_prepare_error), Toast.LENGTH_SHORT).show()
      return
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(rawFile).build()
    primaryButton.isEnabled = false
    capture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(this),
        object : ImageCapture.OnImageSavedCallback {
          override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            primaryButton.isEnabled = true
            deletePendingRawFileIfDifferent(rawFile.absolutePath)
            pendingPhotoPath = rawFile.absolutePath
            isReviewingCapture = true
            renderCapturedPreview(rawFile)
            syncUiState()
            Log.d(TAG, "Captured photo saved to ${rawFile.absolutePath}")
          }

          override fun onError(exception: ImageCaptureException) {
            primaryButton.isEnabled = true
            rawFile.delete()
            Log.e(TAG, "Could not take photo", exception)
            Toast.makeText(this@MealCaptureActivity, getString(R.string.meal_capture_take_error), Toast.LENGTH_SHORT)
                .show()
          }
        },
    )
  }

  private fun renderCapturedPreview(file: File) {
    val maxDimension = maxOf(previewView.width, previewView.height, 1600)
    runCatching { ImageCompressor.decodePreviewBitmap(file, maxDimension) }
        .onSuccess { bitmap ->
      capturedPreview.setImageBitmap(bitmap)
        }
        .onFailure {
          Log.e(TAG, "Could not render captured preview", it)
          capturedPreview.setImageDrawable(null)
        }
  }

  private fun persistCapturedImage() {
    val rawPhotoPath = pendingPhotoPath
    if (rawPhotoPath.isNullOrBlank()) {
      Log.e(TAG, "Persist requested with null pendingPhotoPath")
      finish()
      return
    }

    WidgetStateRepository(this).markAnalysisStarted()
    CalorieWidgetRenderer.refreshAll(this)
    WorkManager.getInstance(applicationContext).enqueue(
        MealAnalysisWorker.buildWorkRequest(rawPhotoPath, voiceTranscript),
    )
    Log.d(TAG, "Enqueued background meal analysis for $rawPhotoPath withTranscript=${!voiceTranscript.isNullOrBlank()}")
    pendingPhotoPath = null
    voiceTranscript = null
    finish()
  }

  private fun discardCurrentCapture() {
    stopVoiceCapture()
    deletePendingRawFileIfDifferent(pathToKeep = null)
    pendingPhotoPath = null
    voiceTranscript = null
    isReviewingCapture = false
  }

  private fun syncUiState() {
    if (isReviewingCapture && !pendingPhotoPath.isNullOrBlank()) {
      capturedPreview.visibility = View.VISIBLE
      statusText.visibility = View.GONE
      primaryButton.setImageResource(R.drawable.ic_capture_check)
      primaryButton.contentDescription = getString(R.string.meal_capture_validate)
      microphoneButton.visibility = View.VISIBLE
      val isAwaitingVoiceFinalization = isRecordingVoice || stopRequested
      primaryButton.isEnabled = !isAwaitingVoiceFinalization
      primaryButton.alpha = if (isAwaitingVoiceFinalization) 0.45f else 1f
      microphoneButton.isEnabled = !stopRequested
      microphoneButton.alpha = if (stopRequested) 0.55f else 1f
      updateTranscriptUi()
    } else {
      stopVoiceCapture()
      isReviewingCapture = false
      capturedPreview.setImageDrawable(null)
      capturedPreview.visibility = View.GONE
      statusText.visibility = View.VISIBLE
      statusText.text = getString(R.string.meal_capture_live_hint)
      primaryButton.setImageResource(R.drawable.ic_capture_camera)
      primaryButton.contentDescription = getString(R.string.meal_capture_take_photo)
      primaryButton.isEnabled = true
      primaryButton.alpha = 1f
      microphoneButton.visibility = View.INVISIBLE
      microphoneButton.isEnabled = true
      microphoneButton.alpha = 1f
      transcriptText.visibility = View.GONE
    }
  }

  private fun handleMicrophonePress() {
    if (!isReviewingCapture) {
      return
    }
    if (!SpeechRecognizer.isRecognitionAvailable(this)) {
      Toast.makeText(this, getString(R.string.meal_capture_voice_unavailable), Toast.LENGTH_SHORT)
          .show()
      return
    }
    if (!hasAudioPermission()) {
      Toast.makeText(this, getString(R.string.meal_capture_audio_permission_needed), Toast.LENGTH_SHORT)
          .show()
      audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      return
    }
    startVoiceCapture()
  }

  private fun handleMicrophoneRelease() {
    if (isRecordingVoice) {
      stopVoiceCapture()
    }
  }

  private fun startVoiceCapture() {
    if (isRecordingVoice) {
      return
    }
    val recognizer = getOrCreateSpeechRecognizer() ?: run {
      Toast.makeText(this, getString(R.string.meal_capture_voice_unavailable), Toast.LENGTH_SHORT)
          .show()
      return
    }

    isRecordingVoice = true
    speechReady = false
    speechHasBegun = false
    stopRequested = false
    pendingVoiceTranscript = null
    Log.d(TAG, "Starting voice capture")
    transcriptText.visibility = View.VISIBLE
    transcriptText.text = getString(R.string.meal_capture_recording_hint)

    val recognizerIntent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
          putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
          putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
          putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
        }

    runCatching { recognizer.startListening(recognizerIntent) }
        .onFailure {
          isRecordingVoice = false
          stopRequested = false
          Log.e(TAG, "Unable to start voice recognition", it)
          Toast.makeText(this, getString(R.string.meal_capture_voice_error), Toast.LENGTH_SHORT).show()
          syncUiState()
        }
  }

  private fun stopVoiceCapture() {
    if (!isRecordingVoice) {
      return
    }
    stopRequested = true
    Log.d(
        TAG,
        "Stopping voice capture request ready=$speechReady speechHasBegun=$speechHasBegun partialPresent=${!pendingVoiceTranscript.isNullOrBlank()}",
    )
    syncUiState()
    mainHandler.removeCallbacks(forceStopVoiceRunnable)
    if (speechHasBegun) {
      mainHandler.postDelayed(forceStopVoiceRunnable, 1600L)
    } else {
      mainHandler.postDelayed(forceStopVoiceRunnable, 250L)
    }
  }

  private fun updateTranscriptUi() {
    val transcript = voiceTranscript
    if (isRecordingVoice) {
      transcriptText.visibility = View.VISIBLE
      transcriptText.text = getString(R.string.meal_capture_recording_hint)
      return
    }

    if (transcript.isNullOrBlank()) {
      transcriptText.visibility = View.GONE
      return
    }

    transcriptText.visibility = View.VISIBLE
    transcriptText.text = getString(R.string.meal_capture_transcript_prefix, transcript)
  }

  private fun getOrCreateSpeechRecognizer(): SpeechRecognizer? {
    speechRecognizer?.let { return it }
    if (!SpeechRecognizer.isRecognitionAvailable(this)) {
      return null
    }

    return SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
      recognizer.setRecognitionListener(
          object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
              speechReady = true
              Log.d(TAG, "Voice recognizer ready")
              if (stopRequested) {
                mainHandler.post { speechRecognizer?.stopListening() }
              }
            }

            override fun onBeginningOfSpeech() {
              speechHasBegun = true
              Log.d(TAG, "Voice recognizer detected speech")
            }

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
              Log.d(TAG, "Voice recognizer end of speech")
            }

            override fun onError(error: Int) {
              mainHandler.removeCallbacks(forceStopVoiceRunnable)
              isRecordingVoice = false
              speechReady = false
              speechHasBegun = false
              stopRequested = false
              Log.d(TAG, "Voice recognizer error=$error partial=${pendingVoiceTranscript.orEmpty()}")
              if (!pendingVoiceTranscript.isNullOrBlank()) {
                voiceTranscript = pendingVoiceTranscript?.trim()
                pendingVoiceTranscript = null
                syncUiState()
                return
              }
              if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                  error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                  error != SpeechRecognizer.ERROR_CLIENT) {
                Log.w(TAG, "Voice recognition failed with error=$error")
                Toast.makeText(this@MealCaptureActivity, getString(R.string.meal_capture_voice_error), Toast.LENGTH_SHORT)
                    .show()
              } else if (voiceTranscript.isNullOrBlank()) {
                Toast.makeText(this@MealCaptureActivity, getString(R.string.meal_capture_voice_too_short), Toast.LENGTH_SHORT)
                    .show()
              }
              syncUiState()
            }

            override fun onResults(results: Bundle?) {
              mainHandler.removeCallbacks(forceStopVoiceRunnable)
              isRecordingVoice = false
              speechReady = false
              speechHasBegun = false
              stopRequested = false
              val transcript =
                  results
                      ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                      ?.firstOrNull()
                      ?.trim()
                      .orEmpty()
              if (transcript.isNotBlank()) {
                voiceTranscript = transcript
                pendingVoiceTranscript = null
                Log.d(TAG, "Captured voice transcript length=${transcript.length}")
              } else if (voiceTranscript.isNullOrBlank()) {
                Toast.makeText(this@MealCaptureActivity, getString(R.string.meal_capture_voice_too_short), Toast.LENGTH_SHORT)
                    .show()
              }
              syncUiState()
            }

            override fun onPartialResults(partialResults: Bundle?) {
              val partialTranscript =
                  partialResults
                      ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                      ?.firstOrNull()
                      ?.trim()
                      .orEmpty()
              if (partialTranscript.isNotBlank() && isRecordingVoice) {
                pendingVoiceTranscript = partialTranscript
                Log.d(TAG, "Voice partial transcript length=${partialTranscript.length}")
                transcriptText.visibility = View.VISIBLE
                transcriptText.text = getString(R.string.meal_capture_transcript_prefix, partialTranscript)
              }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
          },
      )
      speechRecognizer = recognizer
    }
  }

  private fun hasCameraPermission(): Boolean =
      ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
          PackageManager.PERMISSION_GRANTED

  private fun hasAudioPermission(): Boolean =
      ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED

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

  private fun deletePendingRawFileIfDifferent(pathToKeep: String?) {
    pendingPhotoPath
        ?.takeIf { it != pathToKeep }
        ?.let { path ->
          Log.d(TAG, "Cleaning up raw capture file at $path")
          runCatching { File(path).delete() }
        }
  }

  companion object {
    private const val TAG = "SculptCapture"
    private const val STATE_PENDING_PHOTO_PATH = "pending_photo_path"
    private const val STATE_VOICE_TRANSCRIPT = "voice_transcript"
    private const val STATE_IS_REVIEWING_CAPTURE = "is_reviewing_capture"
  }
}

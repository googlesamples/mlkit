/*
 * Copyright 2025 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mlkit.genai.speech.demo

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.speechtranslation.SpeechTranslation
import com.google.mlkit.genai.speechtranslation.SpeechTranslator
import com.google.mlkit.genai.speechtranslation.SpeechTranslatorOptions
import com.google.mlkit.genai.speechtranslation.SpeechTranslatorRequest
import com.google.mlkit.genai.speechtranslation.SpeechTranslatorResponse
import com.google.mlkit.genai.speechtranslation.TranslationAudio
import java.text.DecimalFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SpeechTranslationActivity : ComponentActivity() {
  private val TAG = "SpeechTranslationDemo"
  private val PERMISSIONS_REQUEST_CODE = 1001

  private val AUDIO_SAMPLE_RATE = 16000
  private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
  private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

  private var speechTranslator: SpeechTranslator? = null
  private var audioRecord: AudioRecord? = null
  private var recordingJob: Job? = null
  private var audioChannel: Channel<TranslationAudio>? = null
  private var translationJob: Job? = null
  private var srcLocale: Locale = Locale.US
  private var targetLocale: Locale = Locale.CHINA

  private lateinit var textView: TextView
  private lateinit var micButton: Button
  private lateinit var downloadButton: Button

  private lateinit var sourceLanguageSpinner: AutoCompleteTextView
  private lateinit var targetLanguageSpinner: AutoCompleteTextView

  private var isRecording = false
  private var curText: String = ""

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_speech_translation)
    textView = findViewById(R.id.text_view)
    micButton = findViewById(R.id.mic_button)
    downloadButton = findViewById(R.id.download_button)
    sourceLanguageSpinner = findViewById(R.id.source_language_spinner)
    targetLanguageSpinner = findViewById(R.id.target_language_spinner)

    findViewById<View>(R.id.button_back).setOnClickListener { finish() }

    // setup language spinner
    val adapter =
      ArrayAdapter.createFromResource(
        this,
        R.array.languages_array,
        android.R.layout.simple_dropdown_item_1line,
      )
    sourceLanguageSpinner.setAdapter(adapter)
    sourceLanguageSpinner.setText(adapter.getItem(0).toString(), false)

    targetLanguageSpinner.setAdapter(adapter)
    targetLanguageSpinner.setText(adapter.getItem(0).toString(), false)

    micButton.setOnClickListener {
      Log.i(TAG, "micButton clicked, isRecording: $isRecording")
      if (isRecording) {
        stopRecording()
      } else {
        if (createAndSetSpeechTranslator()) {
          Log.i(TAG, "micButton clicked, check feature status")
          lifecycleScope.launch { checkFeatureStatusAndStartTranslation() }
        }
      }
    }

    downloadButton.setOnClickListener {
      if (createAndSetSpeechTranslator()) {
        lifecycleScope.launch { checkFeatureStatusAndDownloadIfNeeded() }
      }
    }

    // Request audio recording permission
    val permissions =
      arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    if (
      ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    stopRecording()
    speechTranslator?.close()
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray,
    deviceId: Int,
  ) {
    if (requestCode == PERMISSIONS_REQUEST_CODE) {
      if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        // Permission granted
      } else {
        // Permission denied, show an error or disable functionality
        Toast.makeText(this, "Audio recording permission denied", Toast.LENGTH_SHORT).show()
      }
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
  }

  private fun startSpeechTranslation() {
    val bufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    if (
      ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      Toast.makeText(this, "Audio recording permission denied", Toast.LENGTH_SHORT).show()
      return
    }

    val record =
      AudioRecord(
        MediaRecorder.AudioSource.MIC,
        AUDIO_SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        bufferSize,
      )
    if (record.state != AudioRecord.STATE_INITIALIZED) {
      Log.e(TAG, "Failed to initialize AudioRecord")
      return
    }
    audioRecord = record
    record.startRecording()
    isRecording = true

    runOnUiThread {
      textView.text = getString(R.string.text_status_running)
      micButton.text = getString(R.string.text_stop_recording)
      setControlUiEnabled(false)
    }

    val channel = Channel<TranslationAudio>(capacity = 10)
    audioChannel = channel
    val audioFlow = channel.receiveAsFlow()

    // Audio capture loop
    recordingJob =
      lifecycleScope.launch(Dispatchers.IO) {
        val buffer = ByteArray(bufferSize)
        try {
          while (isRecording) {
            val read =
              try {
                record.read(buffer, 0, bufferSize)
              } catch (e: Exception) {
                Log.e(TAG, "Error reading from AudioRecord", e)
                -1
              }
            if (read > 0) {
              val audioBytes = buffer.copyOf(read)
              channel.send(TranslationAudio(srcLocale, audioBytes))
            } else if (read < 0) {
              break
            }
          }
        } catch (e: ClosedSendChannelException) {
          Log.d(TAG, "Audio channel closed, stopping recording loop")
        } finally {
          channel.close()
        }
      }

    // Translation response collection
    translationJob = lifecycleScope.launch {
      val request = SpeechTranslatorRequest.Builder(audioFlow).build()
      try {
        speechTranslator?.startTranslation(request)?.collect { response ->
          when (response) {
            is SpeechTranslatorResponse.Text -> {
              runOnUiThread {
                textView.text =
                  getString(R.string.text_transcription_format, curText, response.translatedText)
                curText =
                  getString(R.string.text_transcription_format, curText, response.translatedText)
              }
            }
            is SpeechTranslatorResponse.CompletedResponse -> {
              Log.i(TAG, "Translation completed")
              stopRecording()
              runOnUiThread {
                Toast.makeText(this@SpeechTranslationActivity, "Completed", Toast.LENGTH_SHORT)
                  .show()
              }
            }
            is SpeechTranslatorResponse.ErrorResponse -> {
              Log.e(TAG, "Translation error: ${response.e.message}")
              stopRecording()
              runOnUiThread {
                textView.text =
                  getString(
                    R.string.text_speech_translation_error_message_format,
                    response.e.message,
                  )
              }
            }
            is SpeechTranslatorResponse.Audio -> {
              // Ignore audio output as requested
              Log.d(TAG, "Ignored audio output response")
            }
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error in translation flow", e)
        runOnUiThread {
          textView.text =
            getString(R.string.text_speech_translation_error_message_format, e.message)
        }
      }
    }
  }

  private fun stopRecording() {
    isRecording = false
    runOnUiThread {
      micButton.text = getString(R.string.text_start_recording)
      setControlUiEnabled(true)
    }

    audioChannel?.close()
    audioChannel = null

    recordingJob = null
    translationJob = null

    audioRecord?.apply {
      if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
        stop()
      }
      release()
    }
    audioRecord = null
    curText = ""
  }

  private suspend fun checkFeatureStatusAndStartTranslation() {
    val status = speechTranslator?.checkStatus()
    if (status == FeatureStatus.AVAILABLE) {
      startSpeechTranslation()
    } else {
      runOnUiThread { showFeatureStatusToast(status ?: FeatureStatus.UNAVAILABLE) }
    }
  }

  private suspend fun downloadFeature() {
    var downloadTotalSize: Long = 0
    repeatOnLifecycle(Lifecycle.State.STARTED) {
      speechTranslator?.download()?.collect { status ->
        when (status) {
          is DownloadStatus.DownloadStarted -> {
            downloadTotalSize = status.bytesToDownload
            runOnUiThread {
              Toast.makeText(
                  this@SpeechTranslationActivity,
                  R.string.text_speech_translation_download_started,
                  Toast.LENGTH_SHORT,
                )
                .show()
            }
          }
          is DownloadStatus.DownloadProgress -> {
            runOnUiThread {
              textView.text = formatDownloadProgress(status.totalBytesDownloaded, downloadTotalSize)
            }
          }
          is DownloadStatus.DownloadFailed -> {
            Log.e(TAG, "Download failed", status.e)
            runOnUiThread {
              textView.text =
                getString(
                  R.string.text_speech_translation_download_failed_with_reason,
                  status.e.message,
                )
              Toast.makeText(
                  this@SpeechTranslationActivity,
                  getString(
                    R.string.text_speech_translation_download_failed_with_reason,
                    status.e.message,
                  ),
                  Toast.LENGTH_SHORT,
                )
                .show()
            }
          }
          is DownloadStatus.DownloadCompleted -> {
            Log.i(TAG, "Download completed.")
            runOnUiThread {
              textView.text = getString(R.string.text_speech_translation_download_completed)
              Toast.makeText(
                  this@SpeechTranslationActivity,
                  R.string.text_speech_translation_download_completed,
                  Toast.LENGTH_SHORT,
                )
                .show()
            }
          }
        }
      }
    }
  }

  private suspend fun checkFeatureStatusAndDownloadIfNeeded() {
    val translator = speechTranslator ?: return
    when (val status = translator.checkStatus()) {
      FeatureStatus.DOWNLOADABLE -> {
        showFeatureStatusToast(status)
        downloadFeature()
      }
      else -> showFeatureStatusToast(status)
    }
  }

  private fun createAndSetSpeechTranslator(): Boolean {
    val sourceLanguage = sourceLanguageSpinner.text.toString()
    val targetLanguage = targetLanguageSpinner.text.toString()

    if (sourceLanguage == targetLanguage) {
      Toast.makeText(
          this,
          R.string.text_speech_translation_unsupported_language_combination,
          Toast.LENGTH_SHORT,
        )
        .show()
      return false
    }

    speechTranslator?.close()
    srcLocale = Locale.Builder().setLanguageTag(sourceLanguage).build()
    targetLocale = Locale.Builder().setLanguageTag(targetLanguage).build()
    val options = SpeechTranslatorOptions.Builder(srcLocale, targetLocale).build()
    speechTranslator = SpeechTranslation.getClient(options)
    return true
  }

  private fun showFeatureStatusToast(status: Int) {
    val messageRes =
      when (status) {
        FeatureStatus.DOWNLOADABLE -> R.string.text_speech_translation_feature_status_downloadable
        FeatureStatus.DOWNLOADING -> R.string.text_speech_translation_feature_status_downloading
        FeatureStatus.UNAVAILABLE -> R.string.text_speech_translation_feature_status_unavailable
        FeatureStatus.AVAILABLE -> R.string.text_speech_translation_feature_status_available
        else -> return // Should not be called for other statuses
      }
    Toast.makeText(this@SpeechTranslationActivity, messageRes, Toast.LENGTH_SHORT).show()
  }

  private fun setControlUiEnabled(enabled: Boolean) {
    sourceLanguageSpinner.isEnabled = enabled
    targetLanguageSpinner.isEnabled = enabled
  }

  private fun formatDownloadProgress(downloadedBytes: Long, totalBytes: Long): String {
    val decimalFormat = DecimalFormat("#.#")

    val downloadedMB = downloadedBytes.toDouble() / (1024 * 1024)
    val totalMB = totalBytes.toDouble() / (1024 * 1024)

    val downloadedString = decimalFormat.format(downloadedMB)
    val totalString = decimalFormat.format(totalMB)

    return getString(
      R.string.text_speech_translation_downloading_progress_format,
      downloadedString,
      totalString,
    )
  }
}

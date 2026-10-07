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
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.util.IllformedLocaleException
import java.util.Locale
import kotlinx.coroutines.launch

class SpeechRecognitionActivity : ComponentActivity() {
  private var speechRecognizer: SpeechRecognizer? = null

  private lateinit var textView: TextView

  private lateinit var micButton: Button

  private lateinit var downloadButton: Button

  private lateinit var warmupButton: Button

  private lateinit var languageSpinner: AutoCompleteTextView
  private lateinit var modeSpinner: Spinner
  private lateinit var audioSourceSpinner: Spinner
  private lateinit var wordTimingSwitch: SwitchMaterial
  private lateinit var filepathLayout: View
  private lateinit var filepathInput: EditText
  private lateinit var audioLevelLayout: View
  private lateinit var progressBar: ProgressBar

  private lateinit var pipe: Array<ParcelFileDescriptor>
  private var recordingThread: Thread? = null
  private var isRecording = false
  private var audioRecord: AudioRecord? = null
  private var audioFile: File? = null
  private var curText: String = ""
  private var isWarmedUp = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_speech_recognition)
    textView = findViewById(R.id.text_view)
    micButton = findViewById(R.id.mic_button)
    downloadButton = findViewById(R.id.download_button)
    warmupButton = findViewById(R.id.warmup_button)
    languageSpinner = findViewById(R.id.language_spinner)
    modeSpinner = findViewById(R.id.mode_spinner)
    audioSourceSpinner = findViewById(R.id.audiosource_spinner)
    wordTimingSwitch = findViewById(R.id.switch_word_timing)
    audioLevelLayout = findViewById(R.id.audio_level_layout)
    progressBar = findViewById(R.id.progress_bar)
    filepathLayout = findViewById(R.id.filepath_layout)
    filepathInput = findViewById(R.id.filepath_input)

    audioSourceSpinner.onItemSelectedListener =
      object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
          val selected = parent?.getItemAtPosition(position).toString()
          if (selected == SOURCE_AUDIO_FILE) {
            filepathLayout.visibility = View.VISIBLE
            audioLevelLayout.visibility = View.GONE
            wordTimingSwitch.isChecked = false
            wordTimingSwitch.isEnabled = false
          } else {
            filepathLayout.visibility = View.GONE
            audioLevelLayout.visibility = View.VISIBLE
            wordTimingSwitch.isEnabled = true
          }
          if (!isRecording) {
            micButton.text = getStartButtonText(selected)
          }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
          filepathLayout.visibility = View.GONE
          audioLevelLayout.visibility = View.VISIBLE
        }
      }

    findViewById<View>(R.id.button_back).setOnClickListener { finish() }

    // setup language spinner
    val adapter =
      ArrayAdapter.createFromResource(
        this,
        R.array.languages_array,
        android.R.layout.simple_dropdown_item_1line,
      )
    languageSpinner.setAdapter(adapter)
    languageSpinner.setText(adapter.getItem(0).toString(), false)

    // setup mode spinner
    val modeAdapter =
      ArrayAdapter.createFromResource(
        this,
        R.array.modes_array,
        android.R.layout.simple_spinner_item,
      )
    modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    modeSpinner.adapter = modeAdapter
    modeSpinner.setSelection(0)

    // setup audio source spinner
    val audioSourceAdapter =
      ArrayAdapter.createFromResource(
        this,
        R.array.audiosource_array,
        android.R.layout.simple_spinner_item,
      )
    audioSourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    audioSourceSpinner.adapter = audioSourceAdapter
    audioSourceSpinner.setSelection(0)

    micButton.setOnClickListener {
      Log.i(TAG, "micButton clicked, isRecording: $isRecording")
      if (isRecording) {
        stopRecording()
      } else {
        if (createAndSetSpeechRecognizer()) {
          Log.i(TAG, "micButton clicked, check feature status")
          lifecycleScope.launch { checkFeatureStatusAndStartRecognition() }
        }
      }
    }

    downloadButton.setOnClickListener {
      if (createAndSetSpeechRecognizer()) {
        lifecycleScope.launch { checkFeatureStatusAndDownloadIfNeeded() }
      }
    }

    warmupButton.setOnClickListener {
      if (createAndSetSpeechRecognizer()) {
        lifecycleScope.launch { checkFeatureStatusAndWarmup() }
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
    speechRecognizer?.close()
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

  private suspend fun processSpeechRecognitionResponse(request: SpeechRecognizerRequest) {
    speechRecognizer?.startRecognition(request)?.collect { response ->
      when (response) {
        is SpeechRecognizerResponse.PartialTextResponse -> {
          runOnUiThread {
            textView.text = getString(R.string.text_transcription_format, curText, response.text)
            micButton.text = getStopButtonText()
          }
        }
        is SpeechRecognizerResponse.FinalTextResponse -> {
          runOnUiThread {
            val textToDisplay =
              if (response.wordTimings.isNotEmpty()) {
                response.wordTimings.joinToString(" ") { "${it.text} (${it.timestampMs}ms)" }
              } else {
                response.text
              }
            textView.text = getString(R.string.text_transcription_format, curText, textToDisplay)
            micButton.text = getStopButtonText()
            curText = if (curText.isEmpty()) textToDisplay else "$curText $textToDisplay"
          }
        }
        is SpeechRecognizerResponse.CompletedResponse -> {
          Log.i(TAG, "CompletedResponse")
          stopRecording()
          runOnUiThread {
            Toast.makeText(this, "Transcription completed.", Toast.LENGTH_SHORT).show()
          }
          curText = ""
        }
        is SpeechRecognizerResponse.AudioLevelResponse -> {
          Log.v(TAG, "AudioLevelResponse: ${response.audioLevel}")
          runOnUiThread {
            progressBar.progress = response.audioLevel
          }
        }
        is SpeechRecognizerResponse.ErrorResponse -> {
          Log.i(TAG, "ErrorResponse")
          stopRecording()
          runOnUiThread {
            textView.text =
              getString(R.string.text_error_format, response.e.message, response.e.errorCode)
          }
          response.e.printStackTrace()
          curText = ""
        }
      }
    }
  }

  private suspend fun startSpeechRecognition() {
    runOnUiThread {
      textView.text = ""
      micButton.text = getStopButtonText()
      setControlUiEnabled(false)
      progressBar.progress = 0
    }

    isRecording = true
    val selectedSource = audioSourceSpinner.selectedItem.toString()
    if (selectedSource == SOURCE_MICPHONE) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Toast.makeText(this, "Microphone input requires API level 31 or higher", Toast.LENGTH_SHORT)
          .show()
        stopRecording()
        return
      }
      processSpeechRecognitionResponse(
        speechRecognizerRequest {
          audioSource = AudioSource.fromMic()
          recognitionOptions { enableWordTiming = wordTimingSwitch.isChecked }
        }
      )
    } else if (selectedSource == SOURCE_AUDIO_FILE) {
      val filePath = filepathInput.text.toString()
      if (filePath.isEmpty()) {
        Toast.makeText(this, "Please enter a file path", Toast.LENGTH_SHORT).show()
        stopRecording()
        return
      }
      val file = File(filePath)
      if (!file.exists() || !file.canRead()) {
        Toast.makeText(this, "File does not exist or is not readable", Toast.LENGTH_SHORT).show()
        stopRecording()
        return
      }
      try {
        AudioSource.fromFile(file).use { source ->
          processSpeechRecognitionResponse(
            speechRecognizerRequest {
              this.audioSource = source
              recognitionOptions { enableWordTiming = false }
            }
          )
        }
      } catch (e: IOException) {
        Log.e(TAG, "Error opening file: $filePath", e)
        Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        stopRecording()
      } catch (e: IllegalArgumentException) {
        Log.e(TAG, "Error starting recognition: $filePath", e)
        Toast.makeText(this, "Error starting recognition: ${e.message}", Toast.LENGTH_SHORT).show()
        stopRecording()
      }
    } else {
      // Create a temporary file to store the audio
      audioFile = File.createTempFile("audio_record", ".pcm", cacheDir)

      // The following code demonstrates how to stream audio data from the microphone to the
      // SpeechRecognizer using a ParcelFileDescriptor. For most microphone-based use cases,
      // it is simpler to use `AudioSource.fromMic()` as shown above.
      pipe = ParcelFileDescriptor.createPipe()
      try {
        val bufferSize =
          AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
          )
        if (
          ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
          Toast.makeText(this, "Audio recording permission denied", Toast.LENGTH_SHORT).show()
          return
        }
        audioRecord =
          AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
          )
        audioRecord?.startRecording()

        runOnUiThread { micButton.text = getStopButtonText() }

        recordingThread = Thread { writeAudioDataToFile(bufferSize) }
        recordingThread?.start()

        // Start the recording thread first and then start the recognition.
        processSpeechRecognitionResponse(
          speechRecognizerRequest {
            audioSource = AudioSource.fromPfd(pipe[0], AudioSource.Mode.STREAMING)
            recognitionOptions { enableWordTiming = wordTimingSwitch.isChecked }
          }
        )
      } catch (e: IOException) {
        runOnUiThread {
          textView.text = getString(R.string.text_error_create_file)
          micButton.text = getStartButtonText()
        }
        isRecording = false
        return
      }
    }
  }

  private fun writeAudioDataToFile(bufferSize: Int) {
    val audioData = ByteArray(bufferSize)
    var fileOutputStream: FileOutputStream? = null

    try {
      fileOutputStream = FileOutputStream(pipe[1].fileDescriptor)
      while (isRecording) {
        // Read audio data into the buffer
        val read = audioRecord?.read(audioData, 0, bufferSize) ?: 0

        if (read > 0) {
          Log.i(TAG, "Write data size: " + read)
          // Write the audio data to the file
          fileOutputStream.write(audioData, 0, read)
        }
      }
    } catch (e: IOException) {
      e.printStackTrace()
    } finally {
      try {
        fileOutputStream?.close()
      } catch (e: IOException) {
        e.printStackTrace()
      }
    }
  }

  private fun stopRecording() {
    isRecording = false
    runOnUiThread {
      micButton.text = getStartButtonText()
      setControlUiEnabled(true)
      progressBar.progress = 0
    }

    lifecycleScope.launch { speechRecognizer?.stopRecognition() }

    val selectedSource = audioSourceSpinner.selectedItem.toString()
    if (selectedSource == SOURCE_MICPHONE || selectedSource == SOURCE_AUDIO_FILE) {
      return
    }

    // Stop and release the AudioRecord object
    audioRecord?.apply {
      stop()
      release()
    }
    audioRecord = null

    // Stop the recording thread
    recordingThread?.interrupt()
    recordingThread = null

    // Close the pipe
    if (this::pipe.isInitialized) {
      try {
        pipe[0].close()
        pipe[1].close()
      } catch (e: IOException) {
        Log.e(TAG, "Error closing pipe", e)
      }
    }
  }

  private suspend fun checkFeatureStatusAndStartRecognition() {
    speechRecognizer?.checkStatus()?.let {
      if (it == FeatureStatus.AVAILABLE) {
        speechRecognizer?.warmup()
        Log.i(TAG, "Feature is available, starting recognition.")
        startSpeechRecognition()
      } else {
        showFeatureStatusToast(it)
      }
    }
  }

  private suspend fun checkFeatureStatusAndWarmup() {
    speechRecognizer?.checkStatus()?.let {
      if (it == FeatureStatus.AVAILABLE) {
        Toast.makeText(this, "Warming up...", Toast.LENGTH_SHORT).show()
        speechRecognizer?.warmup()
        isWarmedUp = true
        Toast.makeText(this, "Warmup completed", Toast.LENGTH_SHORT).show()
      } else {
        showFeatureStatusToast(it)
      }
    }
  }

  private suspend fun downloadFeature() {
    var downloadTotalSize: Long = 0
    repeatOnLifecycle(Lifecycle.State.STARTED) {
      speechRecognizer?.download()?.collect {
        when (it) {
          is DownloadStatus.DownloadStarted -> {
            downloadTotalSize = it.bytesToDownload
            Log.i(TAG, "Download started, total size to download: $downloadTotalSize")
            runOnUiThread {
              Toast.makeText(
                  this@SpeechRecognitionActivity,
                  "Download started...",
                  Toast.LENGTH_SHORT,
                )
                .show()
            }
          }
          is DownloadStatus.DownloadProgress -> {
            runOnUiThread {
              textView.text = formatDownloadProgress(it.totalBytesDownloaded, downloadTotalSize)
            }
          }
          is DownloadStatus.DownloadFailed -> {
            Log.e(TAG, "Download failed\n${it.e}")
            runOnUiThread {
              textView.text = ""
              Toast.makeText(
                  this@SpeechRecognitionActivity,
                  "Download failed: ${it.e.message}",
                  Toast.LENGTH_LONG,
                )
                .show()
            }
          }
          is DownloadStatus.DownloadCompleted -> {
            Log.i(TAG, "Download completed")
            runOnUiThread {
              textView.text = ""
              Toast.makeText(
                  this@SpeechRecognitionActivity,
                  "Download completed!",
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
    speechRecognizer?.checkStatus()?.let {
      Log.i(TAG, "Feature status: $it")
      if (it == FeatureStatus.DOWNLOADABLE) {
        Log.i(TAG, "Feature is downloadable, starting download.")
        downloadFeature()
      } else {
        showFeatureStatusToast(it)
      }
    }
  }

  private fun createAndSetSpeechRecognizer(): Boolean {
    try {
      val languageTag = languageSpinner.text.toString()
      val selectedLocale = Locale.Builder().setLanguageTag(languageTag).build()
      Log.i(TAG, "selectedLocale: ${selectedLocale.toLanguageTag()}")
      if (
        selectedLocale.equals(Locale.ROOT) ||
          !selectedLocale.toLanguageTag().equals(languageTag, ignoreCase = true)
      ) {
        throw IllformedLocaleException("Invalid language tag format: ${languageSpinner.text}")
      }
      speechRecognizer?.close()
      speechRecognizer = createSpeechRecognizer(selectedLocale, getSelectedMode())
      return true
    } catch (e: IllformedLocaleException) {
      Log.e(TAG, "Invalid language tag format: ${languageSpinner.text}", e)
      Toast.makeText(
          this,
          "Invalid language tag format: ${languageSpinner.text}",
          Toast.LENGTH_SHORT,
        )
        .show()
      return false
    }
  }

  private fun showFeatureStatusToast(status: Int) {
    runOnUiThread {
      val message =
        when (status) {
          FeatureStatus.DOWNLOADABLE -> "Feature is not available, please download first."
          FeatureStatus.DOWNLOADING -> "Download is in progress, please wait..."
          FeatureStatus.UNAVAILABLE -> "Feature is not available in this device."
          FeatureStatus.AVAILABLE -> "Feature is already available."
          else -> return@runOnUiThread // Should not be called for other statuses
        }
      Toast.makeText(this@SpeechRecognitionActivity, message, Toast.LENGTH_SHORT).show()
    }
  }

  private fun createSpeechRecognizer(locale: Locale, mode: Int): SpeechRecognizer {
    return SpeechRecognition.getClient(
      speechRecognizerOptions {
        this.locale = locale
        this.preferredMode = mode
      }
    )
  }

  private fun getSelectedMode(): Int {
    return if (modeSpinner.selectedItem.toString() == "Advanced") {
      SpeechRecognizerOptions.Mode.MODE_ADVANCED
    } else {
      SpeechRecognizerOptions.Mode.MODE_BASIC
    }
  }

  private fun setControlUiEnabled(enabled: Boolean) {
    languageSpinner.isEnabled = enabled
    modeSpinner.isEnabled = enabled
    audioSourceSpinner.isEnabled = enabled
    wordTimingSwitch.isEnabled =
      enabled && audioSourceSpinner.selectedItem?.toString() != SOURCE_AUDIO_FILE
  }

  private fun formatDownloadProgress(downloadedBytes: Long, totalBytes: Long): String {
    val decimalFormat = DecimalFormat("#.#")

    val downloadedMB = downloadedBytes.toDouble() / (1024 * 1024)
    val totalMB = totalBytes.toDouble() / (1024 * 1024)

    val downloadedString = decimalFormat.format(downloadedMB)
    val totalString = decimalFormat.format(totalMB)

    return "Downloading progress: ${downloadedString}MB / ${totalString}MB"
  }

  private fun getStartButtonText(selectedSource: String?): String {
    return if (selectedSource == SOURCE_AUDIO_FILE) {
      getString(R.string.text_start_playing)
    } else {
      getString(R.string.text_start_recording)
    }
  }

  private fun getStartButtonText(): String {
    return getStartButtonText(audioSourceSpinner.selectedItem?.toString())
  }

  private fun getStopButtonText(selectedSource: String?): String {
    return if (selectedSource == SOURCE_AUDIO_FILE) {
      getString(R.string.text_stop_playing)
    } else {
      getString(R.string.text_stop_recording)
    }
  }

  private fun getStopButtonText(): String {
    return getStopButtonText(audioSourceSpinner.selectedItem?.toString())
  }

  companion object {
    private const val TAG = "SpeechToTextDemo"
    private const val PERMISSIONS_REQUEST_CODE = 1001
    private const val AUDIO_SAMPLE_RATE = 16000
    private const val SOURCE_MICPHONE = "Microphone"
    private const val SOURCE_AUDIO_FILE = "Audio File"
  }
}

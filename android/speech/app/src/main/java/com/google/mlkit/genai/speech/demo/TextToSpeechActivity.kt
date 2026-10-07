/*
 * Copyright 2026 Google LLC. All rights reserved.
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

import android.media.AudioAttributes
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.texttospeech.SpeechSynthesis
import com.google.mlkit.genai.texttospeech.SpeechSynthesizer
import com.google.mlkit.genai.texttospeech.SpeechSynthesizerRequest
import com.google.mlkit.genai.texttospeech.SpeechSynthesizerResponse
import com.google.mlkit.genai.texttospeech.VoiceInfo
import com.google.mlkit.genai.texttospeech.speechSynthesizerOptions
import java.text.DecimalFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextToSpeechActivity : ComponentActivity() {

  private var speechSynthesizer: SpeechSynthesizer? = null

  private lateinit var inputText: TextInputEditText
  private lateinit var submitButton: Button
  private lateinit var queryVoicesButton: Button
  private lateinit var playEarconButton: Button
  private lateinit var statusText: TextView
  private lateinit var downloadProgressBar: ProgressBar
  private lateinit var localeSpinner: AutoCompleteTextView
  private lateinit var earconSpinner: AutoCompleteTextView
  private lateinit var fallbackSwitch: SwitchMaterial
  private lateinit var voicesListView: ListView

  private var voicesList = emptyList<VoiceInfo>()
  private lateinit var adapter: ArrayAdapter<String>
  private var selectedVoice: VoiceInfo? = null
  // Locale of the last successful voice query. initSpeechSynthesizer() builds the client with it,
  // so toggling the fallback switch keeps the language the user is working in.
  private var currentLocale: Locale = Locale.US
  private var earconsMap = emptyMap<String, Uri>()
  private var downloadJob: Job? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_text_to_speech)

    inputText = findViewById(R.id.input_text)
    submitButton = findViewById(R.id.submit_button)
    queryVoicesButton = findViewById(R.id.query_voices_button)
    playEarconButton = findViewById(R.id.play_earcon_button)
    statusText = findViewById(R.id.status_text)
    downloadProgressBar = findViewById(R.id.download_progress_bar)
    localeSpinner = findViewById(R.id.locale_spinner)
    earconSpinner = findViewById(R.id.earcon_spinner)
    fallbackSwitch = findViewById(R.id.switch_voice_fallback)
    voicesListView = findViewById(R.id.voices_list_view)

    findViewById<View>(R.id.button_back).setOnClickListener { finish() }

    // Setup locale spinner
    val localeAdapter =
      ArrayAdapter.createFromResource(
        this,
        R.array.languages_array,
        android.R.layout.simple_dropdown_item_1line,
      )
    localeSpinner.setAdapter(localeAdapter)
    localeSpinner.setText(localeAdapter.getItem(0).toString(), false)

    fallbackSwitch.setOnCheckedChangeListener { _, isChecked ->
      initSpeechSynthesizer(voiceFallbackEnabled = isChecked)
      statusText.text =
        getString(
          if (isChecked) R.string.text_fallback_enabled else R.string.text_fallback_disabled
        )
    }

    // Setup ListView
    adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
    voicesListView.adapter = adapter
    voicesListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
      if (position < voicesList.size) {
        onVoiceSelected(voicesList[position])
      }
    }

    // Disable buttons until initialized
    setButtonsEnabled(false)

    lifecycleScope.launch {
      earconsMap = loadEarconsAsync()
      setupEarconSpinner()
      initSpeechSynthesizer(fallbackSwitch.isChecked)
      setButtonsEnabled(true)
    }

    submitButton.setOnClickListener { speakText() }
    playEarconButton.setOnClickListener { playEarcon() }
    queryVoicesButton.setOnClickListener {
      queryVoicesButton.isEnabled = false
      lifecycleScope.launch {
        try {
          queryVoices()
        } finally {
          queryVoicesButton.isEnabled = true
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    downloadJob?.cancel()
    speechSynthesizer?.close()
  }

  private suspend fun queryVoices() {
    downloadJob?.cancel()
    downloadJob = null
    downloadProgressBar.visibility = View.GONE

    val languageTag = localeSpinner.text.toString().trim()
    val selectedLocale =
      try {
        val locale = Locale.Builder().setLanguageTag(languageTag).build()
        if (
          locale == Locale.ROOT || !locale.toLanguageTag().equals(languageTag, ignoreCase = true)
        ) {
          throw java.util.IllformedLocaleException("Invalid language tag")
        }
        locale
      } catch (e: Exception) {
        Toast.makeText(
            this,
            getString(R.string.toast_invalid_language_tag, languageTag),
            Toast.LENGTH_SHORT,
          )
          .show()
        return
      }

    currentLocale = selectedLocale
    voicesList = speechSynthesizer?.getSupportedVoices(selectedLocale) ?: emptyList()
    adapter.clear()
    adapter.addAll(voicesList.map { it.name })
    adapter.notifyDataSetChanged()

    statusText.text = getString(R.string.text_found_voices, voicesList.size, languageTag)
    selectedVoice = null // Reset selected voice
  }

  private fun onVoiceSelected(voice: VoiceInfo) {
    selectedVoice = voice
    statusText.text = getString(R.string.text_selected_voice, voice.name)
    voicesListView.isEnabled = false
    lifecycleScope.launch {
      try {
        checkFeatureStatusAndDownload(voice)
      } finally {
        voicesListView.isEnabled = true
      }
    }
  }

  private fun speakText() {
    val text = inputText.text.toString()
    if (text.isBlank()) {
      Toast.makeText(this, "Input text is empty", Toast.LENGTH_SHORT).show()
      return
    }
    val voiceInfo = selectedVoice
    if (voiceInfo == null) {
      Toast.makeText(this@TextToSpeechActivity, "Please select a voice first", Toast.LENGTH_SHORT)
        .show()
      return
    }

    lifecycleScope.launch {
      val status = speechSynthesizer?.checkStatus(voiceInfo) ?: FeatureStatus.UNAVAILABLE
      when (status) {
        FeatureStatus.AVAILABLE -> {
          inputText.setText("")
          statusText.text = getString(R.string.text_speaking, text)
          performSynthesis(text, voiceInfo)
        }
        FeatureStatus.DOWNLOADABLE -> {
          Toast.makeText(
              this@TextToSpeechActivity,
              getString(R.string.toast_voice_not_downloaded),
              Toast.LENGTH_LONG,
            )
            .show()
          startVoiceDownload(voiceInfo)
        }
        FeatureStatus.DOWNLOADING -> {
          Toast.makeText(
              this@TextToSpeechActivity,
              getString(R.string.toast_voice_downloading),
              Toast.LENGTH_SHORT,
            )
            .show()
          if (downloadJob == null || downloadJob?.isActive != true) {
            startVoiceDownload(voiceInfo)
          }
        }
        FeatureStatus.UNAVAILABLE -> {
          if (fallbackSwitch.isChecked) {
            Toast.makeText(
                this@TextToSpeechActivity,
                getString(R.string.text_voice_fallback_toast),
                Toast.LENGTH_SHORT,
              )
              .show()
            inputText.setText("")
            statusText.text = getString(R.string.text_speaking, text)
            performSynthesis(text, voiceInfo)
          } else {
            Toast.makeText(
                this@TextToSpeechActivity,
                getString(R.string.toast_voice_unavailable),
                Toast.LENGTH_LONG,
              )
              .show()
            statusText.text = getString(R.string.text_voice_unavailable_status)
          }
        }
      }
    }
  }

  private suspend fun performSynthesis(text: String, voiceInfo: VoiceInfo) {
    val request =
      SpeechSynthesizerRequest(
        input = SpeechSynthesizerRequest.Input.Text(text, voiceInfo),
        audioAttributes =
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build(),
        queueMode = SpeechSynthesizerRequest.QueueMode.QUEUE_FLUSH,
      )
    speechSynthesizer?.synthesize(request)?.collect { response ->
      when (response) {
        is SpeechSynthesizerResponse.StatusUpdate -> {
          Log.i(TAG, "Synthesis status update: ${response.status}")
        }
        is SpeechSynthesizerResponse.AudioAvailable -> {
          Log.i(TAG, "Audio chunk available: ${response.audio.size} bytes")
        }
        is SpeechSynthesizerResponse.Error -> {
          Log.e(TAG, "Synthesis error occurred: ${response.errorCode}")
          val errorMessage =
            when (response.errorCode) {
              TextToSpeech.ERROR_NOT_INSTALLED_YET ->
                "Synthesis error: Voice model not installed on device (-9)"
              TextToSpeech.ERROR_SERVICE -> "Synthesis error: TTS service error (-1)"
              TextToSpeech.ERROR_NETWORK -> "Synthesis error: Network error (-2)"
              TextToSpeech.ERROR_INVALID_REQUEST -> "Synthesis error: Invalid request (-8)"
              else -> getString(R.string.text_synthesis_error, response.errorCode)
            }
          Toast.makeText(
              this@TextToSpeechActivity,
              errorMessage,
              Toast.LENGTH_LONG,
            )
            .show()
          statusText.text = errorMessage
        }
      }
    }
  }

  private suspend fun checkFeatureStatusAndDownload(voiceInfo: VoiceInfo) {
    val status = speechSynthesizer?.checkStatus(voiceInfo) ?: FeatureStatus.UNAVAILABLE
    when (status) {
      FeatureStatus.AVAILABLE -> {
        downloadProgressBar.visibility = View.GONE
        Toast.makeText(this, "Voice is already available.", Toast.LENGTH_SHORT).show()
        statusText.text = getString(R.string.text_voice_ready, voiceInfo.name)
      }
      FeatureStatus.DOWNLOADING -> {
        Toast.makeText(this, getString(R.string.toast_voice_downloading), Toast.LENGTH_SHORT).show()
        startVoiceDownload(voiceInfo)
      }
      FeatureStatus.DOWNLOADABLE -> {
        startVoiceDownload(voiceInfo)
      }
      FeatureStatus.UNAVAILABLE -> {
        downloadProgressBar.visibility = View.GONE
        Toast.makeText(this, getString(R.string.toast_voice_unavailable), Toast.LENGTH_LONG).show()
        statusText.text = getString(R.string.text_voice_unavailable_status)
      }
    }
  }

  private fun startVoiceDownload(voiceInfo: VoiceInfo) {
    downloadJob?.cancel()
    downloadJob = lifecycleScope.launch {
      downloadProgressBar.visibility = View.VISIBLE
      downloadProgressBar.isIndeterminate = true
      statusText.text = getString(R.string.text_download_started)
      var downloadTotalSize: Long = 0

      speechSynthesizer?.download(voiceInfo)?.collect { downloadStatus ->
        when (downloadStatus) {
          is DownloadStatus.DownloadStarted -> {
            downloadTotalSize = downloadStatus.bytesToDownload
            if (downloadTotalSize > 0) {
              downloadProgressBar.isIndeterminate = false
              downloadProgressBar.max = 100
              downloadProgressBar.progress = 0
            }
            Toast.makeText(
                this@TextToSpeechActivity,
                getString(R.string.text_download_started),
                Toast.LENGTH_SHORT,
              )
              .show()
            statusText.text = formatDownloadProgress(0, downloadTotalSize)
          }
          is DownloadStatus.DownloadProgress -> {
            if (downloadTotalSize > 0) {
              downloadProgressBar.isIndeterminate = false
              downloadProgressBar.max = 100
              val percent =
                ((downloadStatus.totalBytesDownloaded.toDouble() / downloadTotalSize) * 100).toInt()
              downloadProgressBar.progress = percent.coerceIn(0, 100)
            }
            statusText.text =
              formatDownloadProgress(downloadStatus.totalBytesDownloaded, downloadTotalSize)
          }
          is DownloadStatus.DownloadFailed -> {
            downloadProgressBar.visibility = View.GONE
            Toast.makeText(
                this@TextToSpeechActivity,
                "Download failed: ${downloadStatus.e.message}",
                Toast.LENGTH_LONG,
              )
              .show()
            statusText.text = getString(R.string.text_download_failed)
          }
          is DownloadStatus.DownloadCompleted -> {
            downloadProgressBar.isIndeterminate = false
            downloadProgressBar.progress = 100
            downloadProgressBar.visibility = View.GONE
            Toast.makeText(
                this@TextToSpeechActivity,
                "Download completed! Voice is ready.",
                Toast.LENGTH_SHORT,
              )
              .show()
            statusText.text = getString(R.string.text_voice_ready, voiceInfo.name)
          }
        }
      }
    }
  }

  private fun formatDownloadProgress(downloadedBytes: Long, totalBytes: Long): String {
    val decimalFormat = DecimalFormat("#.#")
    val downloadedMB = downloadedBytes.toDouble() / (1024 * 1024)
    val totalMB = totalBytes.toDouble() / (1024 * 1024)
    val percentStr =
      if (totalBytes > 0) {
        val pct = ((downloadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
        " ($pct%)"
      } else {
        ""
      }
    return getString(
      R.string.text_downloading_voice,
      decimalFormat.format(downloadedMB),
      decimalFormat.format(totalMB),
    ) + percentStr
  }

  private fun playEarcon() {
    val selectedEarcon = earconSpinner.text.toString()
    if (selectedEarcon.isEmpty() || !earconsMap.containsKey(selectedEarcon)) {
      Toast.makeText(this, "Please select a valid earcon", Toast.LENGTH_SHORT).show()
      return
    }
    statusText.text = getString(R.string.text_playing_earcon)
    lifecycleScope.launch {
      val request =
        SpeechSynthesizerRequest(
          input = SpeechSynthesizerRequest.Input.Earcon(selectedEarcon),
          audioAttributes =
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build(),
          queueMode = SpeechSynthesizerRequest.QueueMode.QUEUE_FLUSH,
        )
      speechSynthesizer?.synthesize(request)?.collect { response ->
        when (response) {
          is SpeechSynthesizerResponse.StatusUpdate -> {
            Log.i(TAG, "Earcon status update: ${response.status}")
            statusText.text = getString(R.string.text_earcon_status, response.status)
          }
          is SpeechSynthesizerResponse.AudioAvailable -> {}
          is SpeechSynthesizerResponse.Error -> {
            Log.e(TAG, "Earcon error occurred: ${response.errorCode}")
            Toast.makeText(
                this@TextToSpeechActivity,
                getString(R.string.text_earcon_error_toast, response.errorCode),
                Toast.LENGTH_SHORT,
              )
              .show()
            statusText.text = getString(R.string.text_earcon_error, response.errorCode)
          }
        }
      }
    }
  }

  private fun setButtonsEnabled(enabled: Boolean) {
    submitButton.isEnabled = enabled
    playEarconButton.isEnabled = enabled
    queryVoicesButton.isEnabled = enabled
    fallbackSwitch.isEnabled = enabled
  }

  private suspend fun loadEarconsAsync(): Map<String, Uri> =
    withContext(Dispatchers.IO) {
      val jingleUri = "android.resource://$packageName/raw/jingle".toUri()
      mapOf("jingle" to jingleUri)
    }

  private fun setupEarconSpinner() {
    val earconAdapter =
      ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, earconsMap.keys.toList())
    earconSpinner.setAdapter(earconAdapter)
    if (earconsMap.isNotEmpty()) {
      earconSpinner.setText(earconAdapter.getItem(0), false)
    }
  }

  private fun initSpeechSynthesizer(voiceFallbackEnabled: Boolean = true) {
    downloadJob?.cancel()
    downloadJob = null
    downloadProgressBar.visibility = View.GONE
    speechSynthesizer?.close()
    speechSynthesizer =
      SpeechSynthesis.getClient(
        speechSynthesizerOptions(currentLocale) {
          setVoiceFallbackEnabled(voiceFallbackEnabled)
          for ((name, uri) in earconsMap) {
            addEarcon(name, uri)
          }
        }
      )
  }

  companion object {
    private const val TAG = "TextToSpeechActivity"
  }
}

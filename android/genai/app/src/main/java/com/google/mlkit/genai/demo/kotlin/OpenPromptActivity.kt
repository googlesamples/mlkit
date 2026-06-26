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
package com.google.mlkit.genai.demo.kotlin

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.StreamingCallback
import com.google.mlkit.genai.demo.ContentItem
import com.google.mlkit.genai.demo.GenerationConfigDialog
import com.google.mlkit.genai.demo.GenerationConfigUtils
import com.google.mlkit.genai.demo.R
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.Candidate.FinishReason
import com.google.mlkit.genai.prompt.Content
import com.google.mlkit.genai.prompt.CountTokensResponse
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.GenerateContentResponse
import com.google.mlkit.genai.prompt.GenerateTypedContentResponse
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.PromptPrefix
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.createCachedContextRequest
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * An activity that demonstrates a chat-like interface for the Open Prompt API, allowing requests
 * with both text and images, and including generation configuration.
 */
class OpenPromptActivity :
  BaseActivity<ContentItem>(), GenerationConfigDialog.OnConfigUpdateListener {
  private val TAG = "OpenPromptActivity"
  private val ACTION_CLEAR_CACHES = 1000

  private var generativeModel: GenerativeModel? = null
  private lateinit var requestEditText: EditText
  private lateinit var sendButton: Button
  private lateinit var selectImageButton: ImageButton
  private lateinit var imagePreview: ImageView
  private lateinit var configButton: Button
  private lateinit var prefixEditText: EditText
  private lateinit var createCacheCheckBox: CheckBox
  private lateinit var extraButtonsContainer: LinearLayout
  private lateinit var plantButton: Button
  private lateinit var scheduleEventButton: Button
  private lateinit var infoButton: ImageButton

  private var curTemperature: Float? = null
  private var curTopK: Int? = null
  private var curSeed: Int? = null
  private var curMaxOutputTokens: Int? = null
  private var curCandidateCount: Int? = null
  private var useDefaultConfig = false
  private var useExplicitCache = false
  private var useStructuredOutput = false
  private var selectedOutputClass: KClass<*>? = Plant::class
  private lateinit var systemPromptEditText: EditText
  private var curEnableThinking: Boolean = false

  private val pickImageLauncher =
    registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)) {
      uris: List<Uri> ->
      if (uris.isNotEmpty()) {
        for (uri in uris) {
          insertMediaToEditText(uri)
        }
        Toast.makeText(this, "${uris.size} images selected", Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
      }
    }

  private fun insertMediaToEditText(uri: android.net.Uri) {
    val editable = requestEditText.editableText
    val cursorPosition = requestEditText.selectionStart

    editable.insert(cursorPosition, " ")

    // Load actual thumbnail if needed. Using default gallery icon for now.
    val drawable = resources.getDrawable(android.R.drawable.ic_menu_gallery, null)
    val thumbnailSize = resources.getDimensionPixelSize(R.dimen.interleaved_thumbnail_size)
    drawable.setBounds(0, 0, thumbnailSize, thumbnailSize) // Set fixed bounds for thumbnail
    val span = android.text.style.ImageSpan(drawable, uri.toString())

    editable.setSpan(
      span,
      cursorPosition,
      cursorPosition + 1,
      android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
    requestEditText.setSelection(cursorPosition + 1)
  }

  private suspend fun getContentFromEditText(): List<com.google.mlkit.genai.prompt.Part> =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      val result = mutableListOf<com.google.mlkit.genai.prompt.Part>()
      val editable = requestEditText.editableText
      val text = editable.toString()
      val spans = editable.getSpans(0, editable.length, android.text.style.ImageSpan::class.java)

      // Sort spans by their start index to guarantee visual layout order, matching the exact
      // sequence of text and images entered by the user.
      var lastIndex = 0
      for (span in spans.sortedBy { editable.getSpanStart(it) }) {
        val start = editable.getSpanStart(span)
        val end = editable.getSpanEnd(span)

        // Extract and slice the plain text segment situated between the last processed image span
        // and the current image span.
        if (start > lastIndex) {
          result.add(com.google.mlkit.genai.prompt.TextPart(text.substring(lastIndex, start)))
        }

        val uriString = span.source
        if (uriString != null) {
          try {
            val uri = uriString.toUri()
            val bitmap =
              contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
              }
            result.add(com.google.mlkit.genai.prompt.ImagePart(bitmap!!))
          } catch (e: Exception) {
            Log.e("OpenPromptActivity", "Error decoding image from span", e)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
              Toast.makeText(this@OpenPromptActivity, "Failed to load image", Toast.LENGTH_SHORT)
                .show()
            }
          }
        }
        lastIndex = end
      }

      // Extract any remaining trailing plain text located after the very last image span has been
      // processed.
      if (lastIndex < editable.length) {
        result.add(com.google.mlkit.genai.prompt.TextPart(text.substring(lastIndex)))
      }
      result
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requestEditText = findViewById(R.id.request_edit_text)
    sendButton = findViewById(R.id.send_button)
    selectImageButton = findViewById(R.id.select_image_prompt_button)
    imagePreview = findViewById(R.id.image_thumbnail_preview_input)
    configButton = findViewById(R.id.config_button)
    prefixEditText = findViewById(R.id.prefix_edit_text)
    createCacheCheckBox = findViewById(R.id.create_cache_checkbox)
    createCacheCheckBox.setOnCheckedChangeListener { _, _ ->
      prefixEditText.setText("")
      updateRequestEditTextHint()
      updatePrefixEditTextState()
    }
    systemPromptEditText = findViewById(R.id.system_prompt_edit_text)
    extraButtonsContainer = findViewById(R.id.extra_buttons_container)
    plantButton = findViewById(R.id.plant_button)
    scheduleEventButton = findViewById(R.id.schedule_event_button)
    infoButton = findViewById(R.id.info_button)

    plantButton.setOnClickListener { selectOutputClass(Plant::class) }
    scheduleEventButton.setOnClickListener { selectOutputClass(ScheduleEvent::class) }
    infoButton.setOnClickListener {
      val outputClass = selectedOutputClass
      if (outputClass != null) {
        val schema = GenerableDataUtils.getJsonSchema(outputClass)
        if (schema != null) {
          showSchemaDialog(getString(R.string.dialog_title_output_class_schema), schema)
        } else {
          Toast.makeText(this, "Schema not available", Toast.LENGTH_SHORT).show()
        }
      }
    }

    selectImageButton.setOnClickListener {
      pickImageLauncher.launch(
        PickVisualMediaRequest.Builder()
          .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
          .build()
      )
    }

    configButton.setOnClickListener { GenerationConfigDialog().show(supportFragmentManager, null) }

    sendButton.setOnClickListener {
      if (useExplicitCache) {
        val cacheName = prefixEditText.text.toString().trim()
        if (TextUtils.isEmpty(cacheName)) {
          Toast.makeText(this, R.string.cache_name_empty, Toast.LENGTH_SHORT).show()
          return@setOnClickListener
        }
        val text = requestEditText.text.toString().trim()
        if (createCacheCheckBox.isChecked) {
          if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, R.string.prefix_to_cache_empty, Toast.LENGTH_SHORT).show()
            return@setOnClickListener
          }
          onSend(ContentItem.CacheRequestItem.fromRequest(cacheName, text))
        } else {
          if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, R.string.input_message_is_empty, Toast.LENGTH_SHORT).show()
            return@setOnClickListener
          }
          onSend(ContentItem.TextWithPrefixCacheItem.fromRequest(cacheName, text))
        }
        requestEditText.setText("")
        return@setOnClickListener
      }

      lifecycleScope.launch {
        val parts = getContentFromEditText()
        if (
          parts.isEmpty() ||
            (parts.size == 1 &&
              parts[0] is com.google.mlkit.genai.prompt.TextPart &&
              (parts[0] as com.google.mlkit.genai.prompt.TextPart).textString.trim().isEmpty())
        ) {
          Toast.makeText(
              this@OpenPromptActivity,
              R.string.input_message_is_empty,
              Toast.LENGTH_SHORT,
            )
            .show()
          return@launch
        }

        val prefixText = prefixEditText.text.toString().trim()
        val systemText = systemPromptEditText.text.toString().trim()

        val resolvedParts = parts.toMutableList()
        if (prefixText.isNotEmpty()) {
          resolvedParts.add(0, com.google.mlkit.genai.prompt.TextPart(prefixText))
        }

        val requestItem = ContentItem.InterleavedContentItem.fromRequest(resolvedParts, systemText)
        onSend(requestItem)
        requestEditText.setText("")
        systemPromptEditText.setText("")
      }
    }

    onConfigUpdated()

    initGenerator()
  }

  private fun selectOutputClass(outputClass: KClass<*>) {
    selectedOutputClass = outputClass
    updateOutputClassButtonColors()
  }

  private fun updateOutputClassButtonColors() {
    val purple = ContextCompat.getColor(this, R.color.purple_500)
    val grey = ContextCompat.getColor(this, R.color.grey)
    plantButton.setBackgroundColor(if (selectedOutputClass == Plant::class) purple else grey)
    scheduleEventButton.setBackgroundColor(
      if (selectedOutputClass == ScheduleEvent::class) purple else grey
    )
  }

  private fun showSchemaDialog(title: String, schema: String) {
    AlertDialog.Builder(this)
      .setTitle(title)
      .setMessage(schema)
      .setPositiveButton("Dismiss") { dialog, _ -> dialog.dismiss() }
      .show()
  }

  override fun onConfigUpdated() {
    useDefaultConfig = GenerationConfigUtils.getUseDefaultConfig(applicationContext)
    if (useDefaultConfig) {
      // Cache, structured output, and function calling cannot be used in the simple utility API.
      GenerationConfigUtils.setUseExplicitCache(applicationContext, false)
      GenerationConfigUtils.setUseStructuredOutput(applicationContext, false)
    }
    useExplicitCache = GenerationConfigUtils.getUseExplicitCache(applicationContext)
    useStructuredOutput = GenerationConfigUtils.getUseStructuredOutput(applicationContext)

    var disableStreaming = useStructuredOutput
    if (disableStreaming) {
      GenerationConfigUtils.setUseStreaming(applicationContext, false)
    }

    var showExtraButtons = useStructuredOutput
    extraButtonsContainer.visibility =
      if (showExtraButtons) View.VISIBLE else View.GONE
    if (showExtraButtons) {
      if (useStructuredOutput) {
        plantButton.visibility = View.VISIBLE
        scheduleEventButton.visibility = View.VISIBLE
        updateOutputClassButtonColors()
      }
    }

    if (useExplicitCache) {
      prefixEditText.visibility = View.VISIBLE
      prefixEditText.setHint(R.string.hint_add_cache_name)

      createCacheCheckBox.visibility = View.VISIBLE
      configButton.visibility = View.VISIBLE
      selectImageButton.visibility = View.GONE
      imagePreview.visibility = View.GONE
    } else {
      prefixEditText.visibility = if (useDefaultConfig) View.GONE else View.VISIBLE
      prefixEditText.setHint(R.string.hint_add_prompt_prefix)
      createCacheCheckBox.visibility = View.GONE
      configButton.visibility = if (useDefaultConfig) View.GONE else View.VISIBLE
      selectImageButton.visibility = if (useDefaultConfig) View.GONE else View.VISIBLE
      imagePreview.visibility = View.GONE
    }
    prefixEditText.setText("")
    requestEditText.setText("")
    updateRequestEditTextHint()
    updatePrefixEditTextState()

    curTemperature = GenerationConfigUtils.getTemperature(applicationContext)
    curTopK = GenerationConfigUtils.getTopK(applicationContext)
    curSeed = GenerationConfigUtils.getSeed(applicationContext)
    curCandidateCount = GenerationConfigUtils.getCandidateCount(applicationContext)
    curMaxOutputTokens = GenerationConfigUtils.getMaxOutputTokens(applicationContext)
    curEnableThinking = GenerationConfigUtils.getEnableThinking(applicationContext)
    refreshConfigs()
    invalidateOptionsMenu()
  }

  private fun updateRequestEditTextHint() {
    requestEditText.setHint(
      if (useExplicitCache) {
        if (createCacheCheckBox.isChecked) {
          R.string.hint_add_prefix_to_cache
        } else {
          R.string.hint_add_suffix_for_inference
        }
      } else {
        R.string.hint_type_a_message
      }
    )
  }

  override fun getLayoutResId(): Int = R.layout.activity_openprompt

  override fun getBaseModelName(): ListenableFuture<String> = lifecycleScope.future {
    checkNotNull(generativeModel).getBaseModelName()
  }

  override fun checkFeatureStatus(): ListenableFuture<Int> = lifecycleScope.future {
    checkNotNull(generativeModel).checkStatus()
  }

  override fun downloadFeature(callback: DownloadCallback): ListenableFuture<Void> {
    return CallbackToFutureAdapter.getFuture { completer ->
      val job = lifecycleScope.launch {
        try {
          checkNotNull(generativeModel).download().collect { status ->
            when (status) {
              is DownloadStatus.DownloadStarted ->
                callback.onDownloadStarted(status.bytesToDownload)
              is DownloadStatus.DownloadProgress ->
                callback.onDownloadProgress(status.totalBytesDownloaded)
              is DownloadStatus.DownloadFailed -> callback.onDownloadFailed(status.e)
              is DownloadStatus.DownloadCompleted -> callback.onDownloadCompleted()
            }
          }
          completer.set(null)
        } catch (e: Exception) {
          completer.setException(e)
        }
      }

      completer.addCancellationListener({ job.cancel() }, ContextCompat.getMainExecutor(this))

      "downloadFeature"
    }
  }

  override fun runInferenceImpl(
    request: ContentItem,
    streamingCallback: StreamingCallback?,
  ): ListenableFuture<List<ContentItem>> {
    if (request is ContentItem.CacheRequestItem) {
      return lifecycleScope.future {
        listOf(ContentItem.TextItem.fromResponse(createCache(request), null))
      }
    }
    return lifecycleScope.future {
      if (useStructuredOutput) {
        val genRequest = createGenerateContentRequest(request)
        val response =
          checkNotNull(generativeModel)
            .generateContent(
              generateTypedContentRequest(
                generateContentRequest = genRequest,
                outputClass = checkNotNull(selectedOutputClass),
                includeSchemaInPrompt = true,
              )
            )
        return@future resultToContentItemsTyped(response)
      }

      if (request is ContentItem.TextItem && useDefaultConfig) {
        // useDefaultConfig is used for the case where user wants to use utility function with
        // default config values
        val result =
          if (streamingCallback != null) {
            checkNotNull(generativeModel).generateContent(request.text, streamingCallback)
          } else {
            checkNotNull(generativeModel).generateContent(request.text)
          }
        return@future resultToContentItems(result)
      }

      val genRequest = createGenerateContentRequest(request)
      val result =
        if (streamingCallback != null) {
          checkNotNull(generativeModel).generateContent(genRequest, streamingCallback)
        } else {
          checkNotNull(generativeModel).generateContent(genRequest)
        }
      resultToContentItems(result)
    }
  }

  override fun runInferenceStreamImpl(request: ContentItem): Flow<ContentItem>? {
    if (request is ContentItem.CacheRequestItem) {
      return flow { emit(ContentItem.TextItem.fromResponse(createCache(request), null)) }
    }
    val showThinking = GenerationConfigUtils.getShowThinking(applicationContext)
    if (request is ContentItem.TextItem && useDefaultConfig) {
      // useDefaultConfig is used for the case where user wants to use utility function with
      // default config values
      return flow {
        checkNotNull(generativeModel)
          .generateContentStream(request.text)
          .map { result ->
            val candidate = result.candidates.firstOrNull()
            if (candidate != null) {
              val text = candidate.text
              val finishReason = candidate.finishReason
              val formattedText =
                if (finishReason == Candidate.FinishReason.MAX_TOKENS) {
                  "$text\n(FinishReason: MAX_TOKENS)"
                } else {
                  text
                }
              ContentItem.TextItem.fromStreamingResponse(formattedText)
            } else {
              val thought = result.thoughtProcess.firstOrNull()
              if (thought != null && showThinking) {
                ContentItem.TextItem.fromStreamingThoughtResponse(thought.text)
              } else {
                null
              }
            }
          }
          .filterNotNull()
          .collect { emit(it) }
      }
    }
    return flow {
      val genRequest = createGenerateContentRequest(request)
      checkNotNull(generativeModel)
        .generateContentStream(genRequest)
        .map { result ->
          val candidate = result.candidates.firstOrNull()
          if (candidate != null) {
            val text = candidate.text
            val finishReason = candidate.finishReason
            val formattedText =
              if (finishReason == Candidate.FinishReason.MAX_TOKENS) {
                "$text\n(FinishReason: MAX_TOKENS)"
              } else {
                text
              }
            ContentItem.TextItem.fromStreamingResponse(formattedText)
          } else {
            val thought = result.thoughtProcess.firstOrNull()
            if (thought != null && showThinking) {
              ContentItem.TextItem.fromStreamingThoughtResponse(thought.text)
            } else {
              null
            }
          }
        }
        .filterNotNull()
        .collect { emit(it) }
    }
  }

  private fun showCacheSelectionDialog() {
    lifecycleScope.launch {
      val caches = checkNotNull(generativeModel).caches.list()
      if (caches.isEmpty()) {
        Toast.makeText(this@OpenPromptActivity, "No caches available to select", Toast.LENGTH_SHORT)
          .show()
        return@launch
      }
      val cacheNames = caches.map { it.name }.toTypedArray()
      AlertDialog.Builder(this@OpenPromptActivity)
        .setTitle("Select Cache")
        .setItems(cacheNames) { _, which -> prefixEditText.setText(cacheNames[which]) }
        .show()
    }
  }

  private fun updatePrefixEditTextState() {
    if (useExplicitCache && !createCacheCheckBox.isChecked) {
      prefixEditText.isFocusable = false
      prefixEditText.isClickable = true
      prefixEditText.setOnClickListener { showCacheSelectionDialog() }
      prefixEditText.setHint(R.string.hint_select_cache_name)
    } else {
      prefixEditText.isFocusable = true
      prefixEditText.isFocusableInTouchMode = true
      prefixEditText.isClickable = false
      prefixEditText.setOnClickListener(null)
      if (useExplicitCache) {
        prefixEditText.setHint(R.string.hint_add_cache_name)
      } else {
        prefixEditText.setHint(R.string.hint_add_prompt_prefix)
      }
    }
  }

  private suspend fun createCache(request: ContentItem.CacheRequestItem): String {
    val unused =
      checkNotNull(generativeModel)
        .caches
        .create(createCachedContextRequest(request.cacheName, PromptPrefix(request.prefixToCache)))

    // Return a string to indicate the cache is created successfully.
    return "${getString(R.string.prefix_cached)}: ${request.cacheName}"
  }

  private fun createGenerateContentRequest(request: ContentItem): GenerateContentRequest {
    var requestText = ""
    var promptPrefixText = ""
    var imageBitmap: Bitmap? = null
    var cachedContextNameText: String? = null
    var systemPromptText: String = ""

    when (request) {
      is ContentItem.TextItem -> {
        requestText = request.text
        systemPromptText = request.systemInstruction
      }
      is ContentItem.TextAndImagesItem -> {
        requestText = request.text
        systemPromptText = request.systemInstruction
        for (uri in request.imageUris) {
          try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
              BitmapFactory.decodeStream(inputStream)?.let { bitmap -> imageBitmap = bitmap }
            }
          } catch (e: java.io.IOException) {
            Log.e("OpenPromptActivity", "Error decoding image URI: $uri", e)
          }
        }
      }
      is ContentItem.ImageItem -> {
        try {
          contentResolver.openInputStream(request.imageUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)?.let { bitmap -> imageBitmap = bitmap }
          }
        } catch (e: java.io.IOException) {
          Log.e("OpenPromptActivity", "Error decoding image URI: ${request.imageUri}", e)
        }
      }
      is ContentItem.TextWithPromptPrefixItem -> {
        requestText = request.dynamicSuffix
        systemPromptText = request.systemInstruction
        promptPrefixText = request.promptPrefix
      }
      is ContentItem.TextWithPrefixCacheItem -> {
        requestText = request.dynamicSuffix
        cachedContextNameText = request.cacheName
      }
      is ContentItem.CacheRequestItem -> {
        throw IllegalStateException("CacheRequestItem is for creating cache only.")
      }
      is ContentItem.InterleavedContentItem -> {
        val contentBuilder = com.google.mlkit.genai.prompt.Content.builder()
        for (part in request.parts) {
          contentBuilder.addPart(part)
        }
        return generateContentRequest(contentBuilder.build()) {
          temperature = curTemperature
          topK = curTopK
          seed = curSeed
          maxOutputTokens = curMaxOutputTokens
          candidateCount = curCandidateCount
          enableThinking = curEnableThinking

          if (request.systemInstruction.isNotEmpty()) {
            systemInstruction = SystemInstruction(request.systemInstruction)
          }
        }
      }
    }

    return if (imageBitmap != null) {
      generateContentRequest(
        SystemInstruction(systemPromptText),
        ImagePart(imageBitmap),
        TextPart(requestText),
      ) {
        temperature = curTemperature
        topK = curTopK
        seed = curSeed
        maxOutputTokens = curMaxOutputTokens
        candidateCount = curCandidateCount
        enableThinking = curEnableThinking
      }
    } else {
      generateContentRequest(SystemInstruction(systemPromptText), TextPart(requestText)) {
        if (useExplicitCache) {
          cachedContextName = cachedContextNameText
        } else {
          promptPrefix = PromptPrefix(promptPrefixText)
        }
        temperature = curTemperature
        topK = curTopK
        seed = curSeed
        maxOutputTokens = curMaxOutputTokens
        candidateCount = curCandidateCount
        enableThinking = curEnableThinking
      }
    }
  }

  private fun resultToContentItems(result: GenerateContentResponse): List<ContentItem> {
    val items = mutableListOf<ContentItem>()
    if (GenerationConfigUtils.getShowThinking(applicationContext)) {
      for (candidate in result.thoughtProcess) {
        items.add(ContentItem.TextItem.fromThoughtResponse(candidate.text))
      }
    }
    for (candidate in result.candidates) {
      val text = candidate.text
      if (text.isNotBlank()) {
        val formattedText =
          if (candidate.finishReason == Candidate.FinishReason.MAX_TOKENS) {
            "$text\n(FinishReason: MAX_TOKENS)"
          } else {
            text
          }
        items.add(ContentItem.TextItem.fromResponse(formattedText, null))
      }
    }
    return items
  }

  private fun resultToContentItemsTyped(
    result: GenerateTypedContentResponse<*>
  ): List<ContentItem> =
    result.candidates.map { candidate ->
      val text = candidate.response.toString()
      val formattedText =
        if (candidate.finishReason == Candidate.FinishReason.MAX_TOKENS) {
          "$text\n(FinishReason: MAX_TOKENS)"
        } else {
          text
        }
      ContentItem.TextItem.fromResponse(formattedText, null)
    }

  override fun runInferenceForBatchTask(request: String): List<String> {
    return runBlocking {
      val resultText =
        try {
          if (useDefaultConfig) {
            // useDefaultConfig is used for the case where user wants to use utility function with
            // default config values
            checkNotNull(generativeModel).generateContent(request).candidates.first().text
          } else {
            val genRequest =
              generateContentRequest(TextPart(request)) {
                temperature = curTemperature
                topK = curTopK
                seed = curSeed
                maxOutputTokens = curMaxOutputTokens
                candidateCount = curCandidateCount
              }
            checkNotNull(generativeModel).generateContent(genRequest).candidates.first().text
          }
        } catch (e: Exception) {
          "Failed to run inference: ${e.message}"
        }
      listOf(checkNotNull(resultText))
    }
  }

  override suspend fun countTokens(request: ContentItem): CountTokensResponse {
    if (request is ContentItem.CacheRequestItem) {
      // Count tokens does not support for cache request by now.
      return CountTokensResponse(0)
    }
    val genRequest = createGenerateContentRequest(request)
    return if (useStructuredOutput) {
      checkNotNull(generativeModel)
        .countTokens(
          generateTypedContentRequest(
            generateContentRequest = genRequest,
            outputClass = checkNotNull(selectedOutputClass),
            includeSchemaInPrompt = true,
          )
        )
    } else {
      checkNotNull(generativeModel).countTokens(genRequest)
    }
  }

  override suspend fun getTokenLimit(): Int {
    return checkNotNull(generativeModel).getTokenLimit()
  }

  override fun startGeneratingUi() {
    super.startGeneratingUi()
    sendButton.isEnabled = false
    requestEditText.isEnabled = false
    selectImageButton.isEnabled = false
    sendButton.setText(R.string.generating)
  }

  override fun endGeneratingUi(debugInfo: String) {
    super.endGeneratingUi(debugInfo)
    sendButton.isEnabled = true
    requestEditText.isEnabled = true
    selectImageButton.isEnabled = true
    sendButton.setText(R.string.button_send)
  }

  private fun initGenerator() {
    generativeModel?.close()
    val generationConfig = generationConfig {
    }
    generativeModel = com.google.mlkit.genai.prompt.Generation.getClient(generationConfig)
    resetProcessor()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    if (!super.onCreateOptionsMenu(menu)) {
      return false
    }
    menu.add(Menu.NONE, ACTION_CLEAR_CACHES, Menu.NONE, "Clear all prefix caches")
    return true
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    menu.findItem(R.id.action_simple_api)?.apply {
      isVisible = true
      isChecked = useDefaultConfig
    }
    menu.findItem(R.id.action_explicit_cache)?.apply {
      isVisible = true
      isChecked = useExplicitCache
      isEnabled = !useDefaultConfig
    }
    menu.findItem(R.id.action_structured_output)?.apply {
      isVisible = true
      isChecked = useStructuredOutput
      isEnabled = !useDefaultConfig
    }
    menu.findItem(R.id.action_streaming)?.apply {
      var disableStreaming = useStructuredOutput
      if (disableStreaming) {
        isEnabled = false
        isChecked = false
      } else {
        isEnabled = true
      }
    }
    return super.onPrepareOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      ACTION_CLEAR_CACHES -> {
        lifecycleScope.launch {
          if (useExplicitCache) {
            val caches = checkNotNull(generativeModel).caches.list()
            if (caches.isNotEmpty()) {
              Log.d(TAG, "Going to delete explicit caches, size: ${caches.size}")
              for (cacheName in caches.map { it.name }) {
                if (checkNotNull(generativeModel).caches.delete(cacheName)) {
                  Log.d(TAG, "Deleted explicit cache: $cacheName")
                } else {
                  Log.d(TAG, "Failed to delete explicit cache: $cacheName")
                }
              }
              prefixEditText.setText("")
            }
          } else {
            checkNotNull(generativeModel).clearImplicitCaches()
            Log.d(TAG, "Cleared implicit caches")
          }
          Toast.makeText(this@OpenPromptActivity, "Caches cleared", Toast.LENGTH_SHORT).show()
        }
        return true
      }
      R.id.action_simple_api -> {
        val newState = !item.isChecked
        item.isChecked = newState
        GenerationConfigUtils.setUseDefaultConfig(applicationContext, newState)
        onConfigUpdated()
        return true
      }
      R.id.action_explicit_cache -> {
        val newState = !item.isChecked
        item.isChecked = newState
        GenerationConfigUtils.setUseExplicitCache(applicationContext, newState)
        onConfigUpdated()
        return true
      }
      R.id.action_structured_output -> {
        val newState = !item.isChecked
        item.isChecked = newState
        GenerationConfigUtils.setUseStructuredOutput(applicationContext, newState)
        onConfigUpdated()
        return true
      }
    }
    return super.onOptionsItemSelected(item)
  }
}

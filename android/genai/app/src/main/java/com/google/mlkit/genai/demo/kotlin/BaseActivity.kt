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
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.common.StreamingCallback
import com.google.mlkit.genai.demo.ContentAdapter
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_RESPONSE_STREAMING
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_RESPONSE_STREAMING_THOUGHT
import com.google.mlkit.genai.demo.ContentItem
import com.google.mlkit.genai.demo.GenerationConfigUtils
import com.google.mlkit.genai.demo.R
import com.google.mlkit.genai.prompt.CountTokensResponse
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Base Activity for ML Kit GenAI APIs. */
abstract class BaseActivity<RequestT : ContentItem> : AppCompatActivity() {

  private lateinit var debugInfoTextView: TextView
  private lateinit var contentAdapter: ContentAdapter

  private var modelDownloaded = false
  private var totalBytesToDownload = 0L

  private var streaming = true
  private var useStreamingCallbackApi = false
  private var hasFirstStreamingResult = false
  private var firstTokenLatency = 0L

  private var batchInputUri: Uri? = null
  private var batchRunCancelled = false
  private var batchProcessingIndex = 0

  private lateinit var createBatchOutputFileLauncher: ActivityResultLauncher<String>
  private lateinit var chooseBatchInputLauncher: ActivityResultLauncher<String>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(getLayoutResId())
    streaming = GenerationConfigUtils.getUseStreaming(this)

    debugInfoTextView = findViewById(R.id.debug_info_text_view)

    findViewById<RecyclerView>(R.id.content_recycler_view).apply {
      layoutManager =
        LinearLayoutManager(this@BaseActivity).apply {
          // This enables focusing to the bottom when new content is added.
          stackFromEnd = true
        }
      adapter = ContentAdapter().also { contentAdapter = it }
    }

    createBatchOutputFileLauncher =
      registerForActivityResult(CreateDocument("text/csv")) { batchOutputUri ->
        batchInputUri?.let { inputUri -> batchOutputUri?.let { batchRun(inputUri, it) } }
      }
    chooseBatchInputLauncher =
      registerForActivityResult(GetContent()) { uri ->
        uri?.let {
          batchInputUri = it
          val outputFileName =
            "mlkit_genai_result_${SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault()).format(Date())}.csv"
          createBatchOutputFileLauncher.launch(outputFileName)
        }
      }
  }

  protected fun refreshConfigs() {
    streaming = GenerationConfigUtils.getUseStreaming(this)
  }

  override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)
    Futures.addCallback(
      getBaseModelName(),
      object : FutureCallback<String> {
        override fun onSuccess(result: String) {
          debugInfoTextView.visibility = View.VISIBLE
          debugInfoTextView.text = getString(R.string.base_model_name, result)
        }

        override fun onFailure(t: Throwable) {
          Log.e(TAG, "Failed to get base model name.", t)
        }
      },
      ContextCompat.getMainExecutor(this),
    )
  }

  protected fun onSend(request: RequestT) {
    contentAdapter.addContent(request)
    startGeneratingUi()
    if (modelDownloaded) {
      runInference(request)
    } else {
      checkFeatureStatus(request)
    }
  }

  protected abstract fun getBaseModelName(): ListenableFuture<String>

  protected abstract fun getLayoutResId(): Int

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)
    menu.findItem(R.id.action_streaming)?.isChecked = streaming
    return true
  }

  /**
   * This method is called right before the menu is shown. We use it to dynamically enable/disable
   * the API submenu and set radio checks.
   */
  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    super.onPrepareOptionsMenu(menu)
    val streamingItem = menu.findItem(R.id.action_streaming)
    val streamingApiSubmenu = menu.findItem(R.id.action_streaming_api_submenu)

    streamingApiSubmenu?.isEnabled = streaming
    streamingItem?.isChecked = streaming

    if (useStreamingCallbackApi) {
      menu.findItem(R.id.action_streaming_callback)?.isChecked = true
    } else {
      menu.findItem(R.id.action_streaming_flow)?.isChecked = true
    }
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_streaming -> {
        streaming = !streaming
        GenerationConfigUtils.setUseStreaming(this, streaming)
        item.isChecked = streaming
        invalidateOptionsMenu()
        true
      }
      R.id.action_streaming_flow -> {
        useStreamingCallbackApi = false
        item.isChecked = true
        true
      }
      R.id.action_streaming_callback -> {
        useStreamingCallbackApi = true
        item.isChecked = true
        true
      }
      R.id.action_batch_run -> {
        chooseBatchInputLauncher.launch("text/csv")
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun checkFeatureStatus(request: RequestT) {
    Futures.addCallback(
      checkFeatureStatus(),
      object : FutureCallback<Int> {
        override fun onSuccess(featureStatus: Int) {
          when (featureStatus) {
            FeatureStatus.AVAILABLE -> {
              modelDownloaded = true
              runInference(request)
            }
            FeatureStatus.UNAVAILABLE -> displayErrorMessage("Feature is unavailable.")
            else -> downloadAndRunInference(request)
          }
        }

        override fun onFailure(t: Throwable) {
          Log.e(TAG, "Failed to check status.", t)
          displayErrorMessage("Failed to check status", t)
        }
      },
      ContextCompat.getMainExecutor(this),
    )
  }

  protected abstract fun checkFeatureStatus(): @FeatureStatus ListenableFuture<Int>

  private fun downloadAndRunInference(request: RequestT) {
    Futures.addCallback(
      downloadFeature(
        object : DownloadCallback {
          override fun onDownloadStarted(bytesToDownload: Long) {
            totalBytesToDownload = bytesToDownload
          }

          override fun onDownloadFailed(e: GenAiException) {
            displayErrorMessage("Failed to download model", e)
          }

          override fun onDownloadProgress(totalBytesDownloaded: Long) {
            if (totalBytesToDownload > 0) {
              debugInfoTextView.run {
                visibility = View.VISIBLE
                text =
                  String.format(
                    Locale.ENGLISH,
                    "Downloading model: %d / %d MB (%.2f%%)",
                    totalBytesDownloaded / MEGABYTE,
                    totalBytesToDownload / MEGABYTE,
                    100.0 * totalBytesDownloaded / totalBytesToDownload,
                  )
              }
            }
          }

          override fun onDownloadCompleted() {
            modelDownloaded = true
            runInference(request)
          }
        }
      ),
      object : FutureCallback<Void?> {
        override fun onSuccess(result: Void?) {}

        override fun onFailure(t: Throwable) {
          Log.e(TAG, "Failed to download feature.", t)
          displayErrorMessage("Failed to download feature", t)
        }
      },
      ContextCompat.getMainExecutor(this),
    )
  }

  protected abstract fun downloadFeature(callback: DownloadCallback): ListenableFuture<Void>

  private fun runInference(request: RequestT) {
    lifecycleScope.launch {
      val startMs = System.currentTimeMillis()
      val tokenInfoTextBuilder = StringBuilder()

      try {
        val tokenInfo = countTokens(request)
        tokenInfoTextBuilder.append("Input Token count: ${tokenInfo.totalTokens}")
      } catch (e: UnsupportedOperationException) {} catch (e: Exception) {
        Log.e(TAG, "Failed to get token count.", e)
        tokenInfoTextBuilder.append("Token count failed")
      }

      try {
        val tokenLimit = getTokenLimit()
        if (tokenInfoTextBuilder.isNotEmpty()) {
          tokenInfoTextBuilder.append(". ")
        }
        tokenInfoTextBuilder.append("Token limit: $tokenLimit")
      } catch (e: UnsupportedOperationException) {
        // Expected for APIs that don't support token counting.
      } catch (e: Exception) {
        Log.e(TAG, "Failed to get token limit.", e)
        if (tokenInfoTextBuilder.isNotEmpty()) {
          tokenInfoTextBuilder.append(". ")
        }
        tokenInfoTextBuilder.append("Token limit failed")
      }

      val tokenInfoText = tokenInfoTextBuilder.toString()

      if (streaming) {
        var lastViewType: Int? = null
        val thoughtBuilder = StringBuilder()
        val textBuilder = StringBuilder()

        val onChunk: (ContentItem) -> Unit = { chunkItem ->
          if (chunkItem is ContentItem.TextItem) {
            runOnUiThread {
              if (chunkItem.viewType == VIEW_TYPE_RESPONSE_STREAMING_THOUGHT) {
                thoughtBuilder.append(chunkItem.text)
                if (lastViewType == VIEW_TYPE_RESPONSE_STREAMING_THOUGHT) {
                  contentAdapter.updateStreamingThoughtResponse(thoughtBuilder.toString())
                } else {
                  contentAdapter.addContent(
                    ContentItem.TextItem.fromStreamingThoughtResponse(thoughtBuilder.toString())
                  )
                  lastViewType = VIEW_TYPE_RESPONSE_STREAMING_THOUGHT
                }
              } else if (chunkItem.viewType == VIEW_TYPE_RESPONSE_STREAMING) {
                textBuilder.append(chunkItem.text)
                if (lastViewType == VIEW_TYPE_RESPONSE_STREAMING_THOUGHT) {
                  // Transition from thought to text
                  contentAdapter.finalizeStreamingThought(thoughtBuilder.toString())
                  contentAdapter.addContent(
                    ContentItem.TextItem.fromStreamingResponse(textBuilder.toString())
                  )
                  lastViewType = VIEW_TYPE_RESPONSE_STREAMING
                } else if (lastViewType == VIEW_TYPE_RESPONSE_STREAMING) {
                  contentAdapter.updateStreamingResponse(textBuilder.toString())
                } else {
                  contentAdapter.addContent(
                    ContentItem.TextItem.fromStreamingResponse(textBuilder.toString())
                  )
                  lastViewType = VIEW_TYPE_RESPONSE_STREAMING
                  firstTokenLatency = Instant.now().minusMillis(startMs).toEpochMilli()
                }
              }
            }
          }
        }
        val onSuccess: (List<ContentItem>) -> Unit = { results ->
          val totalLatency: Long = Instant.now().minusMillis(startMs).toEpochMilli()
          val debugInfo = getString(R.string.debug_info_streaming, firstTokenLatency, totalLatency)
          val latencyMetadata =
            if (tokenInfoText.isEmpty()) debugInfo else "$tokenInfoText\n$debugInfo"

          if (lastViewType == VIEW_TYPE_RESPONSE_STREAMING_THOUGHT) {
            contentAdapter.finalizeStreamingThought(thoughtBuilder.toString())
          }

          for (result in results) {
            val finalizedResult =
              if (result is ContentItem.TextItem) {
                if (result.viewType == ContentAdapter.VIEW_TYPE_RESPONSE) {
                  result.copy(metadata = latencyMetadata)
                } else {
                  result
                }
              } else {
                result
              }
            contentAdapter.addContent(finalizedResult)
          }
          endGeneratingUi(debugInfo)
        }
        val onFailure: (Throwable) -> Unit = { t ->
          Log.d(TAG, "Streaming result so far:\n$textBuilder")
          Log.e(TAG, "Failed to run inference.", t)
          displayErrorMessage("Failed to run inference", t)
        }

        val streamFlow = if (useStreamingCallbackApi) null else runInferenceStreamImpl(request)

        if (streamFlow != null) {
          try {
            streamFlow.collect { onChunk(it) }
            val finalResults = mutableListOf<ContentItem>()
            if (thoughtBuilder.isNotEmpty()) {
              finalResults.add(ContentItem.TextItem.fromThoughtResponse(thoughtBuilder.toString()))
            }
            if (textBuilder.isNotEmpty()) {
              finalResults.add(ContentItem.TextItem.fromResponse(textBuilder.toString(), null))
            }
            onSuccess(finalResults)
          } catch (t: Throwable) {
            onFailure(t)
          }
        } else {
          val callback =
            object : StreamingCallback {
              override fun onNewText(additionalText: String) {
                onChunk(ContentItem.TextItem.fromStreamingResponse(additionalText))
              }

              override fun onNewThought(additionalThought: String) {
                if (GenerationConfigUtils.getShowThinking(this@BaseActivity)) {
                  onChunk(ContentItem.TextItem.fromStreamingThoughtResponse(additionalThought))
                }
              }
            }
          Futures.addCallback(
            runInferenceImpl(request, callback),
            object : FutureCallback<List<ContentItem>> {
              override fun onSuccess(results: List<ContentItem>) {
                onSuccess(results)
              }

              override fun onFailure(t: Throwable) {
                onFailure(t)
              }
            },
            ContextCompat.getMainExecutor(this@BaseActivity),
          )
        }
      } else {
        Futures.addCallback(
          runInferenceImpl(request, streamingCallback = null),
          object : FutureCallback<List<ContentItem>> {
            override fun onSuccess(results: List<ContentItem>) {
              val debugInfo =
                getString(R.string.debug_info, Instant.now().minusMillis(startMs).toEpochMilli())
              val latencyMetadata =
                if (tokenInfoText.isEmpty()) debugInfo else "$tokenInfoText\n$debugInfo"
              for (result in results) {
                val finalizedResult =
                  if (
                    result is ContentItem.TextItem &&
                      result.viewType == ContentAdapter.VIEW_TYPE_RESPONSE
                  ) {
                    result.copy(metadata = latencyMetadata)
                  } else {
                    result
                  }
                contentAdapter.addContent(finalizedResult)
              }
              endGeneratingUi(debugInfo)
            }

            override fun onFailure(t: Throwable) {
              Log.e(TAG, "Failed to run inference.", t)
              displayErrorMessage("Failed to run inference", t)
            }
          },
          ContextCompat.getMainExecutor(this@BaseActivity),
        )
      }
    }
  }

  protected abstract fun runInferenceImpl(
    request: RequestT,
    streamingCallback: StreamingCallback?,
  ): ListenableFuture<List<ContentItem>>

  protected open fun runInferenceStreamImpl(request: RequestT): Flow<ContentItem>? = null

  protected open suspend fun countTokens(request: RequestT): CountTokensResponse =
    throw UnsupportedOperationException("Not implemented")

  protected open suspend fun getTokenLimit(): Int =
    throw UnsupportedOperationException("Not implemented")

  protected fun setupSpinner(spinnerId: Int, arrayId: Int, onItemSelected: (Int) -> Unit) {
    val spinner = findViewById<Spinner>(spinnerId)
    val adapter =
      ArrayAdapter.createFromResource(this, arrayId, android.R.layout.simple_spinner_item).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
      }

    spinner.adapter = adapter
    spinner.onItemSelectedListener =
      object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
          onItemSelected(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>) {}
      }
  }

  private fun displayErrorMessage(errorMessage: String) {
    displayErrorMessage(errorMessage, cause = null)
  }

  private fun displayErrorMessage(errorMessage: String, cause: Throwable?) {
    var fullErrorMessage = errorMessage
    if (cause != null) {
      fullErrorMessage += ": $cause"
      if (cause.cause != null) {
        fullErrorMessage += "\nCause: ${cause.cause}"
      }
    }
    contentAdapter.addContent(ContentItem.TextItem.fromErrorResponse(fullErrorMessage))
    endGeneratingUi(getString(R.string.empty))
  }

  protected open fun startGeneratingUi() {
    debugInfoTextView.visibility = View.GONE
  }

  protected open fun endGeneratingUi(debugInfo: String) {
    debugInfoTextView.run {
      text = debugInfo
      visibility = if (debugInfo.isEmpty()) View.GONE else View.VISIBLE
    }
  }

  protected fun resetProcessor() {
    modelDownloaded = false
  }

  protected abstract fun runInferenceForBatchTask(request: String): List<String>

  /**
   * Runs inference on batch text requests from an input file.
   *
   * The input file must be a CSV file selected via the file picker, with the first column
   * containing the text requests.
   *
   * The output file will also be in CSV format, containing all the content from the input file with
   * the inference results appended to the end of each row. Each inference result occupies two
   * additional columns: one for the result text (or error message) and one for the score,
   * continuing sequentially.
   */
  private fun batchRun(inputUri: Uri, outputUri: Uri) {
    batchRunCancelled = false
    val processingDialog =
      AlertDialog.Builder(this)
        .setMessage(R.string.batch_run_start_message)
        .setNegativeButton(R.string.button_cancel) { _: DialogInterface, _: Int ->
          batchRunCancelled = true
        }
        .setCancelable(false)
        .show()
    processingDialog.setCanceledOnTouchOutside(false)

    val unused =
      Executors.newSingleThreadExecutor().submit {
        requireNotNull(contentResolver.openInputStream(inputUri)).use { input ->
          InputStreamReader(input).use { reader ->
            val csvReader = CSVReader(reader)
            requireNotNull(contentResolver.openOutputStream(outputUri)).use { output ->
              OutputStreamWriter(output).use { writer ->
                val csvWriter = CSVWriter(writer)
                val inputRows = csvReader.readAll()
                batchProcessingIndex = 0
                for (inputRow in inputRows) {
                  if (batchRunCancelled) {
                    break
                  }

                  batchProcessingIndex++
                  runOnUiThread {
                    processingDialog.setMessage(
                      "Processing $batchProcessingIndex/${inputRows.size}"
                    )
                  }

                  val startTimeMs = System.currentTimeMillis()
                  val resultRow = buildList {
                    addAll(inputRow)
                    addAll(runInferenceForBatchTask(inputRow[0]))
                  }
                  csvWriter.writeNext(resultRow.toTypedArray<String>())
                  val elapsedTimeMs = System.currentTimeMillis() - startTimeMs
                  val remainingTimeMs = MIN_INFERENCE_INTERVAL_MS - elapsedTimeMs
                  if (remainingTimeMs > 0) {
                    Thread.sleep(remainingTimeMs)
                  }
                }
              }
            }
          }
        }

        runOnUiThread { processingDialog.dismiss() }
      }
  }

  companion object {
    private const val TAG = "TextInputBasedActivity"
    private const val MEGABYTE = 1024 * 1024L
    private const val MIN_INFERENCE_INTERVAL_MS = 6000L
  }
}

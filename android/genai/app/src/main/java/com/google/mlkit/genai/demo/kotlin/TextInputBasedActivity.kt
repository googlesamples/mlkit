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
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.TextUtils
import android.text.style.StyleSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.common.StreamingCallback
import com.google.mlkit.genai.demo.ContentAdapter
import com.google.mlkit.genai.demo.R
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Base Activity for APIs that accept text input as request. */
abstract class TextInputBasedActivity : AppCompatActivity() {

  private lateinit var requestEditText: EditText
  private lateinit var sendButton: Button
  private lateinit var debugInfoTextView: TextView
  private lateinit var contentAdapter: ContentAdapter

  private var modelDownloaded = false
  private var totalBytesToDownload = 0L

  private var streaming = true
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

    requestEditText = findViewById(R.id.request_edit_text)
    debugInfoTextView = findViewById(R.id.debug_info_text_view)

    sendButton = findViewById(R.id.send_button)
    sendButton.setOnClickListener {
      val request = requestEditText.text.toString()
      if (TextUtils.isEmpty(request)) {
        Toast.makeText(this, R.string.input_message_is_empty, Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }

      contentAdapter.addContent(ContentAdapter.VIEW_TYPE_REQUEST_TEXT, request)
      startGeneratingUi()
      if (modelDownloaded) {
        runInference(request)
      } else {
        checkFeatureStatus(request)
      }
    }

    findViewById<RecyclerView>(R.id.content_recycler_view).apply {
      layoutManager = LinearLayoutManager(this@TextInputBasedActivity)
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

  protected abstract fun getBaseModelName(): ListenableFuture<String>

  protected abstract fun getLayoutResId(): Int

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_streaming -> {
        streaming = !streaming
        item.isChecked = streaming
        true
      }
      R.id.action_batch_run -> {
        chooseBatchInputLauncher.launch("text/csv")
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun checkFeatureStatus(request: String) {
    Futures.addCallback(
      checkFeatureStatus(),
      object : FutureCallback<Int> {
        override fun onSuccess(featureStatus: Int) {
          when (featureStatus) {
            FeatureStatus.AVAILABLE -> runInference(request)
            FeatureStatus.UNAVAILABLE -> displayErrorMessage("Feature is unavailable.")
            else -> downloadAndRunInference(request)
          }
        }

        override fun onFailure(t: Throwable) {
          Log.e(TAG, "Failed to check status.", t)
          displayErrorMessage("Failed to check status: $t")
        }
      },
      ContextCompat.getMainExecutor(this),
    )
  }

  protected abstract fun checkFeatureStatus(): @FeatureStatus ListenableFuture<Int>

  private fun downloadAndRunInference(request: String) {
    Futures.addCallback(
      downloadFeature(
        object : DownloadCallback {
          override fun onDownloadStarted(bytesToDownload: Long) {
            totalBytesToDownload = bytesToDownload
          }

          override fun onDownloadFailed(e: GenAiException) {
            displayErrorMessage("Failed to download model: $e")
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
          displayErrorMessage("Failed to download feature: $t")
        }
      },
      ContextCompat.getMainExecutor(this),
    )
  }

  protected abstract fun downloadFeature(callback: DownloadCallback): ListenableFuture<Void>

  private fun runInference(request: String) {
    val startMs = System.currentTimeMillis()
    if (streaming) {
      hasFirstStreamingResult = false
      val resultBuilder = SpannableStringBuilder(STREAMING_INDICATOR)
      resultBuilder.setSpan(
        StyleSpan(Typeface.BOLD),
        0,
        STREAMING_INDICATOR.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )

      Futures.addCallback(
        runInferenceImpl(request) { additionalText ->
          runOnUiThread {
            resultBuilder.append(additionalText)
            if (hasFirstStreamingResult) {
              contentAdapter.updateStreamingResponse(SpannedString(resultBuilder))
            } else {
              contentAdapter.addContent(
                ContentAdapter.VIEW_TYPE_RESPONSE,
                SpannedString(resultBuilder),
              )
              hasFirstStreamingResult = true
              firstTokenLatency = Instant.now().minusMillis(startMs).toEpochMilli()
            }
          }
        },
        object : FutureCallback<List<String>> {
          override fun onSuccess(results: List<String>) {
            results.forEach { result ->
              contentAdapter.addContent(ContentAdapter.VIEW_TYPE_RESPONSE, result)
            }
            val totalLatency: Long = Instant.now().minusMillis(startMs).toEpochMilli()
            val debugInfo =
              getString(R.string.debug_info_streaming, firstTokenLatency, totalLatency)
            endGeneratingUi(debugInfo)
          }

          override fun onFailure(t: Throwable) {
            Log.d(TAG, "Streaming result so far:\n$resultBuilder")
            Log.e(TAG, "Failed to run inference.", t)
            displayErrorMessage("Failed to run inference: $t")
          }
        },
        ContextCompat.getMainExecutor(this),
      )
    } else {
      Futures.addCallback(
        runInferenceImpl(request, streamingCallback = null),
        object : FutureCallback<List<String>> {
          override fun onSuccess(results: List<String>) {
            results.forEach { result ->
              contentAdapter.addContent(ContentAdapter.VIEW_TYPE_RESPONSE, result)
            }
            val debugInfo =
              getString(R.string.debug_info, Instant.now().minusMillis(startMs).toEpochMilli())
            endGeneratingUi(debugInfo)
          }

          override fun onFailure(t: Throwable) {
            Log.e(TAG, "Failed to run inference.", t)
            displayErrorMessage("Failed to run inference: $t")
          }
        },
        ContextCompat.getMainExecutor(this),
      )
    }
  }

  protected abstract fun runInferenceImpl(
    request: String,
    streamingCallback: StreamingCallback?,
  ): ListenableFuture<List<String>>

  protected fun setupSpinner(spinnerId: Int, arrayId: Int, onItemSelected: (Int) -> Unit) {
    val spinner = findViewById<Spinner>(spinnerId)
    val adapter =
      ArrayAdapter.createFromResource(this, arrayId, android.R.layout.simple_spinner_item).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
      }

    spinner.adapter = adapter
    spinner.onItemSelectedListener =
      object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
          onItemSelected(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>) {}
      }
  }

  private fun displayErrorMessage(errorMessage: String) {
    contentAdapter.addContent(ContentAdapter.VIEW_TYPE_RESPONSE_ERROR, errorMessage)
    endGeneratingUi(getString(R.string.empty))
  }

  private fun startGeneratingUi() {
    sendButton.isEnabled = false
    sendButton.setText(R.string.generating)
    requestEditText.setText(R.string.empty)
    debugInfoTextView.visibility = View.GONE
  }

  private fun endGeneratingUi(debugInfo: String) {
    sendButton.isEnabled = true
    sendButton.setText(R.string.button_send)
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
    private const val STREAMING_INDICATOR = "STREAMING...\n"
    private const val MEGABYTE = 1024 * 1024L
    private const val MIN_INFERENCE_INTERVAL_MS = 6000L
  }
}

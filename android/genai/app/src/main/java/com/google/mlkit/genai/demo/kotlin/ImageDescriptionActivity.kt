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

import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.style.StyleSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.demo.ContentAdapter
import com.google.mlkit.genai.demo.R
import com.google.mlkit.genai.imagedescription.ImageDescriber
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import com.google.mlkit.genai.imagedescription.ImageDescriptionResult
import java.io.IOException
import java.time.Instant
import java.util.Locale

/** Demonstrates the Image Description API usage. */
class ImageDescriptionActivity : AppCompatActivity() {
  private lateinit var selectImageButton: Button
  private lateinit var debugInfoTextView: TextView
  private lateinit var contentAdapter: ContentAdapter

  private var imageDescriber: ImageDescriber? = null
  private var streaming = true
  private var hasFirstStreamingResult = false
  private var firstTokenLatency = 0L
  private var modelDownloaded = false
  private var totalBytesToDownload = 0L

  private lateinit var selectImageLauncher: ActivityResultLauncher<String>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_image_description)

    selectImageLauncher =
      registerForActivityResult(ActivityResultContracts.GetContent()) { imageUri: Uri? ->
        imageUri?.let {
          contentAdapter.addContent(ContentAdapter.VIEW_TYPE_REQUEST_IMAGE, it)
          startGeneratingUi()
          if (modelDownloaded) {
            describe(it)
          } else {
            checkFeatureStatus(it)
          }
        } ?: run { Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show() }
      }

    selectImageButton = findViewById(R.id.select_image_button)
    selectImageButton.setOnClickListener { selectImageLauncher.launch("image/*") }
    debugInfoTextView = findViewById(R.id.debug_info_text_view)

    findViewById<RecyclerView>(R.id.content_recycler_view).apply {
      layoutManager = LinearLayoutManager(this@ImageDescriptionActivity)
      adapter = ContentAdapter().also { contentAdapter = it }
    }

    initImageDescriber()

    Futures.addCallback(
      checkNotNull(imageDescriber).getBaseModelName(),
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

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)
    return true
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    val item = menu.findItem(R.id.action_batch_run)
    item?.setVisible(false)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == R.id.action_streaming) {
      streaming = !streaming
      item.setChecked(streaming)
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onDestroy() {
    super.onDestroy()
    imageDescriber?.close()
  }

  private fun checkFeatureStatus(imageUri: Uri) {
    Futures.addCallback(
      checkNotNull(imageDescriber).checkFeatureStatus(),
      object : FutureCallback<Int> {
        override fun onSuccess(featureStatus: Int) {
          when (featureStatus) {
            FeatureStatus.AVAILABLE -> describe(imageUri)
            FeatureStatus.UNAVAILABLE -> displayErrorMessage("Feature is unavailable.")
            else -> downloadAndDescribe(imageUri)
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

  private fun downloadAndDescribe(imageUri: Uri) {
    Futures.addCallback(
      checkNotNull(imageDescriber)
        .downloadFeature(
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
                      "Downloading model:  %d / %d MB (%.2f%%)",
                      totalBytesDownloaded / MEGABYTE,
                      totalBytesToDownload / MEGABYTE,
                      100.0 * totalBytesDownloaded / totalBytesToDownload,
                    )
                }
              }
            }

            override fun onDownloadCompleted() {
              modelDownloaded = true
              describe(imageUri)
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

  private fun describe(imageUri: Uri) {
    try {
      val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, imageUri))
      val startMs = System.currentTimeMillis()
      if (streaming) {
        firstTokenLatency = 0
        hasFirstStreamingResult = false
        val resultBuilder = SpannableStringBuilder(STREAMING_INDICATOR)
        resultBuilder.setSpan(
          StyleSpan(Typeface.BOLD),
          0,
          STREAMING_INDICATOR.length,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        Futures.addCallback(
          checkNotNull(imageDescriber).runInference(
            ImageDescriptionRequest.builder(bitmap).build()
          ) { additionalText: String ->
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
          object : FutureCallback<ImageDescriptionResult> {
            override fun onSuccess(result: ImageDescriptionResult) {
              contentAdapter.addContent(ContentAdapter.VIEW_TYPE_RESPONSE, result.description)
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
          checkNotNull(imageDescriber)
            .runInference(ImageDescriptionRequest.builder(bitmap).build()),
          object : FutureCallback<ImageDescriptionResult> {
            override fun onSuccess(result: ImageDescriptionResult) {
              contentAdapter.addContent(ContentAdapter.VIEW_TYPE_RESPONSE, result.description)
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
    } catch (e: IOException) {
      Log.e(TAG, "Failed to decode image uri.", e)
      endGeneratingUi("Failed to decode image uri: $e")
    }
  }

  private fun displayErrorMessage(errorMessage: String) {
    contentAdapter.addContent(ContentAdapter.VIEW_TYPE_RESPONSE_ERROR, errorMessage)
    endGeneratingUi(getString(R.string.empty))
  }

  private fun startGeneratingUi() {
    selectImageButton.isEnabled = false
    selectImageButton.setText(R.string.generating)
    debugInfoTextView.visibility = View.GONE
  }

  private fun endGeneratingUi(debugInfo: String) {
    selectImageButton.isEnabled = true
    selectImageButton.setText(R.string.button_select_image)
    debugInfoTextView.run {
      text = debugInfo
      visibility = if (debugInfo.isEmpty()) View.GONE else View.VISIBLE
    }
  }

  private fun initImageDescriber() {
    imageDescriber?.close()
    val options = ImageDescriberOptions.builder(this).build()
    imageDescriber = ImageDescription.getClient(options)
    modelDownloaded = false
  }

  companion object {
    private val TAG: String = ImageDescriptionActivity::class.java.simpleName
    private const val STREAMING_INDICATOR = "STREAMING...\n"
    private const val MEGABYTE = 1024 * 1024L
  }
}

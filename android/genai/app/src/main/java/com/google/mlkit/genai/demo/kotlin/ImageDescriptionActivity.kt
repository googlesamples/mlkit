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
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.Futures.immediateFailedFuture
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.StreamingCallback
import com.google.mlkit.genai.demo.ContentItem
import com.google.mlkit.genai.demo.R
import com.google.mlkit.genai.imagedescription.ImageDescriber
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import java.io.IOException

/** Demonstrates the Image Description API usage. */
class ImageDescriptionActivity : BaseActivity<ContentItem.ImageItem>() {
  private lateinit var selectImageButton: Button

  private var imageDescriber: ImageDescriber? = null

  private lateinit var selectImageLauncher: ActivityResultLauncher<String>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    selectImageLauncher =
      registerForActivityResult(ActivityResultContracts.GetContent()) { imageUri: Uri? ->
        imageUri?.let { onSend(ContentItem.ImageItem.fromRequest(it)) }
          ?: run { Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show() }
      }
    selectImageButton = findViewById(R.id.select_image_button)
    selectImageButton.setOnClickListener { selectImageLauncher.launch("image/*") }

    initImageDescriber()
  }

  override fun getLayoutResId(): Int {
    return R.layout.activity_image_description
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    val item = menu.findItem(R.id.action_batch_run)
    item?.isVisible = false
    return true
  }

  override fun onDestroy() {
    super.onDestroy()
    imageDescriber?.close()
  }

  override fun getBaseModelName(): ListenableFuture<String> {
    return checkNotNull(imageDescriber).baseModelName
  }

  override fun checkFeatureStatus(): @FeatureStatus ListenableFuture<Int> {
    return checkNotNull(imageDescriber).checkFeatureStatus()
  }

  override fun downloadFeature(callback: DownloadCallback): ListenableFuture<Void> {
    return checkNotNull(imageDescriber).downloadFeature(callback)
  }

  override fun runInferenceImpl(
    request: ContentItem.ImageItem,
    streamingCallback: StreamingCallback?,
  ): ListenableFuture<List<String>> {
    try {
      val bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, request.imageUri))
      val imageDescriptionRequest = ImageDescriptionRequest.builder(bitmap).build()
      val inferenceFuture =
        checkNotNull(imageDescriber).let { imageDescriber ->
          streamingCallback?.let { imageDescriber.runInference(imageDescriptionRequest, it) }
            ?: imageDescriber.runInference(imageDescriptionRequest)
        }

      return Futures.transform(
        inferenceFuture,
        { imageDescriptionResult -> listOf(imageDescriptionResult.description) },
        ContextCompat.getMainExecutor(this),
      )
    } catch (e: IOException) {
      return immediateFailedFuture(e)
    }
  }

  override fun startGeneratingUi() {
    selectImageButton.isEnabled = false
    selectImageButton.setText(R.string.generating)
  }

  override fun endGeneratingUi(debugInfo: String) {
    selectImageButton.isEnabled = true
    selectImageButton.setText(R.string.button_select_image)
  }

  override fun runInferenceForBatchTask(request: String): List<String> {
    throw UnsupportedOperationException("Not supported")
  }

  private fun initImageDescriber() {
    imageDescriber?.close()
    val options = ImageDescriberOptions.builder(this).build()
    imageDescriber = ImageDescription.getClient(options)
    resetProcessor()
  }
}

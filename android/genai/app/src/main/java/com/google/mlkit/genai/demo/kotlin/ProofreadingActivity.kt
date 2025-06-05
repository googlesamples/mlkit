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

import android.os.Bundle
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.StreamingCallback
import com.google.mlkit.genai.demo.ContentItem
import com.google.mlkit.genai.demo.R
import com.google.mlkit.genai.proofreading.Proofreader
import com.google.mlkit.genai.proofreading.ProofreaderOptions.InputType
import com.google.mlkit.genai.proofreading.ProofreaderOptions.Language
import com.google.mlkit.genai.proofreading.ProofreaderOptions.builder
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest

/** Demonstrates the Proofreading API usage. */
class ProofreadingActivity : TextInputBaseActivity() {
  private var inputType = InputType.KEYBOARD
  private var language = Language.ENGLISH
  private var proofreader: Proofreader? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setUpSpinners()
    initProofreader()
  }

  override fun getLayoutResId(): Int {
    return R.layout.activity_proofreading
  }

  override fun runInferenceForBatchTask(request: String): List<String> {
    return try {
      buildList {
        val result =
          checkNotNull(proofreader).runInference(ProofreadingRequest.builder(request).build()).get()
        for (suggestion in result.results) {
          add(suggestion.text)
        }
      }
    } catch (e: Exception) {
      listOf("Failed to run inference: ${e.message}", "0")
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    proofreader?.close()
  }

  override fun getBaseModelName(): ListenableFuture<String> {
    return checkNotNull(proofreader).baseModelName
  }

  override fun checkFeatureStatus(): @FeatureStatus ListenableFuture<Int> {
    return checkNotNull(proofreader).checkFeatureStatus()
  }

  override fun downloadFeature(callback: DownloadCallback): ListenableFuture<Void> {
    return checkNotNull(proofreader).downloadFeature(callback)
  }

  override fun runInferenceImpl(
    request: ContentItem.TextItem,
    streamingCallback: StreamingCallback?,
  ): ListenableFuture<List<String>> {
    val proofreadingRequest = ProofreadingRequest.builder(request.text).build()
    val inferenceFuture =
      checkNotNull(proofreader).let { proofreader ->
        streamingCallback?.let { proofreader.runInference(proofreadingRequest, it) }
          ?: proofreader.runInference(proofreadingRequest)
      }

    return Futures.transform(
      inferenceFuture,
      { proofreadingResult -> proofreadingResult.results.map { it.text } },
      ContextCompat.getMainExecutor(this),
    )
  }

  private fun setUpSpinners() {
    setupSpinner(R.id.input_type_spinner, R.array.proofreading_input_types) { position ->
      inputType =
        when (position) {
          0 -> InputType.KEYBOARD
          1 -> InputType.VOICE
          else -> inputType
        }
      initProofreader()
    }

    setupSpinner(R.id.language_spinner, R.array.proofreading_languages) { position ->
      language =
        when (position) {
          0 -> Language.ENGLISH
          1 -> Language.JAPANESE
          2 -> Language.GERMAN
          3 -> Language.FRENCH
          4 -> Language.ITALIAN
          5 -> Language.SPANISH
          6 -> Language.KOREAN
          else -> language
        }
      initProofreader()
    }
  }

  private fun initProofreader() {
    proofreader?.close()
    val options = builder(this).setInputType(inputType).setLanguage(language).build()
    proofreader = Proofreading.getClient(options)
    resetProcessor()
  }
}

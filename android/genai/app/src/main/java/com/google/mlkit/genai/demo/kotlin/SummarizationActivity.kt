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
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizationResult
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.google.mlkit.genai.summarization.SummarizerOptions.InputType
import com.google.mlkit.genai.summarization.SummarizerOptions.Language
import com.google.mlkit.genai.summarization.SummarizerOptions.OutputType

/** Demonstrates the Summarization API usage. */
class SummarizationActivity : TextInputBaseActivity() {
  private var inputType = InputType.ARTICLE
  private var outputType = OutputType.ONE_BULLET
  private var language = Language.ENGLISH
  private var summarizer: Summarizer? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setUpInputAndOutputTypeSpinners()
    initSummarizer()
  }

  override fun getLayoutResId(): Int {
    return R.layout.activity_summarization
  }

  override fun runInferenceForBatchTask(request: String): List<String> {
    try {
      val result =
        checkNotNull(summarizer).runInference(SummarizationRequest.builder(request).build()).get()
      return listOf(result.summary)
    } catch (e: Exception) {
      return listOf("Failed to run inference: ${e.message}")
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    summarizer?.close()
  }

  override fun getBaseModelName(): ListenableFuture<String> {
    return checkNotNull(summarizer).baseModelName
  }

  override fun checkFeatureStatus(): @FeatureStatus ListenableFuture<Int> {
    return checkNotNull(summarizer).checkFeatureStatus()
  }

  override fun downloadFeature(callback: DownloadCallback): ListenableFuture<Void> {
    return checkNotNull(summarizer).downloadFeature(callback)
  }

  override fun runInferenceImpl(
    request: ContentItem.TextItem,
    streamingCallback: StreamingCallback?,
  ): ListenableFuture<List<String>> {
    val summarizeRequest = SummarizationRequest.builder(request.text).build()
    val inferenceFuture =
      checkNotNull(summarizer).let { summarizer ->
        streamingCallback?.let { summarizer.runInference(summarizeRequest, it) }
          ?: summarizer.runInference(summarizeRequest)
      }

    return Futures.transform<SummarizationResult, List<String>>(
      inferenceFuture,
      { summarizeResult -> listOf(summarizeResult.summary) },
      ContextCompat.getMainExecutor(this),
    )
  }

  private fun setUpInputAndOutputTypeSpinners() {
    setupSpinner(R.id.input_type_spinner, R.array.summarization_input_types) { position ->
      inputType =
        when (position) {
          0 -> InputType.ARTICLE
          1 -> InputType.CONVERSATION
          else -> inputType
        }
      initSummarizer()
    }
    setupSpinner(R.id.output_type_spinner, R.array.summarization_output_types) { position ->
      outputType =
        when (position) {
          0 -> OutputType.ONE_BULLET
          1 -> OutputType.TWO_BULLETS
          2 -> OutputType.THREE_BULLETS
          else -> outputType
        }
      initSummarizer()
    }
    setupSpinner(R.id.language_spinner, R.array.summarization_languages) { position ->
      language =
        when (position) {
          0 -> Language.ENGLISH
          1 -> Language.JAPANESE
          2 -> Language.KOREAN
          else -> language
        }
      initSummarizer()
    }
  }

  private fun initSummarizer() {
    summarizer?.close()
    val options =
      SummarizerOptions.builder(this)
        .setInputType(inputType)
        .setOutputType(outputType)
        .setLanguage(language)
        .build()
    summarizer = Summarization.getClient(options)
    resetProcessor()
  }
}

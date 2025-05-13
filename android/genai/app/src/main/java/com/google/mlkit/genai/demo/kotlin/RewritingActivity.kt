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
import com.google.mlkit.genai.demo.R
import com.google.mlkit.genai.rewriting.Rewriter
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.RewriterOptions.Language
import com.google.mlkit.genai.rewriting.RewriterOptions.OutputType
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewritingRequest

/** Demonstrates the Rewriting API usage. */
class RewritingActivity : TextInputBasedActivity() {
  private var outputType = OutputType.ELABORATE
  private var language = Language.ENGLISH
  private var rewriter: Rewriter? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setUpSpinners()
    initRewriter()
  }

  override fun getLayoutResId(): Int {
    return R.layout.activity_rewrite
  }

  override fun runInferenceForBatchTask(request: String): List<String> {
    return try {
      buildList {
        val result =
          checkNotNull(rewriter).runInference(RewritingRequest.builder(request).build()).get()
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
    rewriter?.close()
  }

  override fun getBaseModelName(): ListenableFuture<String> {
    return checkNotNull(rewriter).getBaseModelName()
  }

  override fun checkFeatureStatus(): @FeatureStatus ListenableFuture<Int> {
    return checkNotNull(rewriter).checkFeatureStatus()
  }

  override fun downloadFeature(callback: DownloadCallback): ListenableFuture<Void> {
    return checkNotNull(rewriter).downloadFeature(callback)
  }

  override fun runInferenceImpl(
    request: String,
    streamingCallback: StreamingCallback?,
  ): ListenableFuture<List<String>> {
    val rewritingRequest = RewritingRequest.builder(request).build()
    val inferenceFuture =
      checkNotNull(rewriter).let { rewriter ->
        streamingCallback?.let { rewriter.runInference(rewritingRequest, it) }
          ?: rewriter.runInference(rewritingRequest)
      }

    return Futures.transform(
      inferenceFuture,
      { rewriteResult -> rewriteResult.results.map { it.text } },
      ContextCompat.getMainExecutor(this),
    )
  }

  private fun setUpSpinners() {
    setupSpinner(R.id.output_type_spinner, R.array.rewriting_output_types) { position ->
      outputType =
        when (position) {
          0 -> OutputType.ELABORATE
          1 -> OutputType.EMOJIFY
          2 -> OutputType.SHORTEN
          3 -> OutputType.FRIENDLY
          4 -> OutputType.PROFESSIONAL
          5 -> OutputType.REPHRASE
          else -> outputType
        }
      initRewriter()
    }
    setupSpinner(R.id.language_spinner, R.array.rewriting_languages) { position ->
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
      initRewriter()
    }
  }

  private fun initRewriter() {
    rewriter?.close()
    val options =
      RewriterOptions.builder(this).setOutputType(outputType).setLanguage(language).build()
    rewriter = Rewriting.getClient(options)
    resetProcessor()
  }
}

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
package com.google.mlkit.genai.demo.kotlin.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class EmbeddingRepository(
  context: Context,
  private val semanticSearchHelper: SemanticSearchHelper = SemanticSearchHelper(context),
) {
  private val modelLock = Mutex()

  open val isDbInitialized: StateFlow<Boolean> = semanticSearchHelper.isDbInitialized

  open val isModelInitialized: StateFlow<Boolean> = semanticSearchHelper.isModelInitialized

  open fun getSelectedModelSignature(): String? = semanticSearchHelper.getDefaultModelSignature()

  open fun getSelectedModelDimension(): Int = semanticSearchHelper.getDefaultModelDimension()

  open fun getSelectedModelTokenLimit(): Int = semanticSearchHelper.getDefaultModelTokenLimit()

  open fun close() {
    semanticSearchHelper.close()
  }

  open suspend fun initAppSearch() {
    semanticSearchHelper.initSearchSession()
    checkSignatureAndReindexIfNeeded()
  }

  open suspend fun initModel() {
    semanticSearchHelper.initEmbeddingModel()
    isDbInitialized.first { it }
    checkSignatureAndReindexIfNeeded()
  }

  /**
   * Checks if the active model signature differs from the AppSearch metadata or if the database is
   * empty, and automatically clears and reindexes sample data if a mismatch is detected.
   */
  open suspend fun checkSignatureAndReindexIfNeeded() {
    modelLock.withLock { checkSignatureAndReindexInternal() }
  }

  private suspend fun checkSignatureAndReindexInternal() {
    if (semanticSearchHelper.isDatabaseEmpty() || semanticSearchHelper.isSignatureMismatch()) {
      semanticSearchHelper.clearAllDocuments()
      reindexSampleDataInternal()
    }
  }

  open suspend fun reindexSampleData() {
    modelLock.withLock { reindexSampleDataInternal() }
  }

  /**
   * Clears all existing AppSearch documents and re-indexes sample documents using the active model.
   */
  open suspend fun manualReindex() {
    modelLock.withLock {
      semanticSearchHelper.clearAllDocuments()
      reindexSampleDataInternal()
    }
  }

  private suspend fun reindexSampleDataInternal() {
    isDbInitialized.first { it }
    val unused = semanticSearchHelper.indexDocuments(SAMPLE_DOCUMENTS)
    semanticSearchHelper.getDefaultModelSignature()?.let {
      semanticSearchHelper.setMetadataSignature(it)
    }
  }

  /** Clears all documents stored in the AppSearch session. */
  open suspend fun clearAllDocuments() {
    modelLock.withLock { semanticSearchHelper.clearAllDocuments() }
  }

  open suspend fun performSemanticSearch(
    query: String,
    imageUri: Uri? = null,
    threshold: Double = DEFAULT_SIMILARITY_THRESHOLD,
  ): SearchResult {
    if (!isDbInitialized.value) return SearchResult(emptyList(), null)
    return semanticSearchHelper.search(query, imageUri, threshold)
  }

  open suspend fun isImageModalitySupported(): Boolean =
    semanticSearchHelper.isImageModalitySupported()

  open suspend fun getAllDocuments(): List<Pair<String, Double>> {
    if (!isDbInitialized.value) return emptyList()
    return semanticSearchHelper.getAllDocuments()
  }

  open suspend fun getMetadataSignature(): String? = semanticSearchHelper.getMetadataSignature()

  open suspend fun setMetadataSignature(signature: String) {
    semanticSearchHelper.setMetadataSignature(signature)
  }

  /** Returns all available on-device embedding model signatures. */
  open suspend fun getAvailableModelSignatures(): List<String> =
    semanticSearchHelper.getAvailableModelSignatures()

  /**
   * Switches the active model to [signature] and automatically triggers database re-indexing if the
   * new model signature differs from indexed documents.
   */
  open suspend fun selectModel(signature: String) {
    modelLock.withLock {
      semanticSearchHelper.selectModel(signature)
      if (isDbInitialized.value) {
        checkSignatureAndReindexInternal()
      }
    }
  }

  companion object {
    /**
     * Default cosine similarity score threshold (0.0 to 1.0) for AppSearch queries. Documents with
     * similarity scores below this threshold are filtered out of search results.
     */
    const val DEFAULT_SIMILARITY_THRESHOLD = 0.22
    private const val TAG = "EmbeddingRepository"
  }
}

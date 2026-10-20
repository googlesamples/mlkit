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
import android.os.SystemClock
import android.util.Log
import androidx.appsearch.app.AppSearchSchema
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.EmbeddingVector
import androidx.appsearch.app.GenericDocument
import androidx.appsearch.app.GetByDocumentIdRequest
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.localstorage.LocalStorage
import androidx.concurrent.futures.await
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.embedding.Content
import com.google.mlkit.genai.embedding.EmbeddingGeneration
import com.google.mlkit.genai.embedding.EmbeddingModel
import com.google.mlkit.genai.embedding.EmbeddingOptions
import com.google.mlkit.genai.embedding.EmbeddingRequest
import com.google.mlkit.genai.embedding.ImagePart
import com.google.mlkit.genai.embedding.Modality
import com.google.mlkit.genai.embedding.Part
import com.google.mlkit.genai.embedding.TextPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Contains metadata and vector results for a query embedding inference request. */
data class QueryEmbeddingInfo(
  val vector: FloatArray,
  val latencyMs: Long,
  val dimension: Int,
  val modalities: List<String>,
)

/**
 * Result of a semantic search operation containing matched documents and optional query embedding
 * metadata.
 */
data class SearchResult(
  val documents: List<Pair<String, Double>>,
  val queryEmbeddingInfo: QueryEmbeddingInfo? = null,
) : List<Pair<String, Double>> by documents

/**
 * Helper class that coordinates local semantic search by bridging the **ML Kit Text Embedding API**
 * and **AndroidX AppSearch**.
 *
 * It is responsible for:
 * 1. **Managing the AppSearch Session**: Opening and maintaining an [AppSearchSession] via
 *    [LocalStorage] for local document storage and retrieval.
 * 2. **Schema Definition**: Registering schema definitions using [SetSchemaRequest],
 *    [AppSearchSchema], and [AppSearchSchema.EmbeddingPropertyConfig] for vector indexing and
 *    quantization.
 * 3. **Embedding Generation & Metadata Extraction**: Interfacing with ML Kit [EmbeddingModel] to
 *    extract model metadata, warm up native weights, and generate float array vector embeddings.
 * 4. **Indexing & Querying**: Populating documents using [PutDocumentsRequest] /
 *    [AppSearchSession.putAsync] and executing semantic vector searches using [SearchSpec] and
 *    [AppSearchSession.search].
 *
 * ### Key AppSearch APIs Used:
 * - **[AppSearchSession]**: Asynchronous database session returned by
 *   [LocalStorage.createSearchSessionAsync] which serves as the primary entry point for schema
 *   registration, indexing, and searching.
 * - **[AppSearchSession.setSchemaAsync]**: Submits [SetSchemaRequest] definitions to configure
 *   database schemas asynchronously.
 * - **[AppSearchSchema.EmbeddingPropertyConfig]**: Configures embedding properties in AppSearch,
 *   specifying [AppSearchSchema.EmbeddingPropertyConfig.INDEXING_TYPE_SIMILARITY] for vector
 *   indexing and [AppSearchSchema.EmbeddingPropertyConfig.QUANTIZATION_TYPE_8_BIT] to compress
 *   float vectors to 8-bit integers.
 * - **[AppSearchSession.putAsync]**: Submits a [PutDocumentsRequest] containing [GenericDocument]
 *   instances with attached [EmbeddingVector]s to index documents with text and vector embeddings.
 * - **[AppSearchSession.search]**: Performs full-text and semantic vector searches using query
 *   expressions like `semanticSearch(getEmbeddingParameter(0), threshold)`.
 * - **[SearchSpec]**: Configures search options including schema filters, metric types
 *   ([SearchSpec.EMBEDDING_SEARCH_METRIC_TYPE_COSINE]), ranking strategies
 *   (`sum(this.matchedSemanticScores(getEmbeddingParameter(0)))`), and embedding parameters
 *   ([SearchSpec.Builder.addEmbeddingParameters]).
 *
 * ### Key ML Kit Embedding APIs Used:
 * - **Model Warmup ([EmbeddingModel.warmup])**: Pre-loads native model weights into memory on
 *   background threads to avoid first-inference latency spikes.
 * - **Metadata Extraction ([EmbeddingModel.getMetadata], [EmbeddingModel.getModelSignature],
 *   [EmbeddingModel.getTokenLimit])**: Extracts unique model signatures, input token limits, and
 *   vector dimensions for validation.
 * - **Vector Generation ([EmbeddingModel.generateEmbedding])**: Takes an [EmbeddingRequest]
 *   wrapping [Content] and [TextPart] instances to generate high-dimensional float array vector
 *   embeddings for text.
 *
 * ### Critical User Journey (CUJ) Usage:
 *
 * To use this class for indexing and searching:
 * 1. **Initialize the helper**: Create an instance and initialize the AppSearch session.
 * 2. **Index data**: Generate embeddings and populate the database (usually if it's empty).
 * 3. **Query**: Perform semantic searches against the indexed data.
 *
 * ### Code Example:
 * ```kotlin
 * val helper = SemanticSearchHelper(context)
 *
 * coroutineScope.launch {
 *     // 1. Initialize AppSearch session and register schemas
 *     helper.initSearchSession()
 *
 *     // 2. Populate database if empty
 *     if (helper.isDatabaseEmpty()) {
 *         val documentsToIndex = listOf(
 *             "Android is a mobile operating system developed by Google.",
 *             "AppSearch is a high-performance on-device search library.",
 *             "Vector embeddings represent semantic meaning of text."
 *         )
 *         // Generates embeddings via ML Kit and indexes them in AppSearch
 *         helper.indexDocuments(documentsToIndex)
 *     }
 *
 *     // 3. Perform semantic search
 *     val query = "What is AppSearch?"
 *     val threshold = 0.22
 *     val results: List<Pair<String, Double>> = helper.search(query, threshold)
 *
 *     results.forEach { (text, score) ->
 *         println("Matched: '$text' with similarity score: $score")
 *     }
 * }
 * ```
 */
class SemanticSearchHelper(
  private val context: Context,
  /**
   * Provider lambda to retrieve the default [EmbeddingModel] instance from the ML Kit SDK.
   *
   * By default, it queries ML Kit for available embedding models via [fetchDefaultEmbeddingModel].
   * Can be overridden in tests to inject a fake model.
   */
  private val defaultModelProvider: suspend () -> EmbeddingModel? = {
    fetchDefaultEmbeddingModel()
  },
  private val modelProvider: suspend (String) -> EmbeddingModel? = { fetchEmbeddingModel(it) },
  private val allModelsProvider: suspend () -> List<EmbeddingModel> = { fetchAllEmbeddingModels() },
) {
  /**
   * The active [AppSearchSession] for performing local database operations, or `null` if
   * uninitialized.
   */
  private var session: AppSearchSession? = null

  /** Cached [EmbeddingModel] instance. */
  private var defaultModel: EmbeddingModel? = null

  /** Cached unique signature string identifying the default embedding model. */
  private var defaultModelSignature: String? = null

  /**
   * Cached output vector dimension of the default embedding model (e.g., 768), or `-1` if
   * uninitialized.
   */
  private var defaultModelDimension: Int = -1

  /**
   * Cached maximum token limit supported by the default embedding model, or `-1` if uninitialized.
   */
  private var defaultModelTokenLimit: Int = -1

  private val _isDbInitialized = MutableStateFlow(false)
  val isDbInitialized: StateFlow<Boolean> = _isDbInitialized.asStateFlow()

  private val _isModelInitialized = MutableStateFlow(false)
  val isModelInitialized: StateFlow<Boolean> = _isModelInitialized.asStateFlow()

  fun getDefaultModelSignature(): String? = defaultModelSignature

  fun getDefaultModelDimension(): Int = defaultModelDimension

  fun getDefaultModelTokenLimit(): Int = defaultModelTokenLimit

  /** Opens the local AppSearch database session and registers schema definitions. */
  suspend fun initDatabaseSession() {
    try {
      session =
        LocalStorage.createSearchSessionAsync(
            LocalStorage.SearchContext.Builder(context, DATABASE_NAME).build()
          )
          .await()
      setSchemas()
      _isDbInitialized.value = true
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "Failed to create AppSearch session.", e)
      throw RuntimeException("Failed to create session", e)
    }
  }

  /** Warms up [model], downloads required assets if necessary, and caches model metadata. */
  private suspend fun setupModel(model: EmbeddingModel) {
    when (model.checkStatus()) {
      FeatureStatus.AVAILABLE -> {
        model.warmup()
        defaultModelSignature = model.getModelSignature()
        defaultModelTokenLimit = model.getTokenLimit()
        defaultModelDimension = model.getMetadata()?.dimension ?: -1
        _isModelInitialized.value = true
      }
      FeatureStatus.DOWNLOADABLE,
      FeatureStatus.DOWNLOADING -> {
        val unused = model.download().first { it is DownloadStatus.DownloadCompleted }
        model.warmup()
        defaultModelSignature = model.getModelSignature()
        defaultModelTokenLimit = model.getTokenLimit()
        defaultModelDimension = model.getMetadata()?.dimension ?: -1
        _isModelInitialized.value = true
      }
      FeatureStatus.UNAVAILABLE -> {
        defaultModelSignature = null
      }
    }
  }

  /**
   * Resolves the default embedding model, downloads required model assets if necessary, warms up
   * model weights, and caches model metadata.
   */
  suspend fun initEmbeddingModel() {
    try {
      val model = defaultModel ?: defaultModelProvider()?.also { defaultModel = it }
      // If no model is available on device or model resolution fails, mark model signature
      // as null so UI updates to "Unavailable" status, and abort remaining setup.
      if (model == null) {
        defaultModelSignature = null
        return
      }
      setupModel(model)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "Failed to warmup default model and cache metadata", e)
    }
  }

  /**
   * Opens the local AppSearch database session, registers schema definitions, resolves the default
   * embedding model, downloads required model assets if necessary, and warms up model weights.
   *
   * Convenience function calling [initDatabaseSession] and [initEmbeddingModel] sequentially.
   */
  suspend fun initSearchSession() {
    initDatabaseSession()
    _isDbInitialized.value = session != null
    initEmbeddingModel()
    _isModelInitialized.value = defaultModel != null
  }

  /** Releases active model engine and AppSearch session handles. */
  fun close() {
    runCatching { defaultModel?.close() }.onFailure { if (it is CancellationException) throw it }
    defaultModel = null
    runCatching { session?.close() }.onFailure { if (it is CancellationException) throw it }
    session = null
  }

  /**
   * Registers the document and metadata schemas in AppSearch.
   *
   * Schema Definition Details:
   * - Document Schema Type: [SCHEMA_TYPE_DOCUMENT] ("schema_type_document").
   * - Text Property: [PROPERTY_TEXT] ("text"), indexed with prefix matching and plain text
   *   tokenization.
   * - Embedding Property: [PROPERTY_EMBEDDING] ("embedding"), configured with:
   *     - Indexing Type: [AppSearchSchema.EmbeddingPropertyConfig.INDEXING_TYPE_SIMILARITY] for
   *       vector indexing.
   *     - Quantization Type: [AppSearchSchema.EmbeddingPropertyConfig.QUANTIZATION_TYPE_8_BIT] to
   *       compress float vectors.
   * - Metadata Schema Type: [SCHEMA_TYPE_METADATA] ("DbMetadata"), storing
   *   [PROPERTY_MODEL_SIGNATURE].
   */
  private suspend fun setSchemas() {
    val documentSchema =
      AppSearchSchema.Builder(SCHEMA_TYPE_DOCUMENT)
        .addProperty(
          AppSearchSchema.StringPropertyConfig.Builder(PROPERTY_TEXT)
            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
            .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
            .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
            .build()
        )
        .addProperty(
          AppSearchSchema.EmbeddingPropertyConfig.Builder(PROPERTY_EMBEDDING)
            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
            .setIndexingType(AppSearchSchema.EmbeddingPropertyConfig.INDEXING_TYPE_SIMILARITY)
            .setQuantizationType(AppSearchSchema.EmbeddingPropertyConfig.QUANTIZATION_TYPE_8_BIT)
            .build()
        )
        .build()

    val metadataSchema =
      AppSearchSchema.Builder(SCHEMA_TYPE_METADATA)
        .addProperty(
          AppSearchSchema.StringPropertyConfig.Builder(PROPERTY_MODEL_SIGNATURE)
            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
            .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_NONE)
            .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_NONE)
            .build()
        )
        .build()

    val request =
      SetSchemaRequest.Builder()
        .addSchemas(documentSchema, metadataSchema)
        .setForceOverride(true)
        .build()
    try {
      session?.setSchemaAsync(request)?.await()
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "SetSchema failed", e)
    }
  }

  /**
   * Retrieves the stored model signature from database metadata, or `null` if none exists.
   *
   * The active model signature is persisted directly within AppSearch via [SCHEMA_TYPE_METADATA] to
   * keep metadata co-located and atomic with the database lifecycle. This ensures that clearing or
   * resetting the database (e.g. via [clearAllDocuments]) atomically clears the associated metadata
   * in the same datastore without risk of desynchronization.
   */
  suspend fun getMetadataSignature(): String? {
    val request = GetByDocumentIdRequest.Builder(NAMESPACE).addIds(METADATA_ID).build()
    return try {
      val response = session?.getByDocumentIdAsync(request)?.await()
      val doc = response?.successes?.get(METADATA_ID)
      doc?.getPropertyString(PROPERTY_MODEL_SIGNATURE)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      null
    }
  }

  /** Persists the active model signature into the AppSearch metadata document. */
  suspend fun setMetadataSignature(signature: String) {
    val doc =
      GenericDocument.Builder<GenericDocument.Builder<*>>(
          NAMESPACE,
          METADATA_ID,
          SCHEMA_TYPE_METADATA,
        )
        .setPropertyString(PROPERTY_MODEL_SIGNATURE, signature)
        .build()
    val request = PutDocumentsRequest.Builder().addGenericDocuments(doc).build()
    try {
      session?.putAsync(request)?.await()
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "Failed to write metadata document", e)
    }
  }

  /**
   * Checks whether the stored database metadata signature differs from the currently active model.
   * If metadata is missing on a non-empty database, returns `true` to trigger re-indexing.
   */
  suspend fun isSignatureMismatch(): Boolean {
    val activeSig = getDefaultModelSignature() ?: return false
    val dbSig = getMetadataSignature()
    if (dbSig == null) {
      return !isDatabaseEmpty()
    }
    return dbSig != activeSig
  }

  /** Clears all indexed documents and metadata from the AppSearch session. */
  suspend fun clearAllDocuments() {
    val searchSpec =
      SearchSpec.Builder().addFilterSchemas(SCHEMA_TYPE_DOCUMENT, SCHEMA_TYPE_METADATA).build()
    try {
      session?.removeAsync("", searchSpec)?.await()
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "Failed to clear all documents", e)
    }
  }

  /**
   * Checks whether the AppSearch database currently contains any documents.
   *
   * @return `true` if the database is empty or uninitialized, `false` otherwise.
   */
  suspend fun isDatabaseEmpty(): Boolean {
    val searchSpec =
      SearchSpec.Builder().addFilterSchemas(SCHEMA_TYPE_DOCUMENT).setResultCountPerPage(1).build()
    val results = session?.search("", searchSpec) ?: return true
    return try {
      val page = results.nextPageAsync.await()
      page.isEmpty()
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      true
    } finally {
      results.close()
    }
  }

  /**
   * Saves a list of pre-constructed [GenericDocument] objects into AppSearch.
   *
   * @param docs List of [GenericDocument] instances to index.
   */
  suspend fun saveDocuments(docs: List<GenericDocument>) {
    val request = PutDocumentsRequest.Builder().addGenericDocuments(docs).build()
    try {
      val result = session?.putAsync(request)?.await()
      // AppSearch can succeed partially. We check result.failures to see if individual documents
      // failed.
      if (result != null && result.failures.isNotEmpty()) {
        Log.e(TAG, "Failed to index some documents: ${result.failures}")
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "Failed to index documents", e)
    }
  }

  /**
   * Generates a vector embedding for text and/or an image URI using the active ML Kit
   * [EmbeddingModel].
   *
   * Execution Sequence:
   * 1. Switches to [Dispatchers.IO] context.
   * 2. Adds [TextPart] if [text] is non-blank, and [ImagePart] if [imageUri] is provided.
   * 3. Wraps [ImagePart] in try/finally to ensure decoded bitmaps are always recycled via
   *    [ImagePart.close].
   * 4. Tracks inference latency with [SystemClock.elapsedRealtime].
   * 5. Calls [EmbeddingModel.generateEmbedding] and returns [QueryEmbeddingInfo].
   *
   * @param text Optional input text string to embed.
   * @param imageUri Optional image [Uri] to embed.
   * @return The [QueryEmbeddingInfo] with vector and metadata, or `null` if generation failed.
   */
  internal suspend fun generateEmbeddingRaw(
    text: String? = null,
    imageUri: Uri? = null,
  ): QueryEmbeddingInfo? {
    if (text.isNullOrBlank() && imageUri == null) return null
    val model = defaultModel ?: defaultModelProvider()?.also { defaultModel = it } ?: return null
    return withContext(Dispatchers.IO) {
      val parts = mutableListOf<Part>()
      val modalities = mutableListOf<String>()
      if (!text.isNullOrBlank()) {
        parts.add(TextPart(text))
        modalities.add("TEXT")
      }
      var imagePart: ImagePart? = null
      try {
        if (imageUri != null) {
          imagePart = ImagePart(imageUri)
          parts.add(imagePart)
          modalities.add("IMAGE")
        }
        if (parts.isEmpty()) return@withContext null
        val request = EmbeddingRequest(Content(parts), taskConfig = null)
        val startTime = SystemClock.elapsedRealtime()
        val result = model.generateEmbedding(request)
        val latencyMs = SystemClock.elapsedRealtime() - startTime
        val vector = result.embedding.vector
        QueryEmbeddingInfo(
          vector = vector,
          latencyMs = latencyMs,
          dimension = vector.size,
          modalities = modalities,
        )
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Failed to generate embedding raw", e)
        null
      } finally {
        imagePart?.close()
      }
    }
  }

  /**
   * Generates vector embeddings for a list of raw text strings and indexes them into AppSearch as
   * [GenericDocument] instances.
   *
   * Each document is indexed under the [NAMESPACE] with a unique ID format `"doc_$index"`,
   * attaching both the raw text in [PROPERTY_TEXT] and the computed [EmbeddingVector] in
   * [PROPERTY_EMBEDDING].
   *
   * @param texts List of text strings to generate embeddings for and store in AppSearch.
   */
  suspend fun indexDocuments(texts: List<String>) {
    val model = defaultModel ?: defaultModelProvider()?.also { defaultModel = it } ?: return
    val signature = model.getModelSignature()
    val docs = mutableListOf<GenericDocument>()
    for ((index, text) in texts.withIndex()) {
      val vectorResult = generateEmbeddingRaw(text = text)?.vector ?: continue
      val doc =
        GenericDocument.Builder<GenericDocument.Builder<*>>(
            NAMESPACE,
            "${DOCUMENT_ID_PREFIX}$index",
            SCHEMA_TYPE_DOCUMENT,
          )
          .setPropertyString(PROPERTY_TEXT, text)
          // Store the vector along with the model signature that generated it.
          // AppSearch uses the signature to ensure vector search is only run on compatible vectors.
          .setPropertyEmbedding(PROPERTY_EMBEDDING, EmbeddingVector(vectorResult, signature))
          .build()
      docs.add(doc)
    }
    saveDocuments(docs)
  }

  /**
   * Retrieves all documents stored in AppSearch up to a maximum limit without applying semantic
   * score ranking.
   *
   * @return A list of pairs containing document text and a default score of `1.0`.
   */
  suspend fun getAllDocuments(): List<Pair<String, Double>> {
    val searchSpec =
      SearchSpec.Builder().addFilterSchemas(SCHEMA_TYPE_DOCUMENT).setResultCountPerPage(100).build()
    val results = session?.search("", searchSpec) ?: return emptyList()
    return try {
      val page = results.nextPageAsync.await()
      page.map {
        val doc = it.genericDocument
        val text = doc.getPropertyString(PROPERTY_TEXT) ?: ""
        Pair(text, 1.0)
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "getAllDocuments failed", e)
      emptyList()
    } finally {
      results.close()
    }
  }

  /**
   * Performs a local semantic search in AppSearch using cosine similarity with text and/or image
   * query.
   *
   * @param query The text search query string.
   * @param imageUri Optional image [Uri] to search with.
   * @param threshold Cosine similarity score threshold (between 0.0 and 1.0) for matching
   *   documents.
   * @return [SearchResult] containing matched document text, similarity scores, and
   *   [QueryEmbeddingInfo].
   */
  suspend fun search(query: String, imageUri: Uri? = null, threshold: Double = 0.22): SearchResult {
    if (query.isBlank() && imageUri == null) {
      return SearchResult(getAllDocuments(), null)
    }
    val model =
      defaultModel
        ?: defaultModelProvider()?.also { defaultModel = it }
        ?: return SearchResult(emptyList(), null)
    val signature = model.getModelSignature()
    val queryInfo =
      generateEmbeddingRaw(text = query, imageUri = imageUri)
        ?: return SearchResult(emptyList(), null)

    val specBuilder = SearchSpec.Builder().addFilterSchemas(SCHEMA_TYPE_DOCUMENT)
    // Configure similarity metric to Cosine Distance
    specBuilder.setDefaultEmbeddingSearchMetricType(SearchSpec.EMBEDDING_SEARCH_METRIC_TYPE_COSINE)
    // Rank results based on the score returned by the semanticSearch function
    specBuilder.setRankingStrategy("sum(this.matchedSemanticScores(getEmbeddingParameter(0)))")
    specBuilder.setListFilterQueryLanguageEnabled(true)
    specBuilder.setResultCountPerPage(5)
    // Register our query vector as parameter 0
    specBuilder.addEmbeddingParameters(EmbeddingVector(queryInfo.vector, signature))

    val queryExpression = "semanticSearch(getEmbeddingParameter(0), $threshold)"
    val searchSpec = specBuilder.build()
    val results =
      session?.search(queryExpression, searchSpec) ?: return SearchResult(emptyList(), queryInfo)

    return try {
      val page = results.nextPageAsync.await()
      val matched = page.map {
        val doc = it.genericDocument
        val text = doc.getPropertyString(PROPERTY_TEXT) ?: ""
        // rankingSignal contains the computed cosine similarity score (between 0.0 and 1.0)
        Pair(text, it.rankingSignal)
      }
      SearchResult(matched, queryInfo)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "Search failed", e)
      SearchResult(emptyList(), queryInfo)
    } finally {
      results.close()
    }
  }

  /** Returns whether the active embedding model supports image modality input. */
  suspend fun isImageModalitySupported(): Boolean {
    val model = defaultModel ?: defaultModelProvider()?.also { defaultModel = it } ?: return false
    return try {
      model.getMetadata()?.supportedModalities?.contains(Modality.IMAGE) == true
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "Failed to check if image modality is supported", e)
      false
    }
  }

  /**
   * Queries on-device model discovery to return all available embedding model signatures.
   *
   * Automatically closes model instances opened during discovery that are not currently active.
   */
  suspend fun getAvailableModelSignatures(): List<String> {
    return withContext(Dispatchers.IO) {
      val models =
        try {
          allModelsProvider()
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          Log.e(TAG, "Failed to fetch available models", e)
          emptyList()
        }
      val signatures =
        models
          .mapNotNull { model ->
            runCatching { model.getModelSignature() }
              .onFailure { if (it is CancellationException) throw it }
              .getOrNull()
          }
          .filter { it.isNotBlank() }
          .distinct()

      signatures.ifEmpty { listOfNotNull(defaultModelSignature) }
    }
  }

  /**
   * Selects and switches the active model to the given [signature], warming it up and caching its
   * metadata.
   */
  suspend fun selectModel(signature: String) {
    if (defaultModelSignature == signature && defaultModel != null) return
    withContext(Dispatchers.IO) {
      val newModel =
        try {
          modelProvider(signature) ?: defaultModelProvider()
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          Log.e(TAG, "Failed to instantiate model for signature: $signature", e)
          null
        } ?: return@withContext

      if (defaultModel != null && defaultModel != newModel) {
        runCatching { defaultModel?.close() }
          .onFailure { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to close previous embedding model", e)
          }
      }
      defaultModel = newModel
      try {
        setupModel(newModel)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Failed to setup selected model: $signature", e)
      }
    }
  }

  companion object {
    /** The schema type name used to register documents in AppSearch ("schema_type_document"). */
    const val SCHEMA_TYPE_DOCUMENT = "schema_type_document"

    /** The schema type name used for database metadata ("DbMetadata"). */
    const val SCHEMA_TYPE_METADATA = "DbMetadata"

    /** The document ID for the database metadata record ("db_metadata_id"). */
    const val METADATA_ID = "db_metadata_id"

    /** The AppSearch namespace used to isolate documents of this application ("embedding_demo"). */
    const val NAMESPACE = "embedding_demo"

    /** The property name for storing raw text content ("text"). */
    const val PROPERTY_TEXT = "text"

    /** The property name for storing vector embeddings ("embedding"). */
    const val PROPERTY_EMBEDDING = "embedding"

    /** The property name for storing model signature in metadata ("modelSignature"). */
    const val PROPERTY_MODEL_SIGNATURE = "modelSignature"

    /** The name of the AppSearch local database ("embedding_demo_db"). */
    const val DATABASE_NAME = "embedding_demo_db"

    /** The prefix used to generate unique document IDs (e.g., "doc_0"). */
    private const val DOCUMENT_ID_PREFIX = "doc_"
    private const val TAG = "SemanticSearchHelper"

    /**
     * List of preferred model signature substrings to select on device. When searching available
     * embedding models, models matching these signatures in priority order are selected before
     * falling back to any available default model.
     */
    private val PREFERRED_MODEL_SIGNATURES = listOf("embedding_gemma_v1")

    /**
     * Resolves the default embedding model available on device. Prefers models matching
     * [PREFERRED_MODEL_SIGNATURES] in priority order and falls back to the first available model if
     * no preferred model signature matches.
     */
    private suspend fun fetchDefaultEmbeddingModel(): EmbeddingModel? {
      return try {
        val models =
          EmbeddingGeneration.getEmbeddingModels(
            EmbeddingOptions(modelSignature = null, executor = null)
          )
        selectPreferredModel(models)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Failed to fetch default embedding model", e)
        null
      }
    }

    private suspend fun fetchAllEmbeddingModels(): List<EmbeddingModel> {
      return try {
        EmbeddingGeneration.getEmbeddingModels(EmbeddingOptions())
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Failed to fetch all embedding models", e)
        emptyList()
      }
    }

    private suspend fun fetchEmbeddingModel(signature: String): EmbeddingModel? {
      return try {
        EmbeddingGeneration.getEmbeddingModels(EmbeddingOptions(modelSignature = signature))
          .firstOrNull()
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Failed to fetch embedding model for signature: $signature", e)
        null
      }
    }

    /**
     * Selects the preferred embedding model from [models] matching [PREFERRED_MODEL_SIGNATURES] in
     * priority order, closing all other unselected models cleanly.
     */
    private suspend fun selectPreferredModel(models: List<EmbeddingModel>): EmbeddingModel? {
      if (models.isEmpty()) return null
      val modelSignatures = models.map { model ->
        val sig =
          runCatching { model.getModelSignature() }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
        model to sig
      }

      var chosenModel: EmbeddingModel? = null
      for (preferredSig in PREFERRED_MODEL_SIGNATURES) {
        val match = modelSignatures.firstOrNull { (_, sig) -> sig != null && preferredSig in sig }
        if (match != null) {
          chosenModel = match.first
          break
        }
      }

      return chosenModel ?: models.firstOrNull()
    }
  }
}

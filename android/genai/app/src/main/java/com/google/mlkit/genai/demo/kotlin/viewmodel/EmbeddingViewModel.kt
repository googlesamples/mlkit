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
package com.google.mlkit.genai.demo.kotlin.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.mlkit.genai.demo.kotlin.repository.EmbeddingRepository
import com.google.mlkit.genai.demo.kotlin.repository.QueryEmbeddingInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EmbeddingViewModel(
  context: Context,
  private val repository: EmbeddingRepository,
  private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val appContext = context.applicationContext

  val selectedSignature: StateFlow<String?> =
    savedStateHandle.getStateFlow(KEY_SELECTED_SIGNATURE, null)

  val availableModelSignatures: StateFlow<List<String>> =
    savedStateHandle.getStateFlow(KEY_AVAILABLE_SIGNATURES, emptyList())

  val isLoadingModels: StateFlow<Boolean> =
    savedStateHandle.getStateFlow(KEY_IS_LOADING_MODELS, false)

  val showModelSelectorDialog: StateFlow<Boolean> =
    savedStateHandle.getStateFlow(KEY_SHOW_MODEL_SELECTOR, false)

  val isSwitchingModel: StateFlow<Boolean> =
    savedStateHandle.getStateFlow(KEY_IS_SWITCHING_MODEL, false)

  val isReindexing: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_IS_REINDEXING, false)

  private val _selectedImageUri = MutableStateFlow<Uri?>(null)
  val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

  private val _queryEmbeddingInfo = MutableStateFlow<QueryEmbeddingInfo?>(null)
  val queryEmbeddingInfo: StateFlow<QueryEmbeddingInfo?> = _queryEmbeddingInfo.asStateFlow()

  private val _isImageModalitySupported = MutableStateFlow(false)
  val isImageModalitySupported: StateFlow<Boolean> = _isImageModalitySupported.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _searchResults = MutableStateFlow<List<Pair<String, Double>>>(emptyList())
  val searchResults: StateFlow<List<Pair<String, Double>>> = _searchResults.asStateFlow()

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

  val isAppSearchInitializing: StateFlow<Boolean> =
    repository.isDbInitialized
      .map { isDbReady -> !isDbReady }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = true,
      )

  val isModelInitializing: StateFlow<Boolean> =
    repository.isModelInitialized
      .onEach { isModelReady ->
        if (isModelReady) {
          val signature = repository.getSelectedModelSignature()
          val currentSavedSig: String? = savedStateHandle[KEY_SELECTED_SIGNATURE]
          if (currentSavedSig == null) {
            savedStateHandle[KEY_SELECTED_SIGNATURE] = signature
          }
          updateImageModalitySupport()
        }
      }
      .map { isModelReady -> !isModelReady }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = true,
      )

  private var searchJob: Job? = null

  init {
    viewModelScope.launch {
      runCatching { repository.initAppSearch() }
        .onFailure { Log.e(TAG, "Failed to initialize semantic search", it) }
      updateImageModalitySupport()
    }
  }

  fun setSelectedImageUri(uri: Uri?) {
    _selectedImageUri.value = uri
  }

  fun clearSelectedImage() {
    _selectedImageUri.value = null
  }

  @VisibleForTesting
  internal suspend fun updateImageModalitySupport() {
    try {
      val isSupported = repository.isImageModalitySupported()
      _isImageModalitySupported.value = isSupported
      if (!isSupported && _selectedImageUri.value != null) {
        _selectedImageUri.value = null
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "Failed to check image modality support", e)
      _isImageModalitySupported.value = false
      _selectedImageUri.value = null
    }
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun performSearch() {
    val query = searchQuery.value
    val imageUri = if (isImageModalitySupported.value) selectedImageUri.value else null
    searchJob?.cancel()
    searchJob = viewModelScope.launch {
      _isSearching.value = true
      try {
        val result = repository.performSemanticSearch(query, imageUri)
        _searchResults.value = result.documents
        _queryEmbeddingInfo.value = result.queryEmbeddingInfo
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Search failed", e)
      } finally {
        if (coroutineContext[Job]?.isCancelled == false) {
          _isSearching.value = false
        }
      }
    }
  }

  /** Controls the visibility of the model selector dialog and loads available models if needed. */
  fun setShowModelSelectorDialog(show: Boolean) {
    savedStateHandle[KEY_SHOW_MODEL_SELECTOR] = show
    if (show && availableModelSignatures.value.isEmpty() && !isLoadingModels.value) {
      loadAvailableModels()
    }
  }

  /** Loads all available on-device embedding model signatures asynchronously. */
  fun loadAvailableModels() {
    if (isLoadingModels.value) return
    savedStateHandle[KEY_IS_LOADING_MODELS] = true
    viewModelScope.launch {
      try {
        isAppSearchInitializing.first { !it }
        val signatures = repository.getAvailableModelSignatures()
        savedStateHandle[KEY_AVAILABLE_SIGNATURES] = signatures
        if (signatures.isNotEmpty()) {
          val currentSig = selectedSignature.value
          if (currentSig == null || currentSig !in signatures) {
            selectModel(signatures.first())
          }
        }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Failed to load available models", e)
      } finally {
        savedStateHandle[KEY_IS_LOADING_MODELS] = false
      }
    }
  }

  /**
   * Switches the active model to the given [signature], updates model metadata in saved state, and
   * refreshes the search query.
   */
  fun selectModel(signature: String) {
    if (selectedSignature.value == signature && !isSwitchingModel.value) {
      return
    }
    // Prevent race conditions and concurrent model selection requests during an asynchronous
    // switch.
    if (isSwitchingModel.value) return
    savedStateHandle[KEY_IS_SWITCHING_MODEL] = true
    viewModelScope.launch {
      try {
        repository.selectModel(signature)
        savedStateHandle[KEY_SELECTED_SIGNATURE] = signature
        updateImageModalitySupport()
        // Refresh active search query results against the newly selected model and updated vector
        // index.
        if (searchQuery.value.isNotBlank() || selectedImageUri.value != null) {
          performSearch()
        }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Failed to switch model to $signature", e)
      } finally {
        savedStateHandle[KEY_IS_SWITCHING_MODEL] = false
      }
    }
  }

  /**
   * Clears all AppSearch documents, reindexes sample data with the active model, and refreshes
   * search results.
   */
  fun manualReindex() {
    if (isReindexing.value) return
    savedStateHandle[KEY_IS_REINDEXING] = true
    viewModelScope.launch {
      try {
        repository.manualReindex()
        performSearch()
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Manual reindexing failed", e)
      } finally {
        savedStateHandle[KEY_IS_REINDEXING] = false
      }
    }
  }

  override fun onCleared() {
    repository.close()
  }

  companion object {
    private const val TAG = "EmbeddingViewModel"
    private const val KEY_AVAILABLE_SIGNATURES = "availableModelSignatures"
    private const val KEY_SELECTED_SIGNATURE = "selectedSignature"
    private const val KEY_SHOW_MODEL_SELECTOR = "showModelSelectorDialog"
    private const val KEY_IS_LOADING_MODELS = "isLoadingModels"
    private const val KEY_IS_SWITCHING_MODEL = "isSwitchingModel"
    private const val KEY_IS_REINDEXING = "isReindexing"
  }
}

class EmbeddingViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
    val savedStateHandle = extras.createSavedStateHandle()
    val repository = EmbeddingRepository(context.applicationContext)
    return EmbeddingViewModel(context.applicationContext, repository, savedStateHandle) as T
  }
}

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
package com.google.mlkit.genai.demo.kotlin

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.genai.demo.R
import com.google.mlkit.genai.demo.kotlin.repository.QueryEmbeddingInfo
import com.google.mlkit.genai.demo.kotlin.viewmodel.EmbeddingViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EmbeddingScreen(
  viewModel: EmbeddingViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
  val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
  val isAppSearchInitializing by viewModel.isAppSearchInitializing.collectAsStateWithLifecycle()
  val isModelInitializing by viewModel.isModelInitializing.collectAsStateWithLifecycle()
  val selectedSignature by viewModel.selectedSignature.collectAsStateWithLifecycle()
  val showModelSelectorDialog by viewModel.showModelSelectorDialog.collectAsStateWithLifecycle()
  val availableModelSignatures by viewModel.availableModelSignatures.collectAsStateWithLifecycle()
  val isLoadingModels by viewModel.isLoadingModels.collectAsStateWithLifecycle()
  val isSwitchingModel by viewModel.isSwitchingModel.collectAsStateWithLifecycle()
  val selectedImageUri by viewModel.selectedImageUri.collectAsStateWithLifecycle()
  val queryEmbeddingInfo by viewModel.queryEmbeddingInfo.collectAsStateWithLifecycle()
  val isImageModalitySupported by viewModel.isImageModalitySupported.collectAsStateWithLifecycle()
  val isModelLoading = isModelInitializing || isSwitchingModel

  LaunchedEffect(isAppSearchInitializing) {
    if (!isAppSearchInitializing) {
      viewModel.performSearch()
    }
  }

  Scaffold(topBar = { EmbeddingTopAppBar(onBackClick = onBack) }, modifier = modifier) {
    paddingValues ->
    Column(modifier = Modifier.padding(paddingValues)) {
      ModelSelectorPanel(
        modelSignature = selectedSignature,
        isInitializing = isModelLoading,
        onClick = { viewModel.setShowModelSelectorDialog(true) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      )

      SemanticSearchPanel(
        searchQuery = searchQuery,
        onSearchQueryChanged = { viewModel.setSearchQuery(it) },
        onSearchClick = { viewModel.performSearch() },
        searchResults = searchResults,
        isSearching = isSearching,
        isInitializing = isAppSearchInitializing || isModelLoading,
        selectedImageUri = selectedImageUri,
        onImageSelected = { if (it != null) viewModel.setSelectedImageUri(it) },
        onClearImage = { viewModel.clearSelectedImage() },
        isImageSupported = isImageModalitySupported,
        queryEmbeddingInfo = queryEmbeddingInfo,
        modifier = Modifier.weight(1f),
      )
    }
  }

  if (showModelSelectorDialog) {
    ModelSelectorDialog(
      availableModels = availableModelSignatures,
      selectedSignature = selectedSignature,
      isLoading = isLoadingModels,
      onModelSelected = {
        viewModel.selectModel(it)
        viewModel.setShowModelSelectorDialog(false)
      },
      onDismiss = { viewModel.setShowModelSelectorDialog(false) },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmbeddingTopAppBar(onBackClick: () -> Unit) {
  TopAppBar(
    title = { Text(stringResource(R.string.embedding_entry_title)) },
    navigationIcon = {
      IconButton(onClick = onBackClick) {
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
    },
  )
}

@Composable
fun SemanticSearchPanel(
  searchQuery: String,
  onSearchQueryChanged: (String) -> Unit,
  onSearchClick: () -> Unit,
  searchResults: List<Pair<String, Double>>,
  isSearching: Boolean,
  isInitializing: Boolean,
  modifier: Modifier = Modifier,
  selectedImageUri: Uri? = null,
  onImageSelected: (Uri?) -> Unit = {},
  onClearImage: () -> Unit = {},
  isImageSupported: Boolean = false,
  queryEmbeddingInfo: QueryEmbeddingInfo? = null,
) {
  val focusManager = LocalFocusManager.current
  val coroutineScope = rememberCoroutineScope()
  val listState = rememberLazyListState()
  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      onImageSelected(uri)
    }

  LaunchedEffect(searchResults) { listState.scrollToItem(0) }

  Column(
    modifier = modifier.fillMaxSize().padding(16.dp).imePadding(),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChanged,
        label = { Text(stringResource(R.string.label_search_query)) },
        placeholder = { Text(stringResource(R.string.hint_search_query)) },
        leadingIcon = {
          IconButton(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            enabled = !isInitializing && isImageSupported,
          ) {
            Icon(
              painter = painterResource(R.drawable.ic_add_photo),
              contentDescription = stringResource(R.string.button_select_image),
              tint =
                if (!isInitializing && isImageSupported) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
          }
        },
        modifier = Modifier.weight(1f),
        singleLine = true,
        enabled = !isInitializing,
        keyboardOptions =
          KeyboardOptions(
            imeAction = ImeAction.Search,
            capitalization = KeyboardCapitalization.Sentences,
          ),
        keyboardActions =
          KeyboardActions(
            onSearch = {
              focusManager.clearFocus()
              onSearchClick()
              coroutineScope.launch { listState.scrollToItem(0) }
            }
          ),
      )
      Button(
        onClick = {
          focusManager.clearFocus()
          onSearchClick()
          coroutineScope.launch { listState.scrollToItem(0) }
        },
        enabled = !isInitializing,
      ) {
        Text("Search")
      }
    }

    if (selectedImageUri != null) {
      SelectedImageChip(uri = selectedImageUri, onClear = onClearImage)
    }

    if (isSearching || isInitializing) {
      Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    }

    if (queryEmbeddingInfo != null) {
      QueryEmbeddingInfoCard(info = queryEmbeddingInfo)
    }

    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (searchResults.isEmpty() && !isSearching && !isInitializing) {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(R.string.no_results_found),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        items(searchResults) { (text, score) -> SearchResultCard(text = text, score = score) }
      }
    }
  }
}

@Composable
fun SelectedImageChip(uri: Uri, onClear: () -> Unit, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      SelectedImagePreview(uri = uri, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)))
      Text(
        text = stringResource(R.string.attached_image),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = stringResource(R.string.content_desc_clear_image),
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

// Safe to open stream here as the Uri originates directly from the system Photo Picker
// (PickVisualMedia) and is only decoded to display a local thumbnail preview.
@SuppressLint("ContentResolverUri")
@Composable
private fun SelectedImagePreview(uri: Uri, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val bitmap by
    produceState<Bitmap?>(initialValue = null, uri) {
      value =
        withContext(Dispatchers.IO) {
          try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
              BitmapFactory.decodeStream(inputStream)
            }
          } catch (e: Exception) {
            null
          }
        }
    }
  val currentBitmap = bitmap
  if (currentBitmap != null) {
    Image(
      bitmap = currentBitmap.asImageBitmap(),
      contentDescription = null,
      modifier = modifier,
      contentScale = ContentScale.Crop,
    )
  } else {
    Box(modifier = modifier.background(Color.Gray))
  }
}

@Composable
fun QueryEmbeddingInfoCard(info: QueryEmbeddingInfo, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = stringResource(R.string.title_query_embedding_info),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          text = "Dimension: ${info.dimension}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "Latency: ${info.latencyMs} ms",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = "Modality: ${info.modalities.joinToString(", ")}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(2.dp))
      SelectionContainer {
        Text(
          text = EmbeddingUiUtils.formatVector(info.vector),
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

@Composable
fun SearchResultCard(text: String, score: Double, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      SelectionContainer { Text(text = text, style = MaterialTheme.typography.bodyMedium) }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = String.format(Locale.US, "Similarity: %.3f", score),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
fun ModelSelectorPanel(
  modelSignature: String?,
  isInitializing: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit = {},
) {
  val statusColor =
    when {
      isInitializing -> Color(0xFFFFA000) // Amber / Yellow for Initializing or Switching
      modelSignature != null -> Color(0xFF4CAF50) // Green for Ready / Available
      else -> Color(0xFFF44336) // Red for Error / Unavailable
    }

  Card(
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    modifier = modifier.fillMaxWidth().clickable(enabled = !isInitializing, onClick = onClick),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(modifier = Modifier.size(10.dp).background(color = statusColor, shape = CircleShape))
      Text(
        text =
          when {
            isInitializing && modelSignature != null -> "Model Signature: Switching..."
            isInitializing -> "Model Signature: Initializing..."
            modelSignature != null -> stringResource(R.string.label_model_signature, modelSignature)
            else -> "Model Signature: Unavailable"
          },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      Icon(
        imageVector = Icons.Default.ArrowDropDown,
        contentDescription = stringResource(R.string.title_dialog_select_model),
        tint =
          if (isInitializing) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
      )
    }
  }
}

@Composable
fun ModelSelectorDialog(
  availableModels: List<String>,
  selectedSignature: String?,
  isLoading: Boolean,
  onModelSelected: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.title_dialog_select_model),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      when {
        isLoading -> {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator()
            Text("Loading models...")
          }
        }
        availableModels.isEmpty() -> {
          Text(
            text = stringResource(R.string.no_models_found),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        else -> {
          LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(availableModels, key = { it }) { signature ->
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable { onModelSelected(signature) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                RadioButton(
                  selected = (signature == selectedSignature),
                  onClick = { onModelSelected(signature) },
                )
                Text(text = signature, style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }
      }
    },
    confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) } },
  )
}

@Composable
fun EmbeddingTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
  MaterialTheme(colorScheme = colorScheme, content = content)
}

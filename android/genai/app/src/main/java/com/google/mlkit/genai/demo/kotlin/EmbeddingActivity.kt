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

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.google.mlkit.genai.demo.kotlin.viewmodel.EmbeddingViewModel
import com.google.mlkit.genai.demo.kotlin.viewmodel.EmbeddingViewModelFactory

/**
 * Main interactive playground demonstrating standard model metrics and task configurations for
 * On-Device Embeddings, migrated fully to modern Jetpack Compose Material 3 and MVVM architecture.
 */
class EmbeddingActivity : ComponentActivity() {

  // Retrieve our modern state-retained ViewModel using standard Compose factory delegations
  private val viewModel: EmbeddingViewModel by viewModels {
    EmbeddingViewModelFactory(applicationContext)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Programmatically apply the adjustResize flag to safe edges resizing with soft keyboards
    @Suppress("DEPRECATION")
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

    setContent {
      EmbeddingTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          EmbeddingScreen(viewModel = viewModel, onBack = { finish() })
        }
      }
    }
  }
}

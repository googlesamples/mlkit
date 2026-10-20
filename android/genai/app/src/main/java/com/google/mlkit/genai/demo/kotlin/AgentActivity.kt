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
import com.google.mlkit.genai.demo.kotlin.viewmodel.AgentViewModel
import com.google.mlkit.genai.demo.kotlin.viewmodel.AgentViewModelFactory

/**
 * Interactive playground demonstrating the ML Kit GenAI Agent API (`Agent`, `AgentRunner`,
 * `AgentSession`, `AgentEvent`, `AppFunctionAgentTools`, and custom `AgentTool` decorators) using
 * Jetpack Compose Material 3 and MVVM architecture.
 */
class AgentActivity : ComponentActivity() {

  private val viewModel: AgentViewModel by viewModels { AgentViewModelFactory(applicationContext) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    @Suppress("DEPRECATION")
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

    setContent {
      EmbeddingTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          AgentScreen(viewModel = viewModel, onBack = { finish() })
        }
      }
    }
  }
}

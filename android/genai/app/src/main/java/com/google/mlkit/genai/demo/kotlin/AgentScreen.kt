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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.genai.demo.R
import com.google.mlkit.genai.demo.kotlin.repository.AgentConfig
import com.google.mlkit.genai.demo.kotlin.repository.AgentToolMode
import com.google.mlkit.genai.demo.kotlin.viewmodel.AgentTimelineItem
import com.google.mlkit.genai.demo.kotlin.viewmodel.AgentViewModel
import com.google.mlkit.genai.demo.kotlin.viewmodel.PendingToolConfirmation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main Jetpack Compose screen for the ML Kit GenAI Agent API demo.
 *
 * Showcases:
 * - Multi-turn stateful sessions (`AgentRunner.getOrCreateSession`, `deleteSession`,
 *   `clearAllSessions`) and ephemeral single-turn execution (`AgentRunner.runSingleTurnAsync`).
 * - Real-time `Flow<AgentEvent>` timeline rendering `AgentEvent.ToolCall`,
 *   `AgentEvent.ToolResponse`, and streaming `AgentEvent.TextMessage` chunks.
 * - Android `@AppFunction` Fast Path (`enableAppFunctions` / `addAppFunctionIds`) and Advanced
 *   Human-in-the-Loop (`AppFunctionAgentTools.discover` + `HitlConfirmationTool` decorator).
 * - Multimodal `Content` input combining text and images.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(viewModel: AgentViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val sessions by viewModel.sessions.collectAsStateWithLifecycle()
  val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
  val isSingleTurnMode by viewModel.isSingleTurnMode.collectAsStateWithLifecycle()
  val sessionTimelines by viewModel.sessionTimelines.collectAsStateWithLifecycle()
  val singleTurnTimeline by viewModel.singleTurnTimeline.collectAsStateWithLifecycle()
  val promptInput by viewModel.promptInput.collectAsStateWithLifecycle()
  val attachedImageUri by viewModel.attachedImageUri.collectAsStateWithLifecycle()
  val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
  val agentConfig by viewModel.agentConfig.collectAsStateWithLifecycle()
  val baseModelName by viewModel.baseModelName.collectAsStateWithLifecycle()
  val modelStatusMessage by viewModel.modelStatusMessage.collectAsStateWithLifecycle()
  val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
  val showConfigDialog by viewModel.showConfigDialog.collectAsStateWithLifecycle()
  val showToolsSchemaDialog by viewModel.showToolsSchemaDialog.collectAsStateWithLifecycle()
  val toolsSchemaJson by viewModel.toolsSchemaJson.collectAsStateWithLifecycle()
  val showSandboxDialog by viewModel.showSandboxDialog.collectAsStateWithLifecycle()
  val sandboxSnapshot by viewModel.sandboxSnapshot.collectAsStateWithLifecycle()

  val activeTimeline =
    if (isSingleTurnMode) {
      singleTurnTimeline
    } else {
      sessionTimelines[currentSessionId] ?: emptyList()
    }

  val listState = rememberLazyListState()
  val focusManager = LocalFocusManager.current

  val imagePickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
      onResult = { uri -> viewModel.updateAttachedImage(uri) },
    )

  LaunchedEffect(activeTimeline.size, activeTimeline.lastOrNull()) {
    if (activeTimeline.isNotEmpty()) {
      listState.animateScrollToItem(activeTimeline.lastIndex)
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize().imePadding(),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(text = stringResource(R.string.agent_entry_title))
            val subtitle =
              if (baseModelName.isNotEmpty()) {
                "${agentConfig.agentName} • $baseModelName"
              } else {
                "${agentConfig.agentName} • ${agentConfig.toolMode.displayName}"
              }
            Text(
              text = subtitle,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = { viewModel.openToolsSchemaDialog() }) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = "View Discovered @AppFunction Schemas",
            )
          }
          IconButton(onClick = { viewModel.openSandboxDialog() }) {
            Icon(
              imageVector = Icons.Default.Build,
              contentDescription = "View AppFunction Sandbox State",
            )
          }
          IconButton(onClick = { viewModel.openConfigDialog() }) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Agent & Runner Settings",
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 12.dp)) {
      modelStatusMessage?.let { statusMsg ->
        Card(
          modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
          colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
          Text(
            text = statusMsg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(10.dp),
          )
        }
      }

      AgentSessionAndModeHeader(
        sessions = sessions,
        currentSessionId = currentSessionId,
        isSingleTurnMode = isSingleTurnMode,
        toolMode = agentConfig.toolMode,
        onToggleSingleTurn = { viewModel.setSingleTurnMode(it) },
        onSelectSession = { viewModel.selectSession(it) },
        onNewSession = { viewModel.createNewSession() },
        onDeleteCurrentSession = { viewModel.deleteCurrentSession() },
        onClearAllSessions = { viewModel.clearAllSessions() },
        onOpenConfig = { viewModel.openConfigDialog() },
      )

      SamplePromptRow(
        onSelectSample = { samplePrompt -> viewModel.applySamplePrompt(samplePrompt) }
      )

      HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

      if (activeTimeline.isEmpty()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text =
                if (isSingleTurnMode) {
                  "Single-Turn Mode (AgentRunner.runSingleTurnAsync)"
                } else {
                  "Session '$currentSessionId' (AgentSession.runAsync)"
                },
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text =
                "Select a sample prompt chip above or type a message below to watch " +
                  "AgentEvent.FunctionCall, AgentEvent.FunctionResponse, and streaming " +
                  "AgentEvent.TextMessage events.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 24.dp),
            )
          }
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(items = activeTimeline, key = { it.id }) { item -> AgentTimelineCard(item = item) }
        }
      }

      AgentPromptInputBar(
        promptInput = promptInput,
        attachedImageUri = attachedImageUri,
        isGenerating = isGenerating,
        onPromptChange = { viewModel.updatePromptInput(it) },
        onPickImage = {
          imagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        onClearImage = { viewModel.updateAttachedImage(null) },
        onSend = {
          focusManager.clearFocus()
          viewModel.sendPrompt()
        },
        onCancel = { viewModel.cancelCurrentTurn() },
      )
    }
  }

  pendingConfirmation?.let { confirmation ->
    HitlConfirmationDialog(
      confirmation = confirmation,
      onApprove = { viewModel.resolveToolConfirmation(true) },
      onReject = { viewModel.resolveToolConfirmation(false) },
    )
  }

  if (showConfigDialog) {
    AgentConfigDialog(
      initialConfig = agentConfig,
      onDismiss = { viewModel.dismissConfigDialog() },
      onSave = { updated -> viewModel.updateAgentConfig(updated) },
    )
  }

  if (showToolsSchemaDialog) {
    AlertDialog(
      onDismissRequest = { viewModel.dismissToolsSchemaDialog() },
      title = { Text(stringResource(R.string.dialog_title_available_functions)) },
      text = {
        SelectionContainer {
          Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
              text = toolsSchemaJson,
              fontFamily = FontFamily.Monospace,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { viewModel.dismissToolsSchemaDialog() }) { Text("Close") }
      },
    )
  }

  if (showSandboxDialog) {
    AlertDialog(
      onDismissRequest = { viewModel.dismissSandboxDialog() },
      title = { Text("In-App @AppFunction Sandbox State") },
      text = {
        SelectionContainer {
          Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
              text = sandboxSnapshot,
              fontFamily = FontFamily.Monospace,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.resetSandboxData() }) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Reset Seed Data")
        }
      },
      confirmButton = {
        TextButton(onClick = { viewModel.dismissSandboxDialog() }) { Text("Close") }
      },
    )
  }
}

@Composable
private fun AgentSessionAndModeHeader(
  sessions: List<String>,
  currentSessionId: String,
  isSingleTurnMode: Boolean,
  toolMode: AgentToolMode,
  onToggleSingleTurn: (Boolean) -> Unit,
  onSelectSession: (String) -> Unit,
  onNewSession: () -> Unit,
  onDeleteCurrentSession: () -> Unit,
  onClearAllSessions: () -> Unit,
  onOpenConfig: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      AssistChip(
        onClick = onOpenConfig,
        label = { Text(text = toolMode.displayName, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
      )

      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = if (isSingleTurnMode) "Single-Turn" else "Multi-Turn",
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(end = 6.dp),
        )
        Switch(checked = isSingleTurnMode, onCheckedChange = onToggleSingleTurn)
      }
    }

    if (!isSingleTurnMode) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        for (sessionId in sessions) {
          val selected = sessionId == currentSessionId
          FilterChip(
            selected = selected,
            onClick = { onSelectSession(sessionId) },
            label = { Text(sessionId, style = MaterialTheme.typography.labelSmall) },
            leadingIcon =
              if (selected) {
                {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                  )
                }
              } else {
                null
              },
          )
        }

        AssistChip(
          onClick = onNewSession,
          label = { Text("New Session", style = MaterialTheme.typography.labelSmall) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
            )
          },
        )

        AssistChip(
          onClick = onDeleteCurrentSession,
          label = { Text("Delete Session", style = MaterialTheme.typography.labelSmall) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
            )
          },
        )

        AssistChip(
          onClick = onClearAllSessions,
          label = { Text("Clear All", style = MaterialTheme.typography.labelSmall) },
        )
      }
    }
  }
}

@Composable
private fun SamplePromptRow(onSelectSample: (String) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    for (sample in AgentViewModel.SAMPLE_PROMPTS) {
      AssistChip(
        onClick = { onSelectSample(sample.prompt) },
        label = { Text(sample.label, style = MaterialTheme.typography.labelSmall) },
      )
    }
  }
}

@Composable
private fun AgentTimelineCard(item: AgentTimelineItem) {
  when (item) {
    is AgentTimelineItem.UserPrompt -> {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Card(
          modifier = Modifier.fillMaxWidth(0.86f),
          shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
          colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = if (item.isSingleTurn) "You (Single-Turn)" else "You",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            item.imageUri?.let { uri ->
              Spacer(modifier = Modifier.height(6.dp))
              AgentUriThumbnail(uri = uri, modifier = Modifier.size(96.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            SelectionContainer {
              Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }
    }
    is AgentTimelineItem.ToolCall -> {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
      ) {
        Column(modifier = Modifier.padding(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Build,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
              tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "AgentEvent.FunctionCall → ${item.toolName}",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          SelectionContainer {
            Text(
              text = "args: ${item.argsFormatted}",
              fontFamily = FontFamily.Monospace,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
          }
        }
      }
    }
    is AgentTimelineItem.ToolResponse -> {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
      ) {
        Column(modifier = Modifier.padding(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "AgentEvent.FunctionResponse ← ${item.toolName}",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          SelectionContainer {
            Text(
              text = "result: ${item.resultFormatted}",
              fontFamily = FontFamily.Monospace,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }
    }
    is AgentTimelineItem.AgentMessage -> {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Card(
          modifier = Modifier.fillMaxWidth(0.92f),
          shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
          colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text =
                  if (item.isStreaming) {
                    "Agent (Streaming TextMessage...)"
                  } else {
                    "Agent (TextMessage)"
                  },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
              if (item.isStreaming) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
            SelectionContainer {
              Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (item.firstTokenLatencyMs != null || item.totalLatencyMs != null) {
              Spacer(modifier = Modifier.height(6.dp))
              val latencyLabel =
                if (item.firstTokenLatencyMs != null && item.totalLatencyMs != null) {
                  "TTFT: ${item.firstTokenLatencyMs} ms • Total: ${item.totalLatencyMs} ms"
                } else if (item.firstTokenLatencyMs != null) {
                  "TTFT: ${item.firstTokenLatencyMs} ms (streaming...)"
                } else {
                  "Total: ${item.totalLatencyMs} ms"
                }
              Text(
                text = latencyLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
              )
            }
          }
        }
      }
    }
    is AgentTimelineItem.SystemNotice -> {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
          CardDefaults.cardColors(
            containerColor =
              if (item.isError) {
                MaterialTheme.colorScheme.errorContainer
              } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
              }
          ),
      ) {
        Text(
          text = item.message,
          style = MaterialTheme.typography.bodySmall,
          color =
            if (item.isError) {
              MaterialTheme.colorScheme.onErrorContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
          modifier = Modifier.padding(10.dp),
        )
      }
    }
  }
}

@Composable
private fun AgentPromptInputBar(
  promptInput: String,
  attachedImageUri: Uri?,
  isGenerating: Boolean,
  onPromptChange: (String) -> Unit,
  onPickImage: () -> Unit,
  onClearImage: () -> Unit,
  onSend: () -> Unit,
  onCancel: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
    attachedImageUri?.let { uri ->
      Box(modifier = Modifier.padding(bottom = 6.dp).size(72.dp)) {
        AgentUriThumbnail(uri = uri, modifier = Modifier.fillMaxSize())
        IconButton(
          onClick = onClearImage,
          modifier =
            Modifier.align(Alignment.TopEnd)
              .size(22.dp)
              .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape),
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.content_desc_clear_image),
            modifier = Modifier.size(14.dp),
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = promptInput,
        onValueChange = onPromptChange,
        modifier = Modifier.weight(1f),
        placeholder = { Text(stringResource(R.string.agent_prompt_hint)) },
        maxLines = 4,
        leadingIcon = {
          IconButton(onClick = onPickImage, enabled = !isGenerating) {
            Icon(
              painter = painterResource(id = R.drawable.ic_add_photo),
              contentDescription = stringResource(R.string.button_add_image),
            )
          }
        },
        keyboardOptions =
          KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Send,
          ),
        keyboardActions = KeyboardActions(onSend = { if (!isGenerating) onSend() }),
      )

      if (isGenerating) {
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.button_cancel)) }
      } else {
        Button(onClick = onSend, enabled = promptInput.isNotBlank() || attachedImageUri != null) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = stringResource(R.string.button_send),
          )
        }
      }
    }
  }
}

@Composable
private fun HitlConfirmationDialog(
  confirmation: PendingToolConfirmation,
  onApprove: () -> Unit,
  onReject: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onReject,
    icon = {
      Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = { Text("Approve Tool Execution (HITL)") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text =
            "The agent requested to execute a sensitive @AppFunction tool wrapped with HitlConfirmationTool:",
          style = MaterialTheme.typography.bodyMedium,
        )
        Card(
          colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Text(
              text = "Tool: ${confirmation.toolName}",
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Arguments: ${confirmation.argsFormatted}",
              fontFamily = FontFamily.Monospace,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    },
    dismissButton = { OutlinedButton(onClick = onReject) { Text("Deny") } },
    confirmButton = { Button(onClick = onApprove) { Text("Approve & Execute") } },
  )
}

@Composable
private fun AgentConfigDialog(
  initialConfig: AgentConfig,
  onDismiss: () -> Unit,
  onSave: (AgentConfig) -> Unit,
) {
  var agentName by remember { mutableStateOf(initialConfig.agentName) }
  var instructions by remember { mutableStateOf(initialConfig.instructions) }
  var maxTurnsText by remember { mutableStateOf(initialConfig.maxTurns.toString()) }
  var selectedMode by remember { mutableStateOf(initialConfig.toolMode) }
  var scopedIds by remember { mutableStateOf(initialConfig.scopedFunctionIds.toSet()) }
  var hitlNames by remember { mutableStateOf(initialConfig.hitlToolNames) }
  var temperatureText by remember { mutableStateOf(initialConfig.temperature?.toString() ?: "") }
  var topKText by remember { mutableStateOf(initialConfig.topK?.toString() ?: "") }
  var maxOutputTokensText by remember {
    mutableStateOf(initialConfig.maxOutputTokens?.toString() ?: "")
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Agent & AgentRunner Configuration") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = agentName,
          onValueChange = { agentName = it },
          label = { Text("Agent Name (Agent.Builder.setName)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = instructions,
          onValueChange = { instructions = it },
          label = { Text("System Instructions (Agent.Builder.setInstructions)") },
          minLines = 2,
          maxLines = 4,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = maxTurnsText,
          onValueChange = { maxTurnsText = it },
          label = { Text("Max Turns (Agent.Builder.setMaxTurns)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        Text(
          text = "@AppFunction Tool Integration Mode",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )

        for (mode in AgentToolMode.entries) {
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selectedMode == mode, onClick = { selectedMode = mode })
            Column(modifier = Modifier.padding(start = 4.dp)) {
              Text(text = mode.displayName, style = MaterialTheme.typography.bodyMedium)
              Text(
                text = mode.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        if (selectedMode == AgentToolMode.FAST_PATH_SCOPED) {
          Text(
            text = "Scoped Function IDs (addAppFunctionIds):",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
          )
          for (fnId in AgentDemoDataStore.BUILT_IN_FUNCTION_IDS) {
            val checked = scopedIds.contains(fnId)
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = checked,
                onCheckedChange = { isChecked ->
                  scopedIds = if (isChecked) scopedIds + fnId else scopedIds - fnId
                },
              )
              Text(text = fnId, style = MaterialTheme.typography.bodySmall)
            }
          }
        }

        if (selectedMode == AgentToolMode.ADVANCED_HITL) {
          Text(
            text = "Tools Requiring HITL Confirmation (HitlConfirmationTool):",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
          )
          for (fnId in AgentDemoDataStore.BUILT_IN_FUNCTION_IDS) {
            val checked = hitlNames.contains(fnId)
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = checked,
                onCheckedChange = { isChecked ->
                  hitlNames = if (isChecked) hitlNames + fnId else hitlNames - fnId
                },
              )
              Text(text = fnId, style = MaterialTheme.typography.bodySmall)
            }
          }
        }

        HorizontalDivider()
        Text(
          text = "AgentRunner Sampling Parameters (Optional)",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = temperatureText,
            onValueChange = { temperatureText = it },
            label = { Text("Temperature") },
            placeholder = { Text("Default") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = topKText,
            onValueChange = { topKText = it },
            label = { Text("Top-K") },
            placeholder = { Text("Default") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }

        OutlinedTextField(
          value = maxOutputTokensText,
          onValueChange = { maxOutputTokensText = it },
          label = { Text("Max Output Tokens") },
          placeholder = { Text("Default") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
    },
    confirmButton = {
      Button(
        onClick = {
          val maxTurns =
            maxTurnsText.toIntOrNull()?.coerceAtLeast(1) ?: AgentConfig.DEFAULT_MAX_TURNS
          val temp = temperatureText.trim().toFloatOrNull()
          val topK = topKText.trim().toIntOrNull()
          val maxTokens = maxOutputTokensText.trim().toIntOrNull()
          onSave(
            AgentConfig(
              agentName = agentName.trim().ifEmpty { AgentConfig.DEFAULT_AGENT_NAME },
              instructions = instructions.trim(),
              maxTurns = maxTurns,
              toolMode = selectedMode,
              scopedFunctionIds = scopedIds.toList(),
              hitlToolNames = hitlNames,
              temperature = temp,
              topK = topK,
              maxOutputTokens = maxTokens,
            )
          )
        }
      ) {
        Text(stringResource(R.string.button_save))
      }
    },
  )
}

@SuppressLint("ContentResolverUri")
@Composable
private fun AgentUriThumbnail(uri: Uri, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val bitmap by
    produceState<Bitmap?>(initialValue = null, key1 = uri) {
      value =
        withContext(Dispatchers.IO) {
          try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
              BitmapFactory.decodeStream(stream)
            }
          } catch (_: Exception) {
            null
          }
        }
    }

  val loadedBitmap = bitmap
  if (loadedBitmap != null) {
    Image(
      bitmap = loadedBitmap.asImageBitmap(),
      contentDescription = stringResource(R.string.attached_image),
      contentScale = ContentScale.Crop,
      modifier = modifier.clip(RoundedCornerShape(8.dp)),
    )
  }
}

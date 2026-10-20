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
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.agent.AgentEvent
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.FunctionArgValue
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.demo.kotlin.AgentDemoDataStore
import com.google.mlkit.genai.demo.kotlin.repository.AgentConfig
import com.google.mlkit.genai.demo.kotlin.repository.AgentRepository
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Represents a single visual entry in the Agent execution timeline. */
sealed class AgentTimelineItem {
  abstract val id: String

  /** A user message (text and/or attached image) submitted to the agent. */
  data class UserPrompt(
    override val id: String,
    val text: String,
    val imageUri: Uri? = null,
    val isSingleTurn: Boolean = false,
  ) : AgentTimelineItem()

  /** An [AgentEvent.FunctionCall] emitted when the model invokes an `@AppFunction` tool. */
  data class ToolCall(override val id: String, val toolName: String, val argsFormatted: String) :
    AgentTimelineItem()

  /** An [AgentEvent.FunctionResponse] emitted after an `@AppFunction` tool finishes executing. */
  data class ToolResponse(
    override val id: String,
    val toolName: String,
    val resultFormatted: String,
  ) : AgentTimelineItem()

  /**
   * An [AgentEvent.TextMessage] emitted by the model, supporting live token streaming (`isPartial =
   * true`) and final completion (`isPartial = false`) with latency metrics.
   */
  data class AgentMessage(
    override val id: String,
    val text: String,
    val isStreaming: Boolean,
    val firstTokenLatencyMs: Long? = null,
    val totalLatencyMs: Long? = null,
  ) : AgentTimelineItem()

  /** A status or error notice displayed inline in the timeline. */
  data class SystemNotice(
    override val id: String,
    val message: String,
    val isError: Boolean = false,
  ) : AgentTimelineItem()
}

/** Represents a pending Human-in-the-Loop (HITL) tool execution awaiting user confirmation. */
data class PendingToolConfirmation(
  val toolName: String,
  val argsFormatted: String,
  val decision: CompletableDeferred<Boolean>,
)

/** Preset sample prompts highlighting multi-tool, smart home, productivity, and HITL flows. */
data class SampleAgentPrompt(val label: String, val prompt: String)

/**
 * ViewModel orchestrating the ML Kit GenAI Agent API demo screen, including multi-turn
 * [com.google.mlkit.genai.agent.AgentSession] management, ephemeral single-turn runs, streaming
 * [AgentEvent] timeline updates, and Human-in-the-Loop tool confirmation.
 */
class AgentViewModel(private val repository: AgentRepository) : ViewModel() {

  private val idCounter = AtomicLong(1L)
  private var nextSessionNumber = 2
  private var activeTurnJob: Job? = null

  private val _sessions = MutableStateFlow(listOf(DEFAULT_SESSION_ID))
  val sessions: StateFlow<List<String>> = _sessions.asStateFlow()

  private val _currentSessionId = MutableStateFlow(DEFAULT_SESSION_ID)
  val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

  private val _isSingleTurnMode = MutableStateFlow(false)
  val isSingleTurnMode: StateFlow<Boolean> = _isSingleTurnMode.asStateFlow()

  private val _sessionTimelines =
    MutableStateFlow<Map<String, List<AgentTimelineItem>>>(mapOf(DEFAULT_SESSION_ID to emptyList()))
  val sessionTimelines: StateFlow<Map<String, List<AgentTimelineItem>>> =
    _sessionTimelines.asStateFlow()

  private val _singleTurnTimeline = MutableStateFlow<List<AgentTimelineItem>>(emptyList())
  val singleTurnTimeline: StateFlow<List<AgentTimelineItem>> = _singleTurnTimeline.asStateFlow()

  private val _promptInput = MutableStateFlow("")
  val promptInput: StateFlow<String> = _promptInput.asStateFlow()

  private val _attachedImageUri = MutableStateFlow<Uri?>(null)
  val attachedImageUri: StateFlow<Uri?> = _attachedImageUri.asStateFlow()

  private val _isGenerating = MutableStateFlow(false)
  val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

  private val _agentConfig = MutableStateFlow(AgentConfig())
  val agentConfig: StateFlow<AgentConfig> = _agentConfig.asStateFlow()

  private val _baseModelName = MutableStateFlow("")
  val baseModelName: StateFlow<String> = _baseModelName.asStateFlow()

  private val _modelStatusMessage = MutableStateFlow<String?>(null)
  val modelStatusMessage: StateFlow<String?> = _modelStatusMessage.asStateFlow()

  private val _isDownloading = MutableStateFlow(false)
  val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

  private val _pendingConfirmation = MutableStateFlow<PendingToolConfirmation?>(null)
  val pendingConfirmation: StateFlow<PendingToolConfirmation?> = _pendingConfirmation.asStateFlow()

  private val _showConfigDialog = MutableStateFlow(false)
  val showConfigDialog: StateFlow<Boolean> = _showConfigDialog.asStateFlow()

  private val _showToolsSchemaDialog = MutableStateFlow(false)
  val showToolsSchemaDialog: StateFlow<Boolean> = _showToolsSchemaDialog.asStateFlow()

  private val _toolsSchemaJson = MutableStateFlow("")
  val toolsSchemaJson: StateFlow<String> = _toolsSchemaJson.asStateFlow()

  private val _showSandboxDialog = MutableStateFlow(false)
  val showSandboxDialog: StateFlow<Boolean> = _showSandboxDialog.asStateFlow()

  private val _sandboxSnapshot = MutableStateFlow(AgentDemoDataStore.getFormattedSandboxSnapshot())
  val sandboxSnapshot: StateFlow<String> = _sandboxSnapshot.asStateFlow()

  init {
    initializeAgentAndModel()
  }

  private fun initializeAgentAndModel() {
    viewModelScope.launch {
      val unused = repository.rebuildRunner(_agentConfig.value, ::requestUserToolConfirmation)
      val status = repository.checkModelStatus()
      when (status) {
        FeatureStatus.AVAILABLE -> {
          _modelStatusMessage.value = null
          _baseModelName.value = repository.getBaseModelName()
        }
        FeatureStatus.DOWNLOADABLE -> {
          _modelStatusMessage.value = "On-device model is downloadable. Starting download..."
          startModelDownload()
        }
        FeatureStatus.DOWNLOADING -> {
          _modelStatusMessage.value = "On-device model is currently downloading..."
          startModelDownload()
        }
        else -> {
          _modelStatusMessage.value = "On-device Gemini Nano model is unavailable on this device."
        }
      }
    }
  }

  /** Triggers or resumes downloading the on-device Gemini Nano model. */
  fun startModelDownload() {
    if (_isDownloading.value) return
    viewModelScope.launch {
      _isDownloading.value = true
      var bytesToDownload = 0L
      try {
        repository.downloadModel().collect { status ->
          when (status) {
            is DownloadStatus.DownloadStarted -> {
              bytesToDownload = status.bytesToDownload
              _modelStatusMessage.value =
                "Downloading model (${bytesToDownload / BYTES_PER_MB} MB)..."
            }
            is DownloadStatus.DownloadProgress -> {
              val downloadedMb = status.totalBytesDownloaded / BYTES_PER_MB
              if (bytesToDownload > 0L) {
                val totalMb = bytesToDownload / BYTES_PER_MB
                val pct = 100.0 * status.totalBytesDownloaded / bytesToDownload
                _modelStatusMessage.value =
                  String.format(
                    Locale.US,
                    "Downloading model: %d / %d MB (%.1f%%)",
                    downloadedMb,
                    totalMb,
                    pct,
                  )
              } else {
                _modelStatusMessage.value = "Downloading model: $downloadedMb MB..."
              }
            }
            is DownloadStatus.DownloadCompleted -> {
              _modelStatusMessage.value = null
              _baseModelName.value = repository.getBaseModelName()
            }
            is DownloadStatus.DownloadFailed -> {
              _modelStatusMessage.value =
                "Model download failed: ${status.e.message ?: "Unknown error"}"
            }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _modelStatusMessage.value = "Model download failed: ${e.message ?: "Unknown error"}"
      } finally {
        _isDownloading.value = false
      }
    }
  }

  fun updatePromptInput(text: String) {
    _promptInput.value = text
  }

  fun updateAttachedImage(uri: Uri?) {
    _attachedImageUri.value = uri
  }

  fun applySamplePrompt(prompt: String) {
    _promptInput.value = prompt
  }

  fun setSingleTurnMode(singleTurn: Boolean) {
    _isSingleTurnMode.value = singleTurn
  }

  fun selectSession(sessionId: String) {
    if (_sessions.value.contains(sessionId)) {
      _currentSessionId.value = sessionId
      _isSingleTurnMode.value = false
    }
  }

  /** Creates a new stateful conversation session and selects it. */
  fun createNewSession() {
    val newId = "session-${nextSessionNumber++}"
    _sessions.update { if (it.contains(newId)) it else it + newId }
    _sessionTimelines.update { it + (newId to (it[newId] ?: emptyList())) }
    _currentSessionId.value = newId
    _isSingleTurnMode.value = false
  }

  /**
   * Deletes the currently active session's history via [AgentRepository.deleteSession] and clears
   * its timeline.
   */
  fun deleteCurrentSession() {
    val sessionId = _currentSessionId.value
    viewModelScope.launch {
      try {
        repository.deleteSession(sessionId)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // Ignore if session was not yet initialized in runner
      }
      _sessionTimelines.update { current ->
        val updated = current.toMutableMap()
        updated[sessionId] =
          listOf(
            AgentTimelineItem.SystemNotice(
              id = nextItemId(),
              message =
                "Cleared in-memory history for '$sessionId' via AgentRunner.deleteSession().",
            )
          )
        updated
      }
    }
  }

  /**
   * Clears all sessions across the [com.google.mlkit.genai.agent.AgentRunner] via
   * [AgentRepository.clearAllSessions] and resets the UI to a fresh session.
   */
  fun clearAllSessions() {
    viewModelScope.launch {
      try {
        repository.clearAllSessions()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // Ignore if runner was not initialized
      }
      nextSessionNumber = 2
      _sessions.value = listOf(DEFAULT_SESSION_ID)
      _currentSessionId.value = DEFAULT_SESSION_ID
      _singleTurnTimeline.value = emptyList()
      _sessionTimelines.value =
        mapOf(
          DEFAULT_SESSION_ID to
            listOf(
              AgentTimelineItem.SystemNotice(
                id = nextItemId(),
                message = "Cleared all sessions via AgentRunner.clearAllSessions().",
              )
            )
        )
    }
  }

  /**
   * Sends the current prompt (and optional attached image) to the agent using either
   * `AgentSession.runAsync(content)` (multi-turn mode) or `AgentRunner.runSingleTurnAsync(content)`
   * (single-turn mode), collecting streaming [AgentEvent] updates in real time.
   */
  fun sendPrompt() {
    val text = _promptInput.value.trim()
    val imageUri = _attachedImageUri.value
    if ((text.isEmpty() && imageUri == null) || _isGenerating.value) {
      return
    }

    val singleTurn = _isSingleTurnMode.value
    val sessionId = _currentSessionId.value
    _promptInput.value = ""
    _attachedImageUri.value = null

    val userItem =
      AgentTimelineItem.UserPrompt(
        id = nextItemId(),
        text = text.ifEmpty { "(Image attached)" },
        imageUri = imageUri,
        isSingleTurn = singleTurn,
      )
    appendTimelineItem(singleTurn, sessionId, userItem)

    activeTurnJob = viewModelScope.launch {
      _isGenerating.value = true
      val startElapsedMs = SystemClock.elapsedRealtime()
      var firstTokenLatencyMs: Long? = null
      var activeStreamingMessageId: String? = null
      val streamingBuffer = StringBuilder()

      try {
        val content = repository.buildContent(text, imageUri)
        val eventFlow =
          if (singleTurn) {
            repository.runSingleTurn(content, ::requestUserToolConfirmation)
          } else {
            repository.runSessionTurn(sessionId, content, ::requestUserToolConfirmation)
          }

        eventFlow.collect { event ->
          when (event) {
            is AgentEvent.FunctionCall -> {
              activeStreamingMessageId = null
              streamingBuffer.clear()
              appendTimelineItem(
                singleTurn = singleTurn,
                sessionId = sessionId,
                item =
                  AgentTimelineItem.ToolCall(
                    id = nextItemId(),
                    toolName = event.name,
                    argsFormatted = formatMap(event.args),
                  ),
              )
            }
            is AgentEvent.FunctionResponse -> {
              activeStreamingMessageId = null
              streamingBuffer.clear()
              _sandboxSnapshot.value = AgentDemoDataStore.getFormattedSandboxSnapshot()
              appendTimelineItem(
                singleTurn = singleTurn,
                sessionId = sessionId,
                item =
                  AgentTimelineItem.ToolResponse(
                    id = nextItemId(),
                    toolName = event.name,
                    resultFormatted = formatMap(event.response),
                  ),
              )
            }
            is AgentEvent.TextMessage -> {
              if (firstTokenLatencyMs == null && event.text.isNotEmpty()) {
                firstTokenLatencyMs = SystemClock.elapsedRealtime() - startElapsedMs
              }
              if (event.isPartial) {
                streamingBuffer.append(event.text)
                val msgId =
                  activeStreamingMessageId
                    ?: nextItemId().also { newId -> activeStreamingMessageId = newId }
                upsertAgentMessage(
                  singleTurn = singleTurn,
                  sessionId = sessionId,
                  messageId = msgId,
                  text = streamingBuffer.toString(),
                  isStreaming = true,
                  firstTokenLatencyMs = firstTokenLatencyMs,
                  totalLatencyMs = null,
                )
              } else {
                val totalMs = SystemClock.elapsedRealtime() - startElapsedMs
                val finalText =
                  if (event.text.isNotEmpty()) {
                    event.text
                  } else {
                    streamingBuffer.toString()
                  }
                val msgId = activeStreamingMessageId ?: nextItemId()
                upsertAgentMessage(
                  singleTurn = singleTurn,
                  sessionId = sessionId,
                  messageId = msgId,
                  text = finalText,
                  isStreaming = false,
                  firstTokenLatencyMs = firstTokenLatencyMs ?: totalMs,
                  totalLatencyMs = totalMs,
                )
                activeStreamingMessageId = null
                streamingBuffer.clear()
              }
            }
          }
        }

        // If the stream ended after partial chunks without a separate non-partial TextMessage,
        // finalize the active streaming bubble.
        activeStreamingMessageId?.let { msgId ->
          val totalMs = SystemClock.elapsedRealtime() - startElapsedMs
          upsertAgentMessage(
            singleTurn = singleTurn,
            sessionId = sessionId,
            messageId = msgId,
            text = streamingBuffer.toString(),
            isStreaming = false,
            firstTokenLatencyMs = firstTokenLatencyMs ?: totalMs,
            totalLatencyMs = totalMs,
          )
        }
      } catch (e: CancellationException) {
        appendTimelineItem(
          singleTurn = singleTurn,
          sessionId = sessionId,
          item =
            AgentTimelineItem.SystemNotice(
              id = nextItemId(),
              message = "Agent turn cancelled.",
              isError = false,
            ),
        )
        throw e
      } catch (e: GenAiException) {
        val errCodeNote =
          if (e.errorCode == GenAiException.ErrorCode.MAX_TURNS_EXCEEDED) {
            " (MAX_TURNS_EXCEEDED: limit=${_agentConfig.value.maxTurns})"
          } else {
            " (errorCode=${e.errorCode})"
          }
        appendTimelineItem(
          singleTurn = singleTurn,
          sessionId = sessionId,
          item =
            AgentTimelineItem.SystemNotice(
              id = nextItemId(),
              message = "GenAiException$errCodeNote: ${e.message ?: "Agent execution failed"}",
              isError = true,
            ),
        )
      } catch (e: Exception) {
        appendTimelineItem(
          singleTurn = singleTurn,
          sessionId = sessionId,
          item =
            AgentTimelineItem.SystemNotice(
              id = nextItemId(),
              message = "Error: ${e.message ?: e::class.java.simpleName}",
              isError = true,
            ),
        )
      } finally {
        _pendingConfirmation.value?.decision?.complete(false)
        _pendingConfirmation.value = null
        _isGenerating.value = false
      }
    }
  }

  /** Cancels the currently running agent turn coroutine. */
  fun cancelCurrentTurn() {
    _pendingConfirmation.value?.decision?.complete(false)
    _pendingConfirmation.value = null
    activeTurnJob?.cancel()
    activeTurnJob = null
  }

  /** Resolves a pending Human-in-the-Loop tool execution confirmation dialog. */
  fun resolveToolConfirmation(approved: Boolean) {
    val pending = _pendingConfirmation.value ?: return
    _pendingConfirmation.value = null
    pending.decision.complete(approved)
  }

  /**
   * Updates the [AgentConfig], rebuilds the [com.google.mlkit.genai.agent.Agent] and
   * [com.google.mlkit.genai.agent.AgentRunner], and posts a configuration notice to the timeline.
   */
  fun updateAgentConfig(newConfig: AgentConfig) {
    _showConfigDialog.value = false
    viewModelScope.launch {
      _agentConfig.value = newConfig
      val unused = repository.rebuildRunner(newConfig, ::requestUserToolConfirmation)
      val sessionId = _currentSessionId.value
      appendTimelineItem(
        singleTurn = _isSingleTurnMode.value,
        sessionId = sessionId,
        item =
          AgentTimelineItem.SystemNotice(
            id = nextItemId(),
            message =
              "Rebuilt Agent '${newConfig.agentName}' (mode=${newConfig.toolMode.displayName}, maxTurns=${newConfig.maxTurns}).",
          ),
      )
    }
  }

  fun openConfigDialog() {
    _showConfigDialog.value = true
  }

  fun dismissConfigDialog() {
    _showConfigDialog.value = false
  }

  fun openToolsSchemaDialog() {
    viewModelScope.launch {
      _toolsSchemaJson.value = "Discovering @AppFunction schemas..."
      _showToolsSchemaDialog.value = true
      _toolsSchemaJson.value = repository.getAvailableToolSchemaJson(_agentConfig.value)
    }
  }

  fun dismissToolsSchemaDialog() {
    _showToolsSchemaDialog.value = false
  }

  fun openSandboxDialog() {
    _sandboxSnapshot.value = AgentDemoDataStore.getFormattedSandboxSnapshot()
    _showSandboxDialog.value = true
  }

  fun dismissSandboxDialog() {
    _showSandboxDialog.value = false
  }

  fun resetSandboxData() {
    AgentDemoDataStore.resetToDefaults()
    _sandboxSnapshot.value = AgentDemoDataStore.getFormattedSandboxSnapshot()
  }

  private suspend fun requestUserToolConfirmation(
    toolName: String,
    args: Map<String, Any>,
  ): Boolean {
    val deferred = CompletableDeferred<Boolean>()
    _pendingConfirmation.value =
      PendingToolConfirmation(
        toolName = toolName,
        argsFormatted = formatMap(args),
        decision = deferred,
      )
    return try {
      deferred.await()
    } finally {
      _pendingConfirmation.value = null
    }
  }

  private fun appendTimelineItem(singleTurn: Boolean, sessionId: String, item: AgentTimelineItem) {
    if (singleTurn) {
      _singleTurnTimeline.update { it + item }
    } else {
      _sessionTimelines.update { map ->
        val updated = map.toMutableMap()
        updated[sessionId] = (updated[sessionId] ?: emptyList()) + item
        updated
      }
    }
  }

  private fun upsertAgentMessage(
    singleTurn: Boolean,
    sessionId: String,
    messageId: String,
    text: String,
    isStreaming: Boolean,
    firstTokenLatencyMs: Long?,
    totalLatencyMs: Long?,
  ) {
    val newMessage =
      AgentTimelineItem.AgentMessage(
        id = messageId,
        text = text,
        isStreaming = isStreaming,
        firstTokenLatencyMs = firstTokenLatencyMs,
        totalLatencyMs = totalLatencyMs,
      )
    val updateList: (List<AgentTimelineItem>) -> List<AgentTimelineItem> = { list ->
      val existingIndex = list.indexOfFirst { it.id == messageId }
      if (existingIndex >= 0) {
        list.toMutableList().apply { set(existingIndex, newMessage) }
      } else {
        list + newMessage
      }
    }
    if (singleTurn) {
      _singleTurnTimeline.update(updateList)
    } else {
      _sessionTimelines.update { map ->
        val updated = map.toMutableMap()
        updated[sessionId] = updateList(updated[sessionId] ?: emptyList())
        updated
      }
    }
  }

  private fun nextItemId(): String = "item-${idCounter.getAndIncrement()}"

  override fun onCleared() {
    repository.close()
  }

  companion object {
    const val DEFAULT_SESSION_ID = "session-1"
    private const val BYTES_PER_MB = 1024L * 1024L

    val SAMPLE_PROMPTS: List<SampleAgentPrompt> =
      listOf(
        SampleAgentPrompt(
          label = "Account & Home Summary",
          prompt = "Show my account summary and current smart home light status.",
        ),
        SampleAgentPrompt(
          label = "Smart Home Lights",
          prompt = "Turn on the bedroom light to 40% brightness and turn off the kitchen light.",
        ),
        SampleAgentPrompt(
          label = "Create Note & Reminder",
          prompt =
            "Create a note titled 'Launch Checklist' with content 'Verify Agent API sample app' and set a reminder to 'Demo Agent API' for tomorrow at 10:00 AM.",
        ),
        SampleAgentPrompt(label = "List Reminders", prompt = "List all of my scheduled reminders."),
        SampleAgentPrompt(
          label = "Deposit & Withdraw",
          prompt =
            "Deposit $500 into savings and withdraw $120 from checking, then tell me the updated balances.",
        ),
        SampleAgentPrompt(
          label = "Transfer Funds (HITL)",
          prompt = "Transfer $250 from checking to travel fund and tell me the new balances.",
        ),
        SampleAgentPrompt(label = "List Notes", prompt = "List all of my saved notes."),
      )

    internal fun formatMap(map: Map<String, Any?>): String {
      if (map.isEmpty()) return "{}"
      return map.entries.joinToString(prefix = "{ ", postfix = " }", separator = ", ") { (k, v) ->
        "$k: ${formatValue(v)}"
      }
    }

    private fun formatValue(v: Any?): String =
      when (v) {
        is String -> "\"$v\""
        is FunctionArgValue.StringValue -> "\"${v.value}\""
        is FunctionArgValue.BoolValue -> v.value.toString()
        is FunctionArgValue.IntValue -> v.value.toString()
        is FunctionArgValue.LongValue -> v.value.toString()
        is FunctionArgValue.DoubleValue -> v.value.toString()
        is FunctionArgValue.ListValue ->
          v.values.joinToString(prefix = "[", postfix = "]") { formatValue(it) }
        is FunctionArgValue.MapValue -> formatMap(v.map)
        else -> v.toString()
      }
  }
}

/** Factory for creating [AgentViewModel] with an application [Context]. */
class AgentViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(AgentViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return AgentViewModel(AgentRepository(context.applicationContext ?: context)) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}

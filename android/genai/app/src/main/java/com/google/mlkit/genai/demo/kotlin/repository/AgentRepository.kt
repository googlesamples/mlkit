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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.genai.agent.Agent
import com.google.mlkit.genai.agent.AgentEvent
import com.google.mlkit.genai.agent.AgentRunner
import com.google.mlkit.genai.agent.AgentTool
import com.google.mlkit.genai.agent.AppFunctionAgentTools
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.demo.kotlin.AgentDemoDataStore
import com.google.mlkit.genai.demo.kotlin.HitlConfirmationTool
import com.google.mlkit.genai.prompt.Content
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Configures how the demo's [Agent] discovers and registers Android `@AppFunction` tools. */
enum class AgentToolMode(val displayName: String, val subtitle: String) {
  /** Uses `Agent.Builder.enableAppFunctions(true)` for automatic in-app tool discovery. */
  FAST_PATH_ALL(
    displayName = "Fast Path (enableAppFunctions)",
    subtitle = "Auto-discovers and executes all in-app @AppFunction tools",
  ),

  /** Uses `Agent.Builder.addAppFunctionIds(...)` to scope the agent to specific function IDs. */
  FAST_PATH_SCOPED(
    displayName = "Scoped Fast Path (addAppFunctionIds)",
    subtitle = "Restricts agent to selected @AppFunction IDs",
  ),

  /**
   * Uses `AppFunctionAgentTools.discover(context)` and wraps sensitive tools with
   * [HitlConfirmationTool] before calling `Agent.Builder.addTools(...)`.
   */
  ADVANCED_HITL(
    displayName = "Advanced HITL (discover + Decorator)",
    subtitle = "Discovers tools and gates sensitive actions on user confirmation",
  ),

  /** Disables tools so the agent operates in pure conversational mode. */
  DISABLED(
    displayName = "No Tools (Chat Only)",
    subtitle = "Conversational agent without tool execution",
  ),
}

/**
 * Immutable configuration snapshot used to construct [Agent] and [AgentRunner] instances.
 *
 * @property agentName Name passed to [Agent.Builder.setName].
 * @property instructions System instructions passed to [Agent.Builder.setInstructions].
 * @property maxTurns Maximum reasoning/tool-calling turns per request
 *   ([Agent.Builder.setMaxTurns]).
 * @property toolMode How `@AppFunction` tools are registered on the [Agent].
 * @property scopedFunctionIds Function IDs enabled when [toolMode] is
 *   [AgentToolMode.FAST_PATH_SCOPED].
 * @property hitlToolNames Tool names wrapped with [HitlConfirmationTool] when [toolMode] is
 *   [AgentToolMode.ADVANCED_HITL].
 * @property temperature Optional sampling temperature passed to [AgentRunner].
 * @property topK Optional top-K sampling parameter passed to [AgentRunner].
 * @property maxOutputTokens Optional max output tokens passed to [AgentRunner].
 */
data class AgentConfig(
  val agentName: String = DEFAULT_AGENT_NAME,
  val instructions: String = DEFAULT_INSTRUCTIONS,
  val maxTurns: Int = DEFAULT_MAX_TURNS,
  val toolMode: AgentToolMode = AgentToolMode.FAST_PATH_ALL,
  val scopedFunctionIds: List<String> = AgentDemoDataStore.BUILT_IN_FUNCTION_IDS,
  val hitlToolNames: Set<String> = setOf("transferFunds", "withdrawFunds", "setDeviceLight"),
  val temperature: Float? = null,
  val topK: Int? = null,
  val maxOutputTokens: Int? = null,
) {
  companion object {
    const val DEFAULT_AGENT_NAME = "PersonalAssistant"
    const val DEFAULT_MAX_TURNS = 6
    const val DEFAULT_INSTRUCTIONS =
      "You are a helpful on-device personal assistant. Use available tools to create and list " +
        "notes, schedule and list reminders, control smart home lights, and check balances or " +
        "record deposits, withdrawals, and transfers for bank accounts when requested by the " +
        "user. Always summarize the tool results clearly."
  }
}

/**
 * Repository encapsulating [Agent], [AgentRunner], [AppFunctionAgentTools], and underlying
 * [GenerativeModel] lifecycle management for the Agent API demo.
 */
open class AgentRepository(private val context: Context) {

  private val runnerMutex = Mutex()
  private var generativeModel: GenerativeModel? = null
  private var agentRunner: AgentRunner? = null
  private var currentConfig: AgentConfig = AgentConfig()

  /** Checks the on-device Gemini Nano model availability via [GenerativeModel.checkStatus]. */
  open suspend fun checkModelStatus(): Int {
    return try {
      getOrCreateModel().checkStatus()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to check model status", e)
      FeatureStatus.UNAVAILABLE
    }
  }

  /** Downloads the on-device model if its status is [FeatureStatus.DOWNLOADABLE]. */
  open fun downloadModel(): Flow<DownloadStatus> = flow { emitAll(getOrCreateModel().download()) }

  /** Returns the underlying base model name if available. */
  open suspend fun getBaseModelName(): String {
    return try {
      getOrCreateModel().getBaseModelName()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to get base model name", e)
      "Gemini Nano (On-Device)"
    }
  }

  /** Discovers in-app `@AppFunction` tools via [AppFunctionAgentTools.discover]. */
  open suspend fun discoverTools(functionIds: List<String> = emptyList()): List<AgentTool> {
    return if (functionIds.isEmpty()) {
      AppFunctionAgentTools.discover(context)
    } else {
      AppFunctionAgentTools.discover(context, functionIds)
    }
  }

  /**
   * Builds and returns an [Agent] configured according to [config], optionally wrapping sensitive
   * discovered tools with [HitlConfirmationTool] when in [AgentToolMode.ADVANCED_HITL] mode.
   */
  open suspend fun buildAgent(
    config: AgentConfig,
    onConfirmToolCall: suspend (toolName: String, args: Map<String, Any>) -> Boolean = { _, _ ->
      true
    },
  ): Agent {
    val builder =
      Agent.builder()
        .setName(config.agentName.ifBlank { AgentConfig.DEFAULT_AGENT_NAME })
        .setInstructions(config.instructions)
        .setMaxTurns(config.maxTurns.coerceAtLeast(1))

    when (config.toolMode) {
      AgentToolMode.FAST_PATH_ALL -> {
        builder.enableAppFunctions(true)
      }
      AgentToolMode.FAST_PATH_SCOPED -> {
        if (config.scopedFunctionIds.isNotEmpty()) {
          builder.addAppFunctionIds(config.scopedFunctionIds)
        }
      }
      AgentToolMode.ADVANCED_HITL -> {
        val discovered = discoverTools()
        val decoratedTools = discovered.map { tool ->
          val matchesHitl =
            config.hitlToolNames.any { hitlName ->
              tool.name.equals(hitlName, ignoreCase = true) ||
                tool.name.endsWith("#$hitlName", ignoreCase = true) ||
                tool.name.endsWith("_$hitlName", ignoreCase = true)
            }
          if (matchesHitl) {
            HitlConfirmationTool(delegate = tool, onRequireConfirmation = onConfirmToolCall)
          } else {
            tool
          }
        }
        builder.addTools(decoratedTools)
      }
      AgentToolMode.DISABLED -> {
        builder.enableAppFunctions(false)
      }
    }
    return builder.build()
  }

  /** Rebuilds the active [AgentRunner] with [config] and closes any previously active runner. */
  open suspend fun rebuildRunner(
    config: AgentConfig,
    onConfirmToolCall: suspend (toolName: String, args: Map<String, Any>) -> Boolean = { _, _ ->
      true
    },
  ): Agent {
    return runnerMutex.withLock {
      currentConfig = config
      agentRunner?.close()
      val agent = buildAgent(config, onConfirmToolCall)
      val appContext = context.applicationContext ?: context
      val model =
        try {
          getOrCreateModel()
        } catch (e: Exception) {
          Log.w(TAG, "Deferred GenerativeModel creation until first turn", e)
          null
        }
      agentRunner =
        if (model != null) {
          AgentRunner(
            context = appContext,
            agent = agent,
            generativeModel = model,
            temperature = config.temperature,
            topK = config.topK,
            maxOutputTokens = config.maxOutputTokens,
          )
        } else {
          AgentRunner(
            context = appContext,
            agent = agent,
            temperature = config.temperature,
            topK = config.topK,
            maxOutputTokens = config.maxOutputTokens,
          )
        }
      agent
    }
  }

  /**
   * Executes a stateful multi-turn conversation step inside the session identified by [sessionId]
   * via `agentRunner.getOrCreateSession(sessionId).runAsync(content)`.
   */
  open fun runSessionTurn(
    sessionId: String,
    content: Content,
    onConfirmToolCall: suspend (toolName: String, args: Map<String, Any>) -> Boolean = { _, _ ->
      true
    },
  ): Flow<AgentEvent> = flow {
    val runner = ensureRunnerInitialized(onConfirmToolCall)
    val session = runner.getOrCreateSession(sessionId)
    emitAll(session.runAsync(content))
  }

  /**
   * Executes an ephemeral single-turn agent request via `agentRunner.runSingleTurnAsync(content)`.
   */
  open fun runSingleTurn(
    content: Content,
    onConfirmToolCall: suspend (toolName: String, args: Map<String, Any>) -> Boolean = { _, _ ->
      true
    },
  ): Flow<AgentEvent> = flow {
    val runner = ensureRunnerInitialized(onConfirmToolCall)
    emitAll(runner.runSingleTurnAsync(content))
  }

  /**
   * Deletes the conversation history for [sessionId] via [AgentRunner.deleteSession].
   */
  open suspend fun deleteSession(
    sessionId: String,
    onConfirmToolCall: suspend (toolName: String, args: Map<String, Any>) -> Boolean = { _, _ ->
      true
    },
  ) {
    val runner = ensureRunnerInitialized(onConfirmToolCall)
    runner.deleteSession(sessionId)
  }

  /**
   * Clears all conversation histories across all sessions via [AgentRunner.clearAllSessions].
   */
  open suspend fun clearAllSessions(
    onConfirmToolCall: suspend (toolName: String, args: Map<String, Any>) -> Boolean = { _, _ ->
      true
    }
  ) {
    val runner = ensureRunnerInitialized(onConfirmToolCall)
    runner.clearAllSessions()
  }

  /**
   * Builds a multimodal [Content] object combining [promptText] and an optional [imageUri].
   *
   * Demonstrates constructing `Content.builder().image(bitmap).text(promptText).build()` for the
   * Agent API.
   */
  @SuppressLint("ContentResolverUri")
  open fun buildContent(promptText: String, imageUri: Uri? = null): Content {
    val builder = Content.builder()
    if (imageUri != null) {
      try {
        context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
          BitmapFactory.decodeStream(inputStream)?.let { bitmap ->
            if (!bitmap.hasAlpha()) {
              bitmap.setHasAlpha(false)
            }
            builder.image(bitmap)
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to decode attached image URI: $imageUri", e)
      }
    }
    if (promptText.isNotBlank()) {
      builder.text(promptText)
    }
    return builder.build()
  }

  /**
   * Returns a JSON representation of discovered `@AppFunction` tool declarations for the schema
   * viewer dialog. If platform AppSearch indexing has not completed yet, returns the built-in
   * declaration descriptors alongside discovery status.
   */
  open suspend fun getAvailableToolSchemaJson(config: AgentConfig): String {
    val discovered =
      when (config.toolMode) {
        AgentToolMode.FAST_PATH_SCOPED -> discoverTools(config.scopedFunctionIds)
        AgentToolMode.DISABLED -> emptyList()
        else -> discoverTools()
      }
    if (discovered.isNotEmpty()) {
      val discoveredArray = JSONArray()
      for (tool in discovered) {
        val obj = JSONObject().put("name", tool.name).put("description", tool.description)
        discoveredArray.put(obj)
      }
      return discoveredArray.toString(2)
    }
    val fallbackArray = JSONArray()
    for (id in AgentDemoDataStore.BUILT_IN_FUNCTION_IDS) {
      val obj =
        JSONObject()
          .put("functionId", "com.google.mlkit.genai.demo.kotlin.AgentDemoAppFunctionService#$id")
          .put("shortName", id)
          .put("hitlGatedInAdvancedMode", config.hitlToolNames.contains(id))
      fallbackArray.put(obj)
    }
    return JSONObject()
      .put("toolMode", config.toolMode.displayName)
      .put(
        "discoveryStatus",
        if (config.toolMode == AgentToolMode.DISABLED) {
          "Tools disabled in current AgentConfig"
        } else {
          "AppFunctions registered in APK (live schema populated once Android AppSearch indexes the service)"
        },
      )
      .put("declaredAppFunctions", fallbackArray)
      .toString(2)
  }

  /** Closes the active [AgentRunner] and underlying [GenerativeModel]. */
  open fun close() {
    agentRunner?.close()
    agentRunner = null
    generativeModel?.close()
    generativeModel = null
  }

  private fun getOrCreateModel(): GenerativeModel {
    return generativeModel ?: Generation.getClient().also { generativeModel = it }
  }

  private suspend fun ensureRunnerInitialized(
    onConfirmToolCall: suspend (toolName: String, args: Map<String, Any>) -> Boolean
  ): AgentRunner {
    runnerMutex.withLock {
      agentRunner?.let {
        return it
      }
    }
    val unused = rebuildRunner(currentConfig, onConfirmToolCall)
    return runnerMutex.withLock { checkNotNull(agentRunner) }
  }

  companion object {
    private const val TAG = "AgentRepository"
  }
}

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

package com.google.mlkit.genai.demo

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

object GenerationConfigUtils {
  internal fun getStoredTemperature(context: Context): Float =
    PreferenceManager.getDefaultSharedPreferences(context)
      .getFloat(context.getString(R.string.pref_key_temperature), 0.2f)

  @JvmStatic
  fun getTemperature(context: Context): Float? {
    if (getUseDefaultConfig(context)) {
      return null
    }
    return getStoredTemperature(context)
  }

  @JvmStatic
  fun setTemperature(context: Context, temperature: Float) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putFloat(context.getString(R.string.pref_key_temperature), temperature)
    }
  }

  internal fun getStoredTopK(context: Context): Int =
    PreferenceManager.getDefaultSharedPreferences(context)
      .getInt(context.getString(R.string.pref_key_top_k), 16)

  @JvmStatic
  fun getTopK(context: Context): Int? {
    if (getUseDefaultConfig(context)) {
      return null
    }
    return getStoredTopK(context)
  }

  @JvmStatic
  fun setTopK(context: Context, topK: Int) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putInt(context.getString(R.string.pref_key_top_k), topK)
    }
  }

  internal fun getStoredSeed(context: Context): Int =
    PreferenceManager.getDefaultSharedPreferences(context)
      .getInt(context.getString(R.string.pref_key_seed), 0)

  @JvmStatic
  fun getSeed(context: Context): Int? {
    if (getUseDefaultConfig(context)) {
      return null
    }
    return getStoredSeed(context)
  }

  @JvmStatic
  fun setSeed(context: Context, seed: Int) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putInt(context.getString(R.string.pref_key_seed), seed)
    }
  }

  internal fun getStoredMaxOutputTokens(context: Context): Int =
    PreferenceManager.getDefaultSharedPreferences(context)
      .getInt(context.getString(R.string.pref_key_max_output_tokens), 256)

  @JvmStatic
  fun getMaxOutputTokens(context: Context): Int? {
    if (getUseDefaultConfig(context)) {
      return null
    }
    return getStoredMaxOutputTokens(context)
  }

  @JvmStatic
  fun setMaxOutputTokens(context: Context, maxTokenCount: Int) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putInt(context.getString(R.string.pref_key_max_output_tokens), maxTokenCount)
    }
  }

  internal fun getStoredCandidateCount(context: Context): Int =
    PreferenceManager.getDefaultSharedPreferences(context)
      .getInt(context.getString(R.string.pref_key_candidate_count), 1)

  @JvmStatic
  fun getCandidateCount(context: Context): Int? {
    if (getUseDefaultConfig(context)) {
      return null
    }
    return getStoredCandidateCount(context)
  }

  @JvmStatic
  fun setCandidateCount(context: Context, candidateCount: Int) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putInt(context.getString(R.string.pref_key_candidate_count), candidateCount)
    }
  }

  @JvmStatic
  fun getUseDefaultConfig(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context)
      .getBoolean(context.getString(R.string.pref_key_use_default_config), false)
  }

  @JvmStatic
  fun setUseDefaultConfig(context: Context, useDefaultConfig: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putBoolean(context.getString(R.string.pref_key_use_default_config), useDefaultConfig)
    }
  }

  @JvmStatic
  fun getUseExplicitCache(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context)
      .getBoolean(context.getString(R.string.pref_key_use_explicit_cache), false)
  }

  @JvmStatic
  fun setUseExplicitCache(context: Context, useExplicitCache: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putBoolean(context.getString(R.string.pref_key_use_explicit_cache), useExplicitCache)
    }
  }

  @JvmStatic
  fun getUseStructuredOutput(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context)
      .getBoolean(context.getString(R.string.pref_key_use_structured_output), false)
  }

  @JvmStatic
  fun setUseStructuredOutput(context: Context, useStructuredOutput: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putBoolean(context.getString(R.string.pref_key_use_structured_output), useStructuredOutput)
    }
  }

  @JvmStatic
  fun getUseStreaming(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context)
      .getBoolean(context.getString(R.string.pref_key_use_streaming), true)
  }

  @JvmStatic
  fun setUseStreaming(context: Context, useStreaming: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putBoolean(context.getString(R.string.pref_key_use_streaming), useStreaming)
    }
  }

  @JvmStatic
  fun setEnableThinking(context: Context, enableThinking: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putBoolean(context.getString(R.string.pref_key_enable_thinking), enableThinking)
    }
  }

  @JvmStatic
  fun getEnableThinking(context: Context): Boolean =
    PreferenceManager.getDefaultSharedPreferences(context)
      .getBoolean(context.getString(R.string.pref_key_enable_thinking), false)

  @JvmStatic
  fun setShowThinking(context: Context, showThinking: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putBoolean(context.getString(R.string.pref_key_show_thinking), showThinking)
    }
  }

  @JvmStatic
  fun getShowThinking(context: Context): Boolean =
    PreferenceManager.getDefaultSharedPreferences(context)
      .getBoolean(context.getString(R.string.pref_key_show_thinking), true)
}

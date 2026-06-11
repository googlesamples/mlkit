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

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import android.widget.CheckBox
import android.widget.EditText
import com.google.mlkit.genai.demo.GenerationConfigUtils.getEnableThinking
import com.google.mlkit.genai.demo.GenerationConfigUtils.getShowThinking
import com.google.mlkit.genai.demo.GenerationConfigUtils.getStoredCandidateCount
import com.google.mlkit.genai.demo.GenerationConfigUtils.getStoredMaxOutputTokens
import com.google.mlkit.genai.demo.GenerationConfigUtils.getStoredSeed
import com.google.mlkit.genai.demo.GenerationConfigUtils.getStoredTemperature
import com.google.mlkit.genai.demo.GenerationConfigUtils.getStoredTopK
import com.google.mlkit.genai.demo.GenerationConfigUtils.setCandidateCount
import com.google.mlkit.genai.demo.GenerationConfigUtils.setEnableThinking
import com.google.mlkit.genai.demo.GenerationConfigUtils.setMaxOutputTokens
import com.google.mlkit.genai.demo.GenerationConfigUtils.setSeed
import com.google.mlkit.genai.demo.GenerationConfigUtils.setShowThinking
import com.google.mlkit.genai.demo.GenerationConfigUtils.setTemperature
import com.google.mlkit.genai.demo.GenerationConfigUtils.setTopK
import java.text.NumberFormat
import java.text.ParseException

class GenerationConfigDialog : DialogFragment() {
  interface OnConfigUpdateListener {
    fun onConfigUpdated()
  }

  private lateinit var temperatureEditText: EditText
  private lateinit var topKEditText: EditText
  private lateinit var seedEditText: EditText
  private lateinit var maxOutputTokensEditText: EditText
  private lateinit var candidateCountEditText: EditText
  private lateinit var enableThinkingCheckBox: CheckBox
  private lateinit var showThinkingCheckBox: CheckBox
  private val numberFormat = NumberFormat.getInstance()

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val activity: Activity = requireActivity()
    val builder = AlertDialog.Builder(activity)

    val view = layoutInflater.inflate(R.layout.dialog_generation_config, null)
    temperatureEditText = view.findViewById<EditText>(R.id.temperature_edit_text)
    temperatureEditText.setText(numberFormat.format(getStoredTemperature(activity)))
    topKEditText = view.findViewById<EditText>(R.id.top_k_edit_text)
    topKEditText.setText(numberFormat.format(getStoredTopK(activity)))
    seedEditText = view.findViewById<EditText>(R.id.seed_edit_text)
    seedEditText.setText(numberFormat.format(getStoredSeed(activity)))
    maxOutputTokensEditText = view.findViewById<EditText>(R.id.max_output_tokens_edit_text)
    maxOutputTokensEditText.setText(numberFormat.format(getStoredMaxOutputTokens(activity)))
    candidateCountEditText = view.findViewById<EditText>(R.id.candidate_count_edit_text)
    candidateCountEditText.setText(numberFormat.format(getStoredCandidateCount(activity)))
    enableThinkingCheckBox = view.findViewById<CheckBox>(R.id.enable_thinking_checkbox)
    enableThinkingCheckBox.isChecked = getEnableThinking(activity)
    showThinkingCheckBox = view.findViewById<CheckBox>(R.id.show_thinking_checkbox)
    showThinkingCheckBox.isChecked = getShowThinking(activity)
    showThinkingCheckBox.isEnabled = enableThinkingCheckBox.isChecked
    enableThinkingCheckBox.setOnCheckedChangeListener { _, isChecked ->
      showThinkingCheckBox.isEnabled = isChecked
    }

    builder
      .setView(view)
      .setPositiveButton(R.string.button_save, null)
      .setNegativeButton(R.string.button_cancel, null)
    return builder.create()
  }

  override fun onStart() {
    super.onStart()
    val dialog = dialog as AlertDialog?
    dialog?.let {
      val positiveButton = it.getButton(Dialog.BUTTON_POSITIVE)
      positiveButton.setOnClickListener {
        if (validateInputs()) {
          saveConfig()
          if (activity is OnConfigUpdateListener) {
            (activity as OnConfigUpdateListener).onConfigUpdated()
          }
          dismiss()
        }
      }
    }
  }

  private fun getNumberFromString(text: String): Number? {
    return try {
      numberFormat.parse(text)
    } catch (e: ParseException) {
      null
    }
  }

  private fun validateInputs(): Boolean {
    var isValid = true

    val tempValue = getNumberFromString(temperatureEditText.text.toString())?.toFloat()
    if (tempValue == null || tempValue < 0.0f || tempValue > 1.0f) {
      temperatureEditText.error = "Must be a floating point number between 0.0 and 1.0"
      isValid = false
    }

    val topKValue = getNumberFromString(topKEditText.text.toString())?.toInt()
    if (topKValue == null || topKValue < 1) {
      topKEditText.error = "Must be a positive integer (>0)"
      isValid = false
    }

    val seedValue = getNumberFromString(seedEditText.text.toString())?.toInt()
    if (seedValue == null || seedValue < 0) {
      seedEditText.error = "Must be a non-negative integer (>=0)"
      isValid = false
    }

    val maxTokensValue = getNumberFromString(maxOutputTokensEditText.text.toString())?.toInt()
    if (maxTokensValue == null || maxTokensValue < 1) {
      maxOutputTokensEditText.error = "Must be a positive integer (>0)"
      isValid = false
    }

    val candidateCountValue = getNumberFromString(candidateCountEditText.text.toString())?.toInt()
    if (candidateCountValue == null || candidateCountValue !in 1..8) {
      candidateCountEditText.error = "Must be an integer between 1 and 8 (>=1 and <=8)"
      isValid = false
    }

    return isValid
  }

  private fun saveConfig() {
    val activity = requireActivity()
    val temperature =
      checkNotNull(getNumberFromString(temperatureEditText.text.toString())) {
        "Temperature should not be null after validation"
      }
    setTemperature(activity, temperature.toFloat())

    val topK =
      checkNotNull(getNumberFromString(topKEditText.text.toString())) {
        "TopK should not be null after validation"
      }
    setTopK(activity, topK.toInt())

    val seed =
      checkNotNull(getNumberFromString(seedEditText.text.toString())) {
        "Seed should not be null after validation"
      }
    setSeed(activity, seed.toInt())

    val maxOutputTokens =
      checkNotNull(getNumberFromString(maxOutputTokensEditText.text.toString())) {
        "MaxOutputTokens should not be null after validation"
      }
    setMaxOutputTokens(activity, maxOutputTokens.toInt())

    val candidateCount =
      checkNotNull(getNumberFromString(candidateCountEditText.text.toString())) {
        "CandidateCount should not be null after validation"
      }
    setCandidateCount(activity, candidateCount.toInt())
    setEnableThinking(activity, enableThinkingCheckBox.isChecked)
    setShowThinking(activity, showThinkingCheckBox.isChecked)
  }
}

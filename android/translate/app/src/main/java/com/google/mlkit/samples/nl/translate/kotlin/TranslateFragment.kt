/*
 * Copyright 2019 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.google.mlkit.samples.nl.translate.kotlin

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.ToggleButton
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.samples.nl.translate.R

/***
 * Fragment view for handling translations
 */
class TranslateFragment : Fragment() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(false)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View? {
    return inflater.inflate(R.layout.translate_fragment, container, false)
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val switchButton = view.findViewById<Button>(R.id.buttonSwitchLang)
    val sourceSyncButton = view.findViewById<ToggleButton>(R.id.buttonSyncSource)
    val targetSyncButton = view.findViewById<ToggleButton>(R.id.buttonSyncTarget)
    val srcTextView: TextInputEditText = view.findViewById(R.id.sourceText)
    val targetTextView = view.findViewById<TextView>(R.id.targetText)
    val downloadedModelsTextView = view.findViewById<TextView>(R.id.downloadedModels)
    val sourceLangSelector = view.findViewById<Spinner>(R.id.sourceLangSelector)
    val targetLangSelector = view.findViewById<Spinner>(R.id.targetLangSelector)
    val viewModel = ViewModelProviders.of(this).get(
      TranslateViewModel::class.java
    )

    // Get available language list and set up source and target language spinners
    // with default selections.
    val adapter = ArrayAdapter(
      context!!,
      android.R.layout.simple_spinner_dropdown_item, viewModel.availableLanguages
    )
    sourceLangSelector.adapter = adapter
    targetLangSelector.adapter = adapter
    sourceLangSelector.setSelection(adapter.getPosition(TranslateViewModel.Language("en")))
    targetLangSelector.setSelection(adapter.getPosition(TranslateViewModel.Language("es")))
    sourceLangSelector.onItemSelectedListener = object : OnItemSelectedListener {
      override fun onItemSelected(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long,
      ) {
        setProgressText(targetTextView)
        viewModel.sourceLang.setValue(adapter.getItem(position))
      }

      override fun onNothingSelected(parent: AdapterView<*>?) {
        targetTextView.text = ""
      }
    }
    targetLangSelector.onItemSelectedListener = object : OnItemSelectedListener {
      override fun onItemSelected(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long,
      ) {
        setProgressText(targetTextView)
        viewModel.targetLang.setValue(adapter.getItem(position))
      }

      override fun onNothingSelected(parent: AdapterView<*>?) {
        targetTextView.text = ""
      }
    }
    switchButton.setOnClickListener {
      val targetText = targetTextView.text.toString()
      setProgressText(targetTextView)
      val sourceLangPosition = sourceLangSelector.selectedItemPosition
      sourceLangSelector.setSelection(targetLangSelector.selectedItemPosition)
      targetLangSelector.setSelection(sourceLangPosition)

      // Also update srcTextView with targetText
      srcTextView.setText(targetText)
      viewModel.sourceText.setValue(targetText)
    }

    // Set up toggle buttons to delete or download remote models locally.
    sourceSyncButton.setOnCheckedChangeListener { buttonView, isChecked ->
      val language = adapter.getItem(sourceLangSelector.selectedItemPosition)
      if (isChecked) {
        viewModel.downloadLanguage(language!!)
      } else {
        viewModel.deleteLanguage(language!!)
      }
    }
    targetSyncButton.setOnCheckedChangeListener { buttonView, isChecked ->
      val language = adapter.getItem(targetLangSelector.selectedItemPosition)
      if (isChecked) {
        viewModel.downloadLanguage(language!!)
      } else {
        viewModel.deleteLanguage(language!!)
      }
    }

    // Translate input text as it is typed
    srcTextView.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: Editable) {
        setProgressText(targetTextView)
        viewModel.sourceText.postValue(s.toString())
      }
    })
    viewModel.translatedText.observe(
      viewLifecycleOwner,
      { resultOrError ->
        if (resultOrError.error != null) {
          srcTextView.setError(resultOrError.error!!.localizedMessage)
        } else {
          targetTextView.text = resultOrError.result
        }
      }
    )

    // Update sync toggle button states based on downloaded models list.
    viewModel.availableModels.observe(
      viewLifecycleOwner,
      { translateRemoteModels ->
        val output = context!!.getString(
          R.string.downloaded_models_label,
          translateRemoteModels
        )
        downloadedModelsTextView.text = output

        sourceSyncButton.isChecked = !viewModel.requiresModelDownload(
          adapter.getItem(sourceLangSelector.selectedItemPosition)!!,
          translateRemoteModels)
        targetSyncButton.isChecked = !viewModel.requiresModelDownload(
          adapter.getItem(targetLangSelector.selectedItemPosition)!!,
          translateRemoteModels)
      }
    )
  }

  private fun setProgressText(tv: TextView) {
    tv.text = context!!.getString(R.string.translate_progress)
  }

  companion object {
    fun newInstance(): TranslateFragment {
      return TranslateFragment()
    }
  }
}

/*
 * Copyright 2020 Google LLC. All rights reserved.
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

package com.google.mlkit.samples.nl.languageid.kotlin

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.samples.nl.languageid.R

/** Default launcher activity. */
class MainActivityKotlin : AppCompatActivity() {
  private lateinit var textBox: EditText
  private lateinit var language: TextView
  private lateinit var input: TextView
  private lateinit var languageIdentifier: LanguageIdentifier

  companion object {
    private const val TAG = "MyActivity"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    languageIdentifier = LanguageIdentification.getClient()
    lifecycle.addObserver(languageIdentifier)
    textBox = findViewById(R.id.text_input)

    input = findViewById(R.id.last_input)
    language = findViewById(R.id.text_output)
    findViewById<Button>(R.id.button).setOnClickListener { _ ->
      hideKeyboard()
      val input = getInputText()
      if (input.isEmpty()) {
        return@setOnClickListener
      }

      identifyLanguage(input)
    }
    findViewById<Button>(R.id.button_possible).setOnClickListener { _ ->
      hideKeyboard()
      val input = getInputText()
      if (input.isEmpty()) {
        return@setOnClickListener
      }

      identifyPossibleLanguages(input)
    }
    findViewById<Button>(R.id.clear_text).setOnClickListener { _ -> textBox.text.clear() }
  }

  private fun hideKeyboard() {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val view = getCurrentFocus()
    if (inputMethodManager != null && view != null) {
      inputMethodManager.hideSoftInputFromWindow(
        view.getWindowToken(),
        InputMethodManager.HIDE_NOT_ALWAYS
      )
    }
  }

  private fun getInputText(): String {
    val input = textBox.getText().toString()
    if (input.isEmpty()) {
      Toast.makeText(this@MainActivityKotlin, R.string.empty_text_message, Toast.LENGTH_LONG).show()
      return input
    }
    return input
  }

  /**
   * Identify a language.
   *
   * @param inputText Input string to find language of.
   */
  private fun identifyLanguage(inputText: String) {
    language.text = getString(R.string.wait_message)
    languageIdentifier
      .identifyLanguage(inputText)
      .addOnSuccessListener { identifiedLanguage ->
        input.text = getString(R.string.input, inputText)
        language.text = getString(R.string.language, identifiedLanguage)
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Language identification error", e)
        input.text = getString(R.string.input, inputText)
        language.text = ""
        Toast.makeText(
            this@MainActivityKotlin,
            getString(R.string.language_id_error) +
              "\nError: " +
              e.getLocalizedMessage() +
              "\nCause: " +
              e.cause,
            Toast.LENGTH_LONG
          )
          .show()
      }
  }

  /**
   * Identify all possible languages.
   *
   * @param inputText Input string to find language of.
   */
  private fun identifyPossibleLanguages(inputText: String) {
    language.text = getString(R.string.wait_message)
    languageIdentifier
      .identifyPossibleLanguages(inputText)
      .addOnSuccessListener { identifiedLanguages ->
        input.text = getString(R.string.input, inputText)

        var output = ""
        for (identifiedLanguage in identifiedLanguages) {
          output += identifiedLanguage.languageTag + " (" + identifiedLanguage.confidence + "), "
        }
        language.text = getString(R.string.language, output.substring(0, output.length - 2))
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Language identification error", e)
        input.text = getString(R.string.input, inputText)
        language.text = ""
        Toast.makeText(
            this@MainActivityKotlin,
            getString(R.string.language_id_error) +
              "\nError: " +
              e.getLocalizedMessage() +
              "\nCause: " +
              e.cause,
            Toast.LENGTH_LONG
          )
          .show()
      }
  }
}

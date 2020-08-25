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

package com.google.mlkit.samples.languageid.kotlin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.samples.languageid.R
import kotlinx.android.synthetic.main.activity_langid_main.buttonIdAll
import kotlinx.android.synthetic.main.activity_langid_main.buttonIdLanguage
import kotlinx.android.synthetic.main.activity_langid_main.inputText
import java.util.ArrayList
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var outputText: TextView? = null
    private lateinit var languageIdentification: LanguageIdentifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_langid_main)

        outputText = findViewById(R.id.outputText)

        languageIdentification = LanguageIdentification.getClient()
        // Any new instances of LanguageIdentification needs to be closed appropriately.
        // LanguageIdentification automatically calls close() on the ON_DESTROY lifecycle event,
        // so here we can add our languageIdentification instance as a LifecycleObserver for this
        // activity and have it be closed when this activity is destroyed.
        lifecycle.addObserver(languageIdentification)

        buttonIdLanguage.setOnClickListener {
            val input = inputText.text?.toString()
            input?.let {
                inputText.text?.clear()
                identifyLanguage(it)
            }
        }

        buttonIdAll.setOnClickListener {
            val input = inputText.text?.toString()
            input?.let {
                inputText.text?.clear()
                identifyPossibleLanguages(input)
            }
        }
    }

    private fun identifyPossibleLanguages(inputText: String) {
        languageIdentification
            .identifyPossibleLanguages(inputText)
            .addOnSuccessListener(this@MainActivity) { identifiedLanguages ->
                val detectedLanguages = ArrayList<String>(identifiedLanguages.size)
                for (language in identifiedLanguages) {
                    detectedLanguages.add(
                        String.format(
                            Locale.US,
                            "%s (%3f)",
                            language.languageTag,
                            language.confidence
                        )
                    )
                }
                outputText?.append(
                    String.format(
                        Locale.US,
                        "\n%s - [%s]",
                        inputText,
                        TextUtils.join(", ", detectedLanguages)
                    )
                )
            }
            .addOnFailureListener(this@MainActivity) { e ->
                Log.e(TAG, "Language identification error", e)
                Toast.makeText(
                    this@MainActivity, R.string.language_id_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun identifyLanguage(inputText: String) {
        languageIdentification
            .identifyLanguage(inputText)
            .addOnSuccessListener(this@MainActivity) { s ->
                outputText?.append(
                    String.format(
                        Locale.US,
                        "\n%s - %s",
                        inputText,
                        s
                    )
                )
            }
            .addOnFailureListener(this@MainActivity) { e ->
                Log.e(TAG, "Language identification error", e)
                Toast.makeText(
                    this@MainActivity, R.string.language_id_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

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

package com.google.mlkit.samples.nl.languageid.java;

import android.content.Context;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mlkit.nl.languageid.IdentifiedLanguage;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.samples.nl.languageid.R;

/** Default launcher activity. */
public class MainActivityJava extends AppCompatActivity {

  private static final String TAG = "MyActivity";

  private EditText textBox;
  private TextView language;
  private TextView input;
  private LanguageIdentifier languageIdentifier;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    languageIdentifier = LanguageIdentification.getClient();
    getLifecycle().addObserver(languageIdentifier);
    textBox = findViewById(R.id.text_input);
    input = findViewById(R.id.last_input);
    language = findViewById(R.id.text_output);

    findViewById(R.id.button)
        .setOnClickListener(
            v -> {
              hideKeyboard();
              String input = getInputText();
              if (input.isEmpty()) {
                return;
              }

              identifyLanguage(input);
            });

    findViewById(R.id.button_possible)
        .setOnClickListener(
            v -> {
              hideKeyboard();
              String input = getInputText();
              if (input.isEmpty()) {
                return;
              }

              identifyPossibleLanguages(input);
            });

    findViewById(R.id.clear_text).setOnClickListener(v -> textBox.getText().clear());
  }

  private void hideKeyboard() {
    InputMethodManager inputMethodManager =
        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    View view = getCurrentFocus();
    if (inputMethodManager != null && view != null) {
      inputMethodManager.hideSoftInputFromWindow(
          view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }
  }

  private String getInputText() {
    String input = textBox.getText().toString();
    if (input.isEmpty()) {
      Toast.makeText(MainActivityJava.this, R.string.empty_text_message, Toast.LENGTH_LONG).show();
      return input;
    }
    return input;
  }

  /**
   * Identify a language.
   *
   * @param inputText Input string to find language of.
   */
  private void identifyLanguage(final String inputText) {
    language.setText(R.string.wait_message);

    languageIdentifier
        .identifyLanguage(inputText)
        .addOnSuccessListener(
            identifiedLanguage -> {
              input.setText(getString(R.string.input, inputText));
              language.setText(getString(R.string.language, identifiedLanguage));
            })
        .addOnFailureListener(
            e -> {
              Log.e(TAG, "Language identification error", e);
              input.setText(getString(R.string.input, inputText));
              language.setText("");
              Toast.makeText(
                      MainActivityJava.this,
                      getString(R.string.language_id_error)
                          + "\nError: "
                          + e.getLocalizedMessage()
                          + "\nCause: "
                          + e.getCause(),
                      Toast.LENGTH_LONG)
                  .show();
            });
  }

  /**
   * Identify all possible languages.
   *
   * @param inputText Input string to find language of.
   */
  private void identifyPossibleLanguages(final String inputText) {
    language.setText(R.string.wait_message);

    languageIdentifier
        .identifyPossibleLanguages(inputText)
        .addOnSuccessListener(
            identifiedLanguages -> {
              input.setText(getString(R.string.input, inputText));

              String output = "";
              for (IdentifiedLanguage identifiedLanguage : identifiedLanguages) {
                output +=
                    identifiedLanguage.getLanguageTag()
                        + " ("
                        + identifiedLanguage.getConfidence()
                        + "), ";
              }
              language.setText(
                  getString(R.string.language, output.substring(0, output.length() - 2)));
            })
        .addOnFailureListener(
            e -> {
              Log.e(TAG, "Language identification error", e);
              input.setText(getString(R.string.input, inputText));
              language.setText("");
              Toast.makeText(
                      MainActivityJava.this,
                      getString(R.string.language_id_error)
                          + "\nError: "
                          + e.getLocalizedMessage()
                          + "\nCause: "
                          + e.getCause(),
                      Toast.LENGTH_LONG)
                  .show();
            });
  }
}

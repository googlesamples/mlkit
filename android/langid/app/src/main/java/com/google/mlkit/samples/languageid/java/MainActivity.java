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

package com.google.mlkit.samples.languageid.java;

import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.textfield.TextInputEditText;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.nl.languageid.IdentifiedLanguage;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.samples.languageid.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private TextView outputText;
    private static final String TAG = "MainActivity";
    private LanguageIdentifier languageIdentification;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_langid_main);

        final TextInputEditText inputText = findViewById(R.id.inputText);
        Button idLanguageButton = findViewById(R.id.buttonIdLanguage);
        Button findAllButton = findViewById(R.id.buttonIdAll);
        outputText = findViewById(R.id.outputText);

        languageIdentification = LanguageIdentification.getClient();
        // Any new instances of LanguageIdentification needs to be closed appropriately.
        // LanguageIdentification automatically calls close() on the ON_DESTROY lifecycle event,
        // so here we can add our languageIdentification instance as a LifecycleObserver for this
        // activity and have it be closed when this activity is destroyed.
        getLifecycle().addObserver(languageIdentification);

        idLanguageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input = inputText.getText().toString();
                if (input.isEmpty()) {
                    return;
                }
                inputText.getText().clear();
                identifyLanguage(input);
            }
        });

        findAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input = inputText.getText().toString();
                if (input.isEmpty()) {
                    return;
                }
                inputText.getText().clear();
                identifyPossibleLanguages(input);
            }
        });
    }

    private void identifyPossibleLanguages(final String inputText) {
        languageIdentification = LanguageIdentification.getClient();
        languageIdentification
                .identifyPossibleLanguages(inputText)
                .addOnSuccessListener(
                        this,
                        new OnSuccessListener<List<IdentifiedLanguage>>() {
                            @Override
                            public void onSuccess(List<IdentifiedLanguage> identifiedLanguages) {
                                List<String> detectedLanguages =
                                        new ArrayList<>(identifiedLanguages.size());
                                for (IdentifiedLanguage language : identifiedLanguages) {
                                    detectedLanguages.add(
                                            String.format(
                                                    Locale.US,
                                                    "%s (%3f)",
                                                    language.getLanguageTag(),
                                                    language.getConfidence())
                                    );
                                }
                                outputText.append(
                                        String.format(
                                                Locale.US,
                                                "\n%s - [%s]",
                                                inputText,
                                                TextUtils.join(", ", detectedLanguages)));
                            }
                        })
                .addOnFailureListener(
                        this,
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "Language identification error", e);
                                Toast.makeText(
                                        MainActivity.this, R.string.language_id_error,
                                        Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
    }

    private void identifyLanguage(final String inputText) {
        languageIdentification
                .identifyLanguage(inputText)
                .addOnSuccessListener(
                        this,
                        new OnSuccessListener<String>() {
                            @Override
                            public void onSuccess(String s) {
                                outputText.append(
                                        String.format(
                                                Locale.US,
                                                "\n%s - %s",
                                                inputText,
                                                s));
                            }
                        })
                .addOnFailureListener(
                        this,
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "Language identification error", e);
                                Toast.makeText(
                                        MainActivity.this, R.string.language_id_error,
                                        Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
    }
}

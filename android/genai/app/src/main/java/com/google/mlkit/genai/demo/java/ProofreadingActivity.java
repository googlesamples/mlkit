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

package com.google.mlkit.genai.demo.java;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

import android.os.Bundle;
import androidx.annotation.Nullable;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.StreamingCallback;
import com.google.mlkit.genai.demo.R;
import com.google.mlkit.genai.proofreading.Proofreader;
import com.google.mlkit.genai.proofreading.ProofreaderOptions;
import com.google.mlkit.genai.proofreading.ProofreaderOptions.InputType;
import com.google.mlkit.genai.proofreading.ProofreaderOptions.Language;
import com.google.mlkit.genai.proofreading.Proofreading;
import com.google.mlkit.genai.proofreading.ProofreadingRequest;
import com.google.mlkit.genai.proofreading.ProofreadingResult;
import com.google.mlkit.genai.proofreading.ProofreadingSuggestion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Demonstrates the Proofreading API usage. */
public class ProofreadingActivity extends TextInputBasedActivity {
  private static final String TAG = ProofreadingActivity.class.getSimpleName();

  private int inputType = InputType.KEYBOARD;
  private int language = Language.ENGLISH;
  private Proofreader proofreader;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setUpSpinners();
    initProofreader();
  }

  @Override
  protected int getLayoutResId() {
    return R.layout.activity_proofreading;
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (proofreader != null) {
      proofreader.close();
    }
  }

  @Override
  protected ListenableFuture<String> getBaseModelName() {
    return proofreader.getBaseModelName();
  }

  @Override
  @FeatureStatus
  protected ListenableFuture<Integer> checkFeatureStatus() {
    return proofreader.checkFeatureStatus();
  }

  @Override
  protected ListenableFuture<Void> downloadFeature(DownloadCallback callback) {
    return proofreader.downloadFeature(callback);
  }

  @Override
  protected ListenableFuture<List<String>> runInferenceImpl(
      String request, @Nullable StreamingCallback streamingCallback) {
    ProofreadingRequest proofreadingRequest = ProofreadingRequest.builder(request).build();
    return Futures.transform(
        streamingCallback != null
            ? proofreader.runInference(proofreadingRequest, streamingCallback)
            : proofreader.runInference(proofreadingRequest),
        proofreadingResult ->
            requireNonNull(proofreadingResult).getResults().stream()
                .map(ProofreadingSuggestion::getText)
                .collect(toImmutableList()),
        ContextCompat.getMainExecutor(this));
  }

  private void setUpSpinners() {
    setupSpinner(
        R.id.input_type_spinner,
        R.array.proofreading_input_types,
        position -> {
          if (position == 1) {
            inputType = InputType.VOICE;
          } else {
            inputType = InputType.KEYBOARD;
          }
          initProofreader();
        });

    setupSpinner(
        R.id.language_spinner,
        R.array.proofreading_languages,
        position -> {
          switch (position) {
            case 1 -> language = Language.JAPANESE;
            case 2 -> language = Language.GERMAN;
            case 3 -> language = Language.FRENCH;
            case 4 -> language = Language.ITALIAN;
            case 5 -> language = Language.SPANISH;
            case 6 -> language = Language.KOREAN;
            default -> language = Language.ENGLISH;
          }
          initProofreader();
        });
  }

  private void initProofreader() {
    if (proofreader != null) {
      proofreader.close();
    }
    ProofreaderOptions options =
        ProofreaderOptions.builder(this).setInputType(inputType).setLanguage(language).build();
    proofreader = Proofreading.getClient(options);
    resetProcessor();
  }

  @Override
  protected List<String> runInferenceForBatchTask(String request) {
    try {
      List<String> outputColumns = new ArrayList<>();
      ProofreadingResult result =
          proofreader.runInference(ProofreadingRequest.builder(request).build()).get();
      for (ProofreadingSuggestion suggestion : result.getResults()) {
        outputColumns.add(suggestion.getText());
      }
      return outputColumns;
    } catch (ExecutionException | InterruptedException e) {
      Log.e(TAG, "Failed to run inference.", e);
      return Arrays.asList("Failed to run inference: " + e.getMessage(), "0");
    }
  }
}

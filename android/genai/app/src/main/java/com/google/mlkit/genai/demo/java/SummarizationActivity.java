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

import static java.util.Objects.requireNonNull;

import android.os.Bundle;
import androidx.annotation.Nullable;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.StreamingCallback;
import com.google.mlkit.genai.demo.R;
import com.google.mlkit.genai.summarization.Summarization;
import com.google.mlkit.genai.summarization.SummarizationRequest;
import com.google.mlkit.genai.summarization.SummarizationResult;
import com.google.mlkit.genai.summarization.Summarizer;
import com.google.mlkit.genai.summarization.SummarizerOptions;
import com.google.mlkit.genai.summarization.SummarizerOptions.InputType;
import com.google.mlkit.genai.summarization.SummarizerOptions.Language;
import com.google.mlkit.genai.summarization.SummarizerOptions.OutputType;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Demonstrates the Summarization API usage. */
public class SummarizationActivity extends TextInputBasedActivity {
  private static final String TAG = SummarizationActivity.class.getSimpleName();

  private int inputType = InputType.ARTICLE;
  private int outputType = OutputType.ONE_BULLET;
  private int language = Language.ENGLISH;
  private Summarizer summarizer;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setUpInputAndOutputTypeSpinners();
    initSummarizer();
  }

  @Override
  protected int getLayoutResId() {
    return R.layout.activity_summarization;
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (summarizer != null) {
      summarizer.close();
    }
  }

  @Override
  protected ListenableFuture<String> getBaseModelName() {
    return summarizer.getBaseModelName();
  }

  @Override
  @FeatureStatus
  protected ListenableFuture<Integer> checkFeatureStatus() {
    return summarizer.checkFeatureStatus();
  }

  @Override
  protected ListenableFuture<Void> downloadFeature(DownloadCallback callback) {
    return summarizer.downloadFeature(callback);
  }

  @Override
  protected ListenableFuture<List<String>> runInferenceImpl(
      String request, @Nullable StreamingCallback streamingCallback) {
    SummarizationRequest summarizationRequest = SummarizationRequest.builder(request).build();
    return Futures.transform(
        streamingCallback != null
            ? summarizer.runInference(summarizationRequest, streamingCallback)
            : summarizer.runInference(summarizationRequest),
        result -> ImmutableList.of(requireNonNull(result).getSummary()),
        ContextCompat.getMainExecutor(this));
  }

  private void setUpInputAndOutputTypeSpinners() {
    setupSpinner(
        R.id.input_type_spinner,
        R.array.summarization_input_types,
        position -> {
          if (position == 1) {
            inputType = InputType.CONVERSATION;
          } else {
            inputType = InputType.ARTICLE;
          }
          initSummarizer();
        });

    setupSpinner(
        R.id.output_type_spinner,
        R.array.summarization_output_types,
        position -> {
          switch (position) {
            case 1 -> outputType = OutputType.TWO_BULLETS;
            case 2 -> outputType = OutputType.THREE_BULLETS;
            default -> outputType = OutputType.ONE_BULLET;
          }
          initSummarizer();
        });

    setupSpinner(
        R.id.language_spinner,
        R.array.summarization_languages,
        position -> {
          switch (position) {
            case 1 -> language = Language.JAPANESE;
            case 2 -> language = Language.KOREAN;
            default -> language = Language.ENGLISH;
          }
          initSummarizer();
        });
  }

  private void initSummarizer() {
    if (summarizer != null) {
      summarizer.close();
    }
    SummarizerOptions options =
        SummarizerOptions.builder(this)
            .setInputType(inputType)
            .setOutputType(outputType)
            .setLanguage(language)
            .build();
    summarizer = Summarization.getClient(options);
    resetProcessor();
  }

  @Override
  protected List<String> runInferenceForBatchTask(String request) {
    try {
      SummarizationResult result =
          summarizer.runInference(SummarizationRequest.builder(request).build()).get();
      return Arrays.asList(result.getSummary());
    } catch (ExecutionException | InterruptedException e) {
      Log.e(TAG, "Failed to run inference.", e);
      return Arrays.asList("Failed to run inference: " + e.getMessage(), "0");
    }
  }
}

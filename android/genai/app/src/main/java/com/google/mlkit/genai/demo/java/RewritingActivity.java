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
import com.google.mlkit.genai.rewriting.Rewriter;
import com.google.mlkit.genai.rewriting.RewriterOptions;
import com.google.mlkit.genai.rewriting.RewriterOptions.Language;
import com.google.mlkit.genai.rewriting.RewriterOptions.OutputType;
import com.google.mlkit.genai.rewriting.Rewriting;
import com.google.mlkit.genai.rewriting.RewritingRequest;
import com.google.mlkit.genai.rewriting.RewritingResult;
import com.google.mlkit.genai.rewriting.RewritingSuggestion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Demonstrates the Rewriting API usage. */
public class RewritingActivity extends TextInputBasedActivity {
  private static final String TAG = RewritingActivity.class.getSimpleName();

  private int outputType = OutputType.ELABORATE;
  private int language = Language.ENGLISH;
  private Rewriter rewriter;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setUpSpinners();
    initRewriter();
  }

  @Override
  protected int getLayoutResId() {
    return R.layout.activity_rewrite;
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (rewriter != null) {
      rewriter.close();
    }
  }

  @Override
  protected ListenableFuture<String> getBaseModelName() {
    return rewriter.getBaseModelName();
  }

  @Override
  @FeatureStatus
  protected ListenableFuture<Integer> checkFeatureStatus() {
    return rewriter.checkFeatureStatus();
  }

  @Override
  protected ListenableFuture<Void> downloadFeature(DownloadCallback callback) {
    return rewriter.downloadFeature(callback);
  }

  @Override
  protected ListenableFuture<List<String>> runInferenceImpl(
      String request, @Nullable StreamingCallback streamingCallback) {
    RewritingRequest rewritingRequest = RewritingRequest.builder(request).build();
    return Futures.transform(
        streamingCallback != null
            ? rewriter.runInference(rewritingRequest, streamingCallback)
            : rewriter.runInference(rewritingRequest),
        rewriteResult ->
            requireNonNull(rewriteResult).getResults().stream()
                .map(RewritingSuggestion::getText)
                .collect(toImmutableList()),
        ContextCompat.getMainExecutor(this));
  }

  private void setUpSpinners() {
    setupSpinner(
        R.id.output_type_spinner,
        R.array.rewriting_output_types,
        position -> {
          switch (position) {
            case 1 -> outputType = OutputType.EMOJIFY;
            case 2 -> outputType = OutputType.SHORTEN;
            case 3 -> outputType = OutputType.FRIENDLY;
            case 4 -> outputType = OutputType.PROFESSIONAL;
            case 5 -> outputType = OutputType.REPHRASE;
            default -> outputType = OutputType.ELABORATE;
          }
          initRewriter();
        });

    setupSpinner(
        R.id.language_spinner,
        R.array.rewriting_languages,
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
          initRewriter();
        });
  }

  private void initRewriter() {
    if (rewriter != null) {
      rewriter.close();
    }
    RewriterOptions options =
        RewriterOptions.builder(this).setOutputType(outputType).setLanguage(language).build();
    rewriter = Rewriting.getClient(options);
    resetProcessor();
  }

  @Override
  protected List<String> runInferenceForBatchTask(String request) {
    try {
      List<String> outputColumns = new ArrayList<>();
      RewritingResult result =
          rewriter.runInference(RewritingRequest.builder(request).build()).get();
      for (RewritingSuggestion suggestion : result.getResults()) {
        outputColumns.add(suggestion.getText());
      }
      return outputColumns;
    } catch (ExecutionException | InterruptedException e) {
      Log.e(TAG, "Failed to run inference.", e);
      return Arrays.asList("Failed to run inference: " + e.getMessage(), "0");
    }
  }
}

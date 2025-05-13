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

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.GetContent;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.demo.ContentAdapter;
import com.google.mlkit.genai.demo.R;
import com.google.mlkit.genai.imagedescription.ImageDescriber;
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions;
import com.google.mlkit.genai.imagedescription.ImageDescription;
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest;
import com.google.mlkit.genai.imagedescription.ImageDescriptionResult;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

/** Demonstrates the Image Description API usage. */
public class ImageDescriptionActivity extends AppCompatActivity {
  private static final String TAG = ImageDescriptionActivity.class.getSimpleName();
  private static final String STREAMING_INDICATOR = "STREAMING...\n";
  private static final long MEGABYTE = 1024 * 1024L;

  private Button selectImageButton;
  private TextView debugInfoTextView;

  private ImageDescriber imageDescriber;
  private boolean streaming = true;
  private boolean hasFirstStreamingResult;
  private long firstTokenLatency;
  private boolean modelDownloaded;
  private long totalBytesToDownload;

  private ActivityResultLauncher<String> selectImageLauncher;

  private final ContentAdapter contentAdapter = new ContentAdapter();

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_image_description);

    selectImageLauncher =
        registerForActivityResult(
            new GetContent(),
            imageUri -> {
              if (imageUri != null) {
                contentAdapter.addContent(ContentAdapter.VIEW_TYPE_REQUEST_IMAGE, imageUri);
                startGeneratingUi();
                if (modelDownloaded) {
                  describe(imageUri);
                } else {
                  checkFeatureStatus(imageUri);
                }
              } else {
                Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
              }
            });

    selectImageButton = findViewById(R.id.select_image_button);
    selectImageButton.setOnClickListener(view -> selectImageLauncher.launch("image/*"));
    debugInfoTextView = findViewById(R.id.debug_info_text_view);

    RecyclerView contentRecyclerView = findViewById(R.id.content_recycler_view);
    contentRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    contentRecyclerView.setAdapter(contentAdapter);

    initImageDescriptor();

    Futures.addCallback(
        imageDescriber.getBaseModelName(),
        new FutureCallback<String>() {
          @Override
          public void onSuccess(String result) {
            debugInfoTextView.setVisibility(View.VISIBLE);
            debugInfoTextView.setText(getString(R.string.base_model_name, result));
          }

          @Override
          public void onFailure(Throwable t) {
            Log.e(TAG, "Failed to get base model name.", t);
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuItem item = menu.findItem(R.id.action_batch_run);
    if (item != null) {
      item.setVisible(false);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.action_streaming) {
      streaming = !streaming;
      item.setChecked(streaming);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (imageDescriber != null) {
      imageDescriber.close();
    }
  }

  private void checkFeatureStatus(Uri imageUri) {
    Futures.addCallback(
        imageDescriber.checkFeatureStatus(),
        new FutureCallback<>() {
          @Override
          public void onSuccess(Integer featureStatus) {
            switch (featureStatus) {
              case FeatureStatus.AVAILABLE -> describe(imageUri);
              case FeatureStatus.UNAVAILABLE -> displayErrorMessage("Feature is unavailable.");
              default -> downloadAndDescribe(imageUri);
            }
          }

          @Override
          public void onFailure(@NonNull Throwable t) {
            Log.e(TAG, "Failed to check status.", t);
            displayErrorMessage("Failed to check status: " + t);
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  private void downloadAndDescribe(Uri imageUri) {
    Futures.addCallback(
        imageDescriber.downloadFeature(
            new DownloadCallback() {
              @Override
              public void onDownloadStarted(long bytesToDownload) {
                totalBytesToDownload = bytesToDownload;
              }

              @Override
              public void onDownloadFailed(@NonNull GenAiException e) {
                displayErrorMessage("Failed to download model: " + e);
              }

              @Override
              public void onDownloadProgress(long totalBytesDownloaded) {
                if (totalBytesToDownload > 0) {
                  debugInfoTextView.setVisibility(View.VISIBLE);
                  debugInfoTextView.setText(
                      String.format(
                          Locale.ENGLISH,
                          "Downloading model:  %d / %d MB (%.2f%%)",
                          totalBytesDownloaded / MEGABYTE,
                          totalBytesToDownload / MEGABYTE,
                          100.0 * totalBytesDownloaded / totalBytesToDownload));
                }
              }

              @Override
              public void onDownloadCompleted() {
                modelDownloaded = true;
                describe(imageUri);
              }
            }),
        new FutureCallback<>() {
          @Override
          public void onSuccess(Void result) {}

          @Override
          public void onFailure(@NonNull Throwable t) {
            Log.e(TAG, "Failed to download feature.", t);
            displayErrorMessage("Failed to download feature: " + t);
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  private void describe(Uri imageUri) {
    try {
      Bitmap bitmap =
          ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), imageUri));
      long startMs = System.currentTimeMillis();
      if (streaming) {
        firstTokenLatency = 0;
        hasFirstStreamingResult = false;
        SpannableStringBuilder resultBuilder = new SpannableStringBuilder(STREAMING_INDICATOR);
        resultBuilder.setSpan(
            new StyleSpan(Typeface.BOLD),
            0,
            STREAMING_INDICATOR.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        Futures.addCallback(
            imageDescriber.runInference(
                ImageDescriptionRequest.builder(bitmap).build(),
                additionalText ->
                    runOnUiThread(
                        () -> {
                          resultBuilder.append(additionalText);
                          if (hasFirstStreamingResult) {
                            contentAdapter.updateStreamingResponse(
                                new SpannedString(resultBuilder));
                          } else {
                            contentAdapter.addContent(
                                ContentAdapter.VIEW_TYPE_RESPONSE,
                                new SpannedString(resultBuilder));
                            hasFirstStreamingResult = true;
                            firstTokenLatency = Instant.now().minusMillis(startMs).toEpochMilli();
                          }
                        })),
            new FutureCallback<>() {
              @Override
              public void onSuccess(ImageDescriptionResult result) {
                contentAdapter.addContent(
                    ContentAdapter.VIEW_TYPE_RESPONSE, result.getDescription());
                long totalLatency = Instant.now().minusMillis(startMs).toEpochMilli();
                String debugInfo =
                    getString(R.string.debug_info_streaming, firstTokenLatency, totalLatency);
                endGeneratingUi(debugInfo);
              }

              @Override
              public void onFailure(@NonNull Throwable t) {
                Log.d(TAG, "Streaming result so far:\n" + resultBuilder);
                Log.e(TAG, "Failed to run inference.", t);
                displayErrorMessage("Failed to run inference: " + t);
              }
            },
            ContextCompat.getMainExecutor(this));
      } else {
        Futures.addCallback(
            imageDescriber.runInference(ImageDescriptionRequest.builder(bitmap).build()),
            new FutureCallback<>() {
              @Override
              public void onSuccess(ImageDescriptionResult result) {
                contentAdapter.addContent(
                    ContentAdapter.VIEW_TYPE_RESPONSE, result.getDescription());
                String debugInfo =
                    getString(
                        R.string.debug_info, Instant.now().minusMillis(startMs).toEpochMilli());
                endGeneratingUi(debugInfo);
              }

              @Override
              public void onFailure(@NonNull Throwable t) {
                Log.e(TAG, "Failed to run inference.", t);
                displayErrorMessage("Failed to run inference: " + t);
              }
            },
            ContextCompat.getMainExecutor(this));
      }
    } catch (IOException e) {
      Log.e(TAG, "Failed to decode image uri.", e);
      displayErrorMessage("Failed to decode image uri." + e);
    }
  }

  private void displayErrorMessage(String errorMessage) {
    contentAdapter.addContent(ContentAdapter.VIEW_TYPE_RESPONSE_ERROR, errorMessage);
    endGeneratingUi(getString(R.string.empty));
  }

  private void startGeneratingUi() {
    selectImageButton.setEnabled(false);
    selectImageButton.setText(R.string.generating);
    debugInfoTextView.setVisibility(View.GONE);
  }

  private void endGeneratingUi(String debugInfo) {
    selectImageButton.setEnabled(true);
    selectImageButton.setText(R.string.button_select_image);
    debugInfoTextView.setText(debugInfo);
    debugInfoTextView.setVisibility(debugInfo.isEmpty() ? View.GONE : View.VISIBLE);
  }

  private void initImageDescriptor() {
    if (imageDescriber != null) {
      imageDescriber.close();
    }
    ImageDescriberOptions options = ImageDescriberOptions.builder(this).build();
    imageDescriber = ImageDescription.getClient(options);
    modelDownloaded = false;
  }
}

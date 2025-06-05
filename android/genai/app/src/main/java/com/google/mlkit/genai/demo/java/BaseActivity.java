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

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument;
import androidx.activity.result.contract.ActivityResultContracts.GetContent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.common.StreamingCallback;
import com.google.mlkit.genai.demo.ContentAdapter;
import com.google.mlkit.genai.demo.ContentItem;
import com.google.mlkit.genai.demo.ContentItem.TextItem;
import com.google.mlkit.genai.demo.R;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Base Activity for ML Kit GenAI APIs. */
abstract class BaseActivity<RequestT extends ContentItem> extends AppCompatActivity {

  private static final String TAG = BaseActivity.class.getSimpleName();
  private static final long MEGABYTE = 1024 * 1024L;
  private static final long MIN_INFERENCE_INTERVAL_MS = 6000;

  private TextView debugInfoTextView;

  private boolean modelDownloaded;
  private long totalBytesToDownload;

  private boolean streaming = true;
  private boolean hasFirstStreamingResult;
  private long firstTokenLatency;

  @Nullable private Uri batchInputUri;
  private boolean batchRunCancelled;
  private int batchProcessingIndex;

  private ActivityResultLauncher<String> createBatchOutputFileLauncher;
  private ActivityResultLauncher<String> chooseBatchInputLauncher;

  private final ContentAdapter contentAdapter = new ContentAdapter();

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(getLayoutResId());

    debugInfoTextView = findViewById(R.id.debug_info_text_view);

    RecyclerView contentRecyclerView = findViewById(R.id.content_recycler_view);
    contentRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    contentRecyclerView.setAdapter(contentAdapter);

    createBatchOutputFileLauncher =
        registerForActivityResult(
            new CreateDocument("text/csv"),
            batchOutputUri -> {
              if (batchInputUri != null && batchOutputUri != null) {
                batchRun(batchInputUri, batchOutputUri);
              }
            });
    chooseBatchInputLauncher =
        registerForActivityResult(
            new GetContent(),
            uri -> {
              if (uri != null) {
                batchInputUri = uri;
                SimpleDateFormat dateFormat =
                    new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault());
                String outputFileName =
                    "mlkit_genai_result_" + dateFormat.format(new Date()) + ".csv";
                createBatchOutputFileLauncher.launch(outputFileName);
              }
            });
  }

  @Override
  protected void onPostCreate(@Nullable Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    Futures.addCallback(
        getBaseModelName(),
        new FutureCallback<String>() {
          @Override
          public void onSuccess(String result) {
            debugInfoTextView.setVisibility(View.VISIBLE);
            debugInfoTextView.setText(getString(R.string.base_model_name, result));
          }

          @Override
          public void onFailure(@NonNull Throwable t) {
            Log.e(TAG, "Failed to get base model name.", t);
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  protected void onSend(RequestT request) {
    contentAdapter.addContent(request);
    startGeneratingUi();
    if (modelDownloaded) {
      runInference(request);
    } else {
      checkFeatureStatus(request);
    }
  }

  protected abstract int getLayoutResId();

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.action_streaming) {
      streaming = !streaming;
      item.setChecked(streaming);
      return true;
    } else if (item.getItemId() == R.id.action_batch_run) {
      chooseBatchInputLauncher.launch("text/csv");
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  private void checkFeatureStatus(RequestT request) {
    Futures.addCallback(
        checkFeatureStatus(),
        new FutureCallback<>() {
          @Override
          public void onSuccess(Integer featureStatus) {
            switch (featureStatus) {
              case FeatureStatus.AVAILABLE -> {
                modelDownloaded = true;
                runInference(request);
              }
              case FeatureStatus.UNAVAILABLE -> displayErrorMessage("Feature is unavailable.");
              default -> downloadAndRunInference(request);
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

  @FeatureStatus
  protected abstract ListenableFuture<Integer> checkFeatureStatus();

  protected abstract ListenableFuture<String> getBaseModelName();

  private void downloadAndRunInference(RequestT request) {
    Futures.addCallback(
        downloadFeature(
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
                runInference(request);
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

  protected abstract ListenableFuture<Void> downloadFeature(DownloadCallback callback);

  private void runInference(RequestT request) {
    long startMs = System.currentTimeMillis();
    if (streaming) {
      hasFirstStreamingResult = false;
      StringBuilder resultBuilder = new StringBuilder();
      Futures.addCallback(
          runInferenceImpl(
              request,
              additionalText ->
                  runOnUiThread(
                      () -> {
                        resultBuilder.append(additionalText);
                        if (hasFirstStreamingResult) {
                          contentAdapter.updateStreamingResponse(resultBuilder.toString());
                        } else {
                          contentAdapter.addContent(
                              TextItem.Companion.fromResponse(resultBuilder.toString()));
                          hasFirstStreamingResult = true;
                          firstTokenLatency = Instant.now().minusMillis(startMs).toEpochMilli();
                        }
                      })),
          new FutureCallback<>() {
            @Override
            public void onSuccess(List<String> results) {
              results.forEach(
                  result -> contentAdapter.addContent(TextItem.Companion.fromResponse(result)));
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
          runInferenceImpl(request, /* streamingCallback= */ null),
          new FutureCallback<>() {
            @Override
            public void onSuccess(List<String> results) {
              results.forEach(
                  result -> contentAdapter.addContent(TextItem.Companion.fromResponse(result)));
              String debugInfo =
                  getString(R.string.debug_info, Instant.now().minusMillis(startMs).toEpochMilli());
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
  }

  protected abstract ListenableFuture<List<String>> runInferenceImpl(
      RequestT request, @Nullable StreamingCallback streamingCallback);

  protected void setupSpinner(int spinnerId, int arrayId, Consumer<Integer> onItemSelected) {
    Spinner spinner = findViewById(spinnerId);
    ArrayAdapter<CharSequence> adapter =
        ArrayAdapter.createFromResource(this, arrayId, android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinner.setAdapter(adapter);
    spinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
            onItemSelected.accept(position);
          }

          @Override
          public void onNothingSelected(AdapterView<?> adapterView) {}
        });
  }

  private void displayErrorMessage(String errorMessage) {
    contentAdapter.addContent(new TextItem(errorMessage, ContentAdapter.VIEW_TYPE_RESPONSE_ERROR));
    endGeneratingUi(getString(R.string.empty));
  }

  protected void startGeneratingUi() {
    debugInfoTextView.setVisibility(View.GONE);
  }

  protected void endGeneratingUi(String debugInfo) {
    debugInfoTextView.setText(debugInfo);
    debugInfoTextView.setVisibility(debugInfo.isEmpty() ? View.GONE : View.VISIBLE);
  }

  protected void resetProcessor() {
    modelDownloaded = false;
  }

  protected abstract List<String> runInferenceForBatchTask(String request);

  /**
   * Runs inference on batch text requests from an input file.
   *
   * <p>The input file must be a CSV file selected via the file picker, with the first column
   * containing the text requests.
   *
   * <p>The output file will also be in CSV format, containing all the content from the input file
   * with the inference results appended to the end of each row. Each inference result occupies two
   * additional columns: one for the result text (or error message) and one for the score,
   * continuing sequentially.
   */
  private void batchRun(Uri inputUri, Uri outputUri) {
    batchRunCancelled = false;
    AlertDialog processingDialog =
        new AlertDialog.Builder(this)
            .setMessage(R.string.batch_run_start_message)
            .setNegativeButton(R.string.button_cancel, (dialog, which) -> batchRunCancelled = true)
            .setCancelable(false)
            .show();
    processingDialog.setCanceledOnTouchOutside(false);

    var unused =
        Executors.newSingleThreadExecutor()
            .submit(
                () -> {
                  try (InputStream inputStream = getContentResolver().openInputStream(inputUri);
                      CSVReader csvReader =
                          new CSVReader(new InputStreamReader(requireNonNull(inputStream)));
                      OutputStream outputStream = getContentResolver().openOutputStream(outputUri);
                      CSVWriter csvWriter =
                          new CSVWriter(new OutputStreamWriter(requireNonNull(outputStream)))) {
                    List<String[]> inputRows = csvReader.readAll();
                    batchProcessingIndex = 0;
                    for (String[] inputRow : inputRows) {
                      if (batchRunCancelled) {
                        break;
                      }

                      batchProcessingIndex++;
                      runOnUiThread(
                          () ->
                              processingDialog.setMessage(
                                  "Processing " + batchProcessingIndex + "/" + inputRows.size()));

                      long startTimeMs = System.currentTimeMillis();
                      List<String> resultRow = new ArrayList<>(Arrays.asList(inputRow));
                      List<String> inferenceResult = runInferenceForBatchTask(resultRow.get(0));
                      // Append the result to the end of input columns.
                      resultRow.addAll(inferenceResult);
                      csvWriter.writeNext(resultRow.toArray(new String[0]));
                      long elapsedTimeMs = System.currentTimeMillis() - startTimeMs;
                      long remainingTimeMs = MIN_INFERENCE_INTERVAL_MS - elapsedTimeMs;
                      if (remainingTimeMs > 0) {
                        Thread.sleep(remainingTimeMs);
                      }
                    }
                  } catch (IOException | InterruptedException e) {
                    Log.e(TAG, "Failed to do batch run.", e);
                  }

                  runOnUiThread(processingDialog::dismiss);
                });
  }
}

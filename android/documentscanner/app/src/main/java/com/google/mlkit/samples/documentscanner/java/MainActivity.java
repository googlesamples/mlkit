/*
 * Copyright 2024 Google LLC. All rights reserved.
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

package com.google.mlkit.samples.documentscanner.java;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult;
import androidx.core.content.FileProvider;
import com.bumptech.glide.Glide;
import com.google.mlkit.samples.documentscanner.R;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Demonstrates the document scanner powered by Google Play services. */
public class MainActivity extends AppCompatActivity {

  private static final String TAG = "MainActivity";

  private static final String FULL_MODE = "FULL";
  private static final String BASE_MODE = "BASE";
  private static final String BASE_MODE_WITH_FILTER = "BASE_WITH_FILTER";
  private String selectedMode = FULL_MODE;

  private TextView resultInfo;
  private ImageView firstPageView;
  private EditText pageLimitInputView;
  private ActivityResultLauncher<IntentSenderRequest> scannerLauncher;
  private boolean enableGalleryImport = true;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    resultInfo = findViewById(R.id.result_info);
    firstPageView = findViewById(R.id.first_page_view);
    pageLimitInputView = findViewById(R.id.page_limit_input);

    scannerLauncher =
        registerForActivityResult(new StartIntentSenderForResult(), this::handleActivityResult);
    populateModeSelector();
  }

  public void onEnableGalleryImportCheckboxClicked(View view) {
    enableGalleryImport = ((CheckBox) view).isChecked();
  }

  public void onScanButtonClicked(View view) {
    resultInfo.setText(null);
    Glide.with(this).clear(firstPageView);

    GmsDocumentScannerOptions.Builder options =
        new GmsDocumentScannerOptions.Builder()
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setGalleryImportAllowed(enableGalleryImport);

    switch (selectedMode) {
      case FULL_MODE:
        options.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL);
        break;
      case BASE_MODE:
        options.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE);
        break;
      case BASE_MODE_WITH_FILTER:
        options.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER);
        break;
      default:
        Log.e(TAG, "Unknown selectedMode: " + selectedMode);
    }

    String pageLimitInputText = pageLimitInputView.getText().toString();
    if (!pageLimitInputText.isEmpty()) {
      try {
        int pageLimit = Integer.parseInt(pageLimitInputText);
        options.setPageLimit(pageLimit);
      } catch (RuntimeException e) {
        resultInfo.setText(e.getMessage());
        return;
      }
    }

    GmsDocumentScanning.getClient(options.build())
        .getStartScanIntent(this)
        .addOnSuccessListener(
            intentSender ->
                scannerLauncher.launch(new IntentSenderRequest.Builder(intentSender).build()))
        .addOnFailureListener(
            e -> resultInfo.setText(getString(R.string.error_default_message, e.getMessage())));
  }

  private void populateModeSelector() {
    Spinner featureSpinner = findViewById(R.id.mode_selector);
    List<String> options = new ArrayList<>();
    options.add(FULL_MODE);
    options.add(BASE_MODE);
    options.add(BASE_MODE_WITH_FILTER);

    // Creating adapter for featureSpinner
    ArrayAdapter<String> dataAdapter =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
    // Drop down layout style - list view with radio button
    dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    // Attaching data adapter to spinner
    featureSpinner.setAdapter(dataAdapter);
    featureSpinner.setOnItemSelectedListener(
        new OnItemSelectedListener() {

          @Override
          public void onItemSelected(
              AdapterView<?> parentView, View selectedItemView, int pos, long id) {
            selectedMode = parentView.getItemAtPosition(pos).toString();
          }

          @Override
          public void onNothingSelected(AdapterView<?> arg0) {}
        });
  }

  private void handleActivityResult(ActivityResult activityResult) {
    int resultCode = activityResult.getResultCode();
    GmsDocumentScanningResult result =
        GmsDocumentScanningResult.fromActivityResultIntent(activityResult.getData());
    if (resultCode == Activity.RESULT_OK && result != null) {
      resultInfo.setText(getString(R.string.scan_result, result));

      if (!result.getPages().isEmpty()) {
        Glide.with(this).load(result.getPages().get(0).getImageUri()).into(firstPageView);
      }

      if (result.getPdf() != null) {
        File file = new File(result.getPdf().getUri().getPath());
        Uri externalUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(externalUri, "application/pdf");
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(viewIntent, "view pdf"));
      }
    } else if (resultCode == Activity.RESULT_CANCELED) {
      resultInfo.setText(getString(R.string.error_scanner_cancelled));
    } else {
      resultInfo.setText(getString(R.string.error_default_message));
    }
  }
}

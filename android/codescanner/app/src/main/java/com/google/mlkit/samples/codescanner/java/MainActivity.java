/*
 * Copyright 2022 Google LLC. All rights reserved.
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

package com.google.mlkit.samples.codescanner.java;

import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.samples.codescanner.R;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import java.util.Locale;

/** Demonstrates the code scanner powered by Google Play Services. */
public class MainActivity extends AppCompatActivity {

  private static final String KEY_ALLOW_MANUAL_INPUT = "allow_manual_input";

  private boolean allowManualInput;
  private TextView barcodeResultView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    barcodeResultView = findViewById(R.id.barcode_result_view);
  }

  public void onAllowManualInputCheckboxClicked(View view) {
    allowManualInput = ((CheckBox) view).isChecked();
  }

  public void onScanButtonClicked(View view) {
    GmsBarcodeScannerOptions.Builder optionsBuilder = new GmsBarcodeScannerOptions.Builder();
    if (allowManualInput) {
      optionsBuilder.allowManualInput();
    }
    GmsBarcodeScanner gmsBarcodeScanner =
        GmsBarcodeScanning.getClient(this, optionsBuilder.build());
    gmsBarcodeScanner
        .startScan()
        .addOnSuccessListener(barcode -> barcodeResultView.setText(getSuccessfulMessage(barcode)))
        .addOnFailureListener(
            e -> barcodeResultView.setText(getErrorMessage(e)))
        .addOnCanceledListener(
            () -> barcodeResultView.setText(getString(R.string.error_scanner_cancelled)));
  }

  @Override
  protected void onSaveInstanceState(Bundle savedInstanceState) {
    savedInstanceState.putBoolean(KEY_ALLOW_MANUAL_INPUT, allowManualInput);
    super.onSaveInstanceState(savedInstanceState);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    allowManualInput = savedInstanceState.getBoolean(KEY_ALLOW_MANUAL_INPUT);
  }

  private String getSuccessfulMessage(Barcode barcode) {
    String barcodeValue =
        String.format(
            Locale.US,
            "Display Value: %s\nRaw Value: %s\nFormat: %s\nValue Type: %s",
            barcode.getDisplayValue(),
            barcode.getRawValue(),
            barcode.getFormat(),
            barcode.getValueType());
    return getString(R.string.barcode_result, barcodeValue);
  }

  @SuppressLint("SwitchIntDef")
  private String getErrorMessage(Exception e) {
    if (e instanceof MlKitException) {
      switch (((MlKitException) e).getErrorCode()) {
        case MlKitException.CODE_SCANNER_CAMERA_PERMISSION_NOT_GRANTED:
          return getString(R.string.error_camera_permission_not_granted);
        case MlKitException.CODE_SCANNER_APP_NAME_UNAVAILABLE:
          return getString(R.string.error_app_name_unavailable);
        default:
          return getString(R.string.error_default_message, e);
      }
    } else {
      return e.getMessage();
    }
  }
}

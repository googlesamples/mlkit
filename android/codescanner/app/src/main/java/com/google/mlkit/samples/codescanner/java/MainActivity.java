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
import android.widget.TextView;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.samples.codescanner.R;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import java.util.Locale;

/** Demonstrates the code scanner powered by Google Play Services. */
public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    TextView barcodeResultView = findViewById(R.id.barcode_result_view);
    findViewById(R.id.scan_barcode_button)
        .setOnClickListener(
            v -> {
              GmsBarcodeScanner gmsBarcodeScanner = GmsBarcodeScanning.getClient(this);
              gmsBarcodeScanner
                  .startScan()
                  .addOnSuccessListener(
                      barcode -> barcodeResultView.setText(getSuccessfulMessage(barcode)))
                  .addOnFailureListener(
                      e -> barcodeResultView.setText(getErrorMessage((MlKitException) e)));
            });
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
  private String getErrorMessage(MlKitException e) {
    switch (e.getErrorCode()) {
      case MlKitException.CODE_SCANNER_CANCELLED:
        return getString(R.string.error_scanner_cancelled);
      case MlKitException.CODE_SCANNER_CAMERA_PERMISSION_NOT_GRANTED:
        return getString(R.string.error_camera_permission_not_granted);
      case MlKitException.CODE_SCANNER_APP_NAME_UNAVAILABLE:
        return getString(R.string.error_app_name_unavailable);
      default:
        return getString(R.string.error_default_message, e);
    }
  }
}

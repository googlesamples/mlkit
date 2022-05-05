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

package com.google.mlkit.samples.codescanner.kotlin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.TextView
import com.google.mlkit.common.MlKitException
import com.google.mlkit.samples.codescanner.R
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Locale

/** Demonstrates the code scanner powered by Google Play Services. */
class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    val barcodeResultView = findViewById<TextView>(R.id.barcode_result_view)
    findViewById<View>(R.id.scan_barcode_button).setOnClickListener {
      val gmsBarcodeScanner = GmsBarcodeScanning.getClient(this)
      gmsBarcodeScanner
        .startScan()
        .addOnSuccessListener { barcode: Barcode ->
          barcodeResultView.text = getSuccessfulMessage(barcode)
        }
        .addOnFailureListener { e: Exception ->
          barcodeResultView.text = getErrorMessage(e as MlKitException)
        }
    }
  }

  private fun getSuccessfulMessage(barcode: Barcode): String {
    val barcodeValue =
      String.format(
        Locale.US,
        "Display Value: %s\nRaw Value: %s\nFormat: %s\nValue Type: %s",
        barcode.displayValue,
        barcode.rawValue,
        barcode.format,
        barcode.valueType
      )
    return getString(R.string.barcode_result, barcodeValue)
  }

  private fun getErrorMessage(e: MlKitException): String {
    return when (e.errorCode) {
      MlKitException.CODE_SCANNER_CANCELLED -> getString(R.string.error_scanner_cancelled)
      MlKitException.CODE_SCANNER_CAMERA_PERMISSION_NOT_GRANTED ->
        getString(R.string.error_camera_permission_not_granted)
      MlKitException.CODE_SCANNER_APP_NAME_UNAVAILABLE ->
        getString(R.string.error_app_name_unavailable)
      else -> getString(R.string.error_default_message, e)
    }
  }
}

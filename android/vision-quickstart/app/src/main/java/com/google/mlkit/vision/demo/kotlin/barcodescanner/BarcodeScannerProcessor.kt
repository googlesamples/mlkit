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

package com.google.mlkit.vision.demo.kotlin.barcodescanner

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions.ZoomCallback
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.kotlin.VisionProcessorBase

/** Barcode Detector Demo. */
class BarcodeScannerProcessor(context: Context, zoomCallback: ZoomCallback?) :
  VisionProcessorBase<List<Barcode>>(context) {

  private var barcodeScanner: BarcodeScanner

  init {
    // Note that if you know which format of barcode your app is dealing with, detection will be
    // faster to specify the supported barcode formats one by one, e.g.
    // BarcodeScannerOptions.Builder()
    //     .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
    //     .build();
    barcodeScanner =
      if (zoomCallback != null) {
        val options =
          BarcodeScannerOptions.Builder()
            .setZoomSuggestionOptions(ZoomSuggestionOptions.Builder(zoomCallback).build())
            .build()
        BarcodeScanning.getClient(options)
      } else {
        BarcodeScanning.getClient()
      }
  }

  override fun stop() {
    super.stop()
    barcodeScanner.close()
  }

  override fun detectInImage(image: InputImage): Task<List<Barcode>> {
    return barcodeScanner.process(image)
  }

  override fun onSuccess(barcodes: List<Barcode>, graphicOverlay: GraphicOverlay) {
    if (barcodes.isEmpty()) {
      Log.v(MANUAL_TESTING_LOG, "No barcode has been detected")
    }
    for (i in barcodes.indices) {
      val barcode = barcodes[i]
      graphicOverlay.add(BarcodeGraphic(graphicOverlay, barcode))
      logExtrasForTesting(barcode)
    }
  }

  override fun onFailure(e: Exception) {
    Log.e(TAG, "Barcode detection failed $e")
  }

  companion object {
    private const val TAG = "BarcodeProcessor"

    private fun logExtrasForTesting(barcode: Barcode?) {
      if (barcode != null) {
        Log.v(
          MANUAL_TESTING_LOG,
          String.format(
            "Detected barcode's bounding box: %s",
            barcode.boundingBox!!.flattenToString()
          )
        )
        Log.v(
          MANUAL_TESTING_LOG,
          String.format("Expected corner point size is 4, get %d", barcode.cornerPoints!!.size)
        )
        for (point in barcode.cornerPoints!!) {
          Log.v(
            MANUAL_TESTING_LOG,
            String.format("Corner point is located at: x = %d, y = %d", point.x, point.y)
          )
        }
        Log.v(MANUAL_TESTING_LOG, "barcode display value: " + barcode.displayValue)
        Log.v(MANUAL_TESTING_LOG, "barcode raw value: " + barcode.rawValue)
        val dl = barcode.driverLicense
        if (dl != null) {
          Log.v(MANUAL_TESTING_LOG, "driver license city: " + dl.addressCity)
          Log.v(MANUAL_TESTING_LOG, "driver license state: " + dl.addressState)
          Log.v(MANUAL_TESTING_LOG, "driver license street: " + dl.addressStreet)
          Log.v(MANUAL_TESTING_LOG, "driver license zip code: " + dl.addressZip)
          Log.v(MANUAL_TESTING_LOG, "driver license birthday: " + dl.birthDate)
          Log.v(MANUAL_TESTING_LOG, "driver license document type: " + dl.documentType)
          Log.v(MANUAL_TESTING_LOG, "driver license expiry date: " + dl.expiryDate)
          Log.v(MANUAL_TESTING_LOG, "driver license first name: " + dl.firstName)
          Log.v(MANUAL_TESTING_LOG, "driver license middle name: " + dl.middleName)
          Log.v(MANUAL_TESTING_LOG, "driver license last name: " + dl.lastName)
          Log.v(MANUAL_TESTING_LOG, "driver license gender: " + dl.gender)
          Log.v(MANUAL_TESTING_LOG, "driver license issue date: " + dl.issueDate)
          Log.v(MANUAL_TESTING_LOG, "driver license issue country: " + dl.issuingCountry)
          Log.v(MANUAL_TESTING_LOG, "driver license number: " + dl.licenseNumber)
        }
      }
    }
  }
}

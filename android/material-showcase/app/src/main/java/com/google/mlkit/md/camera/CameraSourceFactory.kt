/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.md.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Log

object CameraSourceFactory {

    const val TAG = "CameraSourceFactory"

    fun createCameraSource(graphicOverlay: GraphicOverlay): CameraSource {
        val characteristics = Camera2APISource.getCameraCharacteristics(graphicOverlay.context)
        val halSupport = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        return if (halSupport == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY){
            Log.d(TAG, "Camera API source used")
            CameraAPISource(graphicOverlay)
        } else {
            Log.d(TAG, "Camera2 API source used")
            Camera2APISource(graphicOverlay)
        }
    }

}
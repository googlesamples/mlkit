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

package com.google.mlkit.md.objectdetection

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.objects.DetectedObject
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Holds the detected object info and its related image info.
 */

class ConfirmedObjectInfo private constructor(val objectId: Int?, val objectIndex: Int, val boundingBox: Rect,
                                              val labels: List<DetectedObject.Label>, val bitmap: Bitmap) {

    private var jpegBytes: ByteArray? = null

    val imageData: ByteArray?
        @Synchronized get() {
            if (jpegBytes == null) {
                try {
                    ByteArrayOutputStream().use { stream ->
                        bitmap.compress(CompressFormat.JPEG, /* quality= */ 100, stream)
                        jpegBytes = stream.toByteArray()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error getting object image data!")
                }
            }
            return jpegBytes
        }

    companion object {
        private const val TAG = "ConfirmedObject"

        fun from(detectedObjectInfo: DetectedObjectInfo): ConfirmedObjectInfo{
            return ConfirmedObjectInfo(detectedObjectInfo.objectId, detectedObjectInfo.objectIndex,
                detectedObjectInfo.boundingBox, detectedObjectInfo.labels, detectedObjectInfo.getBitmap())
        }
    }

}
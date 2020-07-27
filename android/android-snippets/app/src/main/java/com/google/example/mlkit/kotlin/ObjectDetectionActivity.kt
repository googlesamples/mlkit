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

package com.google.example.mlkit.kotlin

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.PredefinedCategory

class ObjectDetectionActivity : AppCompatActivity() {

    private fun useDefaultObjectDetector() {
        // [START create_default_options]
        // Live detection and tracking
        var options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()  // Optional
                .build()

        // Multiple object detection in static images
        options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .enableClassification()  // Optional
                .build()
        // [END create_default_options]

        // [START create_detector]
        val objectDetector = ObjectDetection.getClient(options)
        // [END create_detector]

        val image = InputImage.fromBitmap(
                Bitmap.createBitmap(IntArray(100 * 100), 100, 100, Bitmap.Config.ARGB_8888),
                0)

        // [START process_image]
        objectDetector.process(image)
                .addOnSuccessListener { results ->
                    // Task completed successfully
                    // ...
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    // ...
                }
        // [END process_image]

        val results = listOf<DetectedObject>()
        // [START read_results_default]
        for (detectedObject in results) {
            val boundingBox = detectedObject.boundingBox
            val trackingId = detectedObject.trackingId
            for (label in detectedObject.labels) {
                val text = label.text
                if (PredefinedCategory.FOOD == text) {
                    // ...
                }
                val index = label.index
                if (PredefinedCategory.FOOD_INDEX == index) {
                    // ...
                }
                val confidence = label.confidence
            }
        }
        // [END read_results_default]
    }

    private fun useCustomObjectDetector() {
        val image = InputImage.fromBitmap(
                Bitmap.createBitmap(IntArray(100 * 100), 100, 100, Bitmap.Config.ARGB_8888),
                0)

        // [START create_local_model]
        val localModel =
                LocalModel.Builder()
                        .setAssetFilePath("asset_file_path_to_tflite_model")
                        // or .setAbsoluteFilePath("absolute_file_path_to_tflite_model")
                        .build()
        // [END create_local_model]

        // [START create_custom_options]
        // Live detection and tracking
        var options =
                CustomObjectDetectorOptions.Builder(localModel)
                        .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                        .enableClassification()
                        .setClassificationConfidenceThreshold(0.5f)
                        .setMaxPerObjectLabelCount(3)
                        .build()

        // Multiple object detection in static images
        options =
                CustomObjectDetectorOptions.Builder(localModel)
                        .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                        .enableMultipleObjects()
                        .enableClassification()
                        .setClassificationConfidenceThreshold(0.5f)
                        .setMaxPerObjectLabelCount(3)
                        .build()
        // [END create_custom_options]

        val results = listOf<DetectedObject>()
        // [START read_results_custom]
        for (detectedObject in results) {
            val boundingBox = detectedObject.boundingBox
            val trackingId = detectedObject.trackingId
            for (label in detectedObject.labels) {
                val text = label.text
                val index = label.index
                val confidence = label.confidence
            }
        }
        // [END read_results_custom]
    }
}

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

import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

class ImageLabelingActivity : AppCompatActivity() {

    private fun labelImages(image: InputImage) {
        val options = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.8f)
                .build()

        val labeler = ImageLabeling.getClient(options)

        // [START run_detector]
        val result = labeler.process(image)
                .addOnSuccessListener { labels ->
                    // Task completed successfully
                    // [START_EXCLUDE]
                    // [START get_labels]
                    for (label in labels) {
                        val text = label.text
                        val confidence = label.confidence
                    }
                    // [END get_labels]
                    // [END_EXCLUDE]
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    // ...
                }
        // [END run_detector]
    }

    private fun configureAndRunImageLabeler(image: InputImage) {
        // [START on_device_image_labeler]
        // To use default options:
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        // Or, to set the minimum confidence required:
        // val options = ImageLabelerOptions.Builder()
        //     .setConfidenceThreshold(0.7f)
        //     .build()
        // val labeler = ImageLabeling.getClient(options)

        // [END on_device_image_labeler]

        // Process image with custom onSuccess() example
        // [START process_image]
        labeler.process(image)
                .addOnSuccessListener { labels ->
                    // Task completed successfully
                    // ...
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    // ...
                }
        // [END process_image]

        // Process image with example onSuccess()
        labeler.process(image)
                .addOnSuccessListener { labels ->
                    // [START get_image_label_info]
                    for (label in labels) {
                        val text = label.text
                        val confidence = label.confidence
                        val index = label.index
                    }
                    // [END get_image_label_info]
                }
    }
}

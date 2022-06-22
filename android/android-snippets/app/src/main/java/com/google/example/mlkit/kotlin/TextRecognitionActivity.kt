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
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class TextRecognitionActivity : AppCompatActivity() {

    private fun recognizeText(image: InputImage) {

        // [START get_detector_default]
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        // [END get_detector_default]

        // [START run_detector]
        val result = recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Task completed successfully
                    // [START_EXCLUDE]
                    // [START get_text]
                    for (block in visionText.textBlocks) {
                        val boundingBox = block.boundingBox
                        val cornerPoints = block.cornerPoints
                        val text = block.text

                        for (line in block.lines) {
                            // ...
                            for (element in line.elements) {
                                // ...
                            }
                        }
                    }
                    // [END get_text]
                    // [END_EXCLUDE]
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    // ...
                }
        // [END run_detector]
    }

    private fun processTextBlock(result: Text) {
        // [START mlkit_process_text_block]
        val resultText = result.text
        for (block in result.textBlocks) {
            val blockText = block.text
            val blockCornerPoints = block.cornerPoints
            val blockFrame = block.boundingBox
            for (line in block.lines) {
                val lineText = line.text
                val lineCornerPoints = line.cornerPoints
                val lineFrame = line.boundingBox
                for (element in line.elements) {
                    val elementText = element.text
                    val elementCornerPoints = element.cornerPoints
                    val elementFrame = element.boundingBox
                }
            }
        }
        // [END mlkit_process_text_block]
    }

    private fun getTextRecognizer(): TextRecognizer {
        // [START mlkit_local_doc_recognizer]
        return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        // [END mlkit_local_doc_recognizer]
    }
}

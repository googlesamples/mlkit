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

package com.google.example.mlkit;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.common.model.LocalModel;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import com.google.mlkit.vision.objects.defaults.PredefinedCategory;

import java.util.ArrayList;
import java.util.List;

public class ObjectDetectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private void useDefaultObjectDetector() {
        // [START create_default_options]
        // Live detection and tracking
        ObjectDetectorOptions options =
                new ObjectDetectorOptions.Builder()
                        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                        .enableClassification()  // Optional
                        .build();

        // Multiple object detection in static images
        options =
                new ObjectDetectorOptions.Builder()
                        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                        .enableMultipleObjects()
                        .enableClassification()  // Optional
                        .build();
        // [END create_default_options]

        // [START create_detector]
        ObjectDetector objectDetector = ObjectDetection.getClient(options);
        // [END create_detector]

        InputImage image =
                InputImage.fromBitmap(
                        Bitmap.createBitmap(new int[100 * 100], 100, 100, Bitmap.Config.ARGB_8888),
                        0);

        // [START process_image]
        objectDetector.process(image)
                .addOnSuccessListener(
                        new OnSuccessListener<List<DetectedObject>>() {
                            @Override
                            public void onSuccess(List<DetectedObject> detectedObjects) {
                                // Task completed successfully
                                // ...
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Task failed with an exception
                                // ...
                            }
                        });
        // [END process_image]

        List<DetectedObject> results = new ArrayList<>();
        // [START read_results_default]
        // The list of detected objects contains one item if multiple
        // object detection wasn't enabled.
        for (DetectedObject detectedObject : results) {
            Rect boundingBox = detectedObject.getBoundingBox();
            Integer trackingId = detectedObject.getTrackingId();
            for (DetectedObject.Label label : detectedObject.getLabels()) {
                String text = label.getText();
                if (PredefinedCategory.FOOD.equals(text)) {
                  // ...
                }
                int index = label.getIndex();
                if (PredefinedCategory.FOOD_INDEX == index) {
                  // ...
                }
                float confidence = label.getConfidence();
            }
        }
        // [END read_results_default]
    }

    private void useCustomObjectDetector() {
        InputImage image =
                InputImage.fromBitmap(
                        Bitmap.createBitmap(new int[100 * 100], 100, 100, Bitmap.Config.ARGB_8888),
                        0);

        // [START create_local_model]
        LocalModel localModel =
                new LocalModel.Builder()
                        .setAssetFilePath("asset_file_path_to_tflite_model")
                        // or .setAbsoluteFilePath("absolute_file_path_to_tflite_model")
                        .build();
        // [END create_local_model]

        // [START create_custom_options]
        // Live detection and tracking
        CustomObjectDetectorOptions options =
                new CustomObjectDetectorOptions.Builder(localModel)
                        .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                        .enableClassification()
                        .setClassificationConfidenceThreshold(0.5f)
                        .setMaxPerObjectLabelCount(3)
                        .build();

        // Multiple object detection in static images
        options =
                new CustomObjectDetectorOptions.Builder(localModel)
                        .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                        .enableMultipleObjects()
                        .enableClassification()
                        .setClassificationConfidenceThreshold(0.5f)
                        .setMaxPerObjectLabelCount(3)
                        .build();
        // [END create_custom_options]

        List<DetectedObject> results = new ArrayList<>();
        // [START read_results_custom]
        // The list of detected objects contains one item if multiple
        // object detection wasn't enabled.
        for (DetectedObject detectedObject : results) {
            Rect boundingBox = detectedObject.getBoundingBox();
            Integer trackingId = detectedObject.getTrackingId();
            for (DetectedObject.Label label : detectedObject.getLabels()) {
                String text = label.getText();
                int index = label.getIndex();
                float confidence = label.getConfidence();
            }
        }
        // [END read_results_custom]
    }
}

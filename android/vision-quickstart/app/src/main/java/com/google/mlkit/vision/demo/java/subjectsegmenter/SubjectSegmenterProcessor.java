/*
 * Copyright 2023 Google LLC. All rights reserved.
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

package com.google.mlkit.vision.demo.java.subjectsegmenter;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.java.VisionProcessorBase;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

/** A processor to run Subject Segmenter. */
@RequiresApi(Build.VERSION_CODES.N)
public class SubjectSegmenterProcessor extends VisionProcessorBase<SubjectSegmentationResult> {

  private static final String TAG = "SbjSegmenterProcessor";

  private final SubjectSegmenter subjectSegmenter;
  private int imageWidth;
  private int imageHeight;

  public SubjectSegmenterProcessor(Context context) {
    super(context);
    subjectSegmenter =
        SubjectSegmentation.getClient(
            new SubjectSegmenterOptions.Builder()
                .enableMultipleSubjects(
                    new SubjectSegmenterOptions.SubjectResultOptions.Builder()
                        .enableConfidenceMask()
                        .build())
                .build());

    Log.d(TAG, "SubjectSegmenterProcessor created");
  }

  @Override
  protected Task<SubjectSegmentationResult> detectInImage(InputImage image) {
    this.imageWidth = image.getWidth();
    this.imageHeight = image.getHeight();
    return subjectSegmenter.process(image);
  }

  @Override
  protected void onSuccess(
      @NonNull SubjectSegmentationResult segmentationResult,
      @NonNull GraphicOverlay graphicOverlay) {
    graphicOverlay.add(
        new SubjectSegmentationGraphic(
            graphicOverlay, segmentationResult, imageWidth, imageHeight));
  }

  @Override
  protected void onFailure(@NonNull Exception e) {
    Log.e(TAG, "Subject segmentation failed: ", e);
  }
}

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

package com.google.mlkit.vision.demo.java.labeldetector;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.java.VisionProcessorBase;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabelerOptionsBase;
import com.google.mlkit.vision.label.ImageLabeling;
import java.util.List;

/** Custom InputImage Classifier Demo. */
public class LabelDetectorProcessor extends VisionProcessorBase<List<ImageLabel>> {

  private static final String TAG = "LabelDetectorProcessor";

  private final ImageLabeler imageLabeler;

  public LabelDetectorProcessor(Context context, ImageLabelerOptionsBase options) {
    super(context);
    imageLabeler = ImageLabeling.getClient(options);
  }

  @Override
  public void stop() {
    super.stop();
    imageLabeler.close();
  }

  @Override
  protected Task<List<ImageLabel>> detectInImage(InputImage image) {
    return imageLabeler.process(image);
  }

  @Override
  protected void onSuccess(
      @NonNull List<ImageLabel> labels, @NonNull GraphicOverlay graphicOverlay) {
    graphicOverlay.add(new LabelGraphic(graphicOverlay, labels));
    logExtrasForTesting(labels);
  }

  private static void logExtrasForTesting(List<ImageLabel> labels) {
    if (labels == null) {
      Log.v(MANUAL_TESTING_LOG, "No labels detected");
    } else {
      for (ImageLabel label : labels) {
        Log.v(
            MANUAL_TESTING_LOG,
            String.format("Label %s, confidence %f", label.getText(), label.getConfidence()));
      }
    }
  }

  @Override
  protected void onFailure(@NonNull Exception e) {
    Log.w(TAG, "Label detection failed." + e);
  }
}


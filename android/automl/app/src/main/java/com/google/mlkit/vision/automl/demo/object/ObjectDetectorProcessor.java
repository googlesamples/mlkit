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

package com.google.mlkit.vision.automl.demo.object;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModel;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.vision.automl.demo.GraphicOverlay;
import com.google.mlkit.vision.automl.demo.VisionProcessorBase;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase;
import java.util.ArrayList;
import java.util.List;

/** A processor to run object detector. */
public class ObjectDetectorProcessor extends VisionProcessorBase<List<DetectedObject>> {

  private static final String TAG = "ObjectDetectorProcessor";
  private final ObjectDetector detector;
  private final Context context;
  private final Task<?> modelDownloadingTask;
  private final int detectorMode;

  public ObjectDetectorProcessor(
      Context context, RemoteModel remoteModel, ObjectDetectorOptionsBase options) {
    super(context);
    this.detectorMode = options.getDetectorMode();
    this.context = context;
    detector = ObjectDetection.getClient(options);

    DownloadConditions downloadConditions = new DownloadConditions.Builder().requireWifi().build();
    modelDownloadingTask =
        RemoteModelManager.getInstance()
            .download(remoteModel, downloadConditions)
            .addOnFailureListener(
                ignored ->
                    Toast.makeText(
                            context,
                            "Model download failed, please check your connection.",
                            Toast.LENGTH_LONG)
                        .show());
  }

  @Override
  public void stop() {
    super.stop();
    detector.close();
  }

  @Override
  protected Task<List<DetectedObject>> detectInImage(InputImage image) {
    if (!modelDownloadingTask.isComplete()) {
      if (detectorMode == ObjectDetectorOptionsBase.STREAM_MODE) {
        Log.i(TAG, "Model download is in progress. Skip detecting image.");
        return Tasks.forResult(new ArrayList<>());
      } else {
        Log.i(TAG, "Model download is in progress. Waiting...");
        return modelDownloadingTask.continueWithTask(task -> processImageOnDownloadComplete(image));
      }
    } else {
      return processImageOnDownloadComplete(image);
    }
  }

  private Task<List<DetectedObject>> processImageOnDownloadComplete(InputImage image) {
    if (modelDownloadingTask != null && modelDownloadingTask.isSuccessful()) {
      if (detector == null) {
        Log.e(TAG, "object detector has not been initialized; Skipped.");
        Toast.makeText(context, "no initialized Detector.", Toast.LENGTH_SHORT).show();
      }
      return detector.process(image);
    } else {
      String downloadingError = "Error downloading remote model.";
      Log.e(TAG, downloadingError, modelDownloadingTask.getException());
      Toast.makeText(context, downloadingError, Toast.LENGTH_SHORT).show();
      return Tasks.forException(
          new Exception("Failed to download remote model.", modelDownloadingTask.getException()));
    }
  }

  @Override
  protected void onSuccess(
      @NonNull List<DetectedObject> results, @NonNull GraphicOverlay graphicOverlay) {
    for (DetectedObject object : results) {
      graphicOverlay.add(new ObjectGraphic(graphicOverlay, object));
    }
  }

  @Override
  protected void onFailure(@NonNull Exception e) {
    Log.e(TAG, "Object detection failed!", e);
  }
}

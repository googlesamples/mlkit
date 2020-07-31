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

package com.google.mlkit.vision.automl.demo.automl;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModel;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.vision.automl.demo.GraphicOverlay;
import com.google.mlkit.vision.automl.demo.VisionProcessorBase;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabelerOptionsBase;
import com.google.mlkit.vision.label.ImageLabeling;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

/** AutoML image labeler demo. */
public class AutoMLImageLabelerProcessor extends VisionProcessorBase<List<ImageLabel>> {

  private static final String TAG = "AutoMLProcessor";
  private final ImageLabeler imageLabeler;
  private final Context context;
  private final Task<?> modelDownloadingTask;

  private final Mode mode;

  public AutoMLImageLabelerProcessor(
      Context context, RemoteModel remoteModel, ImageLabelerOptionsBase options, Mode mode) {
    super(context);
    this.mode = mode;
    this.context = context;
    imageLabeler = ImageLabeling.getClient(options);

    DownloadConditions downloadConditions = new DownloadConditions.Builder().requireWifi().build();
    modelDownloadingTask =
        RemoteModelManager.getInstance()
            .download(remoteModel, downloadConditions)
            .addOnFailureListener(
                ignored ->
                    Toast.makeText(
                            context,
                            "Model download failed for AutoMLImageLabelerImpl,"
                                + " please check your connection.",
                            Toast.LENGTH_LONG)
                        .show());
  }

  @Override
  public void stop() {
    super.stop();
    imageLabeler.close();
  }

  @Override
  protected Task<List<ImageLabel>> detectInImage(InputImage image) {
    if (!modelDownloadingTask.isComplete()) {
      if (mode == Mode.LIVE_PREVIEW) {
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

  private Task<List<ImageLabel>> processImageOnDownloadComplete(InputImage image) {
    if (modelDownloadingTask != null && modelDownloadingTask.isSuccessful()) {
      if (imageLabeler == null) {
        Log.e(TAG, "image labeler has not been initialized; Skipped.");
        Toast.makeText(context, "no initialized Labeler.", Toast.LENGTH_SHORT).show();
      }
      return imageLabeler.process(image);
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
      @NonNull List<ImageLabel> labels, @NonNull GraphicOverlay graphicOverlay) {
    graphicOverlay.add(new LabelGraphic(graphicOverlay, labels));
  }

  @Override
  protected void onFailure(@NonNull Exception e) {
    Log.w(TAG, "Label detection failed.", e);
  }

  /**
   * The detection mode of the processor. Different modes will have different behavior on whether or
   * not waiting for the model download complete.
   */
  public enum Mode {
    STILL_IMAGE,
    LIVE_PREVIEW
  }
}


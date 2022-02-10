/*
 * Copyright 2021 Google LLC. All rights reserved.
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

package com.google.mlkit.vision.demo.java;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.annotation.RequiresApi;
import androidx.camera.view.PreviewView;
import com.google.android.gms.common.annotation.KeepName;
import com.google.mlkit.common.model.LocalModel;
import com.google.mlkit.vision.camera.CameraSourceConfig;
import com.google.mlkit.vision.camera.CameraXSource;
import com.google.mlkit.vision.camera.DetectionTaskCallback;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.InferenceInfoGraphic;
import com.google.mlkit.vision.demo.R;
import com.google.mlkit.vision.demo.java.objectdetector.ObjectGraphic;
import com.google.mlkit.vision.demo.preference.PreferenceUtils;
import com.google.mlkit.vision.demo.preference.SettingsActivity;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions;
import java.util.List;
import java.util.Objects;

/** Live preview demo app for ML Kit APIs using CameraXSource API. */
@KeepName
@RequiresApi(VERSION_CODES.LOLLIPOP)
public final class CameraXSourceDemoActivity extends AppCompatActivity
    implements CompoundButton.OnCheckedChangeListener {
  private static final String TAG = "CameraXSourceDemo";

  private static final LocalModel localModel =
      new LocalModel.Builder().setAssetFilePath("custom_models/object_labeler.tflite").build();

  private PreviewView previewView;
  private GraphicOverlay graphicOverlay;

  private boolean needUpdateGraphicOverlayImageSourceInfo;

  private int lensFacing = CameraSourceConfig.CAMERA_FACING_BACK;
  private DetectionTaskCallback<List<DetectedObject>> detectionTaskCallback;
  private CameraXSource cameraXSource;
  private CustomObjectDetectorOptions customObjectDetectorOptions;
  private Size targetResolution;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(TAG, "onCreate");

    setContentView(R.layout.activity_vision_cameraxsource_demo);
    previewView = findViewById(R.id.preview_view);
    if (previewView == null) {
      Log.d(TAG, "previewView is null");
    }
    graphicOverlay = findViewById(R.id.graphic_overlay);
    if (graphicOverlay == null) {
      Log.d(TAG, "graphicOverlay is null");
    }

    ToggleButton facingSwitch = findViewById(R.id.facing_switch);
    facingSwitch.setOnCheckedChangeListener(this);

    ImageView settingsButton = findViewById(R.id.settings_button);
    settingsButton.setOnClickListener(
        v -> {
          Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
          intent.putExtra(
              SettingsActivity.EXTRA_LAUNCH_SOURCE,
              SettingsActivity.LaunchSource.CAMERAXSOURCE_DEMO);
          startActivity(intent);
        });
    detectionTaskCallback =
        detectionTask ->
            detectionTask
                .addOnSuccessListener(this::onDetectionTaskSuccess)
                .addOnFailureListener(this::onDetectionTaskFailure);
  }

  @Override
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    lensFacing =
        lensFacing == CameraSourceConfig.CAMERA_FACING_FRONT
            ? CameraSourceConfig.CAMERA_FACING_BACK
            : CameraSourceConfig.CAMERA_FACING_FRONT;

    createThenStartCameraXSource();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (cameraXSource != null
        && PreferenceUtils.getCustomObjectDetectorOptionsForLivePreview(this, localModel)
            .equals(customObjectDetectorOptions)
        && PreferenceUtils.getCameraXTargetResolution(getApplicationContext(), lensFacing) != null
        && Objects.requireNonNull(
                PreferenceUtils.getCameraXTargetResolution(getApplicationContext(), lensFacing))
            .equals(targetResolution)) {
      cameraXSource.start();
    } else {
      createThenStartCameraXSource();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (cameraXSource != null) {
      cameraXSource.stop();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (cameraXSource != null) {
      cameraXSource.close();
    }
  }

  private void createThenStartCameraXSource() {
    if (cameraXSource != null) {
      cameraXSource.close();
    }
    customObjectDetectorOptions =
        PreferenceUtils.getCustomObjectDetectorOptionsForLivePreview(
            getApplicationContext(), localModel);
    ObjectDetector objectDetector = ObjectDetection.getClient(customObjectDetectorOptions);
    CameraSourceConfig.Builder builder =
        new CameraSourceConfig.Builder(
                getApplicationContext(), objectDetector, detectionTaskCallback)
            .setFacing(lensFacing);
    targetResolution =
        PreferenceUtils.getCameraXTargetResolution(getApplicationContext(), lensFacing);
    if (targetResolution != null) {
      builder.setRequestedPreviewSize(targetResolution.getWidth(), targetResolution.getHeight());
    }
    cameraXSource = new CameraXSource(builder.build(), previewView);
    needUpdateGraphicOverlayImageSourceInfo = true;
    cameraXSource.start();
  }

  private void onDetectionTaskSuccess(List<DetectedObject> results) {
    graphicOverlay.clear();
    if (needUpdateGraphicOverlayImageSourceInfo) {
      Size size = cameraXSource.getPreviewSize();
      if (size != null) {
        Log.d(TAG, "preview width: " + size.getWidth());
        Log.d(TAG, "preview height: " + size.getHeight());
        boolean isImageFlipped =
            cameraXSource.getCameraFacing() == CameraSourceConfig.CAMERA_FACING_FRONT;
        if (isPortraitMode()) {
          // Swap width and height sizes when in portrait, since it will be rotated by
          // 90 degrees. The camera preview and the image being processed have the same size.
          graphicOverlay.setImageSourceInfo(size.getHeight(), size.getWidth(), isImageFlipped);
        } else {
          graphicOverlay.setImageSourceInfo(size.getWidth(), size.getHeight(), isImageFlipped);
        }
        needUpdateGraphicOverlayImageSourceInfo = false;
      } else {
        Log.d(TAG, "previewsize is null");
      }
    }
    Log.v(TAG, "Number of object been detected: " + results.size());
    for (DetectedObject object : results) {
      graphicOverlay.add(new ObjectGraphic(graphicOverlay, object));
    }
    graphicOverlay.add(new InferenceInfoGraphic(graphicOverlay));
    graphicOverlay.postInvalidate();
  }

  private void onDetectionTaskFailure(Exception e) {
    graphicOverlay.clear();
    graphicOverlay.postInvalidate();
    String error = "Failed to process. Error: " + e.getLocalizedMessage();
    Toast.makeText(
            graphicOverlay.getContext(), error + "\nCause: " + e.getCause(), Toast.LENGTH_SHORT)
        .show();
    Log.d(TAG, error);
  }

  private boolean isPortraitMode() {
    return getApplicationContext().getResources().getConfiguration().orientation
        != Configuration.ORIENTATION_LANDSCAPE;
  }
}

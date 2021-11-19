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

package com.google.mlkit.vision.automl.demo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory;
import com.google.android.gms.common.annotation.KeepName;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.common.model.CustomRemoteModel;
import com.google.mlkit.linkfirebase.FirebaseModelSource;
import com.google.mlkit.vision.automl.demo.automl.AutoMLImageLabelerProcessor;
import com.google.mlkit.vision.automl.demo.automl.AutoMLImageLabelerProcessor.Mode;
import com.google.mlkit.vision.automl.demo.object.ObjectDetectorProcessor;
import com.google.mlkit.vision.automl.demo.preference.PreferenceUtils;
import com.google.mlkit.vision.automl.demo.preference.SettingsActivity;
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions;
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions;
import java.util.ArrayList;
import java.util.List;

/** Live preview demo app for ML Kit APIs using CameraX. */
@KeepName
@RequiresApi(VERSION_CODES.LOLLIPOP)
public final class CameraXLivePreviewActivity extends AppCompatActivity
    implements OnRequestPermissionsResultCallback,
        OnItemSelectedListener,
        CompoundButton.OnCheckedChangeListener {
  private static final String TAG = "CameraXLivePreview";
  private static final int PERMISSION_REQUESTS = 1;
  private static final String CUSTOM_AUTOML_LABELING = "Custom AutoML Image Labeling";
  private static final String CUSTOM_AUTOML_OBJECT_DETECTION = "Custom AutoML Object Detection";

  private static final String STATE_SELECTED_MODEL = "selected_model";
  private static final String STATE_LENS_FACING = "lens_facing";

  private PreviewView previewView;
  private GraphicOverlay graphicOverlay;

  @Nullable private ProcessCameraProvider cameraProvider;
  @Nullable private Preview previewUseCase;
  @Nullable private ImageAnalysis analysisUseCase;
  @Nullable private VisionImageProcessor imageProcessor;
  private boolean needUpdateGraphicOverlayImageSourceInfo;

  private String selectedModel = CUSTOM_AUTOML_LABELING;
  private int lensFacing = CameraSelector.LENS_FACING_BACK;
  private CameraSelector cameraSelector;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(TAG, "onCreate");

    if (savedInstanceState != null) {
      selectedModel = savedInstanceState.getString(STATE_SELECTED_MODEL, CUSTOM_AUTOML_LABELING);
      lensFacing = savedInstanceState.getInt(STATE_LENS_FACING, CameraSelector.LENS_FACING_BACK);
    }
    cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

    setContentView(R.layout.activity_camerax_live_preview);
    previewView = findViewById(R.id.preview_view);
    if (previewView == null) {
      Log.d(TAG, "previewView is null");
    }
    graphicOverlay = findViewById(R.id.graphic_overlay);
    if (graphicOverlay == null) {
      Log.d(TAG, "graphicOverlay is null");
    }

    Spinner spinner = findViewById(R.id.spinner);
    List<String> options = new ArrayList<>();
    options.add(CUSTOM_AUTOML_LABELING);
    options.add(CUSTOM_AUTOML_OBJECT_DETECTION);

    // Creating adapter for spinner
    ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, R.layout.spinner_style, options);
    // Drop down layout style - list view with radio button
    dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    // attaching data adapter to spinner
    spinner.setAdapter(dataAdapter);
    spinner.setOnItemSelectedListener(this);

    ToggleButton facingSwitch = findViewById(R.id.facing_switch);
    facingSwitch.setOnCheckedChangeListener(this);

    new ViewModelProvider(this, AndroidViewModelFactory.getInstance(getApplication()))
        .get(CameraXViewModel.class)
        .getProcessCameraProvider()
        .observe(
            this,
            provider -> {
              cameraProvider = provider;
              if (allPermissionsGranted()) {
                bindAllCameraUseCases();
              }
            });

    ImageView settingsButton = findViewById(R.id.settings_button);
    settingsButton.setOnClickListener(
        v -> {
          Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
          intent.putExtra(
              SettingsActivity.EXTRA_LAUNCH_SOURCE,
              SettingsActivity.LaunchSource.CAMERAX_LIVE_PREVIEW);
          startActivity(intent);
        });

    if (!allPermissionsGranted()) {
      getRuntimePermissions();
    }
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putString(STATE_SELECTED_MODEL, selectedModel);
    bundle.putInt(STATE_LENS_FACING, lensFacing);
  }

  @Override
  public synchronized void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
    // An item was selected. You can retrieve the selected item using
    // parent.getItemAtPosition(pos)
    selectedModel = parent.getItemAtPosition(pos).toString();
    Log.d(TAG, "Selected model: " + selectedModel);
    bindAnalysisUseCase();
  }

  @Override
  public void onNothingSelected(AdapterView<?> parent) {
    // Do nothing.
  }

  @Override
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    Log.d(TAG, "Set facing");
    if (cameraProvider == null) {
      return;
    }

    int newLensFacing =
        lensFacing == CameraSelector.LENS_FACING_FRONT
            ? CameraSelector.LENS_FACING_BACK
            : CameraSelector.LENS_FACING_FRONT;
    CameraSelector newCameraSelector =
        new CameraSelector.Builder().requireLensFacing(newLensFacing).build();
    try {
      if (cameraProvider.hasCamera(newCameraSelector)) {
        lensFacing = newLensFacing;
        cameraSelector = newCameraSelector;
        bindAllCameraUseCases();
        return;
      }
    } catch (CameraInfoUnavailableException e) {
      // Falls through
    }
    Toast.makeText(
            getApplicationContext(),
            "This device does not have lens with facing: " + newLensFacing,
            Toast.LENGTH_SHORT)
        .show();
  }

  @Override
  public void onResume() {
    super.onResume();
    bindAllCameraUseCases();
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (imageProcessor != null) {
      imageProcessor.stop();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (imageProcessor != null) {
      imageProcessor.stop();
    }
  }

  private void bindAllCameraUseCases() {
    bindPreviewUseCase();
    bindAnalysisUseCase();
  }

  private void bindPreviewUseCase() {
    if (!PreferenceUtils.isCameraLiveViewportEnabled(this)) {
      return;
    }
    if (cameraProvider == null) {
      return;
    }
    if (previewUseCase != null) {
      cameraProvider.unbind(previewUseCase);
    }

    Preview.Builder builder = new Preview.Builder();
    Size targetResolution = PreferenceUtils.getCameraXTargetResolution(this, lensFacing);
    if (targetResolution != null) {
      builder.setTargetResolution(targetResolution);
    }
    previewUseCase = builder.build();
    previewUseCase.setSurfaceProvider(previewView.getSurfaceProvider());
    cameraProvider.bindToLifecycle(/* lifecycleOwner= */ this, cameraSelector, previewUseCase);
  }

  private void bindAnalysisUseCase() {
    if (cameraProvider == null) {
      return;
    }
    if (analysisUseCase != null) {
      cameraProvider.unbind(analysisUseCase);
    }
    if (imageProcessor != null) {
      imageProcessor.stop();
    }

    try {
      String autoMLRemoteModelName = PreferenceUtils.getAutoMLRemoteModelName(this);
      switch (selectedModel) {
        case CUSTOM_AUTOML_LABELING:
          Log.i(TAG, "Create Custom AutoML Image Label Processor");
          CustomRemoteModel customRemoteModel =
              new CustomRemoteModel.Builder(
                      new FirebaseModelSource.Builder(autoMLRemoteModelName).build())
                  .build();
          CustomImageLabelerOptions customImageLabelerOptions =
              new CustomImageLabelerOptions.Builder(customRemoteModel)
                  .setConfidenceThreshold(0)
                  .build();
          imageProcessor =
              new AutoMLImageLabelerProcessor(
                  this, customRemoteModel, customImageLabelerOptions, Mode.LIVE_PREVIEW);
          break;
        case CUSTOM_AUTOML_OBJECT_DETECTION:
          Log.i(TAG, "Using Custom AutoML Object Detector Processor");
          CustomRemoteModel customODTRemoteModel =
              new CustomRemoteModel.Builder(
                      new FirebaseModelSource.Builder(autoMLRemoteModelName).build())
                  .build();
          CustomObjectDetectorOptions customAutoMLODTOptions =
              new CustomObjectDetectorOptions.Builder(customODTRemoteModel)
                  .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                  .enableClassification()
                  .setClassificationConfidenceThreshold(0)
                  .setMaxPerObjectLabelCount(1)
                  .build();
          imageProcessor =
              new ObjectDetectorProcessor(this, customODTRemoteModel, customAutoMLODTOptions);
          break;
        default:
          throw new IllegalStateException("Invalid model name");
      }
    } catch (RuntimeException e) {
      Log.e(TAG, "Can not create image processor: " + selectedModel, e);
      Toast.makeText(
              getApplicationContext(),
              "Can not create image processor: " + e.getLocalizedMessage(),
              Toast.LENGTH_LONG)
          .show();
      return;
    }

    ImageAnalysis.Builder builder = new ImageAnalysis.Builder();
    Size targetResolution = PreferenceUtils.getCameraXTargetResolution(this, lensFacing);
    if (targetResolution != null) {
      builder.setTargetResolution(targetResolution);
    }
    analysisUseCase = builder.build();

    needUpdateGraphicOverlayImageSourceInfo = true;
    analysisUseCase.setAnalyzer(
        // imageProcessor.processImageProxy will use another thread to run the detection underneath,
        // thus we can just runs the analyzer itself on main thread.
        ContextCompat.getMainExecutor(this),
        imageProxy -> {
          if (needUpdateGraphicOverlayImageSourceInfo) {
            boolean isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT;
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            if (rotationDegrees == 0 || rotationDegrees == 180) {
              graphicOverlay.setImageSourceInfo(
                  imageProxy.getWidth(), imageProxy.getHeight(), isImageFlipped);
            } else {
              graphicOverlay.setImageSourceInfo(
                  imageProxy.getHeight(), imageProxy.getWidth(), isImageFlipped);
            }
            needUpdateGraphicOverlayImageSourceInfo = false;
          }
          try {
            imageProcessor.processImageProxy(imageProxy, graphicOverlay);
          } catch (MlKitException e) {
            Log.e(TAG, "Failed to process image. Error: " + e.getLocalizedMessage());
            Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT)
                .show();
          }
        });

    cameraProvider.bindToLifecycle(/* lifecycleOwner= */ this, cameraSelector, analysisUseCase);
  }

  private String[] getRequiredPermissions() {
    try {
      PackageInfo info =
          this.getPackageManager()
              .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
      String[] ps = info.requestedPermissions;
      if (ps != null && ps.length > 0) {
        return ps;
      } else {
        return new String[0];
      }
    } catch (Exception e) {
      return new String[0];
    }
  }

  private boolean allPermissionsGranted() {
    for (String permission : getRequiredPermissions()) {
      if (!isPermissionGranted(this, permission)) {
        return false;
      }
    }
    return true;
  }

  private void getRuntimePermissions() {
    List<String> allNeededPermissions = new ArrayList<>();
    for (String permission : getRequiredPermissions()) {
      if (!isPermissionGranted(this, permission)) {
        allNeededPermissions.add(permission);
      }
    }

    if (!allNeededPermissions.isEmpty()) {
      ActivityCompat.requestPermissions(
          this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    Log.i(TAG, "Permission granted!");
    if (allPermissionsGranted()) {
      bindAllCameraUseCases();
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  private static boolean isPermissionGranted(Context context, String permission) {
    if (ContextCompat.checkSelfPermission(context, permission)
        == PackageManager.PERMISSION_GRANTED) {
      Log.i(TAG, "Permission granted: " + permission);
      return true;
    }
    Log.i(TAG, "Permission NOT granted: " + permission);
    return false;
  }
}

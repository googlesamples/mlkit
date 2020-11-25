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

import static java.lang.Math.max;

import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.Toast;
import com.google.android.gms.common.annotation.KeepName;
import com.google.mlkit.common.model.CustomRemoteModel;
import com.google.mlkit.linkfirebase.FirebaseModelSource;
import com.google.mlkit.vision.automl.demo.automl.AutoMLImageLabelerProcessor;
import com.google.mlkit.vision.automl.demo.automl.AutoMLImageLabelerProcessor.Mode;
import com.google.mlkit.vision.automl.demo.object.ObjectDetectorProcessor;
import com.google.mlkit.vision.automl.demo.preference.PreferenceUtils;
import com.google.mlkit.vision.automl.demo.preference.SettingsActivity;
import com.google.mlkit.vision.automl.demo.preference.SettingsActivity.LaunchSource;
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions;
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Activity demonstrating different image detector features with a still image from camera. */
@KeepName
public final class StillImageActivity extends AppCompatActivity {

  private static final String TAG = "StillImageActivity";

  private static final String CUSTOM_AUTOML_LABELING = "Custom AutoML Image Labeling";
  private static final String CUSTOM_AUTOML_OBJECT_DETECTION = "Custom AutoML Object Detection";

  private static final String SIZE_SCREEN = "w:screen"; // Match screen width
  private static final String SIZE_1024_768 = "w:1024"; // ~1024*768 in a normal ratio
  private static final String SIZE_640_480 = "w:640"; // ~640*480 in a normal ratio

  private static final String KEY_IMAGE_URI = "com.google.mlkit.vision.automl.demo.KEY_IMAGE_URI";
  private static final String KEY_SELECTED_SIZE =
      "com.google.mlkit.vision.automl.demo.KEY_SELECTED_SIZE";

  private static final int REQUEST_IMAGE_CAPTURE = 1001;
  private static final int REQUEST_CHOOSE_IMAGE = 1002;

  private ImageView preview;
  private GraphicOverlay graphicOverlay;
  private String selectedMode = CUSTOM_AUTOML_LABELING;
  private String selectedSize = SIZE_SCREEN;

  boolean isLandScape;

  private Uri imageUri;
  private int imageMaxWidth;
  private int imageMaxHeight;
  private VisionImageProcessor imageProcessor;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_still_image);

    findViewById(R.id.select_image_button)
        .setOnClickListener(
            view -> {
              // Menu for selecting either: a) take new photo b) select from existing
              PopupMenu popup = new PopupMenu(StillImageActivity.this, view);
              popup.setOnMenuItemClickListener(
                  menuItem -> {
                    int itemId = menuItem.getItemId();
                    if (itemId == R.id.select_images_from_local) {
                      startChooseImageIntentForResult();
                      return true;
                    } else if (itemId == R.id.take_photo_using_camera) {
                      startCameraIntentForResult();
                      return true;
                    }
                    return false;
                  });
              MenuInflater inflater = popup.getMenuInflater();
              inflater.inflate(R.menu.camera_button_menu, popup.getMenu());
              popup.show();
            });
    preview = findViewById(R.id.preview);
    graphicOverlay = findViewById(R.id.graphic_overlay);

    populateFeatureSelector();
    populateSizeSelector();

    isLandScape =
        (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

    if (savedInstanceState != null) {
      imageUri = savedInstanceState.getParcelable(KEY_IMAGE_URI);
      selectedSize = savedInstanceState.getString(KEY_SELECTED_SIZE);
    }

    View rootView = findViewById(R.id.root);
    rootView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        imageMaxWidth = rootView.getWidth();
        imageMaxHeight = rootView.getHeight() - findViewById(R.id.control).getHeight();
        if (SIZE_SCREEN.equals(selectedSize)) {
          tryReloadAndDetectInImage();
        }
      }
    });

    ImageView settingsButton = findViewById(R.id.settings_button);
    settingsButton.setOnClickListener(
        v -> {
          Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
          intent.putExtra(SettingsActivity.EXTRA_LAUNCH_SOURCE, LaunchSource.STILL_IMAGE);
          startActivity(intent);
        });
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "onResume");
    createImageProcessor();
    tryReloadAndDetectInImage();
  }

  private void populateFeatureSelector() {
    Spinner featureSpinner = findViewById(R.id.feature_selector);
    List<String> options = new ArrayList<>();
    options.add(CUSTOM_AUTOML_LABELING);
    options.add(CUSTOM_AUTOML_OBJECT_DETECTION);

    // Creating adapter for featureSpinner
    ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, R.layout.spinner_style, options);
    // Drop down layout style - list view with radio button
    dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    // attaching data adapter to spinner
    featureSpinner.setAdapter(dataAdapter);
    featureSpinner.setOnItemSelectedListener(
        new OnItemSelectedListener() {

          @Override
          public void onItemSelected(
              AdapterView<?> parentView, View selectedItemView, int pos, long id) {
            selectedMode = parentView.getItemAtPosition(pos).toString();
            createImageProcessor();
            tryReloadAndDetectInImage();
          }

          @Override
          public void onNothingSelected(AdapterView<?> arg0) {}
        });
  }

  private void populateSizeSelector() {
    Spinner sizeSpinner = findViewById(R.id.size_selector);
    List<String> options = new ArrayList<>();
    options.add(SIZE_SCREEN);
    options.add(SIZE_1024_768);
    options.add(SIZE_640_480);

    // Creating adapter for featureSpinner
    ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, R.layout.spinner_style, options);
    // Drop down layout style - list view with radio button
    dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    // attaching data adapter to spinner
    sizeSpinner.setAdapter(dataAdapter);
    sizeSpinner.setOnItemSelectedListener(
        new OnItemSelectedListener() {

          @Override
          public void onItemSelected(
              AdapterView<?> parentView, View selectedItemView, int pos, long id) {
            selectedSize = parentView.getItemAtPosition(pos).toString();
            createImageProcessor();
            tryReloadAndDetectInImage();
          }

          @Override
          public void onNothingSelected(AdapterView<?> arg0) {}
        });
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putParcelable(KEY_IMAGE_URI, imageUri);
    outState.putString(KEY_SELECTED_SIZE, selectedSize);
  }

  private void startCameraIntentForResult() {
    // Clean up last time's image
    imageUri = null;
    preview.setImageBitmap(null);

    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
      ContentValues values = new ContentValues();
      values.put(MediaStore.Images.Media.TITLE, "New Picture");
      values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
      imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
      takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
      startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
    }
  }

  private void startChooseImageIntentForResult() {
    Intent intent = new Intent();
    intent.setType("image/*");
    intent.setAction(Intent.ACTION_GET_CONTENT);
    startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_CHOOSE_IMAGE);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
      tryReloadAndDetectInImage();
    } else if (requestCode == REQUEST_CHOOSE_IMAGE && resultCode == RESULT_OK) {
      // In this case, imageUri is returned by the chooser, save it.
      imageUri = data.getData();
      tryReloadAndDetectInImage();
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  private void tryReloadAndDetectInImage() {
    Log.d(TAG, "Try reload and detect image");
    try {
      if (imageUri == null) {
        return;
      }

      if (SIZE_SCREEN.equals(selectedSize) && imageMaxWidth == 0) {
        // UI layout has not finished yet, will reload once it's ready.
        return;
      }

      Bitmap imageBitmap = BitmapUtils.getBitmapFromContentUri(getContentResolver(), imageUri);
      if (imageBitmap == null) {
        return;
      }

      // Clear the overlay first
      graphicOverlay.clear();

      // Get the dimensions of the image view
      Pair<Integer, Integer> targetedSize = getTargetedWidthHeight();

      // Determine how much to scale down the image
      float scaleFactor =
          max(
              (float) imageBitmap.getWidth() / (float) targetedSize.first,
              (float) imageBitmap.getHeight() / (float) targetedSize.second);

      Bitmap resizedBitmap =
          Bitmap.createScaledBitmap(
              imageBitmap,
              (int) (imageBitmap.getWidth() / scaleFactor),
              (int) (imageBitmap.getHeight() / scaleFactor),
              true);

      preview.setImageBitmap(resizedBitmap);

      if (imageProcessor != null) {
        graphicOverlay.setImageSourceInfo(
            resizedBitmap.getWidth(), resizedBitmap.getHeight(), /* isFlipped= */ false);
        imageProcessor.processBitmap(resizedBitmap, graphicOverlay);
      } else {
        Log.e(TAG, "Null imageProcessor, please check adb logs for imageProcessor creation error");
      }
    } catch (IOException e) {
      Log.e(TAG, "Error retrieving saved image");
      imageUri = null;
    }
  }

  private Pair<Integer, Integer> getTargetedWidthHeight() {
    int targetWidth;
    int targetHeight;

    switch (selectedSize) {
      case SIZE_SCREEN:
        targetWidth = imageMaxWidth;
        targetHeight = imageMaxHeight;
        break;
      case SIZE_640_480:
        targetWidth = isLandScape ? 640 : 480;
        targetHeight = isLandScape ? 480 : 640;
        break;
      case SIZE_1024_768:
        targetWidth = isLandScape ? 1024 : 768;
        targetHeight = isLandScape ? 768 : 1024;
        break;
      default:
        throw new IllegalStateException("Unknown size");
    }

    return new Pair<>(targetWidth, targetHeight);
  }

  private void createImageProcessor() {
    try {
      String autoMLRemoteModelName = PreferenceUtils.getAutoMLRemoteModelName(this);
      switch (selectedMode) {
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
                  this, customRemoteModel, customImageLabelerOptions, Mode.STILL_IMAGE);
          break;
        case CUSTOM_AUTOML_OBJECT_DETECTION:
          Log.i(TAG, "Using Custom AutoML Object Detector Processor");
          CustomRemoteModel customODTRemoteModel =
              new CustomRemoteModel.Builder(
                      new FirebaseModelSource.Builder(autoMLRemoteModelName).build())
                  .build();
          CustomObjectDetectorOptions customAutoMLODTOptions =
              new CustomObjectDetectorOptions.Builder(customODTRemoteModel)
                  .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                  .enableClassification()
                  .setClassificationConfidenceThreshold(0)
                  .setMaxPerObjectLabelCount(1)
                  .build();
          imageProcessor =
              new ObjectDetectorProcessor(this, customODTRemoteModel, customAutoMLODTOptions);
          break;
        default:
          Log.e(TAG, "Unknown selectedMode: " + selectedMode);
      }
    } catch (RuntimeException e) {
      Log.e(TAG, "Can not create image processor: " + selectedMode, e);
      Toast.makeText(
              getApplicationContext(),
              "Can not create image processor: " + e.getMessage(),
              Toast.LENGTH_LONG)
          .show();
    }
  }
}

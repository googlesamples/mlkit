/*
 * Copyright 2025 Google LLC. All rights reserved.
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

package com.google.mlkit.genai.demo.java;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static java.util.Objects.requireNonNull;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.GetContent;
import androidx.core.content.ContextCompat;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.StreamingCallback;
import com.google.mlkit.genai.demo.ContentItem.ImageItem;
import com.google.mlkit.genai.demo.R;
import com.google.mlkit.genai.imagedescription.ImageDescriber;
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions;
import com.google.mlkit.genai.imagedescription.ImageDescription;
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest;
import java.io.IOException;
import java.util.List;

/** Demonstrates the Image Description API usage. */
public class ImageDescriptionActivity extends BaseActivity<ImageItem> {

  private Button selectImageButton;
  private ImageDescriber imageDescriber;
  private ActivityResultLauncher<String> selectImageLauncher;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    selectImageLauncher =
        registerForActivityResult(
            new GetContent(),
            imageUri -> {
              if (imageUri != null) {
                onSend(ImageItem.Companion.fromRequest(imageUri));
              } else {
                Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
              }
            });
    selectImageButton = findViewById(R.id.select_image_button);
    selectImageButton.setOnClickListener(view -> selectImageLauncher.launch("image/*"));

    initImageDescriptor();
  }

  @Override
  protected int getLayoutResId() {
    return R.layout.activity_image_description;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuItem item = menu.findItem(R.id.action_batch_run);
    if (item != null) {
      item.setVisible(false);
    }
    return true;
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (imageDescriber != null) {
      imageDescriber.close();
    }
  }

  @Override
  protected ListenableFuture<String> getBaseModelName() {
    return imageDescriber.getBaseModelName();
  }

  @Override
  protected ListenableFuture<Integer> checkFeatureStatus() {
    return imageDescriber.checkFeatureStatus();
  }

  @Override
  protected ListenableFuture<Void> downloadFeature(DownloadCallback callback) {
    return imageDescriber.downloadFeature(callback);
  }

  @Override
  protected ListenableFuture<List<String>> runInferenceImpl(
      ImageItem request, @Nullable StreamingCallback streamingCallback) {
    try {
      Bitmap bitmap =
          ImageDecoder.decodeBitmap(
              ImageDecoder.createSource(getContentResolver(), request.getImageUri()));
      ImageDescriptionRequest imageDescriptionRequest =
          ImageDescriptionRequest.builder(bitmap).build();
      return Futures.transform(
          streamingCallback != null
              ? imageDescriber.runInference(imageDescriptionRequest, streamingCallback)
              : imageDescriber.runInference(imageDescriptionRequest),
          result -> ImmutableList.of(requireNonNull(result).getDescription()),
          ContextCompat.getMainExecutor(this));
    } catch (IOException e) {
      return immediateFailedFuture(e);
    }
  }

  @Override
  protected void startGeneratingUi() {
    super.startGeneratingUi();
    selectImageButton.setEnabled(false);
    selectImageButton.setText(R.string.generating);
  }

  @Override
  protected void endGeneratingUi(String debugInfo) {
    super.endGeneratingUi(debugInfo);
    selectImageButton.setEnabled(true);
    selectImageButton.setText(R.string.button_select_image);
  }

  @Override
  protected List<String> runInferenceForBatchTask(String request) {
    throw new UnsupportedOperationException("Not supported");
  }

  private void initImageDescriptor() {
    if (imageDescriber != null) {
      imageDescriber.close();
    }
    ImageDescriberOptions options = ImageDescriberOptions.builder(this).build();
    imageDescriber = ImageDescription.getClient(options);
    resetProcessor();
  }
}

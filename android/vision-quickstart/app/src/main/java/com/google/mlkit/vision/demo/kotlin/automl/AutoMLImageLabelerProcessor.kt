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

package com.google.mlkit.vision.demo.kotlin.automl

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.kotlin.VisionProcessorBase
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.automl.AutoMLImageLabelerLocalModel
import com.google.mlkit.vision.label.automl.AutoMLImageLabelerOptions
import java.io.IOException

/** AutoML image labeler demo.  */
class AutoMLImageLabelerProcessor(context: Context) :
  VisionProcessorBase<List<ImageLabel>>(context) {
  private val imageLabeler: ImageLabeler

  init {
    Log.d(TAG, "Local model used.")
    val localModel = AutoMLImageLabelerLocalModel.Builder()
      .setAssetFilePath("automl/manifest.json")
      .build()
    imageLabeler = ImageLabeling.getClient(
      AutoMLImageLabelerOptions.Builder(localModel).setConfidenceThreshold(0f).build()
    )
  }

  override fun stop() {
    super.stop()
    try {
      imageLabeler.close()
    } catch (e: IOException) {
      Log.e(
        TAG,
        "Exception thrown while trying to close the image labeler",
        e
      )
    }
  }

  override fun detectInImage(image: InputImage): Task<List<ImageLabel>> {
    return imageLabeler.process(image)
  }

  override fun onSuccess(results: List<ImageLabel>, graphicOverlay: GraphicOverlay) {
    graphicOverlay.add(LabelGraphic(graphicOverlay, results))
  }

  override fun onFailure(e: Exception) {
    Log.w(TAG, "Label detection failed.", e)
  }

  companion object {
    private const val TAG = "AutoMLProcessor"
  }
}

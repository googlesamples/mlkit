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

package com.google.mlkit.vision.demo.kotlin.segmenter

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.kotlin.VisionProcessorBase
import com.google.mlkit.vision.demo.preference.PreferenceUtils
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions

/** A processor to run Segmenter.  */
class SegmenterProcessor :
  VisionProcessorBase<SegmentationMask> {
  private val segmenter: Segmenter

  constructor(context: Context) : this(context, /* isStreamMode= */ true)

  constructor(
    context: Context,
    isStreamMode: Boolean
  ) : super(
    context
  ) {
    val optionsBuilder = SelfieSegmenterOptions.Builder()
    optionsBuilder.setDetectorMode(
      if(isStreamMode) SelfieSegmenterOptions.STREAM_MODE
      else SelfieSegmenterOptions.SINGLE_IMAGE_MODE
    )
    if (PreferenceUtils.shouldSegmentationEnableRawSizeMask(context)) {
      optionsBuilder.enableRawSizeMask()
    }

    val options = optionsBuilder.build()
    segmenter = Segmentation.getClient(options)
    Log.d(TAG, "SegmenterProcessor created with option: " + options)
  }

  override fun detectInImage(image: InputImage): Task<SegmentationMask> {
    return segmenter.process(image)
  }

  override fun onSuccess(
    segmentationMask: SegmentationMask,
    graphicOverlay: GraphicOverlay
  ) {
    graphicOverlay.add(
      SegmentationGraphic(
        graphicOverlay,
        segmentationMask
      )
    )
  }

  override fun onFailure(e: Exception) {
    Log.e(TAG, "Segmentation failed: $e")
  }

  companion object {
    private const val TAG = "SegmenterProcessor"
  }
}

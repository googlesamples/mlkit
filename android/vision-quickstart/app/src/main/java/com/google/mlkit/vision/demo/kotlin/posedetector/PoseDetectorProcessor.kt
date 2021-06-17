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

package com.google.mlkit.vision.demo.kotlin.posedetector

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.odml.image.MlImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.java.posedetector.classification.PoseClassifierProcessor
import com.google.mlkit.vision.demo.kotlin.VisionProcessorBase
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase
import java.util.ArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** A processor to run pose detector. */
class PoseDetectorProcessor(
  private val context: Context,
  options: PoseDetectorOptionsBase,
  private val showInFrameLikelihood: Boolean,
  private val visualizeZ: Boolean,
  private val rescaleZForVisualization: Boolean,
  private val runClassification: Boolean,
  private val isStreamMode: Boolean
) : VisionProcessorBase<PoseDetectorProcessor.PoseWithClassification>(context) {

  private val detector: PoseDetector
  private val classificationExecutor: Executor

  private var poseClassifierProcessor: PoseClassifierProcessor? = null

  /** Internal class to hold Pose and classification results. */
  class PoseWithClassification(val pose: Pose, val classificationResult: List<String>)

  init {
    detector = PoseDetection.getClient(options)
    classificationExecutor = Executors.newSingleThreadExecutor()
  }

  override fun stop() {
    super.stop()
    detector.close()
  }

  override fun detectInImage(image: InputImage): Task<PoseWithClassification> {
    return detector
      .process(image)
      .continueWith(
        classificationExecutor,
        { task ->
          val pose = task.getResult()
          var classificationResult: List<String> = ArrayList()
          if (runClassification) {
            if (poseClassifierProcessor == null) {
              poseClassifierProcessor = PoseClassifierProcessor(context, isStreamMode)
            }
            classificationResult = poseClassifierProcessor!!.getPoseResult(pose)
          }
          PoseWithClassification(pose, classificationResult)
        }
      )
  }

  override fun detectInImage(image: MlImage): Task<PoseWithClassification> {
    return detector
      .process(image)
      .continueWith(
        classificationExecutor,
        { task ->
          val pose = task.getResult()
          var classificationResult: List<String> = ArrayList()
          if (runClassification) {
            if (poseClassifierProcessor == null) {
              poseClassifierProcessor = PoseClassifierProcessor(context, isStreamMode)
            }
            classificationResult = poseClassifierProcessor!!.getPoseResult(pose)
          }
          PoseWithClassification(pose, classificationResult)
        }
      )
  }

  override fun onSuccess(
    poseWithClassification: PoseWithClassification,
    graphicOverlay: GraphicOverlay
  ) {
    graphicOverlay.add(
      PoseGraphic(
        graphicOverlay,
        poseWithClassification.pose,
        showInFrameLikelihood,
        visualizeZ,
        rescaleZForVisualization,
        poseWithClassification.classificationResult
      )
    )
  }

  override fun onFailure(e: Exception) {
    Log.e(TAG, "Pose detection failed!", e)
  }

  override fun isMlImageEnabled(context: Context?): Boolean {
    // Use MlImage in Pose Detection by default, change it to OFF to switch to InputImage.
    return true
  }

  companion object {
    private val TAG = "PoseDetectorProcessor"
  }
}

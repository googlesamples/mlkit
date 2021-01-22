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

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.common.primitives.Ints
import com.google.mlkit.vision.common.PointF3D
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import java.lang.Math.max
import java.lang.Math.min
import java.util.Locale

/** Draw the detected pose in preview.  */
class PoseGraphic internal constructor(
  overlay: GraphicOverlay,
  private val pose: Pose,
  private val showInFrameLikelihood: Boolean,
  private val visualizeZ: Boolean,
  private val rescaleZForVisualization: Boolean
) :
  GraphicOverlay.Graphic(overlay) {
  private val leftPaint: Paint
  private val rightPaint: Paint
  private val whitePaint: Paint
  private var zMin = Float.MAX_VALUE
  private var zMax = Float.MIN_VALUE

  override fun draw(canvas: Canvas) {
    val landmarks =
      pose.allPoseLandmarks
    if (landmarks.isEmpty()) {
      return
    }
    // Draw all the points
    for (landmark in landmarks) {
      drawPoint(canvas, landmark, whitePaint)
      if (visualizeZ && rescaleZForVisualization) {
        zMin = min(zMin, landmark.position3D.z)
        zMax = max(zMax, landmark.position3D.z)
      }
    }
    val leftShoulder =
      pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
    val rightShoulder =
      pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
    val leftElbow =
      pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
    val rightElbow =
      pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
    val leftWrist =
      pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
    val rightWrist =
      pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
    val leftHip =
      pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
    val rightHip =
      pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
    val leftKnee =
      pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
    val rightKnee =
      pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
    val leftAnkle =
      pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
    val rightAnkle =
      pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
    val leftPinky =
      pose.getPoseLandmark(PoseLandmark.LEFT_PINKY)
    val rightPinky =
      pose.getPoseLandmark(PoseLandmark.RIGHT_PINKY)
    val leftIndex =
      pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)
    val rightIndex =
      pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)
    val leftThumb =
      pose.getPoseLandmark(PoseLandmark.LEFT_THUMB)
    val rightThumb =
      pose.getPoseLandmark(PoseLandmark.RIGHT_THUMB)
    val leftHeel =
      pose.getPoseLandmark(PoseLandmark.LEFT_HEEL)
    val rightHeel =
      pose.getPoseLandmark(PoseLandmark.RIGHT_HEEL)
    val leftFootIndex =
      pose.getPoseLandmark(PoseLandmark.LEFT_FOOT_INDEX)
    val rightFootIndex =
      pose.getPoseLandmark(PoseLandmark.RIGHT_FOOT_INDEX)
    drawLine(canvas, leftShoulder!!, rightShoulder!!, whitePaint)
    drawLine(canvas, leftHip!!, rightHip!!, whitePaint)
    // Left body
    drawLine(canvas, leftShoulder, leftElbow!!, leftPaint)
    drawLine(canvas, leftElbow, leftWrist!!, leftPaint)
    drawLine(canvas, leftShoulder, leftHip, leftPaint)
    drawLine(canvas, leftHip, leftKnee!!, leftPaint)
    drawLine(canvas, leftKnee, leftAnkle!!, leftPaint)
    drawLine(canvas, leftWrist, leftThumb!!, leftPaint)
    drawLine(canvas, leftWrist, leftPinky!!, leftPaint)
    drawLine(canvas, leftWrist, leftIndex!!, leftPaint)
    drawLine(canvas, leftIndex, leftPinky!!, leftPaint)
    drawLine(canvas, leftAnkle, leftHeel!!, leftPaint)
    drawLine(canvas, leftHeel, leftFootIndex!!, leftPaint)
    // Right body
    drawLine(canvas, rightShoulder, rightElbow!!, rightPaint)
    drawLine(canvas, rightElbow, rightWrist!!, rightPaint)
    drawLine(canvas, rightShoulder, rightHip, rightPaint)
    drawLine(canvas, rightHip, rightKnee!!, rightPaint)
    drawLine(canvas, rightKnee, rightAnkle!!, rightPaint)
    drawLine(canvas, rightWrist, rightThumb!!, rightPaint)
    drawLine(canvas, rightWrist, rightPinky!!, rightPaint)
    drawLine(canvas, rightWrist, rightIndex!!, rightPaint)
    drawLine(canvas, rightIndex, rightPinky!!, rightPaint)
    drawLine(canvas, rightAnkle, rightHeel!!, rightPaint)
    drawLine(canvas, rightHeel, rightFootIndex!!, rightPaint)

    // Draw inFrameLikelihood for all points
    if (showInFrameLikelihood) {
      for (landmark in landmarks) {
        canvas.drawText(
          String.format(Locale.US, "%.2f", landmark.inFrameLikelihood),
          translateX(landmark.position.x),
          translateY(landmark.position.y),
          whitePaint
        )
      }
    }
  }

  fun drawPoint(canvas: Canvas, landmark: PoseLandmark, paint: Paint) {
    val point = landmark.position
    canvas.drawCircle(
      translateX(point.x),
      translateY(point.y),
      DOT_RADIUS,
      paint
    )
  }

  fun drawLine(
    canvas: Canvas,
    startLandmark: PoseLandmark,
    endLandmark: PoseLandmark,
    paint: Paint
  ) {
    // When visualizeZ is true, sets up the paint to draw body line in different colors based on
    // their z values.
    if (visualizeZ) {
      val start: PointF3D = startLandmark.position3D
      val end: PointF3D = endLandmark.position3D

      // Gets the range of z value.
      val zLowerBoundInScreenPixel: Float
      val zUpperBoundInScreenPixel: Float
      if (rescaleZForVisualization) {
        zLowerBoundInScreenPixel = min(-0.001f, scale(zMin))
        zUpperBoundInScreenPixel = max(0.001f, scale(zMax))
      } else {
        // By default, assume the range of z value in screen pixel is [-canvasWidth, canvasWidth].
        val defaultRangeFactor = 1f
        zLowerBoundInScreenPixel = -defaultRangeFactor * canvas.getWidth()
        zUpperBoundInScreenPixel = defaultRangeFactor * canvas.getWidth()
      }

      // Gets average z for the current body line
      val avgZInImagePixel: Float = (start.getZ() + end.getZ()) / 2
      val zInScreenPixel: Float = scale(avgZInImagePixel)
      if (zInScreenPixel < 0) {
        // Sets up the paint to draw the body line in red if it is in front of the z origin.
        // Maps values within [zLowerBoundInScreenPixel, 0) to [255, 0) and use it to control the
        // color. The larger the value is, the more red it will be.
        var v = (zInScreenPixel / zLowerBoundInScreenPixel * 255).toInt()
        v = Ints.constrainToRange(v, 0, 255)
        paint.setARGB(255, 255, 255 - v, 255 - v)
      } else {
        // Sets up the paint to draw the body line in blue if it is behind the z origin.
        // Maps values within [0, zUpperBoundInScreenPixel] to [0, 255] and use it to control the
        // color. The larger the value is, the more blue it will be.
        var v = (zInScreenPixel / zUpperBoundInScreenPixel * 255).toInt()
        v = Ints.constrainToRange(v, 0, 255)
        paint.setARGB(255, 255 - v, 255 - v, 255)
      }
      canvas.drawLine(
        translateX(start.x),
        translateY(start.y),
        translateX(end.x),
        translateY(end.y),
        paint
      )
    } else {
      val start = startLandmark.position
      val end = endLandmark.position
      canvas.drawLine(
        translateX(start.x), translateY(start.y), translateX(end.x), translateY(end.y), paint
      )
    }
  }

  companion object {
    private const val DOT_RADIUS = 8.0f
    private const val IN_FRAME_LIKELIHOOD_TEXT_SIZE = 30.0f
    private const val STROKE_WIDTH = 10.0f
  }

  init {
    whitePaint = Paint()
    whitePaint.strokeWidth = STROKE_WIDTH
    whitePaint.color = Color.WHITE
    whitePaint.textSize = IN_FRAME_LIKELIHOOD_TEXT_SIZE
    leftPaint = Paint()
    leftPaint.strokeWidth = STROKE_WIDTH
    leftPaint.color = Color.GREEN
    rightPaint = Paint()
    rightPaint.strokeWidth = STROKE_WIDTH
    rightPaint.color = Color.YELLOW
  }
}

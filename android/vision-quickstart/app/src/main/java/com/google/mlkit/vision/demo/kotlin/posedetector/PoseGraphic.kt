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
import android.graphics.PointF
import android.text.TextUtils
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.InferenceInfoGraphic
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import java.util.Locale
import kotlin.math.atan2

/** Draw the detected pose in preview.  */
class PoseGraphic internal constructor(
        overlay: GraphicOverlay,
        private val pose: Pose,
        private val showInFrameLikelihood: Boolean
) :
        GraphicOverlay.Graphic(overlay) {
  private val leftPaint: Paint
  private val rightPaint: Paint
  private val whitePaint: Paint
  private val tipPaint: Paint
  override fun draw(canvas: Canvas) {
    val landmarks =
            pose.allPoseLandmarks
    if (landmarks.isEmpty()) {
      return
    }
    // Draw all the points
    for (landmark in landmarks) {
      drawPoint(canvas, landmark.position, whitePaint)
      if (showInFrameLikelihood) {
        canvas.drawText(
                String.format(Locale.US, "%.2f", landmark.inFrameLikelihood),
                translateX(landmark.position.x),
                translateY(landmark.position.y),
                whitePaint
        )
      }
    }
    val leftShoulder =
            pose.getPoseLandmark(PoseLandmark.Type.LEFT_SHOULDER)
    val rightShoulder =
            pose.getPoseLandmark(PoseLandmark.Type.RIGHT_SHOULDER)
    val leftElbow =
            pose.getPoseLandmark(PoseLandmark.Type.LEFT_ELBOW)
    val rightElbow =
            pose.getPoseLandmark(PoseLandmark.Type.RIGHT_ELBOW)
    val leftWrist =
            pose.getPoseLandmark(PoseLandmark.Type.LEFT_WRIST)
    val rightWrist =
            pose.getPoseLandmark(PoseLandmark.Type.RIGHT_WRIST)
    val leftHip =
            pose.getPoseLandmark(PoseLandmark.Type.LEFT_HIP)
    val rightHip =
            pose.getPoseLandmark(PoseLandmark.Type.RIGHT_HIP)
    val leftKnee =
            pose.getPoseLandmark(PoseLandmark.Type.LEFT_KNEE)
    val rightKnee =
            pose.getPoseLandmark(PoseLandmark.Type.RIGHT_KNEE)
    val leftAnkle =
            pose.getPoseLandmark(PoseLandmark.Type.LEFT_ANKLE)
    val rightAnkle =
            pose.getPoseLandmark(PoseLandmark.Type.RIGHT_ANKLE)
    val leftPinky =
            pose.getPoseLandmark(PoseLandmark.Type.LEFT_PINKY)
    val rightPinky =
            pose.getPoseLandmark(PoseLandmark.Type.RIGHT_PINKY)
    val leftIndex =
            pose.getPoseLandmark(PoseLandmark.Type.LEFT_INDEX)
    val rightIndex =
            pose.getPoseLandmark(PoseLandmark.Type.RIGHT_INDEX)
    val leftThumb =
            pose.getPoseLandmark(PoseLandmark.Type.LEFT_THUMB)
    val rightThumb =
            pose.getPoseLandmark(PoseLandmark.Type.RIGHT_THUMB)
    val leftHeel =
            pose.getPoseLandmark(PoseLandmark.Type.LEFT_HEEL)
    val rightHeel =
            pose.getPoseLandmark(PoseLandmark.Type.RIGHT_HEEL)
    val leftFootIndex =
            pose.getPoseLandmark(PoseLandmark.Type.LEFT_FOOT_INDEX)
    val rightFootIndex =
            pose.getPoseLandmark(PoseLandmark.Type.RIGHT_FOOT_INDEX)
    /////////////////////
    //Calculate whether the hand exceeds the shoulder
    val yRightHand = rightWrist!!.position.y - rightShoulder!!.position.y
    val yLeftHand = leftWrist!!.position.y - leftShoulder!!.position.y
    //Calculate whether the distance between the shoulder and the foot is the same width
    val shoulderDistance = leftShoulder!!.position.x - rightShoulder!!.position.x
    val footDistance = leftAnkle!!.position.x - rightAnkle!!.position.x
    val ratio = footDistance/shoulderDistance
    //angle of point 24-26-28
    val angle24_26_28 = getAngle(rightHip, rightKnee, rightAnkle)

    if(((180-Math.abs(angle24_26_28)) > 15 ||
                    ((Math.abs((leftElbow!!.position.y - leftShoulder.position.y) )> 30)
                            && (Math.abs((rightElbow!!.position.y - rightShoulder.position.y) ) > 30)))){
      isReadyToSpin = false
      lineOneText = "Please stand up straight"
      lineTwoText = "Raise your arms to form a T"
    } else if ((Math.abs((leftElbow!!.position.y - leftShoulder.position.y)) < 50)
            && (Math.abs((rightElbow!!.position.y - rightShoulder.position.y)) < 50)) {
      reInitParams()
      isReadyToSpin = true
      lineOneText = "FIT CHECK TIME!"
      lineTwoText = "Let's see a spin!"
      val currentShoulderRelationship = leftShoulder.position.x - rightShoulder.position.x
      if (isReadyToSpin && currentShoulderRelationship < 0) {
        reInitParams()
        isBackwards = true
        isForwards = false
        lineOneText = "Cute butt!"
        lineTwoText = "Keep spinning!"
      }
      if (currentShoulderRelationship > 0) {
        isForwards = true
        isBackwards = false
      }
      if (backwardsCount == forwardsCount && isBackwards) {
        backwardsCount++
      }
    }
    if (backwardsCount > forwardsCount && isForwards) {
      forwardsCount++
    }

    drawText(canvas, lineOneText,1)
    drawText(canvas, lineTwoText,2)
    drawText(canvas, "Spin count: " + forwardsCount.toString(), 3)
    /////////////////////
    drawLine(canvas, leftShoulder!!.position, rightShoulder!!.position, whitePaint)
    drawLine(canvas, leftHip!!.position, rightHip!!.position, whitePaint)
    // Left body
    drawLine(canvas, leftShoulder.position, leftElbow!!.position, leftPaint)
    drawLine(canvas, leftElbow.position, leftWrist!!.position, leftPaint)
    drawLine(canvas, leftShoulder.position, leftHip.position, leftPaint)
    drawLine(canvas, leftHip.position, leftKnee!!.position, leftPaint)
    drawLine(canvas, leftKnee.position, leftAnkle!!.position, leftPaint)
    drawLine(canvas, leftWrist.position, leftThumb!!.position, leftPaint)
    drawLine(canvas, leftWrist.position, leftPinky!!.position, leftPaint)
    drawLine(canvas, leftWrist.position, leftIndex!!.position, leftPaint)
    drawLine(canvas, leftAnkle.position, leftHeel!!.position, leftPaint)
    drawLine(canvas, leftHeel.position, leftFootIndex!!.position, leftPaint)
    // Right body
    drawLine(canvas, rightShoulder.position, rightElbow!!.position, rightPaint)
    drawLine(canvas, rightElbow.position, rightWrist!!.position, rightPaint)
    drawLine(canvas, rightShoulder.position, rightHip.position, rightPaint)
    drawLine(canvas, rightHip.position, rightKnee!!.position, rightPaint)
    drawLine(canvas, rightKnee.position, rightAnkle!!.position, rightPaint)
    drawLine(canvas, rightWrist.position, rightThumb!!.position, rightPaint)
    drawLine(canvas, rightWrist.position, rightPinky!!.position, rightPaint)
    drawLine(canvas, rightWrist.position, rightIndex!!.position, rightPaint)
    drawLine(canvas, rightAnkle.position, rightHeel!!.position, rightPaint)
    drawLine(canvas, rightHeel.position, rightFootIndex!!.position, rightPaint)
  }

  fun reInitParams(){
    lineOneText = ""
    lineTwoText = ""
    isReadyToSpin = false
    count = 0

  }

  fun drawPoint(canvas: Canvas, point: PointF?, paint: Paint?) {
    if (point == null) {
      return
    }
    canvas.drawCircle(
            translateX(point.x),
            translateY(point.y),
            DOT_RADIUS,
            paint!!
    )
  }

  fun drawLine(
          canvas: Canvas,
          start: PointF?,
          end: PointF?,
          paint: Paint?
  ) {
    if (start == null || end == null) {
      return
    }
    canvas.drawLine(
            translateX(start.x), translateY(start.y), translateX(end.x), translateY(end.y), paint!!
    )
  }

  fun drawText(canvas: Canvas, text:String, line:Int) {
    if (TextUtils.isEmpty(text)) {
      return
    }
    canvas.drawText(text, InferenceInfoGraphic.TEXT_SIZE*5.0f, InferenceInfoGraphic.TEXT_SIZE*6 + InferenceInfoGraphic.TEXT_SIZE*line, tipPaint)
  }

  companion object {
    private const val DOT_RADIUS = 8.0f
    private const val IN_FRAME_LIKELIHOOD_TEXT_SIZE = 30.0f


    var backwardsCount = 0
    var forwardsCount = 0
    var isBackwards = false
    var isForwards = true
    var count = 0
    var lineOneText = ""
    var lineTwoText = ""
    var shoulderRelationship = 0f //
    var minDetectableMovementSize = 0f //最小移动单位，避免测算抖动出现误差
    var lastShoulderRelationship = 0f
    var isReadyToSpin = false;
  }

  init {
    whitePaint = Paint()
    whitePaint.color = Color.WHITE
    whitePaint.textSize = IN_FRAME_LIKELIHOOD_TEXT_SIZE
    leftPaint = Paint()
    leftPaint.color = Color.RED
    rightPaint = Paint()
    rightPaint.color = Color.BLUE

    tipPaint = Paint()
    tipPaint.color = Color.WHITE
    tipPaint.textSize = 40f
  }

  fun getAngle(firstPoint: PoseLandmark?, midPoint: PoseLandmark?, lastPoint: PoseLandmark?): Double {
    var result = Math.toDegrees(atan2(1.0*lastPoint!!.getPosition().y - midPoint!!.getPosition().y,
            1.0*lastPoint.getPosition().x - midPoint.getPosition().x)
            - atan2(firstPoint!!.getPosition().y - midPoint.getPosition().y,
            firstPoint.getPosition().x - midPoint.getPosition().x))
    result = Math.abs(result) // Angle should never be negative
    if (result > 180) {
      result = 360.0 - result // Always get the acute representation of the angle
    }
    return result
  }
}

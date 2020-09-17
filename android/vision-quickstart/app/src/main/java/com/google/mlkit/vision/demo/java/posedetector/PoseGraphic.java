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

package com.google.mlkit.vision.demo.java.posedetector;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import androidx.annotation.Nullable;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;
import java.util.List;
import java.util.Locale;

/** Draw the detected pose in preview. */
public class PoseGraphic extends Graphic {

  private static final float DOT_RADIUS = 8.0f;
  private static final float IN_FRAME_LIKELIHOOD_TEXT_SIZE = 30.0f;

  private final Pose pose;
  private final boolean showInFrameLikelihood;
  private final Paint leftPaint;
  private final Paint rightPaint;
  private final Paint whitePaint;

  PoseGraphic(GraphicOverlay overlay, Pose pose, boolean showInFrameLikelihood) {
    super(overlay);

    this.pose = pose;
    this.showInFrameLikelihood = showInFrameLikelihood;

    whitePaint = new Paint();
    whitePaint.setColor(Color.WHITE);
    whitePaint.setTextSize(IN_FRAME_LIKELIHOOD_TEXT_SIZE);
    leftPaint = new Paint();
    leftPaint.setColor(Color.GREEN);
    rightPaint = new Paint();
    rightPaint.setColor(Color.YELLOW);
  }

  @Override
  public void draw(Canvas canvas) {
    List<PoseLandmark> landmarks = pose.getAllPoseLandmarks();
    if (landmarks.isEmpty()) {
      return;
    }
    // Draw all the points
    for (PoseLandmark landmark : landmarks) {
      drawPoint(canvas, landmark.getPosition(), whitePaint);
      if (showInFrameLikelihood) {
        canvas.drawText(
            String.format(Locale.US, "%.2f", landmark.getInFrameLikelihood()),
            translateX(landmark.getPosition().x),
            translateY(landmark.getPosition().y),
            whitePaint);
      }
    }
    PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
    PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
    PoseLandmark leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);
    PoseLandmark rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);
    PoseLandmark leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
    PoseLandmark rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
    PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
    PoseLandmark rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
    PoseLandmark leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
    PoseLandmark rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE);
    PoseLandmark leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);
    PoseLandmark rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);

    PoseLandmark leftPinky = pose.getPoseLandmark(PoseLandmark.LEFT_PINKY);
    PoseLandmark rightPinky = pose.getPoseLandmark(PoseLandmark.RIGHT_PINKY);
    PoseLandmark leftIndex = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX);
    PoseLandmark rightIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX);
    PoseLandmark leftThumb = pose.getPoseLandmark(PoseLandmark.LEFT_THUMB);
    PoseLandmark rightThumb = pose.getPoseLandmark(PoseLandmark.RIGHT_THUMB);
    PoseLandmark leftHeel = pose.getPoseLandmark(PoseLandmark.LEFT_HEEL);
    PoseLandmark rightHeel = pose.getPoseLandmark(PoseLandmark.RIGHT_HEEL);
    PoseLandmark leftFootIndex = pose.getPoseLandmark(PoseLandmark.LEFT_FOOT_INDEX);
    PoseLandmark rightFootIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_FOOT_INDEX);

    drawLine(canvas, leftShoulder.getPosition(), rightShoulder.getPosition(), whitePaint);
    drawLine(canvas, leftHip.getPosition(), rightHip.getPosition(), whitePaint);

    // Left body
    drawLine(canvas, leftShoulder.getPosition(), leftElbow.getPosition(), leftPaint);
    drawLine(canvas, leftElbow.getPosition(), leftWrist.getPosition(), leftPaint);
    drawLine(canvas, leftShoulder.getPosition(), leftHip.getPosition(), leftPaint);
    drawLine(canvas, leftHip.getPosition(), leftKnee.getPosition(), leftPaint);
    drawLine(canvas, leftKnee.getPosition(), leftAnkle.getPosition(), leftPaint);
    drawLine(canvas, leftWrist.getPosition(), leftThumb.getPosition(), leftPaint);
    drawLine(canvas, leftWrist.getPosition(), leftPinky.getPosition(), leftPaint);
    drawLine(canvas, leftWrist.getPosition(), leftIndex.getPosition(), leftPaint);
    drawLine(canvas, leftAnkle.getPosition(), leftHeel.getPosition(), leftPaint);
    drawLine(canvas, leftHeel.getPosition(), leftFootIndex.getPosition(), leftPaint);

    // Right body
    drawLine(canvas, rightShoulder.getPosition(), rightElbow.getPosition(), rightPaint);
    drawLine(canvas, rightElbow.getPosition(), rightWrist.getPosition(), rightPaint);
    drawLine(canvas, rightShoulder.getPosition(), rightHip.getPosition(), rightPaint);
    drawLine(canvas, rightHip.getPosition(), rightKnee.getPosition(), rightPaint);
    drawLine(canvas, rightKnee.getPosition(), rightAnkle.getPosition(), rightPaint);
    drawLine(canvas, rightWrist.getPosition(), rightThumb.getPosition(), rightPaint);
    drawLine(canvas, rightWrist.getPosition(), rightPinky.getPosition(), rightPaint);
    drawLine(canvas, rightWrist.getPosition(), rightIndex.getPosition(), rightPaint);
    drawLine(canvas, rightAnkle.getPosition(), rightHeel.getPosition(), rightPaint);
    drawLine(canvas, rightHeel.getPosition(), rightFootIndex.getPosition(), rightPaint);
  }

  void drawPoint(Canvas canvas, @Nullable PointF point, Paint paint) {
    if (point == null) {
      return;
    }
    canvas.drawCircle(translateX(point.x), translateY(point.y), DOT_RADIUS, paint);
  }

  void drawLine(Canvas canvas, @Nullable PointF start, @Nullable PointF end, Paint paint) {
    if (start == null || end == null) {
      return;
    }
    canvas.drawLine(
        translateX(start.x), translateY(start.y), translateX(end.x), translateY(end.y), paint);
  }
}

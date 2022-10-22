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

import static java.lang.Math.atan2;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.odml.image.MlImage;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.java.VisionProcessorBase;
import com.google.mlkit.vision.demo.java.posedetector.classification.PoseClassifierProcessor;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** A processor to run pose detector. */
public class PoseDetectorProcessor
    extends VisionProcessorBase<PoseDetectorProcessor.PoseWithClassification> {
  private static final String TAG = "PoseDetectorProcessor";

  boolean isLeftHighKnee = false, isRightHighKnee = false,isNormal=true;
  private final PoseDetector detector;
  private static final float TEXT_SIZE = 60.0f;
  private final boolean showInFrameLikelihood;
  private final boolean visualizeZ;
  private final boolean rescaleZForVisualization;
  private final boolean runClassification;
  private final boolean isStreamMode;
  private final Context context;
  private final Executor classificationExecutor;

  private PoseClassifierProcessor poseClassifierProcessor;
  /** Internal class to hold Pose and classification results. */
  protected static class PoseWithClassification {
    private final Pose pose;
    private final List<String> classificationResult;

    public PoseWithClassification(Pose pose, List<String> classificationResult) {
      this.pose = pose;
      this.classificationResult = classificationResult;
    }

    public Pose getPose() {
      return pose;
    }

    public List<String> getClassificationResult() {
      return classificationResult;
    }
  }

  public PoseDetectorProcessor(
      Context context,
      PoseDetectorOptionsBase options,
      boolean showInFrameLikelihood,
      boolean visualizeZ,
      boolean rescaleZForVisualization,
      boolean runClassification,
      boolean isStreamMode) {
    super(context);
    this.showInFrameLikelihood = showInFrameLikelihood;
    this.visualizeZ = visualizeZ;
    this.rescaleZForVisualization = rescaleZForVisualization;
    detector = PoseDetection.getClient(options);
    this.runClassification = runClassification;
    this.isStreamMode = isStreamMode;
    this.context = context;
    classificationExecutor = Executors.newSingleThreadExecutor();
  }

  @Override
  public void stop() {
    super.stop();
    detector.close();
  }

  @Override
  protected Task<PoseWithClassification> detectInImage(InputImage image) {
    return detector
        .process(image)
        .continueWith(
            classificationExecutor,
            task -> {
              Pose pose = task.getResult();
              List<String> classificationResult = new ArrayList<>();
              if (runClassification) {
                if (poseClassifierProcessor == null) {
                  poseClassifierProcessor = new PoseClassifierProcessor(context, isStreamMode);
                }
                classificationResult = poseClassifierProcessor.getPoseResult(pose);
              }
              return new PoseWithClassification(pose, classificationResult);
            });
  }

  @Override
  protected Task<PoseWithClassification> detectInImage(MlImage image) {
    return detector
        .process(image)
        .continueWith(
            classificationExecutor,
            task -> {
              Pose pose = task.getResult();
              List<String> classificationResult = new ArrayList<>();
              if (runClassification) {
                if (poseClassifierProcessor == null) {
                  poseClassifierProcessor = new PoseClassifierProcessor(context, isStreamMode);
                }
                classificationResult = poseClassifierProcessor.getPoseResult(pose);
              }
              return new PoseWithClassification(pose, classificationResult);
            });
  }

  @Override
  protected void onSuccess(
      @NonNull PoseWithClassification poseWithClassification,
      @NonNull GraphicOverlay graphicOverlay) {
    graphicOverlay.add(
        new PoseGraphic(
            graphicOverlay,
            poseWithClassification.pose,
            showInFrameLikelihood,
            visualizeZ,
            rescaleZForVisualization,
            poseWithClassification.classificationResult));
    highKneeCounter(poseWithClassification.pose);
  }

  private void highKneeCounter(Pose pose) {
    if (pose.getAllPoseLandmarks().size() == 0) {
      return;
    }
    PoseLandmark nose = pose.getPoseLandmark(PoseLandmark.NOSE);
    PoseLandmark lefyEyeInner = pose.getPoseLandmark(PoseLandmark.LEFT_EYE_INNER);
    PoseLandmark lefyEye = pose.getPoseLandmark(PoseLandmark.LEFT_EYE);
    PoseLandmark leftEyeOuter = pose.getPoseLandmark(PoseLandmark.LEFT_EYE_OUTER);
    PoseLandmark rightEyeInner = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE_INNER);
    PoseLandmark rightEye = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE);
    PoseLandmark rightEyeOuter = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE_OUTER);
    PoseLandmark leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR);
    PoseLandmark rightEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR);
    PoseLandmark leftMouth = pose.getPoseLandmark(PoseLandmark.LEFT_MOUTH);
    PoseLandmark rightMouth = pose.getPoseLandmark(PoseLandmark.RIGHT_MOUTH);

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


    // Check whether the object is in center of the screen
    float screenWidth = 360;
    float screenHeight = 640;
    //Check whether the object is in frame or not
    assert leftAnkle != null;
    assert nose != null;
    assert rightAnkle != null;
    if(leftAnkle.getPosition().x<0 || leftAnkle.getPosition().y<0 || rightAnkle.getPosition().x<0 || rightAnkle.getPosition().y<0 || nose.getPosition().x<0 || nose.getPosition().y<0){
      Toast.makeText(context, "Object is out of frame", Toast.LENGTH_SHORT).show();
    }
    if(leftAnkle.getPosition().x>screenWidth || leftAnkle.getPosition().y>screenHeight || rightAnkle.getPosition().x>screenWidth || rightAnkle.getPosition().y>screenHeight || nose.getPosition().x>screenWidth || nose.getPosition().y>screenHeight){
      Toast.makeText(context, "Object is out of frame", Toast.LENGTH_SHORT).show();}

    int leftHighKnee = 0, rightHighKnee = 0;

    float x = TEXT_SIZE * 0.5f;
    float y = TEXT_SIZE * 1.5f;

    assert leftShoulder != null;
    assert leftHip != null;
    assert leftKnee != null;
    assert leftFootIndex != null;
    assert rightFootIndex != null;
    assert rightShoulder != null;
    assert rightHip != null;
    assert rightKnee != null;

    isLeftHighKnee = !(getAngle(leftHip, leftKnee, leftAnkle) > 150);
    isRightHighKnee = !(getAngle(rightHip, rightKnee, rightAnkle) > 150);

    if (isLeftHighKnee && getAngle(leftHip, leftKnee, leftAnkle) > 60 && getAngle(leftHip, leftKnee, leftAnkle) < 120) {
      leftHighKnee++;
      isLeftHighKnee = false;
      isNormal=true;
    }
    if (isRightHighKnee && getAngle(rightHip, rightKnee, rightAnkle) > 60 && getAngle(rightHip, rightKnee, rightAnkle) < 120) {
      rightHighKnee++;
      isRightHighKnee = false;
      isNormal=true;
    }
    Log.d("leftHighKnee: ", String.valueOf(leftHighKnee));
    Log.d("rightHighKnee: ", String.valueOf(rightHighKnee));
  }

  @Override
  protected void onFailure(@NonNull Exception e) {
    Log.e(TAG, "Pose detection failed!", e);
  }

  @Override
  protected boolean isMlImageEnabled(Context context) {
    // Use MlImage in Pose Detection by default, change it to OFF to switch to InputImage.
    return true;
  }
  static double getAngle(PoseLandmark firstPoint, PoseLandmark midPoint, PoseLandmark lastPoint) {
    double result =
            Math.toDegrees(
                    atan2(lastPoint.getPosition().y - midPoint.getPosition().y,
                            lastPoint.getPosition().x - midPoint.getPosition().x)
                            - atan2(firstPoint.getPosition().y - midPoint.getPosition().y,
                            firstPoint.getPosition().x - midPoint.getPosition().x));
    result = Math.abs(result); // Angle should never be negative
    if (result > 180) {
      result = (360.0 - result); // Always get the acute representation of the angle
    }
    return result;
  }
}

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

package com.google.mlkit.vision.demo.java.posedetector.classification;

import static com.google.mlkit.vision.demo.java.posedetector.classification.PoseEmbedding.getPoseEmbedding;
import static com.google.mlkit.vision.demo.java.posedetector.classification.Utils.maxAbs;
import static com.google.mlkit.vision.demo.java.posedetector.classification.Utils.multiply;
import static com.google.mlkit.vision.demo.java.posedetector.classification.Utils.multiplyAll;
import static com.google.mlkit.vision.demo.java.posedetector.classification.Utils.subtract;
import static com.google.mlkit.vision.demo.java.posedetector.classification.Utils.sumAbs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.util.Pair;
import com.google.mlkit.vision.common.PointF3D;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Classifies {link Pose} based on given {@link PoseSample}s.
 *
 * <p>Inspired by K-Nearest Neighbors Algorithm with outlier filtering.
 * https://en.wikipedia.org/wiki/K-nearest_neighbors_algorithm
 */
public class PoseClassifier {
  private static final String TAG = "PoseClassifier";
  private static final int MAX_DISTANCE_TOP_K = 30;
  private static final int MEAN_DISTANCE_TOP_K = 10;
  // Note Z has a lower weight as it is generally less accurate than X & Y.
  private static final PointF3D AXES_WEIGHTS = PointF3D.from(1, 1, 0.2f);

  private final List<PoseSample> poseSamples;
  private final int maxDistanceTopK;
  private final int meanDistanceTopK;
  private final PointF3D axesWeights;

  public PoseClassifier(List<PoseSample> poseSamples) {
    this(poseSamples, MAX_DISTANCE_TOP_K, MEAN_DISTANCE_TOP_K, AXES_WEIGHTS);
  }

  public PoseClassifier(List<PoseSample> poseSamples, int maxDistanceTopK,
      int meanDistanceTopK, PointF3D axesWeights) {
    this.poseSamples = poseSamples;
    this.maxDistanceTopK = maxDistanceTopK;
    this.meanDistanceTopK = meanDistanceTopK;
    this.axesWeights = axesWeights;
  }

  private static List<PointF3D> extractPoseLandmarks(Pose pose) {
    List<PointF3D> landmarks = new ArrayList<>();
    for (PoseLandmark poseLandmark : pose.getAllPoseLandmarks()) {
      landmarks.add(poseLandmark.getPosition3D());
    }
    return landmarks;
  }

  /**
   * Returns the max range of confidence values.
   *
   * <p><Since we calculate confidence by counting {@link PoseSample}s that survived
   * outlier-filtering by maxDistanceTopK and meanDistanceTopK, this range is the minimum of two.
   */
  public int confidenceRange() {
    return min(maxDistanceTopK, meanDistanceTopK);
  }

  public ClassificationResult classify(Pose pose) {
    return classify(extractPoseLandmarks(pose));
  }

  public ClassificationResult classify(List<PointF3D> landmarks) {
    ClassificationResult result = new ClassificationResult();
    // Return early if no landmarks detected.
    if (landmarks.isEmpty()) {
      return result;
    }

    // We do flipping on X-axis so we are horizontal (mirror) invariant.
    List<PointF3D> flippedLandmarks = new ArrayList<>(landmarks);
    multiplyAll(flippedLandmarks, PointF3D.from(-1, 1, 1));

    List<PointF3D> embedding = getPoseEmbedding(landmarks);
    List<PointF3D> flippedEmbedding = getPoseEmbedding(flippedLandmarks);


    // Classification is done in two stages:
    //  * First we pick top-K samples by MAX distance. It allows to remove samples that are almost
    //    the same as given pose, but maybe has few joints bent in the other direction.
    //  * Then we pick top-K samples by MEAN distance. After outliers are removed, we pick samples
    //    that are closest by average.

    // Keeps max distance on top so we can pop it when top_k size is reached.
    PriorityQueue<Pair<PoseSample, Float>> maxDistances = new PriorityQueue<>(
        maxDistanceTopK, (o1, o2) -> -Float.compare(o1.second, o2.second));
    // Retrieve top K poseSamples by least distance to remove outliers.
    for (PoseSample poseSample : poseSamples) {
      List<PointF3D> sampleEmbedding = poseSample.getEmbedding();

      float originalMax = 0;
      float flippedMax = 0;
      for (int i = 0; i < embedding.size(); i++) {
        originalMax =
            max(
                originalMax,
                maxAbs(multiply(subtract(embedding.get(i), sampleEmbedding.get(i)), axesWeights)));
        flippedMax =
            max(
                flippedMax,
                maxAbs(
                    multiply(
                        subtract(flippedEmbedding.get(i), sampleEmbedding.get(i)), axesWeights)));
      }
      // Set the max distance as min of original and flipped max distance.
      maxDistances.add(new Pair<>(poseSample, min(originalMax, flippedMax)));
      // We only want to retain top n so pop the highest distance.
      if (maxDistances.size() > maxDistanceTopK) {
        maxDistances.poll();
      }
    }

    // Keeps higher mean distances on top so we can pop it when top_k size is reached.
    PriorityQueue<Pair<PoseSample, Float>> meanDistances = new PriorityQueue<>(
        meanDistanceTopK, (o1, o2) -> -Float.compare(o1.second, o2.second));
    // Retrive top K poseSamples by least mean distance to remove outliers.
    for (Pair<PoseSample, Float> sampleDistances : maxDistances) {
      PoseSample poseSample = sampleDistances.first;
      List<PointF3D> sampleEmbedding = poseSample.getEmbedding();

      float originalSum = 0;
      float flippedSum = 0;
      for (int i = 0; i < embedding.size(); i++) {
        originalSum += sumAbs(multiply(
            subtract(embedding.get(i), sampleEmbedding.get(i)), axesWeights));
        flippedSum += sumAbs(
            multiply(subtract(flippedEmbedding.get(i), sampleEmbedding.get(i)), axesWeights));
      }
      // Set the mean distance as min of original and flipped mean distances.
      float meanDistance = min(originalSum, flippedSum) / (embedding.size() * 2);
      meanDistances.add(new Pair<>(poseSample, meanDistance));
      // We only want to retain top k so pop the highest mean distance.
      if (meanDistances.size() > meanDistanceTopK) {
        meanDistances.poll();
      }
    }

    for (Pair<PoseSample, Float> sampleDistances : meanDistances) {
      String className = sampleDistances.first.getClassName();
      result.incrementClassConfidence(className);
    }

    return result;
  }
}

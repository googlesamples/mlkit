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

import static com.google.common.primitives.Floats.max;

import com.google.mlkit.vision.common.PointF3D;
import java.util.List;
import java.util.ListIterator;

/**
 * Utility methods for operations on {@link PointF3D}.
 */
public class Utils {
  private Utils() {}

  public static PointF3D add(PointF3D a, PointF3D b) {
    return PointF3D.from(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ());
  }

  public static PointF3D subtract(PointF3D b, PointF3D a) {
    return PointF3D.from(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ());
  }

  public static PointF3D multiply(PointF3D a, float multiple) {
    return PointF3D.from(a.getX() * multiple, a.getY() * multiple, a.getZ() * multiple);
  }

  public static PointF3D multiply(PointF3D a, PointF3D multiple) {
    return PointF3D.from(
        a.getX() * multiple.getX(), a.getY() * multiple.getY(), a.getZ() * multiple.getZ());
  }

  public static PointF3D average(PointF3D a, PointF3D b) {
    return PointF3D.from(
        (a.getX() + b.getX()) * 0.5f, (a.getY() + b.getY()) * 0.5f, (a.getZ() + b.getZ()) * 0.5f);
  }

  public static float l2Norm2D(PointF3D point) {
    return (float) Math.hypot(point.getX(), point.getY());
  }

  public static float maxAbs(PointF3D point) {
    return max(Math.abs(point.getX()), Math.abs(point.getY()), Math.abs(point.getZ()));
  }

  public static float sumAbs(PointF3D point) {
    return Math.abs(point.getX()) + Math.abs(point.getY()) + Math.abs(point.getZ());
  }

  public static void addAll(List<PointF3D> pointsList, PointF3D p) {
    ListIterator<PointF3D> iterator = pointsList.listIterator();
    while (iterator.hasNext()) {
      iterator.set(add(iterator.next(), p));
    }
  }

  public static void subtractAll(PointF3D p, List<PointF3D> pointsList) {
    ListIterator<PointF3D> iterator = pointsList.listIterator();
    while (iterator.hasNext()) {
      iterator.set(subtract(p, iterator.next()));
    }
  }

  public static void multiplyAll(List<PointF3D> pointsList, float multiple) {
    ListIterator<PointF3D> iterator = pointsList.listIterator();
    while (iterator.hasNext()) {
      iterator.set(multiply(iterator.next(), multiple));
    }
  }

  public static void multiplyAll(List<PointF3D> pointsList, PointF3D multiple) {
    ListIterator<PointF3D> iterator = pointsList.listIterator();
    while (iterator.hasNext()) {
      iterator.set(multiply(iterator.next(), multiple));
    }
  }
}

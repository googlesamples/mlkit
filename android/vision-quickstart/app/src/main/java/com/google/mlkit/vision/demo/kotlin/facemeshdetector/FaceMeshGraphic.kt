/*
 * Copyright 2022 Google LLC. All rights reserved.
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
package com.google.mlkit.vision.demo.kotlin.facemeshdetector

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.common.PointF3D
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.preference.PreferenceUtils
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.google.mlkit.vision.facemesh.FaceMeshPoint

/**
 * Graphic instance for rendering face position and mesh info within the associated graphic overlay
 * view.
 */
class FaceMeshGraphic(overlay: GraphicOverlay, private val faceMesh: FaceMesh) :
  GraphicOverlay.Graphic(overlay) {

  private val positionPaint: Paint
  private val boxPaint: Paint
  private val useCase: Int
  private var zMin: Float
  private var zMax: Float

  @FaceMesh.ContourType
  private val DISPLAY_CONTOURS =
    intArrayOf(
      FaceMesh.FACE_OVAL,
      FaceMesh.LEFT_EYEBROW_TOP,
      FaceMesh.LEFT_EYEBROW_BOTTOM,
      FaceMesh.RIGHT_EYEBROW_TOP,
      FaceMesh.RIGHT_EYEBROW_BOTTOM,
      FaceMesh.LEFT_EYE,
      FaceMesh.RIGHT_EYE,
      FaceMesh.UPPER_LIP_TOP,
      FaceMesh.UPPER_LIP_BOTTOM,
      FaceMesh.LOWER_LIP_TOP,
      FaceMesh.LOWER_LIP_BOTTOM,
      FaceMesh.NOSE_BRIDGE
    )

  /** Draws the face annotations for position on the supplied canvas. */
  override fun draw(canvas: Canvas) {

    // Draws the bounding box.
    val rect = RectF(faceMesh.boundingBox)
    // If the image is flipped, the left will be translated to right, and the right to left.
    val x0 = translateX(rect.left)
    val x1 = translateX(rect.right)
    rect.left = Math.min(x0, x1)
    rect.right = Math.max(x0, x1)
    rect.top = translateY(rect.top)
    rect.bottom = translateY(rect.bottom)
    canvas.drawRect(rect, boxPaint)

    // Draw face mesh
    val points =
      if (useCase == USE_CASE_CONTOUR_ONLY) getContourPoints(faceMesh) else faceMesh.allPoints
    val triangles = faceMesh.allTriangles

    zMin = Float.MAX_VALUE
    zMax = Float.MIN_VALUE
    for (point in points) {
      zMin = Math.min(zMin, point.position.z)
      zMax = Math.max(zMax, point.position.z)
    }

    // Draw face mesh points
    for (point in points) {
      updatePaintColorByZValue(
        positionPaint,
        canvas,
        /* visualizeZ= */true,
        /* rescaleZForVisualization= */true,
        point.position.z,
        zMin,
        zMax)
      canvas.drawCircle(
        translateX(point.position.x),
        translateY(point.position.y),
        FACE_POSITION_RADIUS,
        positionPaint
      )
    }

    if (useCase == FaceMeshDetectorOptions.FACE_MESH) {
      // Draw face mesh triangles
      for (triangle in triangles) {
        val point1 = triangle.allPoints[0].position
        val point2 = triangle.allPoints[1].position
        val point3 = triangle.allPoints[2].position
        drawLine(canvas, point1, point2)
        drawLine(canvas, point1, point3)
        drawLine(canvas, point2, point3)
      }
    }
  }

  private fun drawLine(canvas: Canvas, point1: PointF3D, point2: PointF3D) {
    updatePaintColorByZValue(
      positionPaint,
      canvas,
      /* visualizeZ= */true,
      /* rescaleZForVisualization= */true,
      (point1.z + point2.z) / 2,
      zMin,
      zMax)
    canvas.drawLine(
      translateX(point1.x),
      translateY(point1.y),
      translateX(point2.x),
      translateY(point2.y),
      positionPaint
    )
  }

  private fun getContourPoints(faceMesh: FaceMesh): List<FaceMeshPoint> {
    val contourPoints: MutableList<FaceMeshPoint> = ArrayList()
    for (type in DISPLAY_CONTOURS) {
      contourPoints.addAll(faceMesh.getPoints(type))
    }
    return contourPoints
  }

  companion object {
    private const val USE_CASE_CONTOUR_ONLY = 999
    private const val FACE_POSITION_RADIUS = 8.0f
    private const val BOX_STROKE_WIDTH = 5.0f
  }

  init {
    val selectedColor = Color.WHITE
    positionPaint = Paint()
    positionPaint.color = selectedColor

    boxPaint = Paint()
    boxPaint.color = selectedColor
    boxPaint.style = Paint.Style.STROKE
    boxPaint.strokeWidth = BOX_STROKE_WIDTH

    useCase = PreferenceUtils.getFaceMeshUseCase(applicationContext)
    zMin = java.lang.Float.MAX_VALUE
    zMax = java.lang.Float.MIN_VALUE
  }
}

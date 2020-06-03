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

package com.google.mlkit.vision.demo.kotlin.barcodescanner

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic

/** Graphic instance for rendering Barcode position and content information in an overlay view.  */
class BarcodeGraphic constructor(overlay: GraphicOverlay?, private val barcode: Barcode?) :
  Graphic(overlay) {
  private val rectPaint: Paint = Paint()
  private val barcodePaint: Paint
  private val labelPaint: Paint

  init {
    rectPaint.color = MARKER_COLOR
    rectPaint.style = Paint.Style.STROKE
    rectPaint.strokeWidth = STROKE_WIDTH
    barcodePaint = Paint()
    barcodePaint.color = TEXT_COLOR
    barcodePaint.textSize = TEXT_SIZE
    labelPaint = Paint()
    labelPaint.color = MARKER_COLOR
    labelPaint.style = Paint.Style.FILL
  }

  /**
   * Draws the barcode block annotations for position, size, and raw value on the supplied canvas.
   */
  override fun draw(canvas: Canvas) {
    checkNotNull(barcode) { "Attempting to draw a null barcode." }
    // Draws the bounding box around the BarcodeBlock.
    val rect = RectF(barcode.boundingBox)
    rect.left = translateX(rect.left)
    rect.top = translateY(rect.top)
    rect.right = translateX(rect.right)
    rect.bottom = translateY(rect.bottom)
    canvas.drawRect(rect, rectPaint)
    // Draws other object info.
    val lineHeight =
      TEXT_SIZE + 2 * STROKE_WIDTH
    val textWidth = barcodePaint.measureText(barcode.rawValue)
    val left = if (isImageFlipped) rect.right else rect.left
    canvas.drawRect(
      left - STROKE_WIDTH,
      rect.top - lineHeight,
      left + textWidth + 2 * STROKE_WIDTH,
      rect.top,
      labelPaint
    )
    // Renders the barcode at the bottom of the box.
    canvas.drawText(
      barcode.rawValue!!,
      left,
      rect.top - STROKE_WIDTH,
      barcodePaint
    )
  }

  companion object {
    private const val TEXT_COLOR = Color.BLACK
    private const val MARKER_COLOR = Color.WHITE
    private const val TEXT_SIZE = 54.0f
    private const val STROKE_WIDTH = 4.0f
  }
}

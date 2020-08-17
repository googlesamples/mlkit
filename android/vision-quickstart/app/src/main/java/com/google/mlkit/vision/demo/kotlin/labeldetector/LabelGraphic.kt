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

package com.google.mlkit.vision.demo.kotlin.labeldetector

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic
import com.google.mlkit.vision.label.ImageLabel
import java.util.Locale

/** Graphic instance for rendering a label within an associated graphic overlay view.  */
class LabelGraphic(
  private val overlay: GraphicOverlay,
  private val labels: List<ImageLabel>
) : Graphic(overlay) {
  private val textPaint: Paint = Paint()
  private val labelPaint: Paint

  init {
    textPaint.color = Color.WHITE
    textPaint.textSize = TEXT_SIZE
    labelPaint = Paint()
    labelPaint.color = Color.BLACK
    labelPaint.style = Paint.Style.FILL
    labelPaint.alpha = 200
  }

  @Synchronized
  override fun draw(canvas: Canvas) {
    // First try to find maxWidth and totalHeight in order to draw to the center of the screen.
    var maxWidth = 0f
    val totalHeight = labels.size * 2 * TEXT_SIZE
    for (label in labels) {
      val line1Width = textPaint.measureText(label.text)
      val line2Width =
        textPaint.measureText(
          String.format(
            Locale.US,
            LABEL_FORMAT,
            label.confidence * 100,
            label.index
          )
        )

      maxWidth = Math.max(maxWidth, Math.max(line1Width, line2Width))
    }

    val x = Math.max(0f, overlay.width / 2.0f - maxWidth / 2.0f)
    var y = Math.max(200f, overlay.height / 2.0f - totalHeight / 2.0f)

    if (!labels.isEmpty()) {
      val padding = 20f
      canvas.drawRect(
        x - padding,
        y - padding,
        x + maxWidth + padding,
        y + totalHeight + padding,
        labelPaint
      )
    }

    for (label in labels) {
      if (y + TEXT_SIZE * 2 > overlay.height) {
        break
      }
      canvas.drawText(label.text, x, y + TEXT_SIZE, textPaint)
      y += TEXT_SIZE
      canvas.drawText(
        String.format(
          Locale.US,
          LABEL_FORMAT,
          label.confidence * 100,
          label.index
        ),
        x, y + TEXT_SIZE, textPaint
      )
      y += TEXT_SIZE
    }
  }

  companion object {
    private const val TEXT_SIZE = 70.0f
    private const val LABEL_FORMAT = "%.2f%% confidence (index: %d)"
  }
}

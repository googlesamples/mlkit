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

package com.google.mlkit.vision.demo.kotlin.automl

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic
import com.google.mlkit.vision.label.ImageLabel

/** Graphic instance for rendering a label within an associated graphic overlay view.  */
class LabelGraphic(
  private val overlay: GraphicOverlay,
  private val labels: List<ImageLabel>
) : Graphic(overlay) {

  private val textPaint: Paint = Paint()

  init {
    textPaint.color = Color.RED
    textPaint.textSize = TEXT_SIZE
  }

  @Synchronized
  override fun draw(canvas: Canvas) {
    val x = overlay.width / 8.0f
    var y = TEXT_SIZE * 4
    for (label in labels) {
      canvas.drawText(label.text + " (index: " + label.index + ")", x, y, textPaint)
      y += TEXT_SIZE
      canvas.drawText("confidence: " + label.confidence, x, y, textPaint)
      y += TEXT_SIZE
    }
  }

  companion object {
    private const val TEXT_SIZE = 70.0f
  }
}

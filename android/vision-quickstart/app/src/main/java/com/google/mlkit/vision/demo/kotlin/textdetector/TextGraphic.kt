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

package com.google.mlkit.vision.demo.kotlin.textdetector

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic
import com.google.mlkit.vision.text.Text
import java.util.Arrays

/**
 * Graphic instance for rendering TextBlock position, size, and ID within an associated graphic
 * overlay view.
 */
class TextGraphic constructor(overlay: GraphicOverlay?, private val text: Text) :
        Graphic(overlay) {

    private val rectPaint: Paint = Paint()
    private val textPaint: Paint
    private val labelPaint: Paint

    init {
        rectPaint.color = MARKER_COLOR
        rectPaint.style = Paint.Style.STROKE
        rectPaint.strokeWidth = STROKE_WIDTH
        textPaint = Paint()
        textPaint.color = TEXT_COLOR
        textPaint.textSize = TEXT_SIZE
        labelPaint = Paint()
        labelPaint.color = MARKER_COLOR
        labelPaint.style = Paint.Style.FILL
        // Redraw the overlay, as this graphic has been added.
        postInvalidate()
    }

    /** Draws the text block annotations for position, size, and raw value on the supplied canvas.  */
    override fun draw(canvas: Canvas) {
        Log.d(TAG, "Text is: " + text.text)
        for (textBlock in text.textBlocks) { // Renders the text at the bottom of the box.
            Log.d(TAG, "TextBlock text is: " + textBlock.text)
            Log.d(
                    TAG,
                    "TextBlock boundingbox is: " + textBlock.boundingBox
            )
            Log.d(
                    TAG,
                    "TextBlock cornerpoint is: " + Arrays.toString(textBlock.cornerPoints)
            )
            for (line in textBlock.lines) {
                Log.d(TAG, "Line text is: " + line.text)
                Log.d(
                        TAG,
                        "Line boundingbox is: " + line.boundingBox
                )
                Log.d(
                        TAG,
                        "Line cornerpoint is: " + Arrays.toString(line.cornerPoints)
                )
                // Draws the bounding box around the TextBlock.
                val rect = RectF(line.boundingBox)
                rect.left = translateX(rect.left)
                rect.top = translateY(rect.top)
                rect.right = translateX(rect.right)
                rect.bottom = translateY(rect.bottom)
                canvas.drawRect(rect, rectPaint)
                val lineHeight =
                        TEXT_SIZE + 2 * STROKE_WIDTH
                val textWidth = textPaint.measureText(line.text)
                val left = if (isImageFlipped) rect.right else rect.left
                canvas.drawRect(
                        left - STROKE_WIDTH,
                        rect.top - lineHeight,
                        left + textWidth + 2 * STROKE_WIDTH,
                        rect.top,
                        labelPaint
                )
                // Renders the text at the bottom of the box.
                canvas.drawText(
                        line.text,
                        left,
                        rect.top - STROKE_WIDTH,
                        textPaint
                )
                for (element in line.elements) {
                    Log.d(TAG, "Element text is: " + element.text)
                    Log.d(
                            TAG,
                            "Element boundingbox is: " + element.boundingBox
                    )
                    Log.d(
                            TAG,
                            "Element cornerpoint is: " + Arrays.toString(element.cornerPoints)
                    )
                    Log.d(
                            TAG,
                            "Element language is: " + element.recognizedLanguage
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "TextGraphic"
        private const val TEXT_COLOR = Color.BLACK
        private const val MARKER_COLOR = Color.WHITE
        private const val TEXT_SIZE = 54.0f
        private const val STROKE_WIDTH = 4.0f
    }
}

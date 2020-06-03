/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.md.objectdetection

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Paint.Style
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader.TileMode
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.google.mlkit.md.camera.GraphicOverlay
import com.google.mlkit.md.camera.GraphicOverlay.Graphic
import com.google.mlkit.md.R

/**
 * Draws the detected detectedObject info over the camera preview for multiple objects detection mode.
 */
internal class ObjectGraphicInMultiMode(
    overlay: GraphicOverlay,
    private val detectedObject: DetectedObjectInfo,
    private val confirmationController: ObjectConfirmationController
) : Graphic(overlay) {

    private val boxPaint: Paint
    private val scrimPaint: Paint
    private val eraserPaint: Paint

    @ColorInt
    private val boxGradientStartColor: Int

    @ColorInt
    private val boxGradientEndColor: Int
    private val boxCornerRadius: Int
    private val minBoxLen: Int

    init {
        val resources = context.resources
        boxPaint = Paint().apply {
            style = Style.STROKE
            strokeWidth = resources.getDimensionPixelOffset(
                if (confirmationController.isConfirmed) {
                    R.dimen.bounding_box_confirmed_stroke_width
                } else {
                    R.dimen.bounding_box_stroke_width
                }
            ).toFloat()
            color = Color.WHITE
        }

        boxGradientStartColor = ContextCompat.getColor(context, R.color.bounding_box_gradient_start)
        boxGradientEndColor = ContextCompat.getColor(context, R.color.bounding_box_gradient_end)
        boxCornerRadius = resources.getDimensionPixelOffset(R.dimen.bounding_box_corner_radius)

        scrimPaint = Paint().apply {
            shader = LinearGradient(
                0f,
                0f,
                overlay.width.toFloat(),
                overlay.height.toFloat(),
                ContextCompat.getColor(context, R.color.object_confirmed_bg_gradient_start),
                ContextCompat.getColor(context, R.color.object_confirmed_bg_gradient_end),
                TileMode.MIRROR
            )
        }

        eraserPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        minBoxLen = resources.getDimensionPixelOffset(R.dimen.object_reticle_outer_ring_stroke_radius) * 2
    }

    override fun draw(canvas: Canvas) {
        var rect = overlay.translateRect(detectedObject.boundingBox)

        val boxWidth = rect.width() * confirmationController.progress
        val boxHeight = rect.height() * confirmationController.progress
        if (boxWidth < minBoxLen || boxHeight < minBoxLen) {
            // Don't draw the box if its length is too small, otherwise it will intersect with reticle so
            // the UI looks messy.
            return
        }

        val cx = (rect.left + rect.right) / 2
        val cy = (rect.top + rect.bottom) / 2
        rect = RectF(
            cx - boxWidth / 2f,
            cy - boxHeight / 2f,
            cx + boxWidth / 2f,
            cy + boxHeight / 2f
        )

        if (confirmationController.isConfirmed) {
            // Draws the dark background scrim and leaves the detectedObject area clear.
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), scrimPaint)
            canvas.drawRoundRect(rect, boxCornerRadius.toFloat(), boxCornerRadius.toFloat(), eraserPaint)
        }

        boxPaint.shader = if (confirmationController.isConfirmed) {
            null
        } else {
            LinearGradient(
                rect.left,
                rect.top,
                rect.left,
                rect.bottom,
                boxGradientStartColor,
                boxGradientEndColor,
                TileMode.MIRROR
            )
        }
        canvas.drawRoundRect(rect, boxCornerRadius.toFloat(), boxCornerRadius.toFloat(), boxPaint)
    }
}

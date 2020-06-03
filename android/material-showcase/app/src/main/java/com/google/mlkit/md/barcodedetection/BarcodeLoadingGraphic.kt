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

package com.google.mlkit.md.barcodedetection

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import com.google.mlkit.md.camera.GraphicOverlay

/** Draws the graphic to indicate the barcode result is in loading.  */
internal class BarcodeLoadingGraphic(overlay: GraphicOverlay, private val loadingAnimator: ValueAnimator) :
    BarcodeGraphicBase(overlay) {

    private val boxClockwiseCoordinates: Array<PointF> = arrayOf(
        PointF(boxRect.left, boxRect.top),
        PointF(boxRect.right, boxRect.top),
        PointF(boxRect.right, boxRect.bottom),
        PointF(boxRect.left, boxRect.bottom)
    )
    private val coordinateOffsetBits: Array<Point> = arrayOf(
        Point(1, 0),
        Point(0, 1),
        Point(-1, 0),
        Point(0, -1)
    )
    private val lastPathPoint = PointF()

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        val boxPerimeter = (boxRect.width() + boxRect.height()) * 2
        val path = Path()
        // The distance between the box's left-top corner and the starting point of white colored path.
        var offsetLen = boxPerimeter * loadingAnimator.animatedValue as Float % boxPerimeter
        var i = 0
        while (i < 4) {
            val edgeLen = if (i % 2 == 0) boxRect.width() else boxRect.height()
            if (offsetLen <= edgeLen) {
                lastPathPoint.x = boxClockwiseCoordinates[i].x + coordinateOffsetBits[i].x * offsetLen
                lastPathPoint.y = boxClockwiseCoordinates[i].y + coordinateOffsetBits[i].y * offsetLen
                path.moveTo(lastPathPoint.x, lastPathPoint.y)
                break
            }

            offsetLen -= edgeLen
            i++
        }

        // Computes the path based on the determined starting point and path length.
        var pathLen = boxPerimeter * 0.3f
        for (j in 0..3) {
            val index = (i + j) % 4
            val nextIndex = (i + j + 1) % 4
            // The length between path's current end point and reticle box's next coordinate point.
            val lineLen = Math.abs(boxClockwiseCoordinates[nextIndex].x - lastPathPoint.x) +
                    Math.abs(boxClockwiseCoordinates[nextIndex].y - lastPathPoint.y)
            if (lineLen >= pathLen) {
                path.lineTo(
                    lastPathPoint.x + pathLen * coordinateOffsetBits[index].x,
                    lastPathPoint.y + pathLen * coordinateOffsetBits[index].y
                )
                break
            }

            lastPathPoint.x = boxClockwiseCoordinates[nextIndex].x
            lastPathPoint.y = boxClockwiseCoordinates[nextIndex].y
            path.lineTo(lastPathPoint.x, lastPathPoint.y)
            pathLen -= lineLen
        }

        canvas.drawPath(path, pathPaint)
    }
}

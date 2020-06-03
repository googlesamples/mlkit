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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.mlkit.md.R

/** Represents a detected object by drawing a circle dot at the center of object's bounding box.  */
class StaticObjectDotView @JvmOverloads constructor(context: Context, selected: Boolean = false) : View(context) {

    private val paint: Paint = Paint().apply {
        style = Paint.Style.FILL
    }
    private val unselectedDotRadius: Int =
        context.resources.getDimensionPixelOffset(R.dimen.static_image_dot_radius_unselected)
    private val radiusOffsetRange: Int

    private var currentRadiusOffset: Float = 0.toFloat()

    init {
        val selectedDotRadius = context.resources.getDimensionPixelOffset(R.dimen.static_image_dot_radius_selected)
        radiusOffsetRange = selectedDotRadius - unselectedDotRadius
        currentRadiusOffset = (if (selected) radiusOffsetRange else 0).toFloat()
    }

    fun playAnimationWithSelectedState(selected: Boolean) {
        val radiusOffsetAnimator: ValueAnimator =
            if (selected) {
                ValueAnimator.ofFloat(0f, radiusOffsetRange.toFloat())
                    .setDuration(DOT_SELECTION_ANIMATOR_DURATION_MS).apply {
                        startDelay = DOT_DESELECTION_ANIMATOR_DURATION_MS
                    }
            } else {
                ValueAnimator.ofFloat(radiusOffsetRange.toFloat(), 0f)
                    .setDuration(DOT_DESELECTION_ANIMATOR_DURATION_MS)
            }

        radiusOffsetAnimator.interpolator = FastOutSlowInInterpolator()
        radiusOffsetAnimator.addUpdateListener { animation ->
            currentRadiusOffset = animation.animatedValue as Float
            invalidate()
        }
        radiusOffsetAnimator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, unselectedDotRadius + currentRadiusOffset, paint)
    }

    companion object {
        private const val DOT_SELECTION_ANIMATOR_DURATION_MS: Long = 116
        private const val DOT_DESELECTION_ANIMATOR_DURATION_MS: Long = 67
    }
}

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

package com.google.mlkit.md.productsearch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.common.base.Preconditions.checkArgument
import com.google.mlkit.md.R

/** Draws the scrim of bottom sheet with object thumbnail highlighted.  */
class BottomSheetScrimView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val scrimPaint: Paint
    private val thumbnailPaint: Paint
    private val boxPaint: Paint
    private val thumbnailHeight: Int
    private val thumbnailMargin: Int
    private val boxCornerRadius: Int

    private var thumbnailBitmap: Bitmap? = null
    private var thumbnailRect: RectF? = null
    private var downPercentInCollapsed: Float = 0f

    init {
        val resources = context.resources
        scrimPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.dark)
        }

        thumbnailPaint = Paint()

        boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = resources.getDimensionPixelOffset(R.dimen.object_thumbnail_stroke_width).toFloat()
            color = Color.WHITE
        }

        thumbnailHeight = resources.getDimensionPixelOffset(R.dimen.object_thumbnail_height)
        thumbnailMargin = resources.getDimensionPixelOffset(R.dimen.object_thumbnail_margin)
        boxCornerRadius = resources.getDimensionPixelOffset(R.dimen.bounding_box_corner_radius)
    }

    /**
     * Translates the object thumbnail up or down along with bottom sheet's sliding movement, with
     * keeping thumbnail size fixed.
     */
    fun updateWithThumbnailTranslate(
        thumbnailBitmap: Bitmap,
        collapsedStateHeight: Int,
        slideOffset: Float,
        bottomSheet: View
    ) {
        this.thumbnailBitmap = thumbnailBitmap

        val currentSheetHeight: Float
        if (slideOffset < 0) {
            downPercentInCollapsed = -slideOffset
            currentSheetHeight = collapsedStateHeight * (1 + slideOffset)
        } else {
            downPercentInCollapsed = 0f
            currentSheetHeight = collapsedStateHeight + (bottomSheet.height - collapsedStateHeight) * slideOffset
        }

        thumbnailRect = RectF().apply {
            val thumbnailWidth =
                thumbnailBitmap.width.toFloat() / thumbnailBitmap.height.toFloat() * thumbnailHeight.toFloat()
            left = thumbnailMargin.toFloat()
            top = height.toFloat() - currentSheetHeight - thumbnailMargin.toFloat() - thumbnailHeight.toFloat()
            right = left + thumbnailWidth
            bottom = top + thumbnailHeight
        }

        invalidate()
    }

    /**
     * Translates the object thumbnail from original bounding box location to at where the bottom
     * sheet is settled as COLLAPSED state, with its size scales gradually.
     *
     *
     * It's only used by sliding the sheet up from hidden state to collapsed state.
     */
    fun updateWithThumbnailTranslateAndScale(
        thumbnailBitmap: Bitmap,
        collapsedStateHeight: Int,
        slideOffset: Float,
        srcThumbnailRect: RectF
    ) {
        checkArgument(
            slideOffset <= 0,
            "Scale mode works only when the sheet is between hidden and collapsed states."
        )

        this.thumbnailBitmap = thumbnailBitmap
        this.downPercentInCollapsed = 0f

        thumbnailRect = RectF().apply {
            val dstX = thumbnailMargin.toFloat()
            val dstY = (height - collapsedStateHeight - thumbnailMargin - thumbnailHeight).toFloat()
            val dstHeight = thumbnailHeight.toFloat()
            val dstWidth = srcThumbnailRect.width() / srcThumbnailRect.height() * dstHeight
            val dstRect = RectF(dstX, dstY, dstX + dstWidth, dstY + dstHeight)

            val progressToCollapsedState = 1 + slideOffset
            left = srcThumbnailRect.left + (dstRect.left - srcThumbnailRect.left) * progressToCollapsedState
            top = srcThumbnailRect.top + (dstRect.top - srcThumbnailRect.top) * progressToCollapsedState
            right = srcThumbnailRect.right + (dstRect.right - srcThumbnailRect.right) * progressToCollapsedState
            bottom = srcThumbnailRect.bottom + (dstRect.bottom - srcThumbnailRect.bottom) * progressToCollapsedState
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draws the dark background.
        val bitmap = thumbnailBitmap ?: return
        val rect = thumbnailRect ?: return
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        if (downPercentInCollapsed < DOWN_PERCENT_TO_HIDE_THUMBNAIL) {
            val alpha = ((1 - downPercentInCollapsed / DOWN_PERCENT_TO_HIDE_THUMBNAIL) * 255).toInt()

            // Draws the object thumbnail.
            thumbnailPaint.alpha = alpha
            canvas.drawBitmap(bitmap, null, rect, thumbnailPaint)

            // Draws the bounding box.
            boxPaint.alpha = alpha
            canvas.drawRoundRect(rect, boxCornerRadius.toFloat(), boxCornerRadius.toFloat(), boxPaint)
        }
    }

    companion object {
        private const val DOWN_PERCENT_TO_HIDE_THUMBNAIL = 0.42f
    }
}

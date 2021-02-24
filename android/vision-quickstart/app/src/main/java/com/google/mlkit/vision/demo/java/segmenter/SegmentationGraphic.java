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

package com.google.mlkit.vision.demo.java.segmenter;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import androidx.annotation.ColorInt;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import java.nio.ByteBuffer;

/** Draw the mask from SegmentationResult in preview. */
public class SegmentationGraphic extends Graphic {

  private final ByteBuffer mask;
  private final int maskWidth;
  private final int maskHeight;
  private final boolean isRawSizeMaskEnabled;
  private final float scaleX;
  private final float scaleY;

  public SegmentationGraphic(
      GraphicOverlay overlay,
      SegmentationMask segmentationMask) {
    super(overlay);
    mask = segmentationMask.getBuffer();
    maskWidth = segmentationMask.getWidth();
    maskHeight = segmentationMask.getHeight();

    isRawSizeMaskEnabled =
        maskWidth != overlay.getImageWidth()
        || maskHeight != overlay.getImageHeight();
    scaleX = overlay.getImageWidth() * 1f / maskWidth;
    scaleY = overlay.getImageHeight() * 1f / maskHeight;
  }

  /** Draws the segmented background on the supplied canvas. */
  @Override
  public void draw(Canvas canvas) {
    Bitmap bitmap =
        Bitmap.createBitmap(
            maskColorsFromByteBuffer(mask), maskWidth, maskHeight, Config.ARGB_8888);
    if (isRawSizeMaskEnabled) {
      Matrix matrix = new Matrix(getTransformationMatrix());
      matrix.preScale(scaleX, scaleY);
      canvas.drawBitmap(bitmap, matrix, null);
    } else {
      canvas.drawBitmap(bitmap, getTransformationMatrix(), null);
    }
    bitmap.recycle();
    // Reset byteBuffer pointer to beginning, so that the mask can be redrawn if screen is refreshed
    mask.rewind();
  }

  /** Converts byteBuffer floats to ColorInt array that can be used as a mask. */
  @ColorInt
  private int[] maskColorsFromByteBuffer(ByteBuffer byteBuffer) {
    @ColorInt int[] colors = new int[maskWidth * maskHeight];
    for (int i = 0; i < maskWidth * maskHeight; i++) {
      float backgroundLikelihood = 1 - byteBuffer.getFloat();
      if (backgroundLikelihood > 0.9) {
        colors[i] = Color.argb(128, 255, 0, 255);
      } else if (backgroundLikelihood > 0.2) {
        // Linear interpolation to make sure when backgroundLikelihood is 0.2, the alpha is 0 and
        // when backgroundLikelihood is 0.9, the alpha is 128.
        // +0.5 to round the float value to the nearest int.
        int alpha = (int) (182.9 * backgroundLikelihood - 36.6 + 0.5);
        colors[i] = Color.argb(alpha, 255, 0, 255);
      }
    }
    return colors;
  }
}

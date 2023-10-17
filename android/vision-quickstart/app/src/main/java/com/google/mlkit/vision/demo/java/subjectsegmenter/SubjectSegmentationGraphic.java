/*
 * Copyright 2023 Google LLC. All rights reserved.
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

package com.google.mlkit.vision.demo.java.subjectsegmenter;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Build;
import androidx.annotation.ColorInt;
import androidx.annotation.RequiresApi;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic;
import com.google.mlkit.vision.segmentation.subject.Subject;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult;
import java.nio.FloatBuffer;
import java.util.List;

/** Draw the mask from {@link SubjectSegmentationResult} in preview. */
@RequiresApi(Build.VERSION_CODES.N)
public class SubjectSegmentationGraphic extends Graphic {

  private static final int[][] COLORS = {
    {255, 0, 255},
    {0, 255, 255},
    {255, 255, 0},
    {255, 0, 0},
    {0, 255, 0},
    {0, 0, 255},
    {128, 0, 128},
    {0, 128, 128},
    {128, 128, 0},
    {128, 0, 0},
    {0, 128, 0},
    {0, 0, 128}
  };

  private final List<Subject> subjects;
  private final int imageWidth;
  private final int imageHeight;
  private final boolean isRawSizeMaskEnabled;
  private final float scaleX;
  private final float scaleY;

  public SubjectSegmentationGraphic(
      GraphicOverlay overlay,
      SubjectSegmentationResult segmentationResult,
      int imageWidth,
      int imageHeight) {
    super(overlay);
    subjects = segmentationResult.getSubjects();
    this.imageWidth = imageWidth;
    this.imageHeight = imageHeight;

    isRawSizeMaskEnabled =
        imageWidth != overlay.getImageWidth() || imageHeight != overlay.getImageHeight();
    scaleX = overlay.getImageWidth() * 1f / imageWidth;
    scaleY = overlay.getImageHeight() * 1f / imageHeight;
  }

  /** Draws the segmented background on the supplied canvas. */
  @Override
  public void draw(Canvas canvas) {
    Bitmap bitmap =
        Bitmap.createBitmap(maskColorsFromFloatBuffer(), imageWidth, imageHeight, Config.ARGB_8888);
    if (isRawSizeMaskEnabled) {
      Matrix matrix = new Matrix(getTransformationMatrix());
      matrix.preScale(scaleX, scaleY);
      canvas.drawBitmap(bitmap, matrix, null);
    } else {
      canvas.drawBitmap(bitmap, getTransformationMatrix(), null);
    }

    bitmap.recycle();
  }

  /** Converts FloatBuffer floats from all subjects to ColorInt array that can be used as a mask. */
  @ColorInt
  private int[] maskColorsFromFloatBuffer() {
    @ColorInt int[] colors = new int[imageWidth * imageHeight];
    for (int k = 0; k < subjects.size(); k++) {
      Subject subject = subjects.get(k);
      int[] rgb = COLORS[k % COLORS.length];
      int color = Color.argb(128, rgb[0], rgb[1], rgb[2]);
      FloatBuffer mask = subject.getConfidenceMask();
      for (int j = 0; j < subject.getHeight(); j++) {
        for (int i = 0; i < subject.getWidth(); i++) {
          if (mask.get() > 0.5) {
            colors[(subject.getStartY() + j) * imageWidth + subject.getStartX() + i] = color;
          }
        }
      }
      // Reset FloatBuffer pointer to beginning, so that the mask can be redrawn if screen is
      // refreshed
      mask.rewind();
    }
    return colors;
  }
}

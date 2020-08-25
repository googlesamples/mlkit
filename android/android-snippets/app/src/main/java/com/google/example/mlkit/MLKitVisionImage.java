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

package com.google.example.mlkit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import android.util.SparseIntArray;
import android.view.Surface;

import com.google.mlkit.vision.common.InputImage;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.content.Context.CAMERA_SERVICE;

public class MLKitVisionImage {

    private static final String TAG = "MLKIT";
    private static final String MY_CAMERA_ID = "my_camera_id";

    private void imageFromBitmap(Bitmap bitmap) {
        int rotationDegree = 0;
        // [START image_from_bitmap]
        InputImage image = InputImage.fromBitmap(bitmap, rotationDegree);
        // [END image_from_bitmap]
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void imageFromMediaImage(Image mediaImage, int rotation) {
        // [START image_from_media_image]
        InputImage image = InputImage.fromMediaImage(mediaImage, rotation);
        // [END image_from_media_image]
    }

    private void imageFromBuffer(ByteBuffer byteBuffer, int rotationDegrees) {
        // [START set_metadata]
        // TODO How do we document the FrameMetadata developers need to implement?
        // [END set_metadata]

        // [START image_from_buffer]
        InputImage image = InputImage.fromByteBuffer(byteBuffer,
                /* image width */ 480,
                /* image height */ 360,
                rotationDegrees,
                InputImage.IMAGE_FORMAT_NV21 // or IMAGE_FORMAT_YV12
        );
        // [END image_from_buffer]
    }

    private void imageFromArray(byte[] byteArray, int rotation) {
        // [START image_from_array]
        InputImage image = InputImage.fromByteArray(
                byteArray,
                /* image width */480,
                /* image height */360,
                rotation,
                InputImage.IMAGE_FORMAT_NV21 // or IMAGE_FORMAT_YV12
        );
        // [END image_from_array]
    }

    private void imageFromPath(Context context, Uri uri) {
        // [START image_from_path]
        InputImage image;
        try {
            image = InputImage.fromFilePath(context, uri);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // [END image_from_path]
    }

    // [START get_rotation]
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    /**
     * Get the angle by which an image must be rotated given the device's current
     * orientation.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private int getRotationCompensation(String cameraId, Activity activity, boolean isFrontFacing)
            throws CameraAccessException {
        // Get the device's current rotation relative to its "native" orientation.
        // Then, from the ORIENTATIONS table, look up the angle the image must be
        // rotated to compensate for the device's rotation.
        int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int rotationCompensation = ORIENTATIONS.get(deviceRotation);

        // Get the device's sensor orientation.
        CameraManager cameraManager = (CameraManager) activity.getSystemService(CAMERA_SERVICE);
        int sensorOrientation = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION);

        if (isFrontFacing) {
            rotationCompensation = (sensorOrientation + rotationCompensation) % 360;
        } else { // back-facing
            rotationCompensation = (sensorOrientation - rotationCompensation + 360) % 360;
        }
        return rotationCompensation;
    }
    // [END get_rotation]

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void getCompensation(Activity activity, boolean isFrontFacing) throws CameraAccessException {
        // Get the ID of the camera using CameraManager. Then:
        int rotation = getRotationCompensation(MY_CAMERA_ID, activity, isFrontFacing);
    }

}

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

package com.google.example.mlkit.kotlin

import android.app.Activity
import android.content.Context
import android.content.Context.CAMERA_SERVICE
import android.graphics.Bitmap
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image
import android.net.Uri
import android.os.Build
import android.util.SparseIntArray
import android.view.Surface
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import java.io.IOException
import java.nio.ByteBuffer


class MLKitVisionImage {

    private fun imageFromBitmap(bitmap: Bitmap) {
        val rotationDegrees = 0
        // [START image_from_bitmap]
        val image = InputImage.fromBitmap(bitmap, 0)
        // [END image_from_bitmap]
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private fun imageFromMediaImage(mediaImage: Image, rotation: Int) {
        // [START image_from_media_image]
        val image = InputImage.fromMediaImage(mediaImage, rotation)
        // [END image_from_media_image]
    }

    private fun imageFromBuffer(byteBuffer: ByteBuffer, rotationDegrees: Int) {
        // [START set_metadata]
        // TODO How do we document the FrameMetadata developers need to implement?
        // [END set_metadata]
        // [START image_from_buffer]
        val image = InputImage.fromByteBuffer(
                byteBuffer,
                /* image width */ 480,
                /* image height */ 360,
                rotationDegrees,
                InputImage.IMAGE_FORMAT_NV21 // or IMAGE_FORMAT_YV12
        )
        // [END image_from_buffer]
    }

    private fun imageFromArray(byteArray: ByteArray, rotationDegrees: Int) {
        // [START image_from_array]
        val image = InputImage.fromByteArray(
                byteArray,
                /* image width */ 480,
                /* image height */ 360,
                rotationDegrees,
                InputImage.IMAGE_FORMAT_NV21 // or IMAGE_FORMAT_YV12
        )

        // [END image_from_array]
    }

    private fun imageFromPath(context: Context, uri: Uri) {
        // [START image_from_path]
        val image: InputImage
        try {
            image = InputImage.fromFilePath(context, uri)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        // [END image_from_path]
    }

    // [START get_rotation]
    /**
     * Get the angle by which an image must be rotated given the device's current
     * orientation.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Throws(CameraAccessException::class)
    private fun getRotationCompensation(cameraId: String, activity: Activity, isFrontFacing: Boolean): Int {
        // Get the device's current rotation relative to its "native" orientation.
        // Then, from the ORIENTATIONS table, look up the angle the image must be
        // rotated to compensate for the device's rotation.
        val deviceRotation = activity.windowManager.defaultDisplay.rotation
        var rotationCompensation = ORIENTATIONS.get(deviceRotation)

        // Get the device's sensor orientation.
        val cameraManager = activity.getSystemService(CAMERA_SERVICE) as CameraManager
        val sensorOrientation = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION)!!

        if (isFrontFacing) {
            rotationCompensation = (sensorOrientation + rotationCompensation) % 360
        } else { // back-facing
            rotationCompensation = (sensorOrientation - rotationCompensation + 360) % 360
        }
        return rotationCompensation
    }
    // [END get_rotation]

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Throws(CameraAccessException::class)
    private fun getCompensation(activity: Activity, context: Context, isFrontFacing: Boolean) {
        // Get the ID of the camera using CameraManager. Then:
        val rotation = getRotationCompensation(MY_CAMERA_ID, activity, isFrontFacing)
    }

    companion object {

        private val TAG = "MLKIT"
        private val MY_CAMERA_ID = "my_camera_id"

        // [START camera_orientations]
        private val ORIENTATIONS = SparseIntArray()

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 0)
            ORIENTATIONS.append(Surface.ROTATION_90, 90)
            ORIENTATIONS.append(Surface.ROTATION_180, 180)
            ORIENTATIONS.append(Surface.ROTATION_270, 270)
        }
        // [END camera_orientations]
    }
}

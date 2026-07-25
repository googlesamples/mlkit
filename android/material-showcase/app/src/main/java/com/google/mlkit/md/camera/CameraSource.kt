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

package com.google.mlkit.md.camera

import android.util.Size
import android.view.SurfaceHolder

abstract class CameraSource {

    /**
     * Returns an array of supported preview [Size] by the Camera
     */
    abstract fun getSupportedPreviewSizes(): Array<out Size>

    /**
     * Returns an array of supported picture [Size] by the Camera
     */
    abstract fun getSupportedPictureSizes(): Array<out Size>

    /**
     * Set the [FrameProcessor] instance which is use to process the frames return by the Camera
     */
    abstract fun setFrameProcessor(processor: FrameProcessor)

    /**
     * Set the [Boolean] status to turn ON or OFF the flash
     */
    abstract fun setFlashStatus(status: Boolean)

    /**
     * Returns the selected preview [Size] by the Camera
     */
    internal abstract fun getSelectedPreviewSize(): Size?

    /**
     * Opens the camera and starts sending preview frames to the underlying detector. The supplied
     * surface holder is used for the preview so frames can be displayed to the user.
     *
     * @param surfaceHolder the surface holder to use for the preview frames.
     * @throws Exception if the supplied surface holder could not be used as the preview display.
     */
    @Throws(Exception::class)
    internal abstract fun start(surfaceHolder: SurfaceHolder)

    /**
     * Closes the camera and stops sending frames to the underlying frame detector.
     *
     *
     * This camera source may be restarted again by calling [.start].
     *
     *
     * Call [.release] instead to completely shut down this camera source and release the
     * resources of the underlying detector.
     */
    @Throws(Exception::class)
    internal abstract fun stop()

    /**
     * Stops the camera and releases the resources of the camera and underlying detector.
     */
    abstract fun release()

}
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

import com.google.android.odml.image.MlImage
import java.nio.ByteBuffer

/** An interface to process the input camera frame and perform detection on it.  */
interface FrameProcessor {

    /** Processes the input frame with the underlying detector.  */
    @Deprecated("Keeping it only to support Camera API frame processing")
    fun process(data: ByteBuffer, frameMetadata: FrameMetadata, graphicOverlay: GraphicOverlay)

    /** Processes the input frame with the underlying detector.
     * @return true if holding [MlImage] for processing otherwise return false */
    fun process(image: MlImage, graphicOverlay: GraphicOverlay): Boolean

    /** Stops the underlying detector and release resources.  */
    fun stop()
}

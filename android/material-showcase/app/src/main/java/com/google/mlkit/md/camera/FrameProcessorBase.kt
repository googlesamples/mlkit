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

import android.os.SystemClock
import android.util.Log
import androidx.annotation.GuardedBy
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskExecutors
import com.google.mlkit.md.addOnFailureListener
import com.google.mlkit.md.addOnSuccessListener
import com.google.mlkit.md.CameraInputInfo
import com.google.mlkit.md.InputInfo
import com.google.mlkit.md.ScopedExecutor
import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer

/** Abstract base class of [FrameProcessor].  */
abstract class FrameProcessorBase<T> : FrameProcessor {

    // To keep the latest frame and its metadata.
    @GuardedBy("this")
    private var latestFrame: ByteBuffer? = null

    @GuardedBy("this")
    private var latestFrameMetaData: FrameMetadata? = null

    // To keep the frame and metadata in process.
    @GuardedBy("this")
    private var processingFrame: ByteBuffer? = null

    @GuardedBy("this")
    private var processingFrameMetaData: FrameMetadata? = null
    private val executor = ScopedExecutor(TaskExecutors.MAIN_THREAD)

    @Synchronized
    override fun process(
        data: ByteBuffer,
        frameMetadata: FrameMetadata,
        graphicOverlay: GraphicOverlay
    ) {
        latestFrame = data
        latestFrameMetaData = frameMetadata
        if (processingFrame == null && processingFrameMetaData == null) {
            processLatestFrame(graphicOverlay)
        }
    }

    @Synchronized
    private fun processLatestFrame(graphicOverlay: GraphicOverlay) {
        processingFrame = latestFrame
        processingFrameMetaData = latestFrameMetaData
        latestFrame = null
        latestFrameMetaData = null
        val frame = processingFrame ?: return
        val frameMetaData = processingFrameMetaData ?: return
        val image = InputImage.fromByteBuffer(
            frame,
            frameMetaData.width,
            frameMetaData.height,
            frameMetaData.rotation,
            InputImage.IMAGE_FORMAT_NV21
        )
        val startMs = SystemClock.elapsedRealtime()
        detectInImage(image)
            .addOnSuccessListener(executor) { results: T ->
                Log.d(TAG, "Latency is: ${SystemClock.elapsedRealtime() - startMs}")
                this@FrameProcessorBase.onSuccess(CameraInputInfo(frame, frameMetaData), results, graphicOverlay)
                processLatestFrame(graphicOverlay)
            }
            .addOnFailureListener(executor) { e -> OnFailureListener { this@FrameProcessorBase.onFailure(it) } }
    }

    override fun stop() {
        executor.shutdown()
    }

    protected abstract fun detectInImage(image: InputImage): Task<T>

    /** Be called when the detection succeeds.  */
    protected abstract fun onSuccess(
        inputInfo: InputInfo,
        results: T,
        graphicOverlay: GraphicOverlay
    )

    protected abstract fun onFailure(e: Exception)

    companion object {
        private const val TAG = "FrameProcessorBase"
    }
}

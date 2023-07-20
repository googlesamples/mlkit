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

import android.media.Image
import android.os.SystemClock
import android.util.Log
import androidx.annotation.GuardedBy
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskExecutors
import com.google.mlkit.md.*
import com.google.mlkit.vision.common.InputImage

/** Abstract base class of [FrameProcessor].  */
abstract class Frame2ProcessorBase<T> : Frame2Processor {

    // To keep the latest frame and its metadata.
    @GuardedBy("this")
    private var latestFrame: Image? = null

    @GuardedBy("this")
    private var latestFrameRotation: Int? = null

    // To keep the frame and metadata in process.
    @GuardedBy("this")
    private var processingFrame: Image? = null

    @GuardedBy("this")
    private var processingFrameRotation: Int? = null
    private val executor = ScopedExecutor(TaskExecutors.MAIN_THREAD)

    @Synchronized
    override fun process(image: Image, rotation: Int, graphicOverlay: GraphicOverlay) {
        latestFrame = image
        latestFrameRotation = rotation
        if (processingFrame == null && processingFrameRotation == null) {
            processLatestFrame(graphicOverlay)
        }
    }

    @Synchronized
    private fun processLatestFrame(graphicOverlay: GraphicOverlay) {
        processingFrame?.close()
        processingFrame = latestFrame
        processingFrameRotation = latestFrameRotation
        latestFrame = null
        latestFrameRotation = null
        val frame = processingFrame ?: return
        val frameRotation = processingFrameRotation ?: return
        val image = InputImage.fromMediaImage(frame, frameRotation)
        val startMs = SystemClock.elapsedRealtime()
        detectInImage(image)
            .addOnSuccessListener(executor) { results: T ->
                //Log.d(TAG, "Latency is: ${SystemClock.elapsedRealtime() - startMs}")
                this@Frame2ProcessorBase.onSuccess(Camera2InputInfo(frame, frameRotation), results, graphicOverlay)
                processLatestFrame(graphicOverlay)
            }
            .addOnFailureListener(executor) { e -> OnFailureListener {
                Log.d(TAG, "Detect In Image Failure: ${e.message}")
                this@Frame2ProcessorBase.onFailure(it) }
            }
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
